package app.youlauncher.ui.background

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceAtmosphere
import app.youlauncher.core.settings.AtmospherePresetRepository
import app.youlauncher.core.settings.BackgroundKind
import app.youlauncher.core.settings.BackgroundSettings
import app.youlauncher.core.settings.BuiltinPresets
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.LiveBackground
import app.youlauncher.ui.navigation.Screen
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.themeColor
import com.materialkolor.ktx.toHct
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtmosphereTest {
    private fun hueDistance(a: Double, b: Double): Double { val d = abs(a - b) % 360; return min(d, 360 - d) }

    @Test
    fun `scene is deterministic for a version`() {
        assertEquals(SceneGenerator.forVersion("1.21.10"), SceneGenerator.forVersion("1.21.10"))
        assertEquals(SceneGenerator.seedFor("1.8.9"), SceneGenerator.seedFor("1.8.9"))
        assertNotEquals(SceneGenerator.forVersion("1.21.10"), SceneGenerator.forVersion("1.20.1"))
        // FNV-1a от пустой строки: эталонное значение алгоритма.
        assertEquals(-0x340d631b7bdddcdbL, SceneGenerator.hash(""))
    }

    @Test
    fun `versions of one family have related colors`() {
        val a = Hct.fromInt(SceneGenerator.seedFor("1.21.4")).hue
        val b = Hct.fromInt(SceneGenerator.seedFor("1.21.10")).hue
        assertTrue(hueDistance(a, b) <= 31.0, "Оттенки 1.21.x слишком далеки: $a и $b")
        assertEquals("1.21", SceneGenerator.family("1.21.10"))
        assertEquals("26.4", SceneGenerator.family("26.4-snapshot-2"))
        assertEquals("b1.7", SceneGenerator.family("b1.7.3"))
    }

    @Test
    fun `scene shapes stay in sane ranges`() {
        listOf("1.21.10", "1.8.9", "26.1", "b1.7.3", "a1.2.6").forEach { v ->
            val s = SceneGenerator.forVersion(v)
            assertTrue(s.shapes.size in 4..7)
            assertTrue(s.hills.size in 2..3)
            assertTrue(s.shapes.all { it.size in 0.18f..0.56f && it.polygon in 0 until ScenePolygons.COUNT && it.morphTo != it.polygon })
        }
    }

    private val inst = Instance("i1", "One", "1.20.1", atmosphere = InstanceAtmosphere(seed = 0xFF336699))

    @Test
    fun `preview version wins, then instance, then global`() {
        val bg = BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.Waves)
        val preview = AtmosphereResolver.resolve(bg, false, Screen.Instance("i1"), listOf(inst), previewVersion = "26.1", lastInstanceId = null)
        assertEquals(Backdrop.Scene("version:26.1"), preview.backdrop)
        val onInstance = AtmosphereResolver.resolve(bg, false, Screen.Instance("i1"), listOf(inst), null, null)
        assertEquals(Backdrop.Scene("version:1.20.1"), onInstance.backdrop)
        assertEquals(Color(0xFF336699), onInstance.seedOverride)
        val home = AtmosphereResolver.resolve(bg, false, Screen.Home, listOf(inst), null, null)
        assertEquals(Backdrop.Live(ArtStyle.Waves), home.backdrop)
        assertNull(home.seedOverride)
    }

    @Test
    fun `own instance background shows only on that instance`() {
        val bg = BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.Waves)
        val own = Instance("i2", "Two", "1.21.1", atmosphere = InstanceAtmosphere(wallpaper = "file:C:/x/background-1.png"))
        val all = listOf(inst, own)
        val onOwn = AtmosphereResolver.resolve(bg, false, Screen.Instance("i2"), all, null, null)
        assertEquals(Backdrop.Wallpaper(WallpaperRef.File(java.nio.file.Path.of("C:/x/background-1.png"))), onOwn.backdrop)
        // Главная, библиотека и другой экземпляр не берут этот фон.
        assertEquals(Backdrop.Live(ArtStyle.Waves), AtmosphereResolver.resolve(bg, false, Screen.Home, all, null, "i2").backdrop)
        assertEquals(Backdrop.Live(ArtStyle.Waves), AtmosphereResolver.resolve(bg, false, Screen.Library, all, null, "i2").backdrop)
        assertNotEquals(onOwn.backdrop, AtmosphereResolver.resolve(bg, false, Screen.Instance("i1"), all, null, null).backdrop)
        // С «цветами из обоев» тема берётся из картинки экземпляра, если у него нет своего цвета.
        assertNotNull(AtmosphereResolver.resolve(bg, true, Screen.Instance("i2"), all, null, null).extractSeedFrom)
        assertNull(AtmosphereResolver.resolve(bg, false, Screen.Instance("i2"), all, null, null).extractSeedFrom)
    }

    @Test
    fun `version atmosphere can be turned off`() {
        val bg = BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.Calm, versionAtmosphere = false)
        val r = AtmosphereResolver.resolve(bg, false, Screen.Instance("i1"), listOf(inst), "26.1", null)
        assertEquals(Backdrop.Live(ArtStyle.Calm), r.backdrop)
    }

    @Test
    fun `colors from wallpaper`() {
        val builtin = BuiltinWallpapers.all.first()
        val bg = BackgroundSettings(kind = BackgroundKind.Wallpaper, wallpaper = builtin.key)
        val r = AtmosphereResolver.resolve(bg, true, Screen.Home, emptyList(), null, null)
        assertEquals(Color(builtin.seedArgb), r.seedOverride)
        val file = AtmosphereResolver.resolve(bg.copy(wallpaper = "file:C:\\pics\\a.png"), true, Screen.Home, emptyList(), null, null)
        assertNotNull(file.extractSeedFrom)
        assertNull(AtmosphereResolver.resolve(bg, false, Screen.Home, emptyList(), null, null).seedOverride)
    }

    @Test
    fun `version scene background follows the last instance`() {
        val bg = BackgroundSettings(kind = BackgroundKind.VersionScene)
        val r = AtmosphereResolver.resolve(bg, false, Screen.Library, listOf(inst), null, "i1")
        assertEquals(Backdrop.Scene("version:1.20.1"), r.backdrop)
        val empty = AtmosphereResolver.resolve(bg, false, Screen.Library, emptyList(), null, null)
        assertIs<Backdrop.Live>(empty.backdrop)
    }

    @Test
    fun `builtin wallpapers are many and unique`() {
        val all = BuiltinWallpapers.all
        assertTrue(all.size >= 12)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertEquals(it, (WallpaperRef.parse(it.key) as WallpaperRef.Builtin).wallpaper) }
        assertNull(WallpaperRef.parse("builtin:missing"))
        assertNull(WallpaperRef.parse("https://example.org/x.png"))
    }

    @Test
    fun `theme color is extracted from an image`() {
        val image = ImageBitmap(64, 64)
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, 64f, 64f), Paint().apply { color = Color(0xFF1565C0) })
        canvas.drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color(0xFFE0E0E0) })
        val extracted = image.themeColor(fallback = Color(0xFF00FF00))
        val hue = extracted.toHct().hue
        assertTrue(hueDistance(hue, Color(0xFF1565C0).toHct().hue) < 20.0, "Ожидался синий оттенок, получен $hue")
    }

    @Test
    fun `user wallpapers are imported into the folder`() {
        val src = Files.createTempDirectory("youlauncher-src")
        val dst = Files.createTempDirectory("youlauncher-wp")
        val img = src.resolve("pic.png").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        val copied = UserWallpapers.import(img, dst)
        assertNotNull(copied)
        assertEquals(copied, UserWallpapers.import(img, dst))
        assertEquals(listOf(copied), UserWallpapers.list(dst))
        assertNull(UserWallpapers.import(src.resolve("note.txt").also { Files.writeString(it, "x") }, dst))
    }

    @Test
    fun `presets apply background, seed and style`() {
        val preset = BuiltinPresets.all.first { it.style != null }
        val applied = AtmospherePresetRepository.apply(preset, LauncherSettings())
        assertEquals(preset.background, applied.background)
        assertEquals(preset.style, applied.theme.style)
        assertEquals(preset.id, applied.theme.presetId)

        val repo = AtmospherePresetRepository(Files.createTempDirectory("youlauncher-p").resolve("atmospheres.json"))
        val saved = repo.save("Mine", applied)
        assertEquals(listOf(saved), repo.presets.value)
        repo.delete(saved.id)
        assertTrue(repo.presets.value.isEmpty())
    }
}
