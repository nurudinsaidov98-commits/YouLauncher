package app.youlauncher.data.horizon

import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.writeTextAtomic
import app.youlauncher.data.download.Downloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Индекс реестра модов Horizon (docs/api/mod-index.md, схема 1): моды, ресурспаки и шейдеры, сделанные для Horizon. */
@Serializable
data class HorizonModIndex(
    val schema: Int = 1,
    val projects: List<HorizonModProject> = emptyList(),
    val revoked: List<HorizonRevoked> = emptyList(),
)

@Serializable
data class HorizonModProject(
    val id: String,
    val kind: String,
    val name: String,
    val summary: String = "",
    val description: String = "",
    val authors: List<String> = emptyList(),
    val license: String? = null,
    val source: String = "",
    val icon: String? = null,
    /** Открытый ключ minisign автора; с ним каждый файл проекта должен быть подписан. */
    val key: String? = null,
    val downloads: Long? = null,
    val versions: List<HorizonModVersion> = emptyList(),
) {
    /** Сделан самим проектом Horizon (автор Horizon, исходники в организации horizon-rust). */
    val official: Boolean get() = authors == listOf("Horizon") && source.startsWith("https://github.com/horizon-rust/")
}

@Serializable
data class HorizonModVersion(
    val version: String,
    val published: String = "",
    val changelog: String = "",
    val side: String? = null,
    val capabilities: List<String> = emptyList(),
    @SerialName("api_level") val apiLevel: Int? = null,
    val game: String,
    val file: HorizonModFile,
)

@Serializable
data class HorizonModFile(val name: String, val url: String, val size: Long, val sha256: String, val signature: String? = null)

/** Снятый файл (по sha256) или весь проект (без sha256). */
@Serializable
data class HorizonRevoked(val id: String, val sha256: String? = null, val reason: String = "")

/** Что установлено из реестра в экземпляр: файл, версия и хеш, по которому он проверен. */
@Serializable
data class HorizonInstalledMod(val id: String, val kind: String, val title: String, val version: String, val file: String, val sha256: String)

enum class HorizonModKind(val key: String) { Mod("mod"), ResourcePack("resourcepack"), Shader("shader") }

/** Правила реестра без сети и файлов: выбор версии, пути, снятые файлы (проверяются тестами). */
object HorizonModRules {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val SAFE_ID = Regex("^[a-z0-9_]{1,64}$")
    private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$")

    fun parse(text: String): HorizonModIndex = json.decodeFromString(HorizonModIndex.serializer(), text)

    fun kindOf(p: HorizonModProject): HorizonModKind? = HorizonModKind.entries.firstOrNull { it.key == p.kind }

    fun revoked(index: HorizonModIndex, id: String, sha256: String): HorizonRevoked? =
        index.revoked.firstOrNull { it.id == id && (it.sha256 == null || it.sha256.equals(sha256, ignoreCase = true)) }

    /** Новейшая версия для этой версии Minecraft, которую не сняли с реестра. */
    fun newest(index: HorizonModIndex, p: HorizonModProject, gameVersion: String): HorizonModVersion? =
        p.versions.filter { it.game == gameVersion }.sortedByDescending { it.published }.firstOrNull { revoked(index, p.id, it.file.sha256) == null }

    /** Куда кладётся файл в папке игры. null — опасный id или имя файла (с путями, «..», скрытое). */
    fun relativePath(kind: HorizonModKind, id: String, fileName: String): String? {
        if (!SAFE_ID.matches(id) || !SAFE_NAME.matches(fileName) || ".." in fileName) return null
        return when (kind) {
            HorizonModKind.Mod -> "$MODS_DIR/$id.hmod"
            HorizonModKind.ResourcePack -> "resourcepacks/$fileName"
            HorizonModKind.Shader -> "shaderpacks/$fileName"
        }
    }

    /** horizon-mods.txt: строки «mod.<id>:true»; остальные строки сохраняются как есть. */
    fun isEnabled(list: String, id: String): Boolean =
        list.lines().mapNotNull { it.split(':', limit = 2).takeIf { p -> p.size == 2 } }.any { (k, v) -> k.trim() == "mod.$id" && v.trim() == "true" }

