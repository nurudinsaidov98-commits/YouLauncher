package app.youlauncher.ui.screens.mods

import app.youlauncher.AppGraph
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.repo.InstallPreview
import app.youlauncher.core.repo.InstallProgress
import app.youlauncher.core.repo.ModCatalog
import app.youlauncher.core.repo.ModQuery
import app.youlauncher.core.repo.ModSort
import app.youlauncher.resources.Res
import app.youlauncher.resources.mods_batch_installed
import app.youlauncher.resources.notify_install_title
import org.jetbrains.compose.resources.getString
import app.youlauncher.resources.mods_install_failed
import app.youlauncher.resources.mods_installed_msg
import app.youlauncher.resources.mods_installed_with_deps
import app.youlauncher.resources.mods_need_instance
import app.youlauncher.ui.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogSource { Modrinth, CurseForge }

/** Что каталог запоминает, пока открыта страница мода: поиск, фильтры, результаты и прокрутку списка. */
data class ModsMemory(val state: ModsState, val firstItem: Int, val firstOffset: Int, val selected: Map<String, ModProject> = emptyMap())

data class ModsState(
    val source: CatalogSource = CatalogSource.Modrinth,
    val query: ModQuery = ModQuery(),
    val results: List<ModProject> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val categories: List<String> = emptyList(),
    val target: Instance? = null,
    val compatibleOnly: Boolean = true,
    val installed: Set<String> = emptySet(),
) {
    val canLoadMore: Boolean get() = results.size < total
}

