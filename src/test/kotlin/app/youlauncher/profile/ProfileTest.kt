package app.youlauncher.profile

import app.youlauncher.core.profile.Profile
import app.youlauncher.data.profile.LocalProfileRepository
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileTest {
    @Test
    fun `profile is saved and loaded back`() = runTest {
        val dir = Files.createTempDirectory("youlauncher-profile")
        val repo = LocalProfileRepository(dir)
        val p = repo.profile.value.copy(displayName = "Олег", nickname = "oleg_")
        repo.save(p)
        val loaded = LocalProfileRepository(dir).profile.value
        assertEquals(p.copy(updatedAt = loaded.updatedAt), loaded)
        assertEquals(Profile.SCHEMA, loaded.schema)
    }

    @Test
    fun `new profile is stable between launches`() {
        val dir = Files.createTempDirectory("youlauncher-profile")
        val first = LocalProfileRepository(dir).profile.value
        assertTrue(dir.resolve("profile.json").exists())
        assertEquals(first, LocalProfileRepository(dir).profile.value)
    }

    @Test
    fun `old schema file is migrated`() {
        val dir = Files.createTempDirectory("youlauncher-profile")
        dir.resolve("profile.json").writeText("""{"id":"x1","displayName":"Old","nickname":"old","bio":"лишнее поле"}""")
        val p = LocalProfileRepository(dir).profile.value
        assertEquals("Old", p.displayName)
        assertEquals(Profile.SCHEMA, p.schema)
    }

    @Test
    fun `chosen images are copied into the profile folder`() = runTest {
        val dir = Files.createTempDirectory("youlauncher-profile")
        val repo = LocalProfileRepository(dir)
        val src = Files.createTempFile("pic", ".png").also { Files.write(it, byteArrayOf(9, 8, 7)) }
        val copy = assertNotNull(repo.importImage(src, "avatar"))
        assertTrue(Path.of(copy).startsWith(dir))
        assertContentEquals(byteArrayOf(9, 8, 7), Files.readAllBytes(Path.of(copy)))
    }
}
