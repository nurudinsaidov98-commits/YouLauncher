package app.youlauncher.data.mojang

import app.youlauncher.core.AppDirs
import app.youlauncher.core.mojang.AssetIndex
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.mojang.ResolvedLibrary
import app.youlauncher.core.mojang.RuleEnv
import app.youlauncher.core.mojang.VersionResolver
import app.youlauncher.core.repo.LaunchStage
import app.youlauncher.data.download.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/** Всё, что нужно для запуска после установки версии. */
data class InstalledVersion(
    val resolved: ResolvedVersion,
    val libraries: List<ResolvedLibrary>,
    val clientJar: Path,
    val nativesDir: Path,
    val assetsRoot: Path,
    val assetIndexName: String,
    /** Папка для ${game_assets}: virtual/legacy у старых версий, иначе assetsRoot. */
    val gameAssets: Path,
    val loggingArgument: String?,
)

/**
 * Установка версии игры: client.jar, библиотеки (общий кэш), natives с распаковкой и exclude,
 * индекс и объекты ассетов (включая legacy и virtual), конфиг логирования.
 * Файлы берутся только с официальных серверов Mojang (или Maven загрузчика для его библиотек) с проверкой SHA-1.
 */
class GameInstaller(private val api: MojangApi, private val dirs: AppDirs, private val downloaderFactory: () -> Downloader) {

    suspend fun install(
        versionId: String,
        gameDir: Path,
        env: RuleEnv,
        forceVerify: Boolean = false,
        onProgress: (LaunchStage, Float?, String) -> Unit,
    ): InstalledVersion = coroutineScope {
        onProgress(LaunchStage.Preparing, null, versionId)
        val resolved = api.resolve(versionId)
        val json = resolved.json
        val libs = VersionResolver.libraries(json, env)

        // 1. Клиент и библиотеки.
        val clientJar = api.clientJar(resolved.clientId)
        val client = json.downloads?.client ?: error("В версии ${resolved.clientId} нет client.jar")
        val libTasks = libs.map { FileTask(it.url, dirs.librariesDir.resolve(it.path), it.sha1, it.size) }
        val logging = json.logging?.client
        val loggingPath = logging?.let { dirs.assetsDir.resolve("log_configs").resolve(it.file.id) }
        val coreTasks = buildList {
            add(FileTask(client.url, clientJar, client.sha1, client.size))
            addAll(libTasks)
            if (logging != null && loggingPath != null) add(FileTask(logging.file.url, loggingPath, logging.file.sha1, logging.file.size))
        }
        runDownloads(coreTasks, LaunchStage.Libraries, forceVerify, onProgress)

        // 2. Natives старого формата: распаковка в папку версии.
        val nativesDir = api.versionDir(versionId).resolve("natives")
        withContext(Dispatchers.IO) { extractNatives(libs.filter { it.native }, nativesDir) }

        // 3. Ассеты.
        val indexRef = json.assetIndex ?: error("В версии нет assetIndex")
        onProgress(LaunchStage.Assets, null, indexRef.id)
        val index = api.assetIndex(indexRef)
        val objectsDir = dirs.assetsDir.resolve("objects")
        val assetTasks = index.objects.values.distinctBy { it.hash }.map { o ->
            val prefix = o.hash.take(2)
            FileTask(MojangApi.RESOURCES_URL + "$prefix/${o.hash}", objectsDir.resolve(prefix).resolve(o.hash), o.hash, o.size, trustExisting = true)
        }
        runDownloads(assetTasks, LaunchStage.Assets, forceVerify, onProgress)
        val gameAssets = withContext(Dispatchers.IO) { placeLegacyAssets(index, indexRef.id, objectsDir, gameDir) }

        InstalledVersion(
            resolved = resolved,
            libraries = libs,
            clientJar = clientJar,
            nativesDir = nativesDir,
            assetsRoot = dirs.assetsDir,
            assetIndexName = indexRef.id,
            gameAssets = gameAssets,
            loggingArgument = if (logging != null && loggingPath != null) VersionResolver.substitute(logging.argument, mapOf("path" to loggingPath.toString())) else null,
        )
    }

    private suspend fun runDownloads(tasks: List<FileTask>, stage: LaunchStage, forceVerify: Boolean, onProgress: (LaunchStage, Float?, String) -> Unit) = coroutineScope {
        val downloader = downloaderFactory().also { it.forceVerify = forceVerify }
        val watcher = launch {
            downloader.progress.collectLatest { p ->
                onProgress(stage, if (p.totalFiles == 0) 1f else p.fraction, "${p.doneFiles}/${p.totalFiles}")
            }
        }
        try {
            downloader.downloadAll(tasks)
        } finally {
            watcher.cancel()
        }
        onProgress(stage, 1f, "")
    }

    /** Распаковка natives из jar-архивов (старые версии). Папка очищается, чтобы не смешивать версии. */
    fun extractNatives(natives: List<ResolvedLibrary>, target: Path) {
        if (target.exists()) target.toFile().deleteRecursively()
        Files.createDirectories(target)
        for (lib in natives) {
            val jar = dirs.librariesDir.resolve(lib.path)
            if (!jar.exists()) continue
            ZipFile(jar.toFile()).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (lib.extractExclude.any { name.startsWith(it) } || name.startsWith("META-INF/")) continue
                    val out = target.resolve(name).normalize()
                    require(out.startsWith(target)) { "Небезопасный путь в архиве natives: $name" }
                    Files.createDirectories(out.parent)
                    zip.getInputStream(entry).use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
        // Папки нового формата (26.x): ${natives_directory}/java, /jna, /lwjgl, /netty.
        listOf("java", "jna", "lwjgl", "netty").forEach { Files.createDirectories(target.resolve(it)) }
    }

    /**
     * Старые индексы: map_to_resources (до 1.6) копирует ассеты в <gameDir>/resources,
     * virtual (1.6–1.7) раскладывает их в assets/virtual/<id>. Возвращает папку для ${game_assets}.
     */
    fun placeLegacyAssets(index: AssetIndex, indexId: String, objectsDir: Path, gameDir: Path): Path {
        val target = when {
            index.mapToResources -> gameDir.resolve("resources")
            index.virtual -> dirs.assetsDir.resolve("virtual").resolve(indexId)
            else -> return dirs.assetsDir
        }
        for ((name, obj) in index.objects) {
            val out = target.resolve(name).normalize()
            if (!out.startsWith(target)) continue
            if (out.exists() && Files.size(out) == obj.size) continue
            val src = objectsDir.resolve(obj.hash.take(2)).resolve(obj.hash)
            if (!src.exists()) continue
            Files.createDirectories(out.parent)
            Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING)
        }
        return target.takeIf { it.isDirectory() } ?: dirs.assetsDir
    }
}
