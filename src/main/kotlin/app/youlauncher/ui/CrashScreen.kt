package app.youlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.resources.*
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.IllustrationArt
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.YouLauncherTheme
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

/**
 * Экран при неожиданной ошибке интерфейса: окно не закрывается, отчёт без секретов уже сохранён.
 * Можно вернуться в лаунчер, открыть или скопировать отчёт, закрыть программу.
 */
@Composable
fun CrashScreen(
    settings: LauncherSettings,
    report: String,
    reportFile: Path?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    YouLauncherTheme(settings.theme, settings.ui) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 720.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    IllustrationArt(Illustration.Crash, 160.dp)
                    Text(stringResource(Res.string.crash_title), style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
                    Text(
                        stringResource(if (canRetry) Res.string.crash_body else Res.string.crash_body_repeated),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        Text(
                            report.lines().take(40).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canRetry) Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.RestartAlt, stringResource(Res.string.crash_retry)) }
                        FilledTonalButton(onClick = { Platform.copy(report) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.ContentCopy, stringResource(Res.string.crash_copy)) }
                        // Текст ошибки уже в буфере обмена: в форме на GitHub его остаётся только вставить.
                        FilledTonalButton(onClick = { Platform.copy(report); app.youlauncher.ui.screens.settings.AppInfo.reportIssue() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Forum, stringResource(Res.string.report_issue)) }
                        if (reportFile != null) {
                            FilledTonalButton(onClick = { Platform.openFolder(reportFile.parent) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.FolderOpen, stringResource(Res.string.crash_open)) }
                        }
                        OutlinedButton(onClick = onClose, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Close, stringResource(Res.string.crash_close)) }
                    }
                }
            }
        }
    }
}
