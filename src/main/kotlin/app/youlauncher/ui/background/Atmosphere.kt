package app.youlauncher.ui.background

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.youlauncher.core.model.Instance
import app.youlauncher.core.settings.BackgroundKind
import app.youlauncher.core.settings.BackgroundSettings
import app.youlauncher.core.settings.LiveBackground
import app.youlauncher.ui.navigation.Screen

/** Что рисуется за интерфейсом. */
@Immutable
sealed interface Backdrop {
    data class Live(val style: ArtStyle) : Backdrop
    data class Wallpaper(val ref: WallpaperRef) : Backdrop
    data class Scene(val key: String) : Backdrop
}

@Immutable
data class Atmosphere(
    val backdrop: Backdrop,
    /** Seed темы, если атмосфера задаёт свой цвет (экземпляр или «цвета из обоев» для встроенных обоев). */
    val seedOverride: Color? = null,
    /** Нужно извлечь цвет темы из файла обоев. */
    val extractSeedFrom: WallpaperRef.File? = null,
    val scrim: Float,
    val blur: Float,
    val brightness: Float,
)

fun LiveBackground.toArt(): ArtStyle = when (this) {
    LiveBackground.Blobs -> ArtStyle.Blobs
    LiveBackground.Waves -> ArtStyle.Waves
    LiveBackground.MeshGradient -> ArtStyle.Mesh
    LiveBackground.DriftingShapes -> ArtStyle.Drifting
    LiveBackground.Calm -> ArtStyle.Calm
    LiveBackground.Morph -> ArtStyle.Morph
    LiveBackground.Petals -> ArtStyle.Petals
    LiveBackground.Orbits -> ArtStyle.Orbits
    LiveBackground.Ribbons -> ArtStyle.Ribbons
    LiveBackground.Aurora -> ArtStyle.Aurora
    LiveBackground.Confetti -> ArtStyle.Confetti
}

/**
 * Выбор атмосферы по текущему экрану. Чистая функция: предпросмотр версии в мастере важнее всего,
 * затем атмосфера открытого экземпляра, затем общий фон из настроек.
 */
object AtmosphereResolver {
    fun resolve(
        bg: BackgroundSettings,
        colorsFromWallpaper: Boolean,
        screen: Screen,
        instances: List<Instance>,
        previewVersion: String?,
        lastInstanceId: String?,
    ): Atmosphere {
        val base = Atmosphere(global(bg, instances, lastInstanceId), scrim = bg.scrim, blur = bg.blur, brightness = bg.brightness)
        if (previewVersion != null && bg.versionAtmosphere) {
            return base.copy(backdrop = Backdrop.Scene("version:$previewVersion"))
        }
        val instance = (screen as? Screen.Instance)?.let { s -> instances.firstOrNull { it.id == s.id } }
        if (instance != null) {
            val a = instance.atmosphere
            val ref = WallpaperRef.parse(a.wallpaper)
            val backdrop = when {
                ref != null -> Backdrop.Wallpaper(ref)
                a.useVersionScene && bg.versionAtmosphere -> Backdrop.Scene("version:${instance.gameVersion}")
                else -> base.backdrop
            }
            // Свой цвет экземпляра важнее; иначе при «цветах из обоев» тема берётся из фона экземпляра.
            val ownSeed = a.seed?.let { Color(it.toInt()).copy(alpha = 1f) }
            val fromWallpaper = colorsFromWallpaper && ownSeed == null
            return base.copy(
                backdrop = backdrop,
                seedOverride = ownSeed ?: (ref as? WallpaperRef.Builtin)?.takeIf { fromWallpaper }?.let { Color(it.wallpaper.seedArgb) },
                extractSeedFrom = (ref as? WallpaperRef.File)?.takeIf { fromWallpaper },
                scrim = a.scrim ?: bg.scrim,
            )
        }
        val withSeed = if (colorsFromWallpaper) {
            when (val b = base.backdrop) {
                is Backdrop.Wallpaper -> when (val r = b.ref) {
                    is WallpaperRef.Builtin -> base.copy(seedOverride = Color(r.wallpaper.seedArgb))
                    is WallpaperRef.File -> base.copy(extractSeedFrom = r)
                }
                else -> base
            }
        } else base
        return withSeed
    }

    private fun global(bg: BackgroundSettings, instances: List<Instance>, lastInstanceId: String?): Backdrop = when (bg.kind) {
        BackgroundKind.Live -> Backdrop.Live(bg.live.toArt())
        BackgroundKind.Wallpaper -> WallpaperRef.parse(bg.wallpaper)?.let { Backdrop.Wallpaper(it) } ?: Backdrop.Live(bg.live.toArt())
        BackgroundKind.VersionScene -> {
            val inst = instances.firstOrNull { it.id == lastInstanceId } ?: instances.maxByOrNull { it.lastPlayedAt ?: 0 }
            inst?.let { Backdrop.Scene("version:${it.gameVersion}") } ?: Backdrop.Live(bg.live.toArt())
        }
    }

    /** Спецификация сцены по ключу "version:<номер>" или произвольному ключу пресета. */
    fun sceneSpec(key: String): SceneSpec =
        if (key.startsWith("version:")) SceneGenerator.forVersion(key.removePrefix("version:")) else SceneGenerator.forKey(key)
}