    fun setEnabled(list: String, id: String, on: Boolean): String {
        val key = "mod.$id"
        val lines = list.lines().filter { it.isNotEmpty() && it.split(':', limit = 2).first().trim() != key }.toMutableList()
        if (on) lines += "$key:true"
        return if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
    }

    const val MODS_DIR = "horizon-mods"
    const val MODS_LIST = "horizon-mods.txt"
    const val INSTALLED_FILE = "youlauncher-horizon.json"
}

/**
 * Магазин модов Horizon: один индекс на GitHub Pages репозитория horizon-rust/horizon-mods, где каждую
 * публикацию проверяют ревьюеры. Файл проверяется по размеру и SHA-256 из индекса, подписанный — по ключу автора;
 * снятые с реестра файлы не ставятся, а уже установленные выключаются перед запуском.
 */
class HorizonModStore(
    private val http: HttpClient,
    /** Последний индекс на диске: на случай, когда GitHub недоступен. */
    private val cacheFile: Path,
    private val downloader: () -> Downloader,
) {
    private var loaded: Pair<Long, HorizonModIndex>? = null

    /** Индекс, скачанный за последние 10 минут, иначе свежий, иначе сохранённый в прошлый раз. */
    suspend fun index(): HorizonModIndex {
        loaded?.let { (at, idx) -> if (System.currentTimeMillis() - at < FRESH_MS) return idx }
        val idx = runCatching {
            val r = http.get(INDEX_URL)
            if (!r.status.isSuccess()) throw IOException("HTTP ${r.status.value}: $INDEX_URL")
            val text = r.bodyAsText()
            HorizonModRules.parse(text).also { withContext(Dispatchers.IO) { cacheFile.writeTextAtomic(text) } }
        }.getOrElse { e -> cached() ?: throw e }
        loaded = System.currentTimeMillis() to idx
        return idx
    }

    /** Индекс без сети: из памяти или с диска. */
    fun cached(): HorizonModIndex? = loaded?.second ?: runCatching { HorizonModRules.parse(cacheFile.readText()) }.getOrNull()

    fun installed(gameDir: Path): List<HorizonInstalledMod> = runCatching {
        HorizonModRules.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(HorizonInstalledMod.serializer()), gameDir.resolve(HorizonModRules.INSTALLED_FILE).readText())
    }.getOrDefault(emptyList()).filter { gameDir.resolve(it.file).exists() }

    fun isEnabled(gameDir: Path, id: String): Boolean = HorizonModRules.isEnabled(listText(gameDir), id)

    fun setEnabled(gameDir: Path, id: String, on: Boolean) {
        gameDir.resolve(HorizonModRules.MODS_LIST).writeTextAtomic(HorizonModRules.setEnabled(listText(gameDir), id, on))
    }

    /** Ставит новейшую версию проекта для [gameVersion]; мод сразу включается. */
    suspend fun install(gameDir: Path, project: HorizonModProject, gameVersion: String, log: (String) -> Unit = {}): HorizonInstalledMod {
        val index = index()
        val kind = HorizonModRules.kindOf(project) ?: throw IOException("Этот лаунчер пока не ставит «${project.kind}» из реестра Horizon")
        val v = HorizonModRules.newest(index, project, gameVersion)
            ?: throw IOException(HorizonModRules.revoked(index, project.id, project.versions.firstOrNull()?.file?.sha256.orEmpty())?.let { "«${project.name}» снят с реестра: ${it.reason}" }
                ?: "У «${project.name}» нет версии для Minecraft $gameVersion")
        val rel = HorizonModRules.relativePath(kind, project.id, v.file.name) ?: throw IOException("Недопустимое имя файла в реестре: ${v.file.name}")
        if (!v.file.url.startsWith("https://")) throw IOException("Файл не по https: ${v.file.url}")
        if (v.file.size > MAX_FILE) throw IOException("Файл больше 64 МБ")
        val staging = gameDir.resolve("mod-browser/downloads").resolve(v.file.name)
        log("${project.name} ${v.version}: скачивание")
        Files.deleteIfExists(staging)
        downloader().downloadAll(listOf(FileTask(v.file.url, staging, null, v.file.size)))
        withContext(Dispatchers.IO) {
            try {
                if (Files.size(staging) != v.file.size) throw IOException("Размер файла не совпадает с реестром")
                if (!HorizonInstaller.sha256(staging).equals(v.file.sha256, ignoreCase = true)) throw IOException("SHA-256 файла не совпадает с реестром")
                project.key?.let { key ->
                    val sig = v.file.signature ?: throw IOException("У «${project.name}» есть ключ автора, но файл не подписан")
                    if (!Minisign.verify(key, sig, Files.readAllBytes(staging))) throw IOException("Подпись автора не сходится: файл не установлен")
                }
            } catch (e: Exception) {
                Files.deleteIfExists(staging); throw e
            }
            val target = gameDir.resolve(rel)
            Files.createDirectories(target.parent)
            // Прежняя версия того же проекта: другой файл (у ресурспаков имя с версией) убирается.
            installed(gameDir).firstOrNull { it.id == project.id && it.file != rel }?.let { Files.deleteIfExists(gameDir.resolve(it.file)) }
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING)
        }
        if (kind == HorizonModKind.Mod) setEnabled(gameDir, project.id, true)
        val entry = HorizonInstalledMod(project.id, project.kind, project.name, v.version, rel, v.file.sha256.lowercase())
        save(gameDir, installed(gameDir).filter { it.id != project.id } + entry)
        log("${project.name} ${v.version} установлен")
        return entry
    }

    fun remove(gameDir: Path, id: String) {
        val list = installed(gameDir)
        list.firstOrNull { it.id == id }?.let { Files.deleteIfExists(gameDir.resolve(it.file)) }
        setEnabled(gameDir, id, false)
        save(gameDir, list.filter { it.id != id })
    }

    /** Перед запуском: снятые с реестра моды выключаются (по сохранённому индексу, без ожидания сети). */
    fun turnOffRevoked(gameDir: Path): List<String> {
        val index = cached() ?: return emptyList()
        return installed(gameDir).mapNotNull { m ->
            val r = HorizonModRules.revoked(index, m.id, m.sha256) ?: return@mapNotNull null
            if (m.kind == HorizonModKind.Mod.key && isEnabled(gameDir, m.id)) setEnabled(gameDir, m.id, false)
            "«${m.title}» снят с реестра Horizon и выключен: ${r.reason}"
        }
    }

    private fun listText(gameDir: Path) = runCatching { gameDir.resolve(HorizonModRules.MODS_LIST).readText() }.getOrDefault("")

    private fun save(gameDir: Path, list: List<HorizonInstalledMod>) =
        gameDir.resolve(HorizonModRules.INSTALLED_FILE).writeTextAtomic(
            HorizonModRules.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(HorizonInstalledMod.serializer()), list),
        )

    companion object {
        const val INDEX_URL = "https://horizon-rust.github.io/horizon-mods/index.json"
        const val FRESH_MS = 10 * 60_000L
        const val MAX_FILE = 64L shl 20
    }
}

