package app.youlauncher.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.youlauncher.AppGraph
import app.youlauncher.data.mods.CurseForgeCatalog
import app.youlauncher.data.mods.KeySource
import app.youlauncher.resources.Res
import app.youlauncher.resources.cf_key_body
import app.youlauncher.resources.cf_key_builtin
import app.youlauncher.resources.cf_key_delete
import app.youlauncher.resources.cf_key_env
import app.youlauncher.resources.cf_key_get
import app.youlauncher.resources.cf_key_hide
import app.youlauncher.resources.cf_key_invalid
import app.youlauncher.resources.cf_key_label
import app.youlauncher.resources.cf_key_save
import app.youlauncher.resources.cf_key_saved
import app.youlauncher.resources.cf_key_show
import app.youlauncher.resources.cf_key_stored
import app.youlauncher.resources.cf_key_title
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.ViewModel
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.rememberViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data object Saved : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

/** Проверяет ключ запросом к API и только после ответа сохраняет его в диспетчер учётных данных. */
class CurseForgeKeyViewModel(
    private val graph: AppGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val check: suspend (String) -> Unit = { key -> CurseForgeCatalog(graph.http, key).checkKey() },
) : ViewModel(dispatcher) {
    val source: StateFlow<KeySource> = graph.curseForgeKey.source
    private val _status = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val status: StateFlow<KeyCheck> = _status.asStateFlow()

    fun save(key: String) {
        if (key.isBlank() || _status.value == KeyCheck.Checking) return
        _status.value = KeyCheck.Checking
        scope.launch {
            _status.value = runCatching {
                withContext(Dispatchers.IO) {
                    check(key.trim())
                    graph.curseForgeKey.save(key)
                }
            }.fold({ KeyCheck.Saved }, { KeyCheck.Failed(it.message ?: it::class.simpleName.orEmpty()) })
        }
    }

    fun delete() {
        scope.launch(Dispatchers.IO) { runCatching { graph.curseForgeKey.clear() } }
        _status.value = KeyCheck.Idle
    }
}

@Composable
fun CurseForgeKeySection() {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { CurseForgeKeyViewModel(graph) }
    val source by vm.source.collectAsState()
    val status by vm.status.collectAsState()
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.cf_key_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.cf_key_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (source == KeySource.Environment) {
            Text(stringResource(Res.string.cf_key_env, CurseForgeCatalog.KEY_ENV), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            return@Column
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(Res.string.cf_key_label)) },
            placeholder = { if (source == KeySource.Stored) Text(stringResource(Res.string.cf_key_stored)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TIconButton(
                    if (visible) Symbols.VisibilityOff else Symbols.Visibility,
                    stringResource(if (visible) Res.string.cf_key_hide else Res.string.cf_key_show),
                    { visible = !visible },
                )
            },
            isError = status is KeyCheck.Failed,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        when (val s = status) {
            KeyCheck.Saved -> Text(stringResource(Res.string.cf_key_saved), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            is KeyCheck.Failed -> Text(stringResource(Res.string.cf_key_invalid, s.message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            else -> when (source) {
                KeySource.Stored -> Text(stringResource(Res.string.cf_key_stored), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                KeySource.BuiltIn -> Text(stringResource(Res.string.cf_key_builtin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                else -> Unit
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { vm.save(key); key = "" },
                enabled = key.isNotBlank() && status != KeyCheck.Checking,
                shapes = ButtonDefaults.shapes(),
            ) {
                if (status == KeyCheck.Checking) LoadingIndicator(Modifier.size(20.dp)) else IconLabel(Symbols.Key, stringResource(Res.string.cf_key_save))
            }
            if (source == KeySource.Stored) {
                OutlinedButton(onClick = { vm.delete() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Delete, stringResource(Res.string.cf_key_delete)) }
            }
            TextButton(onClick = { Platform.openUrl("https://console.curseforge.com/") }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.OpenInNew, stringResource(Res.string.cf_key_get))
            }
        }
    }
}
