package app.youlauncher.ui.background

import androidx.compose.runtime.Immutable
import app.youlauncher.resources.*
import org.jetbrains.compose.resources.StringResource
import com.materialkolor.hct.Hct
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** Встроенные обои: сгенерированы кодом (стиль + оттенок seed), в репозитории нет ни одного изображения. */
@Immutable
data class BuiltinWallpaper(val id: String, val style: ArtStyle, val hue: Double, val chroma: Double = 40.0, val name: StringResource) {
    val seedArgb: Int get() = Hct.from(hue, chroma, 50.0).toInt()
    val key: String get() = "builtin:$id"
    val artSeed: Long get() = SceneGenerator.hash(id)
}

object BuiltinWallpapers {
    val all: List<BuiltinWallpaper> = listOf(
        BuiltinWallpaper("dawn-blobs", ArtStyle.Blobs, 25.0, name = Res.string.wp_dawn_blobs),
        BuiltinWallpaper("lagoon-blobs", ArtStyle.Blobs, 185.0, name = Res.string.wp_lagoon_blobs),
        BuiltinWallpaper("meadow-hills", ArtStyle.Hills, 130.0, name = Res.string.wp_meadow_hills),
        BuiltinWallpaper("dusk-hills", ArtStyle.Hills, 300.0, name = Res.string.wp_dusk_hills),
        BuiltinWallpaper("coral-arcs", ArtStyle.Arcs, 15.0, 48.0, name = Res.string.wp_coral_arcs),
        BuiltinWallpaper("mint-arcs", ArtStyle.Arcs, 160.0, name = Res.string.wp_mint_arcs),
        BuiltinWallpaper("berry-bubbles", ArtStyle.Bubbles, 330.0, name = Res.string.wp_berry_bubbles),
        BuiltinWallpaper("sky-bubbles", ArtStyle.Bubbles, 230.0, name = Res.string.wp_sky_bubbles),
        BuiltinWallpaper("amber-horizon", ArtStyle.Horizon, 60.0, 50.0, name = Res.string.wp_amber_horizon),
        BuiltinWallpaper("ocean-horizon", ArtStyle.Horizon, 250.0, name = Res.string.wp_ocean_horizon),
        BuiltinWallpaper("lilac-mesh", ArtStyle.Mesh, 280.0, name = Res.string.wp_lilac_mesh),
        BuiltinWallpaper("citrus-mesh", ArtStyle.Mesh, 95.0, 50.0, name = Res.string.wp_citrus_mesh),
        BuiltinWallpaper("garden-collage", ArtStyle.Collage, 140.0, name = Res.string.wp_garden_collage),
        BuiltinWallpaper("candy-collage", ArtStyle.Collage, 350.0, name = Res.string.wp_candy_collage),
        BuiltinWallpaper("tide-waves", ArtStyle.Waves, 200.0, name = Res.string.wp_tide_waves),
        BuiltinWallpaper("ember-waves", ArtStyle.Waves, 35.0, 50.0, name = Res.string.wp_ember_waves),
        BuiltinWallpaper("rose-morph", ArtStyle.Morph, 345.0, 46.0, name = Res.string.wp_rose_morph),
        BuiltinWallpaper("jade-morph", ArtStyle.Morph, 160.0, name = Res.string.wp_jade_morph),
        BuiltinWallpaper("blossom-petals", ArtStyle.Petals, 350.0, 44.0, name = Res.string.wp_blossom_petals),
        BuiltinWallpaper("autumn-petals", ArtStyle.Petals, 35.0, 52.0, name = Res.string.wp_autumn_petals),
        BuiltinWallpaper("cosmic-orbits", ArtStyle.Orbits, 265.0, name = Res.string.wp_cosmic_orbits),
        BuiltinWallpaper("citrine-orbits", ArtStyle.Orbits, 85.0, 48.0, name = Res.string.wp_citrine_orbits),
        BuiltinWallpaper("silk-ribbons", ArtStyle.Ribbons, 300.0, name = Res.string.wp_silk_ribbons),
        BuiltinWallpaper("tide-ribbons", ArtStyle.Ribbons, 195.0, name = Res.string.wp_tide_ribbons),
        BuiltinWallpaper("polar-aurora", ArtStyle.Aurora, 170.0, 48.0, name = Res.string.wp_polar_aurora),
        BuiltinWallpaper("violet-aurora", ArtStyle.Aurora, 285.0, name = Res.string.wp_violet_aurora),
        BuiltinWallpaper("party-confetti", ArtStyle.Confetti, 20.0, 50.0, name = Res.string.wp_party_confetti),
        BuiltinWallpaper("mint-confetti", ArtStyle.Confetti, 150.0, name = Res.string.wp_mint_confetti),
    )

    fun byKey(key: String?): BuiltinWallpaper? = key?.removePrefix("builtin:")?.let { id -> all.firstOrNull { it.id == id } }
}

/** Ссылка на обои в настройках: "builtin:<id>" или "file:<путь>". */
sealed interface WallpaperRef {
    data class Builtin(val wallpaper: BuiltinWallpaper) : WallpaperRef
    data class File(val path: Path) : WallpaperRef

    val key: String get() = when (this) {
        is Builtin -> wallpaper.key
        is File -> "file:$path"
    }

    companion object {
        fun parse(key: String?): WallpaperRef? = when {
            key == null -> null
            key.startsWith("builtin:") -> BuiltinWallpapers.byKey(key)?.let { Builtin(it) }
            key.startsWith("file:") -> File(Path.of(key.removePrefix("file:")))
            else -> null
        }
    }
}

/**
 * Свои обои пользователя: копии в %APPDATA%\YouLauncher\wallpapers и необязательная внешняя папка.
 * Лаунчер ничего не скачивает и не распространяет: только то, что пользователь добавил сам.
 */
object UserWallpapers {
    val extensions = setOf("png", "jpg", "jpeg", "webp", "bmp")

    fun list(vararg dirs: Path?): List<Path> = dirs.filterNotNull().filter { it.exists() }.flatMap { dir ->
        runCatching { Files.list(dir).use { s -> s.filter { it.isRegularFile() && it.extension.lowercase() in extensions }.toList() } }.getOrDefault(emptyList())
    }.sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0) }

    /** Копирует изображение в папку обоев лаунчера и возвращает путь к копии. */
    fun import(source: Path, wallpapersDir: Path): Path? {
        if (source.extension.lowercase() !in extensions) return null
        Files.createDirectories(wallpapersDir)
        var target = wallpapersDir.resolve(source.name)
        var n = 2
        while (target.exists() && Files.size(target) != Files.size(source)) {
            target = wallpapersDir.resolve("${source.name.substringBeforeLast('.')}-$n.${source.extension}")
            n++
        }
        if (!target.exists()) Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
        return target
    }

    const val WALLPAPERS_BY_GOOGLE = "https://play.google.com/store/apps/details?id=com.google.android.apps.wallpaper"
}