/**
 * Проверка подписей minisign (Ed25519): обычной («Ed», подписан сам файл) и с предварительным хешем
 * («ED», подписан BLAKE2b-512 файла), плюс подпись доверенного комментария.
 */
object Minisign {
    fun verify(publicKey: String, signature: String, data: ByteArray): Boolean = runCatching {
        val keyBytes = java.util.Base64.getDecoder().decode(publicKey.lines().map { it.trim() }.last { it.isNotEmpty() && !it.startsWith("untrusted comment:") })
        require(keyBytes.size == 42 && keyBytes[0] == 'E'.code.toByte() && keyBytes[1] == 'd'.code.toByte())
        val lines = signature.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val sigLine = lines.first { !it.startsWith("untrusted comment:") && !it.startsWith("trusted comment:") }
        val trusted = lines.first { it.startsWith("trusted comment:") }.removePrefix("trusted comment:").trimStart()
        val global = java.util.Base64.getDecoder().decode(lines.last())
        val sig = java.util.Base64.getDecoder().decode(sigLine)
        require(sig.size == 74)
        // Ключ подписи должен быть ключом автора.
        require(sig.copyOfRange(2, 10).contentEquals(keyBytes.copyOfRange(2, 10)))
        val alg = String(sig, 0, 2, Charsets.US_ASCII)
        val message = when (alg) {
            "Ed" -> data
            "ED" -> Blake2b.hash512(data)
            else -> return false
        }
        val pk = ed25519Key(keyBytes.copyOfRange(10, 42))
        val raw = sig.copyOfRange(10, 74)
        ed25519(pk, message, raw) && ed25519(pk, raw + trusted.toByteArray(Charsets.UTF_8), global)
    }.getOrDefault(false)

