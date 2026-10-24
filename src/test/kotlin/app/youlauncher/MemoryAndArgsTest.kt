package app.youlauncher

import app.youlauncher.core.profile.AvatarSource
import app.youlauncher.core.profile.Profile
import app.youlauncher.data.accounts.ElyByClient
import app.youlauncher.ui.images.ImageMemoryCache
import app.youlauncher.ui.navigation.LaunchArgs
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.theme.FontFiles
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryAndArgsTest {
    @Test
    fun `launch arguments open screens and start instances`() {
        assertEquals(LaunchArgs("abc", Screen.Instance("abc")), LaunchArgs.parse(arrayOf("--launch", "abc")))
        assertEquals(LaunchArgs(screen = Screen.Settings, action = "settings:Background"), LaunchArgs.parse(arrayOf("--open", "settings:Background")))
        assertEquals(Screen.Mods(), LaunchArgs.parse(arrayOf("--open", "mods")).screen)
        assertEquals(Screen.Instance("x1"), LaunchArgs.parse(arrayOf("--open", "instance:x1")).screen)
        assertEquals("profile", LaunchArgs.parse(arrayOf("--open", "profile")).action)
        assertEquals(LaunchArgs(), LaunchArgs.parse(arrayOf("--launch", "--open")))
        assertEquals(LaunchArgs(), LaunchArgs.parse(arrayOf("--open", "nowhere", "stray")))
    }

    @Test
    fun `image cache keeps within byte budget and evicts least recently used`() {
        val cache = ImageMemoryCache<Int>(budgetBytes = 100) { it.toLong() }
        cache.put("a", 40)
        cache.put("b", 40)
        cache["a"]
        cache.put("c", 40)
        assertNull(cache["b"])
        assertEquals(80, cache.bytes)
        cache.put("huge", 500)
        assertEquals(1, cache.size)
        assertEquals(500, cache.bytes)
        cache.clear()
        assertEquals(0, cache.bytes)
    }

    @Test
    fun `font is extracted once and replaced when content changes`() {
        val dir = Files.createTempDirectory("fonts")
        val first = FontFiles.extract("test.ttf", dir) { ByteArray(10) { 1 } }!!
        assertTrue(first.exists())
        val again = FontFiles.extract("test.ttf", dir) { ByteArray(10) { 1 } }!!
        assertEquals(first, again)
        val changed = FontFiles.extract("test.ttf", dir) { ByteArray(12) { 2 } }!!
        assertNotEquals(first, changed)
        assertEquals(listOf(changed.name), dir.toFile().list()!!.toList())
        assertNull(FontFiles.extract("test.ttf", null) { ByteArray(1) })
    }

    @Test
    fun `data folder of the old name moves to the new one`() {
        val base = Files.createTempDirectory("appdata")
        val old = base.resolve(app.youlauncher.core.LegacyNames.NAME).also { Files.createDirectories(it); Files.writeString(it.resolve("settings.json"), "{}") }
        val target = base.resolve("YouLauncher")
        assertEquals(target, app.youlauncher.core.AppDirs.migrateLegacy(old, target))
        assertTrue(Files.exists(target.resolve("settings.json")))
        assertTrue(Files.notExists(old))
        // Новая папка уже есть: старая не трогается.
        Files.createDirectories(old)
        assertEquals(target, app.youlauncher.core.AppDirs.migrateLegacy(old, target))
        assertTrue(Files.exists(old))
    }

    @Test
    fun `blur slider changes every background style`() {
        val radius = app.youlauncher.ui.background.ArtBlur::radius
        assertEquals(0f, radius(false, 0f))
        assertTrue(radius(false, 1f) > radius(false, 0.5f) && radius(false, 0.5f) > 0f)
        assertTrue(radius(true, 1f) > radius(true, 0f) && radius(true, 0f) > 0f)
    }

    @Test
    fun `ely texture links become https`() {
        assertEquals("https://ely.by/storage/skins/a.png", ElyByClient.secureTextureUrl("http://ely.by/storage/skins/a.png"))
        assertEquals("https://skins.ely.by/a.png", ElyByClient.secureTextureUrl("http://skins.ely.by/a.png"))
        assertEquals("http://evil.example/ely.by.png", ElyByClient.secureTextureUrl("http://evil.example/ely.by.png"))
        assertEquals("https://ely.by/a.png", ElyByClient.secureTextureUrl("https://ely.by/a.png"))
        assertNull(ElyByClient.secureTextureUrl(null))
    }

    @Test
    fun `avatar shows ely skin head unless the user turned it off`() {
        val profile = Profile(id = "p1", avatarImage = "C:/a.png")
        assertEquals(AvatarSource.Skin("https://ely.by/s.png"), AvatarSource.choose(profile, "https://ely.by/s.png"))
        assertEquals(AvatarSource.Picture("C:/a.png"), AvatarSource.choose(profile.copy(avatarFromSkin = false), "https://ely.by/s.png"))
        assertEquals(AvatarSource.Picture("C:/a.png"), AvatarSource.choose(profile, null))
        assertEquals(AvatarSource.Pattern("p1"), AvatarSource.choose(Profile(id = "p1"), null))
        assertEquals(AvatarSource.Pattern("seed"), AvatarSource.choose(Profile(id = "p1", avatarSeed = "seed"), null))
    }
}
