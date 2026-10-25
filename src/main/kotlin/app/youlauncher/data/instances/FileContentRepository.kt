package app.youlauncher.data.instances

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.model.Screenshot
import app.youlauncher.core.model.World
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.data.mods.ContentIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Метаданные мода из jar: Fabric, Quilt, Forge/NeoForge (mods.toml) и старый mcmod.info. */
object ModMetadata {
    data class Info(val name: String?, val version: String?)

    fun read(jar: Path): Info = runCatching {
        ZipFile(jar.toFile()).use { zip ->
            fun text(name: String) = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }
            text("fabric.mod.json")?.let { j ->
                val o = YouLauncherJson.parseToJsonElement(j).jsonObject
                return Info(o["name"]?.jsonPrimitive?.content, o["version"]?.jsonPrimitive?.content)
            }
            text("quilt.mod.json")?.let { j ->
                val loader = YouLauncherJson.parseToJsonElement(j).jsonObject["quilt_loader"]?.jsonObject
                return Info(loader?.get("metadata")?.jsonObject?.get("name")?.jsonPrimitive?.content, loader?.get("version")?.jsonPrimitive?.content)
            }
            (text("META-INF/neoforge.mods.toml") ?: text("META-INF/mods.toml"))?.let { toml ->
                fun field(key: String) = Regex("""(?m)^\s*$key\s*=\s*["']([^"']*)["']""").find(toml)?.groupValues?.get(1)
                val version = field("version")?.takeIf { !it.startsWith("\${") }
                return Info(field("displayName"), version)
            }
            text("mcmod.info")?.let { j ->
                val el = YouLauncherJson.parseToJsonElement(j)
                val first = (el as? JsonArray)?.firstOrNull()?.jsonObject ?: (el as? JsonObject)?.get("modList")?.let { (it as? JsonArray)?.firstOrNull()?.jsonObject }
                return Info(first?.get("name")?.jsonPrimitive?.content, first?.get("version")?.jsonPrimitive?.content)
            }
            Info(null, null)
        }
    }.getOrDefault(Info(null, null))
}

/** Минимальное чтение NBT (level.dat): имя мира, режим, хардкор, время последней игры. */
object LevelDat {
    data class Info(val name: String?, val gameType: Int?, val hardcore: Boolean, val lastPlayed: Long?)

    fun read(file: Path): Info? = runCatching {
        DataInputStream(GZIPInputStream(Files.newInputStream(file)).buffered()).use { input ->
            val values = mutableMapOf<String, Any>()
            fun skipOrRead(type: Int, name: String, depth: Int) {
                when (type) {
                    1 -> { val v = input.readByte(); values[name] = v.toInt() }
                    2 -> input.readShort()
                    3 -> { val v = input.readInt(); values[name] = v }
                    4 -> { val v = input.readLong(); values[name] = v }
                    5 -> input.readFloat()
                    6 -> input.readDouble()
                    7 -> input.skipNBytes(input.readInt().toLong())
                    8 -> { val v = input.readUTF(); values[name] = v }
                    9 -> { val t = input.readByte().toInt(); repeat(input.readInt()) { skipOrRead(t, "", depth + 1) } }
                    10 -> while (true) {
                        val t = input.readByte().toInt()
                        if (t == 0) break
                        val n = input.readUTF()
                        skipOrRead(t, if (depth <= 1) n else "", depth + 1)
                    }
                    11 -> input.skipNBytes(input.readInt() * 4L)
                    12 -> input.skipNBytes(input.readInt() * 8L)
                    else -> error("Неизвестный тег NBT $type")
                }
            }
            val root = input.readByte().toInt()
            input.readUTF()
            skipOrRead(root, "", 0)
            Info(values["LevelName"] as? String, values["GameType"] as? Int, (values["hardcore"] as? Int ?: 0) != 0, values["LastPlayed"] as? Long)
        }
    }.getOrNull()
}

/**
 * Содержимое экземпляра на диске: моды, ресурспаки, шейдеры, датапаки (выключенные файлы с суффиксом
 * .disabled), миры (saves) и скриншоты. Изменения видны после refresh().
 */