    private fun ed25519Key(raw: ByteArray): java.security.PublicKey {
        val prefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
        return java.security.KeyFactory.getInstance("Ed25519").generatePublic(java.security.spec.X509EncodedKeySpec(prefix + raw))
    }

    private fun ed25519(key: java.security.PublicKey, message: ByteArray, signature: ByteArray): Boolean =
        java.security.Signature.getInstance("Ed25519").run { initVerify(key); update(message); verify(signature) }
}

/** BLAKE2b-512 (RFC 7693) для подписей minisign с предварительным хешем: в JDK его нет. */
object Blake2b {
    private val IV = longArrayOf(
        0x6a09e667f3bcc908UL.toLong(), 0xbb67ae8584caa73bUL.toLong(), 0x3c6ef372fe94f82bUL.toLong(), 0xa54ff53a5f1d36f1UL.toLong(),
        0x510e527fade682d1UL.toLong(), 0x9b05688c2b3e6c1fUL.toLong(), 0x1f83d9abfb41bd6bUL.toLong(), 0x5be0cd19137e2179UL.toLong(),
    )
    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
    )

    fun hash512(data: ByteArray): ByteArray {
        val h = IV.copyOf()
        h[0] = h[0] xor 0x01010040L
        var t = 0L
        var offset = 0
        // Последний блок (даже пустой) сжимается с флагом конца.
        while (data.size - offset > 128) {
            t += 128
            compress(h, data, offset, t, last = false)
            offset += 128
        }
        val tail = ByteArray(128)
        System.arraycopy(data, offset, tail, 0, data.size - offset)
        t += (data.size - offset)
        compress(h, tail, 0, t, last = true)
        val out = ByteArray(64)
        for (i in 0 until 64) out[i] = (h[i / 8] ushr (8 * (i % 8))).toByte()
        return out
    }

    private fun compress(h: LongArray, block: ByteArray, off: Int, t: Long, last: Boolean) {
        val m = LongArray(16) { i -> var w = 0L; for (b in 7 downTo 0) w = (w shl 8) or (block[off + i * 8 + b].toLong() and 0xFF); w }
        val v = LongArray(16)
        System.arraycopy(h, 0, v, 0, 8)
        System.arraycopy(IV, 0, v, 8, 8)
        v[12] = v[12] xor t
        if (last) v[14] = v[14].inv()
        fun g(a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
            v[a] = v[a] + v[b] + x; v[d] = (v[d] xor v[a]).rotateRight(32)
            v[c] = v[c] + v[d]; v[b] = (v[b] xor v[c]).rotateRight(24)
            v[a] = v[a] + v[b] + y; v[d] = (v[d] xor v[a]).rotateRight(16)
            v[c] = v[c] + v[d]; v[b] = (v[b] xor v[c]).rotateRight(63)
        }
        for (r in 0 until 12) {
            val s = SIGMA[r % 10]
            g(0, 4, 8, 12, m[s[0]], m[s[1]]); g(1, 5, 9, 13, m[s[2]], m[s[3]])
            g(2, 6, 10, 14, m[s[4]], m[s[5]]); g(3, 7, 11, 15, m[s[6]], m[s[7]])
            g(0, 5, 10, 15, m[s[8]], m[s[9]]); g(1, 6, 11, 12, m[s[10]], m[s[11]])
            g(2, 7, 8, 13, m[s[12]], m[s[13]]); g(3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0 until 8) h[i] = h[i] xor v[i] xor v[i + 8]
    }
}
