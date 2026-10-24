package app.youlauncher.data.mods

import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.model.DependencyKind
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.ModDependency
import app.youlauncher.core.model.ModFile
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.repo.CompatStatus
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.ModCompat
import app.youlauncher.core.repo.DependencyInfo
import app.youlauncher.core.repo.InstallPreview
import app.youlauncher.core.repo.InstallProgress
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.ModCatalog
import app.youlauncher.core.repo.ModInstaller
import app.youlauncher.data.download.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

fun ProjectType.contentKind(): ContentKind? = when (this) {
    ProjectType.Mod -> ContentKind.Mods
    ProjectType.ResourcePack -> ContentKind.ResourcePacks
    ProjectType.Shader -> ContentKind.ShaderPacks
    ProjectType.DataPack -> ContentKind.DataPacks
    ProjectType.Modpack -> null
}

/** Выбор версии: подходящая по игре и загрузчику, сначала релизы, затем самая свежая. */
object VersionPicker {
    fun compatible(v: ModVersion, game: String, loader: LoaderType?): Boolean =
        (v.gameVersions.isEmpty() || game in v.gameVersions) &&
            (loader == null || loader == LoaderType.Vanilla || v.loaders.isEmpty() || loader.modrinthName in v.loaders ||
                // Quilt загружает моды Fabric.
                (loader == LoaderType.Quilt && "fabric" in v.loaders))

    fun best(versions: List<ModVersion>, game: String, loader: LoaderType?): ModVersion? {
        val ok = versions.filter { compatible(it, game, loader) }
        val rank = { v: ModVersion -> when (v.type) { "release" -> 0; "beta" -> 1; else -> 2 } }
        return ok.sortedWith(compareBy<ModVersion> { rank(it) }.thenByDescending { it.publishedAt }).firstOrNull()
    }

    fun primaryFile(v: ModVersion): ModFile? = v.files.firstOrNull { it.primary } ?: v.files.firstOrNull()
}

data class PlannedInstall(val version: ModVersion, val asDependency: Boolean)

data class InstallPlan(val items: List<PlannedInstall>, val missing: List<String>, val conflicts: List<String>)

/**
 * Обход обязательных зависимостей в ширину. Уже установленные проекты пропускаются,
 * несовместимые (incompatible) с установленными или запланированными попадают в conflicts,
 * зависимости без подходящей версии в missing. Встроенные (embedded) и необязательные не ставятся.
 */
class DependencyPlanner(private val resolve: suspend (ModDependency) -> ModVersion?) {
    suspend fun plan(root: ModVersion, installed: Set<String>): InstallPlan {
        val items = mutableListOf(PlannedInstall(root, asDependency = false))
        val planned = mutableSetOf(root.projectId)
        val missing = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            for (d in v.dependencies) {
                val pid = d.projectId
                when (d.kind) {
                    DependencyKind.Incompatible -> if (pid != null && (pid in installed || pid in planned)) conflicts += pid
                    DependencyKind.Required -> {
                        if (pid != null && (pid in installed || pid in planned)) continue
                        val dep = resolve(d)
                        if (dep == null) { missing += (pid ?: d.versionId.orEmpty()); continue }
                        if (dep.projectId in installed || !planned.add(dep.projectId)) continue
                        items += PlannedInstall(dep, asDependency = true)
                        queue += dep
                    }
                    else -> Unit
                }
            }
        }
        return InstallPlan(items, missing.distinct(), conflicts.distinct())
    }
}

/**
 * Установка из Modrinth и CurseForge: выбор версии под экземпляр, обязательные зависимости,
 * загрузка с проверкой SHA-1 (файлы только с CDN каталогов), запись в индекс содержимого,
 * удаление прежней версии того же проекта при обновлении.
 */
