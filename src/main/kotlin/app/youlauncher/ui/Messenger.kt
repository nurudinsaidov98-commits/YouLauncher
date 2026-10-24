package app.youlauncher.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Сообщение для снекбара. Текст берётся из ресурсов, чтобы он был переведён. */
data class UiMessage(
    val text: StringResource,
    val args: List<Any> = emptyList(),
    val actionLabel: StringResource? = null,
    val action: (() -> Unit)? = null,
    val error: Boolean = false,
    /** Готовый текст (например, сообщение сервера), если не задан ресурс. */
    val raw: String? = null,
)

/** Очередь сообщений приложения: ViewModel кладут сообщения, оболочка показывает снекбары. */
class Messenger {
    private val channel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = channel.receiveAsFlow()

    fun show(message: UiMessage) {
        channel.trySend(message)
    }

    fun show(text: StringResource, vararg args: Any, error: Boolean = false) = show(UiMessage(text, args.toList(), error = error))
}

@Composable
fun MessengerEffect(messenger: Messenger, host: SnackbarHostState, onShow: (UiMessage) -> Unit = {}) {
    val show = androidx.compose.runtime.rememberUpdatedState(onShow)
    LaunchedEffect(messenger) {
        messenger.messages.collect { m ->
            show.value(m)
            val text = m.raw ?: getString(m.text, *m.args.toTypedArray())
            val label = m.actionLabel?.let { getString(it) }
            val result = host.showSnackbar(
                message = text,
                actionLabel = label,
                withDismissAction = label == null,
                duration = if (label != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }
}
