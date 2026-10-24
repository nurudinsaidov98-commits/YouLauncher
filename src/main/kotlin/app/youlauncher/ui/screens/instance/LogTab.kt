package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.youlauncher.resources.Res
import app.youlauncher.resources.log_empty_body
import app.youlauncher.resources.log_empty_title
import app.youlauncher.resources.log_open_console
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.screens.console.LogView
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogTab(vm: InstanceViewModel) {
    val lines by vm.log.collectAsState()
    val navigator = LocalNavigator.current
    if (lines.isEmpty()) {
        EmptyState(Illustration.Console, stringResource(Res.string.log_empty_title), stringResource(Res.string.log_empty_body))
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            FilledTonalButton(onClick = { navigator.selectTop(TopLevel.Console) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Terminal, stringResource(Res.string.log_open_console))
            }
        }
        LogView(lines, "", follow = true, modifier = Modifier.fillMaxSize())
    }
}