class CatalogModInstaller(
    private val instances: InstanceRepository,
    private val content: InstanceContentRepository,
    private val index: ContentIndex,
    private val modrinth: ModrinthCatalog,
    private val curseForge: () -> ModCatalog?,
    private val downloaderFactory: () -> Downloader,
) : ModInstaller {
    private val state = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    override val progress: StateFlow<Map<String, InstallProgress>> = state.asStateFlow()

    private fun catalog(source: ContentSource): ModCatalog = when (source) {
        ContentSource.CurseForge -> curseForge() ?: error("CurseForge недоступен: задайте ключ API в переменной окружения ${CurseForgeCatalog.KEY_ENV}")
        else -> modrinth
    }

    private fun loaderFor(type: ProjectType, instance: Instance): LoaderType? =
        if (type == ProjectType.Mod) instance.loader else null

    override suspend fun install(instanceId: String, project: ModProject, version: ModVersion?, withDependencies: Boolean): Result<List<String>> {
        state.update { it + (project.id to InstallProgress(project.id, null)) }
        val result = runCatching { doInstall(instanceId, project, version, withDependencies) }
        state.update { it + (project.id to InstallProgress(project.id, 1f, done = true, error = result.exceptionOrNull()?.message)) }
        return result
    }

    private class Resolved(val catalog: ModCatalog, val kind: ContentKind, val root: ModVersion, val plan: InstallPlan, val installed: Set<String>)

    /** Версия под экземпляр и план обязательных зависимостей (без загрузки файлов). */
    private suspend fun resolve(instanceId: String, project: ModProject, chosen: ModVersion?): Resolved {
        val instance = instances.get(instanceId) ?: error("Экземпляр не найден")
        val kind = project.type.contentKind() ?: error("Сборки устанавливаются через импорт в библиотеке")
        if (project.type == ProjectType.Mod && instance.loader == LoaderType.Vanilla) error("Для модов нужен загрузчик: выберите Fabric, Quilt, Forge или NeoForge в настройках экземпляра")
        val catalog = catalog(project.source)
        val loader = loaderFor(project.type, instance)
        val root = chosen ?: VersionPicker.best(catalog.versions(project.id, instance.gameVersion, loader), instance.gameVersion, loader)
            ?: error("Нет версии «${project.title}» для ${instance.gameVersion}${loader?.let { " и ${it.title}" } ?: ""}")

        val planner = DependencyPlanner { dep ->
            when {
                dep.versionId != null && project.source == ContentSource.Modrinth -> runCatching { modrinth.version(dep.versionId) }.getOrNull()
                dep.projectId != null -> runCatching { VersionPicker.best(catalog.versions(dep.projectId, instance.gameVersion, instance.loader), instance.gameVersion, instance.loader) }.getOrNull()
                else -> null
            }
        }
        val installed = installedProjectIds(instanceId)
        val plan = if (project.type == ProjectType.Mod) planner.plan(root, installed) else InstallPlan(listOf(PlannedInstall(root, false)), emptyList(), emptyList())
        return Resolved(catalog, kind, root, plan, installed)
    }

    override suspend fun preview(instanceId: String, project: ModProject, version: ModVersion?): InstallPreview {
        val r = runCatching { resolve(instanceId, project, version) }.getOrElse { return InstallPreview(project, version, error = it.message ?: it::class.simpleName) }
        val planned = r.plan.items.filter { it.asDependency }.map { item ->
            val p = projectOf(r.catalog, item.version.projectId)
            DependencyInfo(item.version.projectId, p?.title ?: item.version.projectId, p?.iconUrl, item.version.versionNumber, installed = false)
        }
        // Обязательные зависимости, которые уже стоят, показываются отдельно: их качать не нужно.
        val already = r.plan.items.flatMap { it.version.dependencies }
            .filter { it.kind == DependencyKind.Required && it.projectId != null && it.projectId in r.installed }
            .mapNotNull { it.projectId }.distinct()
            .map { pid -> projectOf(r.catalog, pid).let { DependencyInfo(pid, it?.title ?: pid, it?.iconUrl, null, installed = true) } }
        return InstallPreview(
            project, r.root, planned + already,
            missing = r.plan.missing.map { titleOf(r.catalog, it) },
            conflicts = r.plan.conflicts.map { titleOf(r.catalog, it) },
        )
    }

    private suspend fun doInstall(instanceId: String, project: ModProject, chosen: ModVersion?, withDependencies: Boolean): List<String> {
        val r = resolve(instanceId, project, chosen)
        val catalog = r.catalog
        val kind = r.kind
        val plan = r.plan
        if (plan.conflicts.isNotEmpty()) error("«${project.title}» несовместим с уже установленными: ${plan.conflicts.map { titleOf(catalog, it) }.joinToString()}")
        if (withDependencies && plan.missing.isNotEmpty()) error("Не найдены подходящие версии зависимостей: ${plan.missing.map { titleOf(catalog, it) }.joinToString()}")

        val dir = instances.gameDir(instanceId).resolve(kind.folder)
        val items = if (withDependencies) plan.items else plan.items.filterNot { it.asDependency }
        val tasks = items.map { item ->
            val file = VersionPicker.primaryFile(item.version) ?: error("У версии ${item.version.versionNumber} нет файлов")
            if (file.url.isBlank()) error("Автор «${titleOf(catalog, item.version.projectId)}» запретил загрузку из сторонних лаунчеров. Скачайте файл с сайта CurseForge и добавьте вручную")
            item to file
        }
        downloaderFactory().downloadAll(tasks.map { (_, f) -> FileTask(f.url, dir.resolve(f.fileName), f.sha1, f.size) })

        val installed = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            for ((item, file) in tasks) {
                val pid = item.version.projectId
                // Прежняя версия того же проекта удаляется (в корзину), если имя файла сменилось.
                index.read(instanceId).filter { it.source == project.source && it.projectId == pid && it.fileName != file.fileName }.forEach { old ->
                    listOf(old.fileName, old.fileName + ".disabled").forEach { n -> if (dir.resolve(n).exists()) content.remove(instanceId, old.kind, n) }
                }
                val title = if (pid == project.id) project.title else titleOf(catalog, pid)
                index.put(instanceId, IndexedFile(kind, file.fileName, project.source, pid, item.version.id, item.version.versionNumber, title, file.sha1, file.url))
                installed += title
            }
        }
        content.refresh(instanceId)
        return installed
    }

    private val titles = mutableMapOf<String, String>()
    private val projects = mutableMapOf<String, ModProject>()

    private suspend fun projectOf(catalog: ModCatalog, projectId: String): ModProject? = projects[projectId]
        ?: runCatching { catalog.project(projectId) }.getOrNull()?.also { projects[projectId] = it; titles[projectId] = it.title }

    private suspend fun titleOf(catalog: ModCatalog, projectId: String): String = titles[projectId] ?: projectOf(catalog, projectId)?.title ?: projectId

    /**
     * Проекты, уже стоящие в экземпляре: из индекса, а файлы без записи (добавленные вручную)
     * опознаются по SHA-1 через Modrinth и дописываются в индекс.
     */
    override suspend fun installedProjectIds(instanceId: String): Set<String> {
        identify(instanceId)
        val game = instances.gameDir(instanceId)
        return index.read(instanceId).filter { e -> listOf(e.fileName, e.fileName + ".disabled").any { game.resolve(e.kind.folder).resolve(it).exists() } }
            .map { it.projectId }.toSet()
    }

    private suspend fun identify(instanceId: String) {
        val game = instances.gameDir(instanceId)
        val known = index.read(instanceId).map { it.kind to it.fileName }.toSet()
        val unknown = withContext(Dispatchers.IO) {
            ContentKind.entries.flatMap { kind ->
                val d = game.resolve(kind.folder)
                if (!d.isDirectory()) emptyList() else d.listDirectoryEntries().filter { it.isRegularFile() && (kind to it.name.removeSuffix(".disabled")) !in known }
                    .map { Triple(kind, it.name.removeSuffix(".disabled"), Downloader.sha1(it)) }
            }
        }
        if (unknown.isEmpty()) return
        val found = runCatching { modrinth.byHashes(unknown.map { it.third }) }.getOrNull() ?: return
        for ((kind, name, sha1) in unknown) {
            val v = found[sha1] ?: continue
            index.put(instanceId, IndexedFile(kind, name, ContentSource.Modrinth, v.projectId, v.id, v.versionNumber, titles[v.projectId] ?: "", sha1, v.files.firstOrNull { it.sha1 == sha1 }?.url))
        }
    }

    /** Проверка обновлений Modrinth для всех файлов экземпляра; возвращает число найденных обновлений. */
    override suspend fun checkUpdates(instanceId: String): Int {
        val instance = instances.get(instanceId) ?: return 0
        identify(instanceId)
        val entries = index.read(instanceId).filter { it.source == ContentSource.Modrinth && it.sha1 != null }
        var count = 0
        for (kind in ContentKind.entries) {
            val group = entries.filter { it.kind == kind }
            if (group.isEmpty()) continue
            val loader = if (kind == ContentKind.Mods) instance.loader else null
            val latest = runCatching { modrinth.latestByHashes(group.mapNotNull { it.sha1 }, loader, instance.gameVersion) }.getOrNull() ?: continue
            index.update(instanceId) { list ->
                list.map { e ->
                    val v = latest[e.sha1]
                    if (e.kind != kind || e.source != ContentSource.Modrinth) e
                    else if (v != null && v.id != e.versionId) { count++; e.copy(update = v.versionNumber, updateVersionId = v.id) }
                    else e.copy(update = null, updateVersionId = null)
                }
            }
        }
        count += checkCurseForge(instanceId, instance)
        content.refresh(instanceId)
        return count
    }

    /** CurseForge: для каждого файла ищется лучшая совместимая версия; id файлов CurseForge растут со временем. */
    private suspend fun checkCurseForge(instanceId: String, instance: Instance): Int {
        val cf = curseForge() ?: return 0
        val entries = index.read(instanceId).filter { it.source == ContentSource.CurseForge }
        if (entries.isEmpty()) return 0
        val best = entries.associate { e ->
            val loader = if (e.kind == ContentKind.Mods) instance.loader else null
            e.projectId to runCatching { VersionPicker.best(cf.versions(e.projectId, instance.gameVersion, loader), instance.gameVersion, loader) }.getOrNull()
        }
        var count = 0
        index.update(instanceId) { list ->
            list.map { e ->
                val v = best[e.projectId]
                if (e.source != ContentSource.CurseForge) e
                else if (v != null && isNewerCurseFile(v.id, e.versionId)) { count++; e.copy(update = v.versionNumber, updateVersionId = v.id) }
                else e.copy(update = null, updateVersionId = null)
            }
        }
        return count
    }

    override suspend fun repairFiles(instanceId: String): Int {
        val game = instances.gameDir(instanceId)
        val broken = withContext(Dispatchers.IO) {
            index.read(instanceId).filter { it.sha1 != null && it.url != null }.mapNotNull { e ->
                val dir = game.resolve(e.kind.folder)
                val enabled = dir.resolve(e.fileName)
                val disabled = dir.resolve(e.fileName + ".disabled")
                val file = if (disabled.exists()) disabled else enabled
                // Отсутствующий или с чужим хешем файл перекачивается на то же место (выключенный остаётся выключенным).
                if (file.exists() && Downloader.sha1(file).equals(e.sha1, true)) null
                else { java.nio.file.Files.deleteIfExists(file); FileTask(e.url!!, file, e.sha1) }
            }
        }
        if (broken.isNotEmpty()) downloaderFactory().downloadAll(broken)
        content.refresh(instanceId)
        return broken.size
    }

    override suspend fun compatibility(instanceId: String, gameVersion: String, loader: LoaderType): List<ModCompat> {
        identify(instanceId)
        val game = instances.gameDir(instanceId)
        val indexed = index.read(instanceId)
        val result = mutableListOf<ModCompat>()
        for (kind in ContentKind.entries) {
            val dir = game.resolve(kind.folder)
            if (!withContext(Dispatchers.IO) { dir.isDirectory() }) continue
            val files = withContext(Dispatchers.IO) { dir.listDirectoryEntries().filter { it.isRegularFile() }.map { it.name } }
            for (file in files) {
                val base = file.removeSuffix(".disabled")
                val e = indexed.firstOrNull { it.kind == kind && it.fileName == base }
                if (e == null) {
                    // Ресурспаки и шейдеры без записи переживают смену версии сами; моды без записи проверить нельзя.
                    if (kind == ContentKind.Mods) result += ModCompat(kind, file, base.substringBeforeLast('.'), CompatStatus.Unknown)
                    continue
                }
                val modLoader = if (kind == ContentKind.Mods) loader else null
                val best = if (kind == ContentKind.Mods && loader == LoaderType.Vanilla) null
                else runCatching { VersionPicker.best(catalog(e.source).versions(e.projectId, gameVersion, modLoader), gameVersion, modLoader) }.getOrNull()
                val title = e.title.ifBlank { titleOf(catalog(e.source), e.projectId) }
                result += when {
                    best == null -> ModCompat(kind, file, title, CompatStatus.Incompatible, projectId = e.projectId)
                    best.id == e.versionId -> ModCompat(kind, file, title, CompatStatus.Same, best, e.projectId)
                    else -> ModCompat(kind, file, title, CompatStatus.Update, best, e.projectId)
                }
            }
        }
        return result.sortedWith(compareBy({ it.status.ordinal }, { it.title.lowercase() }))
    }

    override suspend fun migrate(instanceId: String, plan: List<ModCompat>): Int {
        var changed = 0
        for (c in plan) {
            when (c.status) {
                CompatStatus.Update -> {
                    val e = index.find(instanceId, c.kind, c.fileName) ?: continue
                    val project = runCatching { catalog(e.source).project(e.projectId) }.getOrNull() ?: continue
                    val wasDisabled = c.fileName.endsWith(".disabled")
                    install(instanceId, project, c.newVersion).onSuccess {
                        changed++
                        if (wasDisabled) VersionPicker.primaryFile(c.newVersion!!)?.let { f -> content.setEnabled(instanceId, c.kind, f.fileName, false) }
                    }
                }
                CompatStatus.Incompatible -> if (!c.fileName.endsWith(".disabled")) {
                    content.setEnabled(instanceId, c.kind, c.fileName, false)
                    changed++
                }
                else -> Unit
            }
        }
        content.refresh(instanceId)
        return changed
    }

    /** Обновляет файл до найденной версии. */
    override suspend fun update(instanceId: String, kind: ContentKind, fileName: String): Result<List<String>> {
        val e = index.find(instanceId, kind, fileName) ?: return Result.failure(IllegalStateException("Файл не из каталога"))
        val target = e.updateVersionId ?: return Result.success(emptyList())
        val project = runCatching { catalog(e.source).project(e.projectId) }.getOrElse { return Result.failure(it) }
        val instance = instances.get(instanceId) ?: return Result.failure(IllegalStateException("Экземпляр не найден"))
        val version = runCatching {
            if (e.source == ContentSource.CurseForge) {
                val loader = if (kind == ContentKind.Mods) instance.loader else null
                catalog(e.source).versions(e.projectId, instance.gameVersion, loader).firstOrNull { it.id == target } ?: error("Версия  не найдена")
            } else modrinth.version(target)
        }.getOrElse { return Result.failure(it) }
        val wasDisabled = fileName.endsWith(".disabled")
        return install(instanceId, project, version).onSuccess {
            if (wasDisabled) VersionPicker.primaryFile(version)?.let { content.setEnabled(instanceId, kind, it.fileName, false) }
        }
    }
}

/** Файл CurseForge новее, если его числовой id больше. */
fun isNewerCurseFile(candidate: String, current: String): Boolean =
    (candidate.toLongOrNull() ?: 0) > (current.toLongOrNull() ?: Long.MAX_VALUE)
