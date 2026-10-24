package app.youlauncher.ui.screens.instance

import app.youlauncher.AppGraph
import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.model.Screenshot
import app.youlauncher.core.model.World
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.resources.Res
import app.youlauncher.resources.modset_applied
import app.youlauncher.resources.modset_saved
import app.youlauncher.resources.msg_export_failed
import app.youlauncher.resources.msg_exported
import app.youlauncher.resources.msg_files_added
import app.youlauncher.resources.msg_removed
import app.youlauncher.resources.msg_updated
import app.youlauncher.resources.world_backup_done
import app.youlauncher.ui.ViewModel
import app.youlauncher.data.mods.ModSet
import app.youlauncher.data.mods.ModSetLogic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import app.youlauncher.core.model.LoaderType
import app.youlauncher.data.loaders.NumericComparator
import app.youlauncher.resources.loader_updated
import app.youlauncher.resources.backup_done
import app.youlauncher.resources.repair_done
import app.youlauncher.resources.repair_failed
import app.youlauncher.resources.backup_failed
import app.youlauncher.resources.backup_restored
import app.youlauncher.data.instances.InstanceBackup
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.file.Path

class InstanceViewModel(
    private val graph: AppGraph,
    val id: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    val instance: StateFlow<Instance?> = graph.instances.instances
        .map { list -> list.firstOrNull { it.id == id } }
        .stateIn(scope, SharingStarted.Eagerly, graph.instances.get(id))

    val launch: StateFlow<LaunchState> = graph.launcher.states
        .map { it[id] ?: LaunchState.Idle }
        .stateIn(scope, SharingStarted.Eagerly, LaunchState.Idle)

    private val contentFlows = ContentKind.entries.associateWith { kind ->
        graph.content.content(id, kind).stateIn(scope, SharingStarted.Lazily, emptyList())
    }

    fun content(kind: ContentKind): StateFlow<List<ContentFile>> = contentFlows.getValue(kind)

    val worlds: StateFlow<List<World>> = graph.content.worlds(id).stateIn(scope, SharingStarted.Lazily, emptyList())
    val screenshots: StateFlow<List<Screenshot>> = graph.content.screenshots(id).stateIn(scope, SharingStarted.Lazily, emptyList())
    val log: StateFlow<List<LogLine>> = graph.log.lines.map { lines -> lines.filter { it.instanceId == id } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val gameDir: Path get() = graph.instances.gameDir(id)

    private val _loaderUpdate = MutableStateFlow<String?>(null)
    /** Более новая рекомендованная (или стабильная) версия загрузчика, если вышла. */
    val loaderUpdate: StateFlow<String?> = _loaderUpdate.asStateFlow()

    // После объявления полей: с Dispatchers.Main.immediate корутина может выполниться сразу, ещё в конструкторе.
    init {
        scope.launch(Dispatchers.IO) { runCatching { graph.modInstaller.checkUpdates(id) } }
        scope.launch { findLoaderUpdate() }
    }

    private suspend fun findLoaderUpdate() {
        val inst = graph.instances.get(id) ?: return
        val current = inst.loaderVersion ?: return
        if (inst.loader == LoaderType.Vanilla) return
        val list = runCatching { graph.loaderVersions.versions(inst.loader, inst.gameVersion) }.getOrDefault(emptyList())
        val best = (list.firstOrNull { it.recommended } ?: list.firstOrNull { it.stable })?.version ?: return
        _loaderUpdate.value = best.takeIf { NumericComparator.compare(it, current) > 0 }
    }

    /** Своя обложка копируется в папку экземпляра, чтобы не зависеть от исходного файла. */
    fun setCover(source: Path?) = scope.launch {
        if (source == null) {
            update { it.copy(coverPath = null) }
            removeOwnImages("cover-")
            return@launch
        }
        val target = copyOwnImage(source, "cover-") ?: return@launch
        update { it.copy(coverPath = target.toString()) }
    }

    /**
     * Свой фон экземпляра: картинка копируется в папку экземпляра и показывается только на его странице;
     * общий фон и другие экземпляры не меняются. null возвращает фон из общих настроек.
     */
    fun setBackground(source: Path?) = scope.launch {
        if (source == null) {
            update { it.copy(atmosphere = it.atmosphere.copy(wallpaper = null)) }
            removeOwnImages("background-")
            return@launch
        }
        val target = copyOwnImage(source, "background-") ?: return@launch
        update { it.copy(atmosphere = it.atmosphere.copy(wallpaper = "file:$target")) }
    }

    // Новое имя у каждой картинки: кэш изображений не покажет старую, прежние файлы с тем же префиксом удаляются.
    private suspend fun copyOwnImage(source: Path, prefix: String): Path? = runCatching {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val dir = gameDir.parent
            dir.toFile().listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
            val t = dir.resolve(prefix + System.currentTimeMillis() + "." + source.fileName.toString().substringAfterLast('.', "png").lowercase())
            java.nio.file.Files.copy(source, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            t
        }
    }.getOrNull()

    private suspend fun removeOwnImages(prefix: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching { gameDir.parent.toFile().listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() } }
    }

    private val _repairing = MutableStateFlow(false)
    val repairing: StateFlow<Boolean> = _repairing.asStateFlow()

    /** Починка: файлы игры и загрузчика по хешам, затем моды из каталогов по индексу. */
    fun repair() {
        if (_repairing.value) return
        _repairing.value = true
        graph.launcher.repair(id) { result ->
            scope.launch {
                val mods = if (result.isSuccess) runCatching { graph.modInstaller.repairFiles(id) }.getOrDefault(0) else 0
                _repairing.value = false
                result.onSuccess { graph.messenger.show(Res.string.repair_done, mods) }
                    .onFailure { graph.messenger.show(Res.string.repair_failed, it.message ?: "", error = true) }
            }
        }
    }

    private val _backups = MutableStateFlow<List<InstanceBackup>?>(null)
    val backups: StateFlow<List<InstanceBackup>?> = _backups.asStateFlow()
    private val _backingUp = MutableStateFlow(false)
    val backingUp: StateFlow<Boolean> = _backingUp.asStateFlow()

    fun loadBackups() = scope.launch { _backups.value = runCatching { graph.instanceBackups.list(id) }.getOrDefault(emptyList()) }

    fun backupNow() {
        if (_backingUp.value) return
        _backingUp.value = true
        scope.launch {
            runCatching { graph.instanceBackups.backup(id) }
                .onSuccess { graph.messenger.show(Res.string.backup_done, it.fileName.toString()) }
                .onFailure { graph.messenger.show(Res.string.backup_failed, it.message ?: "", error = true) }
            _backingUp.value = false
            loadBackups()
        }
    }

    fun restoreBackup(backup: InstanceBackup, name: String, onDone: (String) -> Unit) = scope.launch {
        runCatching { graph.instanceBackups.restore(backup.path, name) }
            .onSuccess { graph.messenger.show(Res.string.backup_restored, it.name); onDone(it.id) }
            .onFailure { graph.messenger.show(Res.string.backup_failed, it.message ?: "", error = true) }
    }

    fun deleteBackup(backup: InstanceBackup) = scope.launch {
        graph.instanceBackups.delete(backup.path)
        loadBackups()
    }

    /** Новая версия загрузчика записывается в экземпляр и ставится при следующем запуске. */
    fun updateLoader() {
        val v = _loaderUpdate.value ?: return
        scope.launch {
            graph.instances.update(id) { it.copy(loaderVersion = v) }
            _loaderUpdate.value = null
            graph.messenger.show(Res.string.loader_updated, v)
        }
    }

    fun play() {
        graph.settings.update { it.copy(lastInstanceId = id) }
        graph.launcher.launch(id)
    }

    fun stop() = graph.launcher.stop(id)
    fun dismiss() = graph.launcher.dismiss(id)

    fun update(transform: (Instance) -> Instance) = scope.launch { graph.instances.update(id, transform) }

    fun setEnabled(kind: ContentKind, file: ContentFile, enabled: Boolean) = scope.launch {
        graph.content.setEnabled(id, kind, file.fileName, enabled)
    }

    fun remove(kind: ContentKind, file: ContentFile) = scope.launch {
        graph.content.remove(id, kind, file.fileName)
        graph.messenger.show(Res.string.msg_removed, file.title)
    }

    fun addFiles(kind: ContentKind, files: List<Path>) = scope.launch {
        if (files.isEmpty()) return@launch
        graph.content.addFiles(id, kind, files)
        graph.messenger.show(Res.string.msg_files_added, files.size)
    }

    fun updateAll(kind: ContentKind) = scope.launch {
        val updatable = content(kind).value.filter { it.updateAvailable != null }
        for (file in updatable) graph.modInstaller.update(id, kind, file.fileName)
        graph.messenger.show(Res.string.msg_updated, updatable.size)
    }

    fun export(target: Path, mrpack: Boolean) = scope.launch {
        runCatching { if (mrpack) graph.packExporter.exportMrpack(id, target) else graph.packExporter.exportPrism(id, target) }
            .onSuccess { graph.messenger.show(Res.string.msg_exported, it.fileName.toString()) }
            .onFailure { graph.messenger.show(Res.string.msg_export_failed, it.message ?: it::class.simpleName.orEmpty(), error = true) }
    }

    val modSets: StateFlow<List<ModSet>> = graph.modSets.sets(id)

    fun saveModSet(name: String) {
        graph.modSets.save(id, ModSetLogic.capture(name, content(ContentKind.Mods).value))
        graph.messenger.show(Res.string.modset_saved, name)
    }

    fun applyModSet(set: ModSet) = scope.launch {
        for ((file, enable) in ModSetLogic.changes(content(ContentKind.Mods).value, set)) graph.content.setEnabled(id, ContentKind.Mods, file, enable)
        graph.messenger.show(Res.string.modset_applied, set.name)
    }

    fun deleteModSet(set: ModSet) = graph.modSets.delete(id, set.name)

    fun backupWorld(world: World) = scope.launch {
        if (graph.content.backupWorld(id, world.folder) != null) graph.messenger.show(Res.string.world_backup_done, world.name)
    }

    fun deleteWorld(world: World) = scope.launch {
        graph.content.deleteWorld(id, world.folder)
        graph.messenger.show(Res.string.msg_removed, world.name)
    }
}
