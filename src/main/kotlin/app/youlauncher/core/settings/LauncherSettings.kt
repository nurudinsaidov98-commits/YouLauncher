package app.youlauncher.core.settings

import kotlinx.serialization.Serializable

/** Все настройки лаунчера. Хранятся в settings.json, новые поля получают значения по умолчанию. */
@Serializable
data class LauncherSettings(
    val schema: Int = CURRENT_SCHEMA,
    val language: AppLanguage = AppLanguage.System,
    val theme: ThemeSettings = ThemeSettings(),
    val ui: UiSettings = UiSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    val game: GameDefaults = GameDefaults(),
    val java: JavaSettings = JavaSettings(),
    val network: NetworkSettings = NetworkSettings(),
    val system: SystemSettings = SystemSettings(),
    val activeAccountId: String? = null,
    val lastInstanceId: String? = null,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

@Serializable
enum class AppLanguage(val tag: String?) { System(null), Russian("ru"), English("en") }

@Serializable
enum class ThemeMode { System, Light, Dark }

/** Стили палитры MaterialKolor, которые предлагаются пользователю. */
@Serializable
enum class PaletteStyleOption { TonalSpot, Neutral, Vibrant, Expressive, Rainbow, FruitSalad, Monochrome, Fidelity, Content }

@Serializable
enum class ContrastOption(val level: Double) { Reduced(-1.0), Standard(0.0), Medium(0.5), High(1.0) }

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    /** ARGB seed-цвета. null: первый цвет из списка seed-цветов в Theme.kt. */
    val seed: Long? = null,
    val style: PaletteStyleOption = PaletteStyleOption.Expressive,
    val contrast: ContrastOption = ContrastOption.Standard,
    val amoled: Boolean = false,
    /** Брать seed из текущих обоев. */
    val colorsFromWallpaper: Boolean = false,
    val presetId: String? = null,
    /** Свои цвета ролей схемы: ключ роли ("primary", "surfaceContainer"...) -> ARGB. */
    val colorOverrides: Map<String, Long> = emptyMap(),
    /** Второй ARGB-цвет: акцент плавно переливается между seed и им. null — без переливания. */
    val shimmer: Long? = null,
)

@Serializable
enum class MotionLevel { Full, Reduced, Off }

/** Сила отскока пространственных пружин (движение, масштаб, морфинг форм). */
@Serializable
enum class SpringBounce { Standard, Bouncy, Jelly }

@Serializable
enum class NavLayout { Side, Bottom }

@Serializable
enum class UiDensity { Comfortable, Compact }

@Serializable
enum class FontChoice { GoogleSansFlex, System }

@Serializable
enum class WindowMode { Normal, Compact, Mini }

@Serializable
enum class HomeWidget { ContinueCard, RecentInstances, LastSession, Stats, Shortcuts }

@Serializable
data class UiSettings(
    val scale: Float = 1f,
    val fontScale: Float = 1f,
    val font: FontChoice = FontChoice.GoogleSansFlex,
    val cornerScale: Float = 1f,
    val density: UiDensity = UiDensity.Comfortable,
    val motion: MotionLevel = MotionLevel.Full,
    val bounce: SpringBounce = SpringBounce.Bouncy,
    val navigation: NavLayout = NavLayout.Side,
    val railExpanded: Boolean = false,
    val windowMode: WindowMode = WindowMode.Normal,
    val homeWidgets: List<HomeWidget> = listOf(
        HomeWidget.ContinueCard, HomeWidget.RecentInstances, HomeWidget.Stats, HomeWidget.Shortcuts, HomeWidget.LastSession,
    ),
    /** Действие -> сочетание клавиш, например "palette" -> "Ctrl+K". */
    val hotkeys: Map<String, String> = DefaultHotkeys,
    val sounds: Boolean = false,
    val notifications: Boolean = true,
)

val DefaultHotkeys: Map<String, String> = mapOf(
    "palette" to "Ctrl+K",
    "home" to "Ctrl+1",
    "library" to "Ctrl+2",
    "mods" to "Ctrl+3",
    "accounts" to "Ctrl+4",
    "console" to "Ctrl+5",
    "settings" to "Ctrl+Comma",
    "play" to "Ctrl+Enter",
    "newInstance" to "Ctrl+N",
)

@Serializable
enum class LiveBackground { Blobs, Waves, MeshGradient, DriftingShapes, Calm, Morph, Petals, Orbits, Ribbons, Aurora, Confetti }

@Serializable
enum class BackgroundKind { Live, Wallpaper, VersionScene }

@Serializable
data class BackgroundSettings(
    val kind: BackgroundKind = BackgroundKind.Live,
    val live: LiveBackground = LiveBackground.Blobs,
    /** id встроенных обоев ("builtin:3") или путь к своему изображению ("file:C:\..."). */
    val wallpaper: String? = null,
    val brightness: Float = 1f,
    val scrim: Float = 0.35f,
    val blur: Float = 0.6f,
    val parallax: Boolean = true,
    val saver: Boolean = false,
    /** Сцена версии на главной и в библиотеке, когда выбран экземпляр. */
    val versionAtmosphere: Boolean = true,
    val userWallpaperFolder: String? = null,
)

@Serializable
data class GameDefaults(
    val minMemoryMb: Int = 1024,
    val maxMemoryMb: Int = 4096,
    val jvmArgs: String = "",
    val jvmPreset: JvmPreset = JvmPreset.Aikar,
    val env: Map<String, String> = emptyMap(),
    val width: Int = 1280,
    val height: Int = 720,
    val fullscreen: Boolean = false,
    val preLaunchCommand: String = "",
    val postExitCommand: String = "",
    val wrapperCommand: String = "",
    val closeLauncherOnStart: Boolean = false,
    val showConsoleOnStart: Boolean = true,
)

@Serializable
enum class JvmPreset { None, Aikar, Zgc, FastStart }

@Serializable
data class JavaSettings(
    /** Путь к java.exe, выбранный глобально. null: автоподбор по версии игры. */
    val globalJavaPath: String? = null,
    val autoDownload: Boolean = true,
    val extraSearchPaths: List<String> = emptyList(),
)

@Serializable
data class NetworkSettings(
    val proxy: ProxySettings = ProxySettings(),
    val maxConcurrentDownloads: Int = 8,
    /** КБ/с, 0: без ограничения. */
    val speedLimitKbps: Int = 0,
    val retries: Int = 4,
)

@Serializable
enum class ProxyType { None, Http, Socks }

@Serializable
data class ProxySettings(
    val type: ProxyType = ProxyType.None,
    val host: String = "",
    val port: Int = 0,
)

@Serializable
enum class UpdateChannel { Stable, Beta, Off }

@Serializable
data class SystemSettings(
    val updateChannel: UpdateChannel = UpdateChannel.Stable,
    val autoCleanCache: Boolean = true,
    val cacheMaxAgeDays: Int = 30,
)