class ModsViewModel(
    private val graph: AppGraph,
    targetInstanceId: String?,
    restore: ModsMemory? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    private val _state = MutableStateFlow(ModsState())
    val state: StateFlow<ModsState> = _state.asStateFlow()
    val progress: StateFlow<Map<String, InstallProgress>> = graph.modInstaller.progress
    private var searchJob: Job? = null

    // Позиция списка: запоминается вместе с поиском, чтобы после страницы мода вернуться на то же место.
    var scrollIndex = restore?.firstItem ?: 0
    var scrollOffset = restore?.firstOffset ?: 0

    init {
        val saved = restore?.state
        if (saved != null) {
            // Возврат со страницы мода: прежние поиск, фильтры и результаты без нового запроса.
            _state.value = saved.copy(loading = false, loadingMore = false)
            if (saved.results.isEmpty()) search()
            else saved.target?.let { t -> scope.launch { _state.update { it.copy(installed = graph.modInstaller.installedProjectIds(t.id)) } } }
        } else {
            val target = targetInstanceId?.let { graph.instances.get(it) }
                ?: graph.settings.settings.value.lastInstanceId?.let { graph.instances.get(it) }
                ?: graph.instances.instances.value.firstOrNull { it.isModded }
            selectTarget(target)
        }
    }

    fun memory() = ModsMemory(_state.value.copy(loading = false, loadingMore = false), scrollIndex, scrollOffset, _selected.value)

    val catalog: ModCatalog get() = if (_state.value.source == CatalogSource.Modrinth) graph.modrinth else graph.curseForge

    fun instances(): List<Instance> = graph.instances.instances.value

    fun selectTarget(instance: Instance?) {
        _state.update { it.copy(target = instance) }
        scope.launch { _state.update { it.copy(installed = instance?.let { i -> graph.modInstaller.installedProjectIds(i.id) }.orEmpty()) } }
        search()
    }

    fun setSource(source: CatalogSource) {
        _state.update { it.copy(source = source, results = emptyList(), total = 0, categories = emptyList()) }
        search()
    }

    fun setType(type: ProjectType) {
        _state.update { it.copy(query = it.query.copy(type = type, categories = emptySet())) }
        search()
    }

    fun setText(text: String) {
        _state.update { it.copy(query = it.query.copy(text = text)) }
        search(debounce = true)
    }

    fun setSort(sort: ModSort) {
        _state.update { it.copy(query = it.query.copy(sort = sort)) }
        search()
    }

    fun toggleCategory(category: String) {
        _state.update { s ->
            val cats = s.query.categories
            s.copy(query = s.query.copy(categories = if (category in cats) cats - category else cats + category))
        }
        search()
    }

    fun setCompatibleOnly(v: Boolean) {
        _state.update { it.copy(compatibleOnly = v) }
        search()
    }

    private fun effectiveQuery(s: ModsState, offset: Int): ModQuery {
        val t = s.target
        val needsLoader = s.query.type == ProjectType.Mod || s.query.type == ProjectType.Modpack
        return s.query.copy(
            offset = offset,
            gameVersion = if (s.compatibleOnly && t != null && s.query.type != ProjectType.Modpack) t.gameVersion else null,
            loader = if (s.compatibleOnly && t != null && needsLoader && t.loader != LoaderType.Vanilla) t.loader else null,
        )
    }

    @OptIn(FlowPreview::class)
    fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (debounce) delay(300)
            val cat = catalog
            if (!cat.available) {
                _state.update { it.copy(loading = false, results = emptyList(), total = 0, error = null) }
                return@launch
            }
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val q = effectiveQuery(_state.value, 0)
                val page = cat.search(q)
                val cats = runCatching { cat.categories(q.type) }.getOrDefault(emptyList())
                _state.update { it.copy(results = page.hits, total = page.total, loading = false, categories = cats) }
            }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: e::class.simpleName) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.canLoadMore) return
        scope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { catalog.search(effectiveQuery(_state.value, _state.value.results.size)) }
                .onSuccess { page -> _state.update { it.copy(results = (it.results + page.hits).distinctBy { p -> p.id }, total = page.total, loadingMore = false) } }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    // Выбор нескольких проектов галочками для установки одним окном.
    private val _selected = MutableStateFlow(restore?.selected ?: emptyMap())
    val selected: StateFlow<Map<String, ModProject>> = _selected.asStateFlow()

    fun toggleSelected(project: ModProject) = _selected.update { if (project.id in it) it - project.id else it + (project.id to project) }
    fun clearSelection() { _selected.value = emptyMap() }

    private val _sheet = MutableStateFlow<InstallSheet?>(null)
    val sheet: StateFlow<InstallSheet?> = _sheet.asStateFlow()
    private val _checking = MutableStateFlow<Set<String>>(emptySet())
    /** Проекты, для которых сейчас собирается список зависимостей (кнопка показывает индикатор). */
    val checking: StateFlow<Set<String>> = _checking.asStateFlow()

    private fun requireTarget(): Instance? = _state.value.target ?: run {
        graph.messenger.show(Res.string.mods_need_instance, error = true)
        null
    }

    /**
     * Одиночная установка: сначала предпросмотр. Если нужно докачать зависимости или что-то мешает,
     * открывается окно с подтверждением, иначе мод ставится сразу.
     */
    fun install(project: ModProject, version: ModVersion? = null) {
        val target = requireTarget() ?: return
        if (project.id in _checking.value) return
        _checking.update { it + project.id }
        scope.launch {
            val preview = graph.modInstaller.preview(target.id, project, version)
            _checking.update { it - project.id }
            if (preview.canInstall && preview.toDownload.isEmpty()) {
                runInstall(target, listOf(InstallEntry(project, version, preview, EntryStatus.Ready)), withDependencies = true, sheet = false)
            } else {
                _sheet.value = InstallSheet(listOf(InstallEntry(project, version, preview, EntryStatus.Ready)), batch = false)
            }
        }
    }

    /** Пакетная установка выбранного: окно со всеми модами и их зависимостями. */
    fun installSelected() {
        val target = requireTarget() ?: return
        val projects = _selected.value.values.toList()
        if (projects.isEmpty()) return
        _sheet.value = InstallSheet(projects.map { InstallEntry(it, null, null, EntryStatus.Loading) }, batch = true)
        projects.forEach { p ->
            scope.launch {
                val preview = graph.modInstaller.preview(target.id, p)
                updateEntry(p.id) { it.copy(preview = preview, status = EntryStatus.Ready) }
            }
        }
    }

    private fun updateEntry(id: String, transform: (InstallEntry) -> InstallEntry) =
        _sheet.update { s -> s?.copy(entries = s.entries.map { if (it.project.id == id) transform(it) else it }) }

    fun confirmInstall(withDependencies: Boolean) {
        val target = _state.value.target ?: return
        val s = _sheet.value ?: return
        if (s.running) return
        val entries = s.entries.filter { it.preview?.let { p -> p.error == null && p.version != null && p.conflicts.isEmpty() } == true }
        scope.launch { runInstall(target, entries, withDependencies, sheet = true) }
    }

    private suspend fun runInstall(target: Instance, entries: List<InstallEntry>, withDependencies: Boolean, sheet: Boolean) {
        if (sheet) _sheet.update { it?.copy(running = true) }
        val done = mutableListOf<String>()
        var deps = 0
        for (e in entries) {
            if (sheet) updateEntry(e.project.id) { it.copy(status = EntryStatus.Installing) }
            graph.modInstaller.install(target.id, e.project, e.preview?.version ?: e.version, withDependencies)
                .onSuccess { names ->
                    done += e.project.id
                    deps += (names.size - 1).coerceAtLeast(0)
                    if (sheet) updateEntry(e.project.id) { it.copy(status = EntryStatus.Done) }
                }
                .onFailure { err ->
                    if (sheet) updateEntry(e.project.id) { it.copy(status = EntryStatus.Failed, error = err.message) }
                    else graph.messenger.show(Res.string.mods_install_failed, e.project.title, err.message ?: "", error = true)
                }
        }
        val installedNow = runCatching { graph.modInstaller.installedProjectIds(target.id) }.getOrDefault(emptySet())
        _state.update { it.copy(installed = it.installed + installedNow + done) }
        if (sheet) {
            _sheet.update { it?.copy(running = false, finished = true) }
            _selected.update { it - done.toSet() }
        }
        if (done.isNotEmpty()) {
            graph.notifier.notify(getString(Res.string.notify_install_title), getString(Res.string.mods_batch_installed, done.size, deps))
        }
        when {
            done.isEmpty() -> Unit
            entries.size == 1 && deps > 0 -> graph.messenger.show(Res.string.mods_installed_with_deps, entries.first().project.title, deps)
            entries.size == 1 -> graph.messenger.show(Res.string.mods_installed_msg, entries.first().project.title, target.name)
            else -> graph.messenger.show(Res.string.mods_batch_installed, done.size, deps)
        }
    }

    fun dismissSheet() {
        if (_sheet.value?.running == true) return
        _sheet.value = null
    }
}

enum class EntryStatus { Loading, Ready, Installing, Done, Failed }

data class InstallEntry(
    val project: ModProject,
    val version: ModVersion?,
    val preview: InstallPreview?,
    val status: EntryStatus,
    val error: String? = null,
)

data class InstallSheet(
    val entries: List<InstallEntry>,
    val batch: Boolean,
    val running: Boolean = false,
    val finished: Boolean = false,
) {
    val loading: Boolean get() = entries.any { it.status == EntryStatus.Loading }
    val installable: List<InstallEntry> get() = entries.filter { it.preview?.canInstall == true }
    /** Зависимости к загрузке без повторов: общая зависимость нескольких модов качается один раз. */
    val dependenciesToDownload: Int get() = entries.flatMap { it.preview?.toDownload.orEmpty() }.map { it.projectId }
        .filterNot { id -> entries.any { it.project.id == id } }.distinct().size
    val progress: Float get() = if (entries.isEmpty()) 0f else entries.count { it.status == EntryStatus.Done || it.status == EntryStatus.Failed }.toFloat() / entries.size
}
