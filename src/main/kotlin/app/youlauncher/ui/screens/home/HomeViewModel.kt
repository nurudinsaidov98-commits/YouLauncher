package app.youlauncher.ui.screens.home

import app.youlauncher.AppGraph
import app.youlauncher.core.model.Account
import app.youlauncher.core.model.Instance
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.ui.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeStats(
    val totalPlayMs: Long,
    val launches: Int,
    val instances: Int,
    /** Минуты по дням, от старого к новому, ровно 14 значений. */
    val last14: List<Int>,
)

data class HomeState(
    val instances: List<Instance> = emptyList(),
    val selected: Instance? = null,
    val launch: LaunchState = LaunchState.Idle,
    val account: Account? = null,
    val stats: HomeStats = HomeStats(0, 0, 0, List(14) { 0 }),
    val lastPlayed: Instance? = null,
)

class HomeViewModel(
    private val graph: AppGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    val state: StateFlow<HomeState> = combine(
        graph.instances.instances,
        graph.launcher.states,
        graph.settings.settings,
        graph.accounts.accounts,
        graph.accounts.activeId,
    ) { instances, launches, settings, accounts, activeId ->
        val sorted = instances.sortedByDescending { it.lastPlayedAt ?: it.createdAt }
        val selected = sorted.firstOrNull { it.id == settings.lastInstanceId } ?: sorted.firstOrNull()
        HomeState(
            instances = sorted,
            selected = selected,
            launch = selected?.let { launches[it.id] } ?: LaunchState.Idle,
            account = accounts.firstOrNull { it.id == activeId },
            stats = computeStats(instances),
            lastPlayed = instances.filter { it.lastPlayedAt != null }.maxByOrNull { it.lastPlayedAt!! },
        )
    }.stateIn(scope, SharingStarted.Eagerly, HomeState())

    fun select(instance: Instance) = graph.settings.update { it.copy(lastInstanceId = instance.id) }

    fun play() {
        val inst = state.value.selected ?: return
        graph.settings.update { it.copy(lastInstanceId = inst.id) }
        graph.launcher.launch(inst.id)
    }

    fun play(instance: Instance) {
        graph.settings.update { it.copy(lastInstanceId = instance.id) }
        graph.launcher.launch(instance.id)
    }

    fun stop() = state.value.selected?.let { graph.launcher.stop(it.id) }

    fun dismiss() = state.value.selected?.let { graph.launcher.dismiss(it.id) }

    companion object {
        fun computeStats(instances: List<Instance>, today: LocalDate = LocalDate.now()): HomeStats {
            val days = (13 downTo 0).map { today.minusDays(it.toLong()).toString() }
            val perDay = days.map { day -> instances.sumOf { it.playLog[day] ?: 0 } }
            return HomeStats(
                totalPlayMs = instances.sumOf { it.totalPlayTimeMs },
                launches = instances.sumOf { it.launches },
                instances = instances.size,
                last14 = perDay,
            )
        }
    }
}