class FileContentRepository(
    private val gameDir: (String) -> Path,
    private val backupsDir: Path,
    private val index: ContentIndex? = null,
) : InstanceContentRepository {
    private val tick = MutableStateFlow<Map<String, Int>>(emptyMap())
    private fun bump(id: String) = tick.update { it + (id to ((it[id] ?: 0) + 1)) }
    private fun folder(id: String, kind: ContentKind) = gameDir(id).resolve(kind.folder)

    private val extensions = mapOf(
        ContentKind.Mods to setOf("jar"),
        ContentKind.ResourcePacks to setOf("zip"),
        ContentKind.ShaderPacks to setOf("zip"),
        ContentKind.DataPacks to setOf("zip"),
    )

    fun scanContent(id: String, kind: ContentKind): List<ContentFile> {
        val dir = folder(id, kind)
        if (!dir.isDirectory()) return emptyList()
        val exts = extensions.getValue(kind)
        val indexed = index?.read(id).orEmpty().filter { it.kind == kind }.associateBy { it.fileName }
        return dir.listDirectoryEntries().filter { it.isRegularFile() }.mapNotNull { p ->
            val enabled = !p.name.endsWith(".disabled")
            val baseName = p.name.removeSuffix(".disabled")
            if (baseName.substringAfterLast('.').lowercase() !in exts) return@mapNotNull null
            val meta = if (kind == ContentKind.Mods) ModMetadata.read(p) else ModMetadata.Info(null, null)
            val entry = indexed[baseName]
            ContentFile(
                fileName = p.name,
                title = meta.name?.takeIf { it.isNotBlank() } ?: entry?.title?.takeIf { it.isNotBlank() } ?: baseName.substringBeforeLast('.'),
                version = meta.version ?: entry?.versionNumber,
                enabled = enabled,
                sizeBytes = p.fileSize(),
                sha1 = entry?.sha1,
                projectId = entry?.projectId,
                source = entry?.source?.name?.lowercase() ?: "local",
                updateAvailable = entry?.update,
                addedAt = runCatching { java.nio.file.Files.getLastModifiedTime(p).toMillis() }.getOrDefault(0),
            )
        }.sortedBy { it.title.lowercase() }
    }

    override fun content(instanceId: String, kind: ContentKind): Flow<List<ContentFile>> =
        tick.map { it[instanceId] ?: 0 }.map { scanContent(instanceId, kind) }.flowOn(Dispatchers.IO)

    fun scanWorlds(id: String): List<World> {
        val saves = gameDir(id).resolve("saves")
        if (!saves.isDirectory()) return emptyList()
        return saves.listDirectoryEntries().filter { it.isDirectory() && it.resolve("level.dat").exists() }.map { dir ->
            val info = LevelDat.read(dir.resolve("level.dat"))
            val mode = when {
                info?.hardcore == true -> "hardcore"
                info?.gameType == 1 -> "creative"
                info?.gameType == 2 -> "adventure"
                info?.gameType == 3 -> "spectator"
                else -> "survival"
            }
            val size = runCatching { Files.walk(dir).use { s -> s.filter { it.isRegularFile() }.mapToLong { it.fileSize() }.sum() } }.getOrDefault(0)
            World(dir.name, info?.name ?: dir.name, info?.lastPlayed ?: dir.getLastModifiedTime().toMillis(), size, mode)
        }.sortedByDescending { it.lastPlayed }
    }

    override fun worlds(instanceId: String): Flow<List<World>> =
        tick.map { it[instanceId] ?: 0 }.map { scanWorlds(instanceId) }.flowOn(Dispatchers.IO)

    override fun screenshots(instanceId: String): Flow<List<Screenshot>> = tick.map { it[instanceId] ?: 0 }.map {
        val dir = gameDir(instanceId).resolve("screenshots")
        if (!dir.isDirectory()) emptyList() else dir.listDirectoryEntries("*.png").map { p ->
            val (w, h) = pngSize(p)
            Screenshot(p.toString(), p.getLastModifiedTime().toMillis(), w, h)
        }.sortedByDescending { it.takenAt }
    }.flowOn(Dispatchers.IO)

    private fun pngSize(p: Path): Pair<Int, Int> = runCatching {
        DataInputStream(Files.newInputStream(p)).use { d ->
            d.skipNBytes(16)
            d.readInt() to d.readInt()
        }
    }.getOrDefault(0 to 0)

    override suspend fun setEnabled(instanceId: String, kind: ContentKind, fileName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val file = folder(instanceId, kind).resolve(fileName)
        if (!file.exists()) return@withContext
        val target = if (enabled) fileName.removeSuffix(".disabled") else if (fileName.endsWith(".disabled")) fileName else "$fileName.disabled"
        if (target != fileName) Files.move(file, file.resolveSibling(target), StandardCopyOption.REPLACE_EXISTING)
        bump(instanceId)
    }

    override suspend fun remove(instanceId: String, kind: ContentKind, fileName: String) = withContext(Dispatchers.IO) {
        trash(folder(instanceId, kind).resolve(fileName))
        index?.removeFile(instanceId, kind, fileName)
        bump(instanceId)
    }

    /** Отложенные файлы: вне папок игры, чтобы загрузчик модов их не подхватил. Запись индекса хранится до возврата. */
    private fun stashDir(id: String, kind: ContentKind) = gameDir(id).resolve(STASH_DIR).resolve(kind.folder)
    private val stashed = java.util.concurrent.ConcurrentHashMap<String, app.youlauncher.data.mods.IndexedFile>()

    override suspend fun stash(instanceId: String, kind: ContentKind, fileName: String): Boolean = withContext(Dispatchers.IO) {
        purgeStash(instanceId)
        val source = folder(instanceId, kind).resolve(fileName)
        if (!source.isRegularFile()) return@withContext false
        // Папка с временем отложения в имени: по нему потом видно, что пора в корзину.
        val dir = stashDir(instanceId, kind).resolve(System.currentTimeMillis().toString())
        Files.createDirectories(dir)
        Files.move(source, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
        val base = fileName.removeSuffix(".disabled")
        index?.find(instanceId, kind, base)?.let { stashed["$instanceId/${kind.name}/$fileName"] = it }
        index?.removeFile(instanceId, kind, base)
        bump(instanceId)
        true
    }

    override suspend fun restore(instanceId: String, kind: ContentKind, fileName: String) = withContext(Dispatchers.IO) {
        val held = stashDir(instanceId, kind).takeIf { it.isDirectory() }?.listDirectoryEntries()
            ?.sortedByDescending { it.name.toLongOrNull() ?: 0 }?.map { it.resolve(fileName) }?.firstOrNull { it.isRegularFile() }
        if (held != null) {
            runCatching { Files.deleteIfExists(held.parent) }
            val dir = folder(instanceId, kind)
            Files.createDirectories(dir)
            Files.move(held, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
            stashed.remove("$instanceId/${kind.name}/$fileName")?.let { index?.put(instanceId, it) }
        }
        bump(instanceId)
    }

    /** Отложенное дольше [STASH_TTL_MS] уходит в корзину Windows: вернуть его уже не предлагают. */
    fun purgeStash(instanceId: String, now: Long = System.currentTimeMillis()) {
        val root = gameDir(instanceId).resolve(STASH_DIR)
        if (!root.isDirectory()) return
        runCatching {
            for (kindDir in root.listDirectoryEntries().filter { it.isDirectory() }) {
                for (batch in kindDir.listDirectoryEntries().filter { it.isDirectory() }) {
                    val at = batch.name.toLongOrNull() ?: 0
                    if (now - at <= STASH_TTL_MS) continue
                    batch.listDirectoryEntries().forEach { trash(it) }
                    runCatching { Files.deleteIfExists(batch) }
                }
            }
        }
    }

    override suspend fun addFiles(instanceId: String, kind: ContentKind, files: List<Path>) = withContext(Dispatchers.IO) {
        val dir = folder(instanceId, kind)
        Files.createDirectories(dir)
        files.filter { it.isRegularFile() }.forEach { Files.copy(it, dir.resolve(it.name), StandardCopyOption.REPLACE_EXISTING) }
        bump(instanceId)
    }

    override suspend fun refresh(instanceId: String) = bump(instanceId)

    override suspend fun deleteWorld(instanceId: String, folder: String) = withContext(Dispatchers.IO) {
        val dir = gameDir(instanceId).resolve("saves").resolve(folder).normalize()
        require(dir.startsWith(gameDir(instanceId).resolve("saves"))) { "Некорректная папка мира" }
        trash(dir)
        bump(instanceId)
    }

    /** Архив мира: backups/<экземпляр>/<мир>-<время>.zip. */
    override suspend fun backupWorld(instanceId: String, folder: String): Path? = withContext(Dispatchers.IO) {
        val dir = gameDir(instanceId).resolve("saves").resolve(folder)
        if (!dir.isDirectory()) return@withContext null
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = backupsDir.resolve(instanceId).resolve("$folder-$stamp.zip")
        Files.createDirectories(target.parent)
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            Files.walk(dir).use { s ->
                s.filter { it.isRegularFile() && it.name != "session.lock" }.forEach { f ->
                    zip.putNextEntry(ZipEntry(folder + "/" + dir.relativize(f).toString().replace('\\', '/')))
                    runCatching { Files.copy(f, zip) }
                    zip.closeEntry()
                }
            }
        }
        target
    }

    private fun trash(path: Path) {
        val f = path.toFile()
        val ok = runCatching {
            java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) &&
                java.awt.Desktop.getDesktop().moveToTrash(f)
        }.getOrDefault(false)
        if (!ok && f.exists()) f.deleteRecursively()
    }

    companion object {
        const val STASH_DIR = ".youlauncher-removed"
        /** Сколько удалённое можно вернуть кнопкой «Отменить»; потом файл уходит в корзину Windows. */
        const val STASH_TTL_MS = 10 * 60 * 1000L
    }
}
