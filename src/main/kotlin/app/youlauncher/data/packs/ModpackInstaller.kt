package app.youlauncher.data.packs

import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.repo.InstallProgress
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.ModCatalog
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.download.Downloader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Установка сборки из каталога одной кнопкой: новый экземпляр со всеми модами сборки. */
interface ModpackInstaller {
    /** Прогресс по id проекта: доля загрузки файла сборки, затем null — идёт импорт и загрузка модов. */
    val progress: StateFlow<Map<String, InstallProgress>>
    suspend fun install(catalog: ModCatalog, project: ModProject, version: ModVersion? = null): Instance
}

/**
 * Файл выбранной (или последней) версии сборки скачивается с CDN каталога с проверкой хеша и сразу
 * импортируется тем же импортом, что и .mrpack или архив CurseForge из библиотеки.
 */
class CatalogModpackInstaller(
    private val instances: InstanceRepository,
    private val importer: PackImporter,
    private val downloaderFactory: () -> Downloader,
    private val cacheDir: Path,
) : ModpackInstaller {
    private val state = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    override val progress: StateFlow<Map<String, InstallProgress>> = state.asStateFlow()

    private fun set(id: String, p: InstallProgress) = state.update { it + (id to p) }

    override suspend fun install(catalog: ModCatalog, project: ModProject, version: ModVersion?): Instance {
        set(project.id, InstallProgress(project.id, 0f))
        try {
            val v = version ?: catalog.versions(project.id, null, null).firstOrNull() ?: throw IOException("У сборки нет версий для загрузки")
            val file = v.files.firstOrNull { it.primary } ?: v.files.firstOrNull() ?: throw IOException("У версии сборки нет файла")
            val target = cacheDir.resolve("modpacks").resolve(file.fileName)
            Files.createDirectories(target.parent)
            val downloader = downloaderFactory()
            coroutineScope {
                val watch = launch {
                    downloader.progress.collect { p ->
                        if (p.totalBytes > 0) set(project.id, InstallProgress(project.id, p.doneBytes.toFloat() / p.totalBytes))
                    }
                }
                downloader.downloadAll(listOf(FileTask(file.url, target, file.sha1, file.size)))
                watch.cancel()
            }
            set(project.id, InstallProgress(project.id, null))
            val name = ModpackNames.unique(project.title, instances.instances.value.map { it.name })
            val instance = importer.import(target, name)
            runCatching { Files.deleteIfExists(target) }
            set(project.id, InstallProgress(project.id, 1f, done = true))
            return instance
        } catch (e: Throwable) {
            set(project.id, InstallProgress(project.id, null, error = e.message ?: e::class.simpleName))
            throw e
        }
    }
}

object ModpackNames {
    /** Имя нового экземпляра: название сборки, при совпадении — с номером. */
    fun unique(base: String, existing: Collection<String>): String {
        val clean = base.trim().ifBlank { "Сборка" }
        if (clean !in existing) return clean
        var n = 2
        while ("$clean $n" in existing) n++
        return "$clean $n"
    }
}

/** Демо: сборка «ставится» за пару секунд и появляется экземпляром в библиотеке. */
class FakeModpackInstaller(private val instances: InstanceRepository) : ModpackInstaller {
    private val state = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    override val progress: StateFlow<Map<String, InstallProgress>> = state.asStateFlow()

    override suspend fun install(catalog: ModCatalog, project: ModProject, version: ModVersion?): Instance {
        for (i in 0..10) {
            state.update { it + (project.id to InstallProgress(project.id, i / 10f)) }
            delay(120)
        }
        val name = ModpackNames.unique(project.title, instances.instances.value.map { it.name })
        val instance = instances.create(NewInstanceSpec(name, "1.21.10", LoaderType.Fabric))
        state.update { it + (project.id to InstallProgress(project.id, 1f, done = true)) }
        return instance
    }
}
