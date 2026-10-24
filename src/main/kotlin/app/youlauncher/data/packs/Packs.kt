package app.youlauncher.data.packs

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.mods.ContentIndex
import app.youlauncher.data.mods.CurseForgeCatalog
import app.youlauncher.data.mods.IndexedFile
import app.youlauncher.data.mods.contentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

enum class PackFormat { Modrinth, CurseForge, MultiMC }

/** Общее описание сборки после разбора любого формата. */
data class PackManifest(
    val format: PackFormat,
    val name: String,
    val gameVersion: String,
    val loader: LoaderType,
    val loaderVersion: String?,
    val files: List<PackFile> = emptyList(),
    val curseFiles: List<CurseFileRef> = emptyList(),
    /** Папки внутри архива, содержимое которых копируется в .minecraft. */
    val overrides: List<String> = emptyList(),
)

data class PackFile(val path: String, val urls: List<String>, val sha1: String?, val size: Long)
data class CurseFileRef(val projectId: Long, val fileId: Long, val required: Boolean)

private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

/** Чистый разбор манифестов (проверяется тестами). */
object PackParsing {
    /** Безопасный относительный путь внутри .minecraft: без абсолютных путей и выхода наверх. */
    fun safePath(path: String): String? {
        val p = path.replace('\\', '/').trimStart('/')
        if (p.isBlank() || p.contains(':') || p.split('/').any { it == ".." }) return null
        return p
    }

