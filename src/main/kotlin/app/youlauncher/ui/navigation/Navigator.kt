package app.youlauncher.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import app.youlauncher.resources.Res
import app.youlauncher.resources.nav_accounts
import app.youlauncher.resources.nav_console
import app.youlauncher.resources.nav_home
import app.youlauncher.resources.nav_library
import app.youlauncher.resources.nav_mods
import app.youlauncher.resources.nav_settings
import app.youlauncher.ui.icons.Symbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource

enum class TopLevel(val label: StringResource, val icon: () -> ImageVector, val selectedIcon: () -> ImageVector) {
    Home(Res.string.nav_home, { Symbols.Home }, { Symbols.HomeFilled }),
    Library(Res.string.nav_library, { Symbols.GridView }, { Symbols.GridViewFilled }),
    Mods(Res.string.nav_mods, { Symbols.Extension }, { Symbols.ExtensionFilled }),
    Accounts(Res.string.nav_accounts, { Symbols.AccountCircle }, { Symbols.AccountCircleFilled }),
    Console(Res.string.nav_console, { Symbols.Terminal }, { Symbols.TerminalFilled }),
    Settings(Res.string.nav_settings, { Symbols.Settings }, { Symbols.SettingsFilled }),
}

enum class InstanceTab { Overview, Mods, ResourcePacks, ShaderPacks, Worlds, Servers, Screenshots, Settings, Log }

@Immutable
sealed interface Screen {
    val top: TopLevel

    data object Home : Screen { override val top = TopLevel.Home }
    data object Library : Screen { override val top = TopLevel.Library }
    data class Instance(val id: String, val tab: InstanceTab = InstanceTab.Overview) : Screen {
        override val top = TopLevel.Library
    }
    data class Mods(val instanceId: String? = null) : Screen { override val top = TopLevel.Mods }
    data class ModProject(val projectId: String, val source: String, val instanceId: String? = null) : Screen {
        override val top = TopLevel.Mods
    }
    data object Accounts : Screen { override val top = TopLevel.Accounts }
    data object Console : Screen { override val top = TopLevel.Console }
    data object Settings : Screen { override val top = TopLevel.Settings }

    companion object {
        fun of(top: TopLevel): Screen = when (top) {
            TopLevel.Home -> Home
            TopLevel.Library -> Library
            TopLevel.Mods -> Mods()
            TopLevel.Accounts -> Accounts
            TopLevel.Console -> Console
            TopLevel.Settings -> Settings
        }
    }
}

/** Простой стек экранов. Верхний уровень заменяет стек, вложенные экраны кладутся сверху. */
class Navigator(start: Screen = Screen.Home) {
    private val stack = MutableStateFlow(listOf(start))
    val backStack: StateFlow<List<Screen>> = stack.asStateFlow()
    val current: Screen get() = stack.value.last()

    fun navigate(screen: Screen) = stack.update { if (it.last() == screen) it else it + screen }

    fun replace(screen: Screen) = stack.update { it.dropLast(1) + screen }

    fun selectTop(top: TopLevel) = stack.update { listOf(Screen.of(top)) }

    fun canGoBack(): Boolean = stack.value.size > 1

    fun back(): Boolean {
        if (!canGoBack()) return false
        stack.update { it.dropLast(1) }
        return true
    }
}

val LocalNavigator = staticCompositionLocalOf { Navigator() }
