package app.youlauncher.ui.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.youlauncher.core.settings.MotionLevel
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.resources.Res
import app.youlauncher.resources.cmd_action
import app.youlauncher.resources.cmd_add_account
import app.youlauncher.resources.cmd_go_to
import app.youlauncher.resources.cmd_instance
import app.youlauncher.resources.cmd_motion_toggle
import app.youlauncher.resources.cmd_new_instance
import app.youlauncher.resources.cmd_open_data
import app.youlauncher.resources.cmd_open_instance
import app.youlauncher.resources.cmd_play_instance
import app.youlauncher.resources.cmd_theme_toggle
import app.youlauncher.resources.profile_open
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.Platform
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.theme.isDarkTheme
import org.jetbrains.compose.resources.stringResource

/** Набор команд палитры: разделы, действия и все экземпляры (играть, открыть). */
@Composable
fun rememberPaletteCommands(@Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit): List<PaletteCommand> {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val ui = LocalAppUi.current
    val settings by graph.settings.settings.collectAsState()
    val instances by graph.instances.instances.collectAsState()
    val dark = isDarkTheme(settings.theme.mode)
    val hotkeys = settings.ui.hotkeys
    val actionLabel = stringResource(Res.string.cmd_action)

    val goTo = TopLevel.entries.map { top ->
        PaletteCommand(
            id = "go:${top.name}",
            title = stringResource(top.label),
            subtitle = stringResource(Res.string.cmd_go_to),
            icon = top.icon(),
            keywords = top.name,
            shortcut = hotkeys[top.name.lowercase()],
            action = { navigator.selectTop(top) },
        )
    }
    val actions = listOf(
        PaletteCommand(
            id = "new-instance", title = stringResource(Res.string.cmd_new_instance), subtitle = actionLabel,
            icon = Symbols.AddCircle, keywords = "new create instance новый создать", shortcut = hotkeys["newInstance"],
            action = { navigator.selectTop(TopLevel.Library); ui.pendingAction = "newInstance" },
        ),
        PaletteCommand(
            id = "profile", title = stringResource(Res.string.profile_open), subtitle = actionLabel,
            icon = Symbols.AccountCircle, keywords = "profile avatar nick профиль аватар ник",
            action = { ui.pendingAction = "profile" },
        ),
        PaletteCommand(
            id = "add-account", title = stringResource(Res.string.cmd_add_account), subtitle = actionLabel,
            icon = Symbols.PersonAdd, keywords = "account login ely аккаунт вход",
            action = { navigator.selectTop(TopLevel.Accounts) },
        ),
        PaletteCommand(
            id = "theme", title = stringResource(Res.string.cmd_theme_toggle), subtitle = actionLabel,
            icon = if (dark) Symbols.LightMode else Symbols.DarkMode, keywords = "theme dark light тема",
            action = { graph.settings.update { it.copy(theme = it.theme.copy(mode = if (dark) ThemeMode.Light else ThemeMode.Dark)) } },
        ),
        PaletteCommand(
            id = "motion", title = stringResource(Res.string.cmd_motion_toggle), subtitle = actionLabel,
            icon = Symbols.Animation, keywords = "motion animation анимации",
            action = {
                graph.settings.update {
                    val next = if (it.ui.motion == MotionLevel.Full) MotionLevel.Reduced else MotionLevel.Full
                    it.copy(ui = it.ui.copy(motion = next))
                }
            },
        ),
        PaletteCommand(
            id = "data", title = stringResource(Res.string.cmd_open_data), subtitle = actionLabel,
            icon = Symbols.FolderOpen, keywords = "folder data appdata папка данные",
            action = { Platform.openFolder(graph.dirs.root) },
        ),
    )
    val instanceCommands = instances.flatMap { inst ->
        val subtitle = stringResource(Res.string.cmd_instance, "${inst.gameVersion} ${inst.loader.title}")
        listOf(
            PaletteCommand(
                id = "play:${inst.id}", title = stringResource(Res.string.cmd_play_instance, inst.name), subtitle = subtitle,
                icon = Symbols.PlayArrow, keywords = "play играть ${inst.gameVersion} ${inst.loader.title}",
                action = { graph.settings.update { it.copy(lastInstanceId = inst.id) }; graph.launcher.launch(inst.id) },
            ),
            PaletteCommand(
                id = "open:${inst.id}", title = stringResource(Res.string.cmd_open_instance, inst.name), subtitle = subtitle,
                icon = Symbols.OpenInFull, keywords = "open открыть ${inst.gameVersion}",
                action = { navigator.navigate(Screen.Instance(inst.id)) },
            ),
        )
    }
    return goTo + actions + instanceCommands
}