    fun mrpack(json: String): PackManifest {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        if (o.s("game") != null && o.s("game") != "minecraft") throw IOException("Сборка не для Minecraft")
        val deps = o["dependencies"]?.jsonObject ?: JsonObject(emptyMap())
        val game = deps.s("minecraft") ?: throw IOException("В modrinth.index.json не указана версия игры")
        val (loader, lv) = when {
            deps.s("fabric-loader") != null -> LoaderType.Fabric to deps.s("fabric-loader")
            deps.s("quilt-loader") != null -> LoaderType.Quilt to deps.s("quilt-loader")
            deps.s("neoforge") != null -> LoaderType.NeoForge to deps.s("neoforge")
            deps.s("forge") != null -> LoaderType.Forge to deps.s("forge")
            else -> LoaderType.Vanilla to null
        }
        val files = (o["files"] as? JsonArray).orEmpty().mapNotNull { e ->
            val f = e as? JsonObject ?: return@mapNotNull null
            if ((f["env"] as? JsonObject)?.s("client") == "unsupported") return@mapNotNull null
            val path = safePath(f.s("path").orEmpty()) ?: throw IOException("Недопустимый путь в сборке: ${f.s("path")}")
            PackFile(
                path = path,
                urls = (f["downloads"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                sha1 = (f["hashes"] as? JsonObject)?.s("sha1"),
                size = (f["fileSize"] as? JsonPrimitive)?.longOrNull ?: 0,
            )
        }
        return PackManifest(PackFormat.Modrinth, o.s("name") ?: "Modrinth", game, loader, lv, files = files, overrides = listOf("overrides", "client-overrides"))
    }

    /** manifest.json сборок CurseForge: modLoaders[].id вида forge-47.1.0, neoforge-21.1.1, fabric-0.16.0. */
    fun curseForge(json: String): PackManifest {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        val mc = o["minecraft"]?.jsonObject ?: throw IOException("Это не сборка CurseForge")
        val loaders = (mc["modLoaders"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val primary = (loaders.firstOrNull { (it["primary"] as? JsonPrimitive)?.contentOrNull == "true" } ?: loaders.firstOrNull())?.s("id")
        val (loader, lv) = parseLoaderId(primary)
        val files = (o["files"] as? JsonArray).orEmpty().mapNotNull { e ->
            val f = e as? JsonObject ?: return@mapNotNull null
            CurseFileRef(
                (f["projectID"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                (f["fileID"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                (f["required"] as? JsonPrimitive)?.contentOrNull != "false",
            )
        }
        return PackManifest(
            PackFormat.CurseForge, o.s("name") ?: "CurseForge", mc.s("version") ?: throw IOException("Нет версии игры"),
            loader, lv, curseFiles = files, overrides = listOf(o.s("overrides") ?: "overrides"),
        )
    }

    fun parseLoaderId(id: String?): Pair<LoaderType, String?> {
        if (id == null) return LoaderType.Vanilla to null
        val name = id.substringBefore('-')
        val version = id.substringAfter('-', "").ifBlank { null }
        return when (name.lowercase()) {
            "forge" -> LoaderType.Forge to version
            "neoforge" -> LoaderType.NeoForge to version
            "fabric" -> LoaderType.Fabric to version
            "quilt" -> LoaderType.Quilt to version
            else -> LoaderType.Vanilla to null
        }
    }

    /** MultiMC и Prism: mmc-pack.json (компоненты) и instance.cfg (имя). */
    fun multiMc(packJson: String, instanceCfg: String?, gameFolder: String): PackManifest {
        val comps = (YouLauncherJson.parseToJsonElement(packJson).jsonObject["components"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun v(uid: String) = comps.firstOrNull { it.s("uid") == uid }?.let { it.s("version") ?: it.s("cachedVersion") }
        val game = v("net.minecraft") ?: throw IOException("В mmc-pack.json нет версии игры")
        val (loader, lv) = when {
            v("net.fabricmc.fabric-loader") != null -> LoaderType.Fabric to v("net.fabricmc.fabric-loader")
            v("org.quiltmc.quilt-loader") != null -> LoaderType.Quilt to v("org.quiltmc.quilt-loader")
            v("net.neoforged") != null -> LoaderType.NeoForge to v("net.neoforged")
            v("net.minecraftforge") != null -> LoaderType.Forge to v("net.minecraftforge")
            else -> LoaderType.Vanilla to null
        }
        val name = instanceCfg?.lines()?.firstOrNull { it.startsWith("name=") }?.substringAfter('=')?.trim()
        return PackManifest(PackFormat.MultiMC, name ?: "MultiMC", game, loader, lv, overrides = listOf(gameFolder))
    }

    /** Определяет формат архива по составу файлов. */
    fun detect(names: Collection<String>): Pair<PackFormat, String>? {
        names.firstOrNull { it == "modrinth.index.json" }?.let { return PackFormat.Modrinth to it }
        names.firstOrNull { it.endsWith("mmc-pack.json") && it.count { c -> c == '/' } <= 1 }?.let { return PackFormat.MultiMC to it }
        names.firstOrNull { it == "manifest.json" }?.let { return PackFormat.CurseForge to it }
        return null
    }

    /** Версия Forge в манифестах бывает полной (1.20.1-47.1.0) или короткой (47.1.0). */
    fun forgeShort(game: String, v: String?): String? = v?.removePrefix("$game-")
}

/**
 * Импорт сборок в новый экземпляр. Файлы модов скачиваются только с разрешённых хостов и проверяются по SHA-1,
 * пути из манифестов проверяются на выход за пределы .minecraft.
 */
class PackImporter(
    private val instances: InstanceRepository,
    private val content: InstanceContentRepository,
    private val index: ContentIndex,
    private val curseForge: () -> CurseForgeCatalog?,
    private val downloaderFactory: () -> Downloader,
) {
    suspend fun import(archive: Path, nameOverride: String? = null, onStage: (String) -> Unit = {}): Instance {
        val (manifest, zipNames) = withContext(Dispatchers.IO) {
            ZipFile(archive.toFile()).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                val (format, entry) = PackParsing.detect(names) ?: throw IOException("Неизвестный формат: нет modrinth.index.json, manifest.json или mmc-pack.json")
                fun text(n: String) = zip.getInputStream(zip.getEntry(n)).use { it.readBytes().decodeToString() }
                val m = when (format) {
                    PackFormat.Modrinth -> PackParsing.mrpack(text(entry))
                    PackFormat.CurseForge -> PackParsing.curseForge(text(entry))
                    PackFormat.MultiMC -> {
                        val base = entry.substringBeforeLast("mmc-pack.json")
                        val cfg = zip.getEntry(base + "instance.cfg")?.let { text(it.name) }
                        val folder = listOf(".minecraft", "minecraft").map { base + it }.firstOrNull { f -> names.any { it.startsWith("$f/") } } ?: (base + ".minecraft")
                        PackParsing.multiMc(text(entry), cfg, folder)
                    }
                }
                m to names
            }
        }
        val lv = if (manifest.loader == LoaderType.Forge) PackParsing.forgeShort(manifest.gameVersion, manifest.loaderVersion) else manifest.loaderVersion
        val instance = instances.create(NewInstanceSpec(nameOverride ?: manifest.name, manifest.gameVersion, manifest.loader, lv))
        val game = instances.gameDir(instance.id)
        try {
            onStage("files")
            when (manifest.format) {
                PackFormat.Modrinth -> downloadModrinth(manifest, instance.id, game)
                PackFormat.CurseForge -> downloadCurse(manifest, instance.id, game)
                PackFormat.MultiMC -> Unit
            }
            onStage("overrides")
            withContext(Dispatchers.IO) { copyOverrides(archive, manifest.overrides, zipNames, game) }
        } catch (e: Throwable) {
            instances.delete(instance.id)
            throw e
        }
        content.refresh(instance.id)
        return instance
    }

    private suspend fun downloadModrinth(m: PackManifest, id: String, game: Path) {
        val tasks = m.files.map { f ->
            val url = f.urls.firstOrNull() ?: throw IOException("Нет адреса для ${f.path}")
            FileTask(url, game.resolve(f.path), f.sha1, f.size)
        }
        downloaderFactory().downloadAll(tasks)
        for (f in m.files) {
            val kind = ContentKind.entries.firstOrNull { f.path.startsWith(it.folder + "/") } ?: continue
            val url = f.urls.first()
            // Адрес CDN Modrinth: https://cdn.modrinth.com/data/<project>/versions/<version>/<file>.
            val parts = Regex("cdn\\.modrinth\\.com/data/([^/]+)/versions/([^/]+)/").find(url)?.groupValues ?: continue
            index.put(id, IndexedFile(kind, f.path.substringAfterLast('/'), ContentSource.Modrinth, parts[1], parts[2], sha1 = f.sha1, url = url))
        }
    }

    private suspend fun downloadCurse(m: PackManifest, id: String, game: Path) {
        val cf = curseForge() ?: throw IOException("Для сборок CurseForge нужен ключ API в переменной окружения ${CurseForgeCatalog.KEY_ENV}")
        val refs = m.curseFiles.filter { it.required }
        val files = cf.filesByIds(refs.map { it.fileId })
        val types = cf.projectTypes(refs.map { it.projectId })
        val blocked = files.filter { it.files.first().url.isBlank() }
        if (blocked.isNotEmpty()) throw IOException("Авторы ${blocked.size} файлов запретили загрузку из сторонних лаунчеров: ${blocked.take(5).joinToString { it.files.first().fileName }}")
        val planned = files.map { v ->
            val kind = types[v.projectId]?.contentKind() ?: ContentKind.Mods
            Triple(v, kind, v.files.first())
        }
        downloaderFactory().downloadAll(planned.map { (_, kind, f) -> FileTask(f.url, game.resolve(kind.folder).resolve(f.fileName), f.sha1, f.size) })
        planned.forEach { (v, kind, f) -> index.put(id, IndexedFile(kind, f.fileName, ContentSource.CurseForge, v.projectId, v.id, v.versionNumber, sha1 = f.sha1, url = f.url)) }
    }

    private fun copyOverrides(archive: Path, folders: List<String>, names: List<String>, game: Path) {
        ZipFile(archive.toFile()).use { zip ->
            for (folder in folders) {
                val prefix = "$folder/"
                for (n in names) {
                    if (!n.startsWith(prefix) || n.endsWith("/")) continue
                    val rel = PackParsing.safePath(n.removePrefix(prefix)) ?: continue
                    val out = game.resolve(rel).normalize()
                    if (!out.startsWith(game)) continue
                    Files.createDirectories(out.parent)
                    zip.getInputStream(zip.getEntry(n)).use { Files.copy(it, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }
}

/** Экспорт экземпляра: .mrpack (моды из Modrinth ссылками, остальное в overrides) и архив Prism/MultiMC. */
class PackExporter(
    private val instances: InstanceRepository,
    private val index: ContentIndex,
) {
    /** Что попадает в overrides: настройки и конфиги, без миров, журналов и кэшей. */
    private val overrideRoots = listOf("config", "defaultconfigs", "kubejs", "scripts", "options.txt", "servers.dat") + ContentKind.entries.map { it.folder }

    suspend fun exportMrpack(instanceId: String, target: Path, version: String = "1.0.0"): Path = withContext(Dispatchers.IO) {
        val inst = instances.get(instanceId) ?: throw IOException("Экземпляр не найден")
        val game = instances.gameDir(instanceId)
        val linked = index.read(instanceId).filter { it.source == ContentSource.Modrinth && it.url != null && it.sha1 != null }
            .filter { game.resolve(it.kind.folder).resolve(it.fileName).exists() }
        val linkedPaths = linked.map { "${it.kind.folder}/${it.fileName}" }.toSet()
        val manifest = buildJsonObject {
            put("formatVersion", 1)
            put("game", "minecraft")
            put("versionId", version)
            put("name", inst.name)
            putJsonArray("files") {
                linked.forEach { e ->
                    val file = game.resolve(e.kind.folder).resolve(e.fileName)
                    add(buildJsonObject {
                        put("path", "${e.kind.folder}/${e.fileName}")
                        putJsonObject("hashes") { put("sha1", e.sha1); put("sha512", sha512(file)) }
                        putJsonObject("env") { put("client", "required"); put("server", if (e.kind == ContentKind.Mods) "required" else "unsupported") }
                        put("downloads", buildJsonArray { add(JsonPrimitive(e.url)) })
                        put("fileSize", Files.size(file))
                    })
                }
            }
            putJsonObject("dependencies") {
                put("minecraft", inst.gameVersion)
                val lv = inst.loaderVersion
                if (lv != null) when (inst.loader) {
                    LoaderType.Fabric -> put("fabric-loader", lv)
                    LoaderType.Quilt -> put("quilt-loader", lv)
                    LoaderType.Forge -> put("forge", lv)
                    LoaderType.NeoForge -> put("neoforge", lv)
                    LoaderType.Vanilla -> Unit
                }
            }
        }
        Files.createDirectories(target.toAbsolutePath().parent)
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("modrinth.index.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            addTree(zip, game, "overrides/") { rel -> rel !in linkedPaths && !rel.endsWith(".disabled") && overrideRoots.any { rel == it || rel.startsWith("$it/") } }
        }
        target
    }

    suspend fun exportPrism(instanceId: String, target: Path): Path = withContext(Dispatchers.IO) {
        val inst = instances.get(instanceId) ?: throw IOException("Экземпляр не найден")
        val game = instances.gameDir(instanceId)
        val components = buildJsonArray {
            add(buildJsonObject { put("uid", "net.minecraft"); put("version", inst.gameVersion); put("important", true) })
            val uid = when (inst.loader) {
                LoaderType.Fabric -> "net.fabricmc.fabric-loader"
                LoaderType.Quilt -> "org.quiltmc.quilt-loader"
                LoaderType.Forge -> "net.minecraftforge"
                LoaderType.NeoForge -> "net.neoforged"
                LoaderType.Vanilla -> null
            }
            if (uid != null && inst.loaderVersion != null) add(buildJsonObject { put("uid", uid); put("version", inst.loaderVersion) })
        }
        val pack = buildJsonObject { put("formatVersion", 1); put("components", components) }
        val cfg = "InstanceType=OneSix\nname=${inst.name}\n" +
            (inst.java.maxMemoryMb?.let { "OverrideMemory=true\nMaxMemAlloc=$it\n" } ?: "")
        Files.createDirectories(target.toAbsolutePath().parent)
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("mmc-pack.json")); zip.write(pack.toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("instance.cfg")); zip.write(cfg.toByteArray()); zip.closeEntry()
            addTree(zip, game, ".minecraft/") { rel -> !rel.startsWith("logs/") && !rel.startsWith("crash-reports/") && !rel.endsWith("session.lock") }
        }
        target
    }

    private fun addTree(zip: ZipOutputStream, root: Path, prefix: String, include: (String) -> Boolean) {
        if (!root.isDirectory()) return
        Files.walk(root).use { s ->
            s.filter { it.isRegularFile() }.forEach { f ->
                val rel = root.relativize(f).toString().replace('\\', '/')
                if (!include(rel)) return@forEach
                zip.putNextEntry(ZipEntry(prefix + rel))
                Files.copy(f, zip)
                zip.closeEntry()
            }
        }
    }

    private fun sha512(file: Path): String {
        val md = MessageDigest.getInstance("SHA-512")
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
