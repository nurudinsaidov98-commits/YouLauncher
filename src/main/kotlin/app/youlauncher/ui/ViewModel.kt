package app.youlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Лёгкий ViewModel: состояние в StateFlow, корутины в собственном scope, который отменяется,
 * когда экран уходит из композиции. В тестах диспетчер подменяется.
 */
abstract class ViewModel(dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    open fun onCleared() {}

    fun clear() {
        onCleared()
        scope.cancel()
    }
}

@Composable
fun <T : ViewModel> rememberViewModel(vararg keys: Any?, factory: () -> T): T {
    val vm = remember(*keys) { factory() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    return vm
}
