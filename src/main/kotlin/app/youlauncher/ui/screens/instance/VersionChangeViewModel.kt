package app.youlauncher.ui.screens.instance

import app.youlauncher.AppGraph
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.CompatStatus
import app.youlauncher.core.repo.GameVersion
import app.youlauncher.core.repo.LoaderVersion
import app.youlauncher.core.repo.ModCompat
import app.youlauncher.core.repo.VersionType
import app.youlauncher.data.loaders.NumericComparator
import app.youlauncher.resources.Res
import app.youlauncher.resources.version_change_done
import app.youlauncher.resources.version_change_failed
import app.youlauncher.ui.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VersionChangeState(
    val fromGame: String,
    val fromLoader: LoaderType,
    val fromLoaderVersion: String?,
    val game: String,
    val loader: LoaderType,
    val loaderVersion: String? = null,
    val versions: List<GameVersion>? = null,
    val loaderVersions: List<LoaderVersion>? = null,
    /** null: ещё не проверено; пустой список: модов нет. */
    val compat: List<ModCompat>? = null,
    val checking: Boolean = false,
    val applying: Boolean = false,
    val backupWorlds: Boolean = true,
) {
    val changed: Boolean get() = game != fromGame || loader != fromLoader || (loader != LoaderType.Vanilla && loaderVersion != fromLoaderVersion)
    /** Переход на более старую версию игры: миры могут повредиться, нужна резервная копия. */
    val downgrade: Boolean get() = NumericComparator.compare(game, fromGame) < 0
    val canApply: Boolean get() = changed && compat != null && !checking && !applying &&
        (loader == LoaderType.Vanilla || loaderVersion != null)
    fun count(status: CompatStatus) = compat.orEmpty().count { it.status == status }
}

/**
 * Смена версии игры и загрузчика у готового экземпляра. Миры и моды остаются; моды проверяются
 * в каталогах: подходящие обновятся, несовместимые выключатся (файлы не удаляются).
 */
class VersionChangeViewModel(
    private val graph: AppGraph,
    private val instanceId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    private val inst = graph.instances.get(instanceId) ?: error("Экземпляр не найден")
    private val _state = MutableStateFlow(VersionChangeState(inst.gameVersion, inst.loader, inst.loaderVersion, inst.gameVersion, inst.loader, inst.loaderVersion))
    val state: StateFlow<VersionChangeState> = _state.asStateFlow()
    private var loaderJob: Job? = null
    private var checkJob: Job? = null

    init {
        scope.launch {
            val list = runCatching { graph.versions.versions() }.getOrDefault(emptyList())
            _state.update { it.copy(versions = list.filter { v -> v.type == VersionType.Release }) }
        }
        loadLoaderVersions()
    }

    fun selectGame(id: String) {
        _state.update { it.copy(game = id, loaderVersion = null, compat = null) }
        loadLoaderVersions()
    }

    fun selectLoader(loader: LoaderType) {
        _state.update { it.copy(loader = loader, loaderVersion = null, compat = null) }
        loadLoaderVersions()
    }

    fun selectLoaderVersion(v: String) = _state.update { it.copy(loaderVersion = v) }

    fun setBackup(on: Boolean) = _state.update { it.copy(backupWorlds = on) }

    private fun loadLoaderVersions() {
        loaderJob?.cancel()
        val s = _state.value
        if (s.loader == LoaderType.Vanilla) {
            _state.update { it.copy(loaderVersions = emptyList()) }
            check()
            return
        }
        _state.update { it.copy(loaderVersions = null) }
        loaderJob = scope.launch {
            val list = runCatching { graph.loaderVersions.versions(s.loader, s.game) }.getOrDefault(emptyList())
            val pick = (list.firstOrNull { it.recommended } ?: list.firstOrNull { it.stable } ?: list.firstOrNull())?.version
            _state.update { it.copy(loaderVersions = list, loaderVersion = it.loaderVersion ?: pick) }
            check()
        }
    }

    /** Проверка модов под выбранную версию; повторный выбор отменяет прежнюю проверку. */
    fun check() {
        checkJob?.cancel()
        _state.update { it.copy(checking = true, compat = null) }
        checkJob = scope.launch {
            val s = _state.value
            val list = runCatching { graph.modInstaller.compatibility(instanceId, s.game, s.loader) }.getOrDefault(emptyList())
            _state.update { it.copy(compat = list, checking = false) }
        }
    }

    fun apply(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canApply) return
        _state.update { it.copy(applying = true) }
        scope.launch {
            runCatching {
                if (s.downgrade && s.backupWorlds) {
                    graph.content.worlds(instanceId).first().forEach { w -> graph.content.backupWorld(instanceId, w.folder) }
                }
                graph.instances.update(instanceId) {
                    it.copy(gameVersion = s.game, loader = s.loader, loaderVersion = if (s.loader == LoaderType.Vanilla) null else s.loaderVersion)
                }
                graph.modInstaller.migrate(instanceId, s.compat.orEmpty())
            }.onSuccess { changed ->
                graph.messenger.show(Res.string.version_change_done, s.game, changed)
                onDone()
            }.onFailure { e ->
                graph.messenger.show(Res.string.version_change_failed, e.message ?: e::class.simpleName.orEmpty(), error = true)
            }
            _state.update { it.copy(applying = false) }
        }
    }
}
