package app.youlauncher.ui.screens.library

import app.youlauncher.AppGraph
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceSort
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.resources.Res
import app.youlauncher.resources.msg_import_failed
import app.youlauncher.resources.msg_import_started
import app.youlauncher.resources.msg_imported
import app.youlauncher.resources.msg_instance_created
import app.youlauncher.resources.msg_instance_deleted
import app.youlauncher.resources.msg_instance_duplicated
import app.youlauncher.resources.msg_undo
import app.youlauncher.ui.UiMessage
import app.youlauncher.ui.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryFilter(
    val query: String = "",
    val loader: LoaderType? = null,
    val favoritesOnly: Boolean = false,
    val group: String? = null,
    val sort: InstanceSort = InstanceSort.LastPlayed,
) {
    val isDefault: Boolean get() = query.isBlank() && loader == null && !favoritesOnly && group == null
}

data class LibraryState(
    val all: List<Instance> = emptyList(),
    val shown: List<Instance> = emptyList(),
    val groups: List<String> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val launches: Map<String, LaunchState> = emptyMap(),
)

/** Логика фильтрации и сортировки вынесена отдельно, чтобы её проверяли тесты. */
object LibraryLogic {
    fun apply(instances: List<Instance>, f: LibraryFilter): List<Instance> {
        val q = f.query.trim().lowercase()
        val filtered = instances.filter { i ->
            (q.isEmpty() || i.name.lowercase().contains(q) || i.gameVersion.contains(q) || i.tags.any { it.lowercase().contains(q) } ||
                i.loader.title.lowercase().contains(q)) &&
                (f.loader == null || i.loader == f.loader) &&
                (!f.favoritesOnly || i.favorite) &&
                (f.group == null || i.group == f.group)
        }
        val sorted = when (f.sort) {
            InstanceSort.LastPlayed -> filtered.sortedByDescending { it.lastPlayedAt ?: 0L }
            InstanceSort.Name -> filtered.sortedBy { it.name.lowercase() }
            InstanceSort.PlayTime -> filtered.sortedByDescending { it.totalPlayTimeMs }
            InstanceSort.Created -> filtered.sortedByDescending { it.createdAt }
            InstanceSort.Version -> filtered.sortedWith(compareByDescending<Instance, String>(VersionComparator) { it.gameVersion })
        }
        // Избранное всегда сверху, порядок внутри групп сохраняется.
        return sorted.sortedByDescending { it.favorite }
    }

    fun uniqueName(base: String, existing: Collection<String>): String {
        if (base !in existing) return base
        var n = 2
        while ("$base $n" in existing) n++
        return "$base $n"
    }
}

/** Сравнение версий вида 1.21.10 / 26.1 / 1.8.9 по числам; снапшоты и прочее сравниваются строками. */
object VersionComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val pa = a.split('.', '-').map { it.toIntOrNull() }
        val pb = b.split('.', '-').map { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: -1
            val y = pb.getOrNull(i) ?: -1
            if (x != y) return x.compareTo(y)
        }
        return a.compareTo(b)
    }
}

class LibraryViewModel(
    private val graph: AppGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    private val filter = MutableStateFlow(LibraryFilter())
    private val pendingDelete = MutableStateFlow<Set<String>>(emptySet())
    private val deleteJobs = mutableMapOf<String, Job>()

    val state: StateFlow<LibraryState> = combine(graph.instances.instances, filter, pendingDelete, graph.launcher.states) { all, f, pending, launches ->
        val visible = all.filterNot { it.id in pending }
        LibraryState(
            all = visible,
            shown = LibraryLogic.apply(visible, f),
            groups = visible.mapNotNull { it.group }.distinct().sorted(),
            filter = f,
            launches = launches,
        )
    }.stateIn(scope, SharingStarted.Eagerly, LibraryState())

    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setLoader(l: LoaderType?) = filter.update { it.copy(loader = l) }
    fun setFavoritesOnly(v: Boolean) = filter.update { it.copy(favoritesOnly = v, group = if (v) null else it.group) }
    fun setGroup(g: String?) = filter.update { it.copy(group = g, favoritesOnly = false) }
    fun setSort(s: InstanceSort) = filter.update { it.copy(sort = s) }
    fun reset() = filter.update { LibraryFilter(sort = it.sort) }

    fun play(id: String) {
        graph.settings.update { it.copy(lastInstanceId = id) }
        graph.launcher.launch(id)
    }

    fun stop(id: String) = graph.launcher.stop(id)

    fun toggleFavorite(id: String) = scope.launch { graph.instances.update(id) { it.copy(favorite = !it.favorite) } }

    fun rename(id: String, name: String) = scope.launch { graph.instances.update(id) { it.copy(name = name) } }

    fun duplicate(id: String, name: String) = scope.launch {
        graph.instances.duplicate(id, name)?.let { graph.messenger.show(Res.string.msg_instance_duplicated, it.name) }
    }

    fun create(spec: NewInstanceSpec, onCreated: (Instance) -> Unit = {}) = scope.launch {
        val instance = graph.instances.create(spec)
        graph.messenger.show(Res.string.msg_instance_created, instance.name)
        onCreated(instance)
    }

    /** Удаление с возможностью отмены: экземпляр сразу скрывается, а удаляется через несколько секунд. */
    fun delete(instance: Instance) {
        pendingDelete.update { it + instance.id }
        deleteJobs[instance.id] = scope.launch {
            delay(8_000)
            graph.instances.delete(instance.id)
            pendingDelete.update { it - instance.id }
        }
        graph.messenger.show(
            UiMessage(
                Res.string.msg_instance_deleted, listOf(instance.name),
                actionLabel = Res.string.msg_undo,
                action = { undoDelete(instance.id) },
            ),
        )
    }

    fun undoDelete(id: String) {
        deleteJobs.remove(id)?.cancel()
        pendingDelete.update { it - id }
    }

    override fun onCleared() {
        // Если экран закрыли до истечения таймера, удаление всё равно выполняется.
        val ids = pendingDelete.value
        if (ids.isNotEmpty()) graph.scope.launch { ids.forEach { graph.instances.delete(it) } }
    }

    /** Импорт сборки или экземпляра другого лаунчера в новый экземпляр. */
    fun import(path: java.nio.file.Path, onDone: (Instance) -> Unit = {}) = scope.launch {
        val name = LibraryLogic.uniqueName(path.fileName.toString().substringBeforeLast('.'), existingNames())
        graph.messenger.show(Res.string.msg_import_started, name)
        runCatching { graph.packImporter.import(path, name) }
            .onSuccess { graph.messenger.show(Res.string.msg_imported, it.name); onDone(it) }
            .onFailure { graph.messenger.show(Res.string.msg_import_failed, it.message ?: it::class.simpleName.orEmpty(), error = true) }
    }

    fun existingNames(): Set<String> = graph.instances.instances.value.map { it.name }.toSet()
}
