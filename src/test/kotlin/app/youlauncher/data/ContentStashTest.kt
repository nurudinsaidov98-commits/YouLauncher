package app.youlauncher.data

import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.data.instances.FileContentRepository
import app.youlauncher.data.mods.ContentIndex
import app.youlauncher.data.mods.IndexedFile
import app.youlauncher.ui.screens.instance.InstalledSort
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentStashTest {
    private val root: Path = Files.createTempDirectory("stash")
    private val game = { _: String -> root.resolve("game") }
    private val index = ContentIndex(game)
    private val repo = FileContentRepository(game, root.resolve("backups"), index)

    @Test
    fun `removed mod can be restored with its catalog metadata`() = runBlocking {
        val mods = Files.createDirectories(root.resolve("game/mods"))
        Files.writeString(mods.resolve("sodium.jar"), "jar")
        index.put("i", IndexedFile(ContentKind.Mods, "sodium.jar", ContentSource.Modrinth, "AANobbMI", "v1", "0.6", "Sodium"))

        assertTrue(repo.stash("i", ContentKind.Mods, "sodium.jar"))
        assertFalse(mods.resolve("sodium.jar").exists())
        assertTrue(repo.scanContent("i", ContentKind.Mods).isEmpty(), "отложенный мод не виден и не загрузится игрой")
        assertNull(index.find("i", ContentKind.Mods, "sodium.jar"))

        repo.restore("i", ContentKind.Mods, "sodium.jar")
        assertTrue(mods.resolve("sodium.jar").exists())
        assertEquals("AANobbMI", assertNotNull(index.find("i", ContentKind.Mods, "sodium.jar")).projectId)
        assertEquals("Sodium", repo.scanContent("i", ContentKind.Mods).single().title)
    }

    @Test
    fun `stashed files are purged after the undo window`() = runBlocking {
        val mods = Files.createDirectories(root.resolve("game/mods"))
        Files.writeString(mods.resolve("old.jar"), "jar")
        assertTrue(repo.stash("i", ContentKind.Mods, "old.jar"))
        val stash = root.resolve("game").resolve(FileContentRepository.STASH_DIR)
        repo.purgeStash("i")
        assertTrue(Files.walk(stash).use { s -> s.anyMatch { it.fileName.toString() == "old.jar" } }, "пока можно отменить, файл на месте")
        repo.purgeStash("i", now = System.currentTimeMillis() + FileContentRepository.STASH_TTL_MS + 1000)
        assertFalse(Files.walk(stash).use { s -> s.anyMatch { it.fileName.toString() == "old.jar" } }, "после окна отмены — в корзину")
    }

    @Test
    fun `installed list sorts by date, size, state and updates`() {
        val files = listOf(
            app.youlauncher.core.model.ContentFile("b.jar", "Beta", enabled = false, sizeBytes = 10, addedAt = 300),
            app.youlauncher.core.model.ContentFile("a.jar", "alpha", sizeBytes = 500, addedAt = 100, updateAvailable = "2.0"),
            app.youlauncher.core.model.ContentFile("c.jar", "Gamma", sizeBytes = 50, addedAt = 200),
        )
        assertEquals(listOf("alpha", "Beta", "Gamma"), InstalledSort.Name.sort(files).map { it.title })
        assertEquals(listOf("Beta", "Gamma", "alpha"), InstalledSort.Newest.sort(files).map { it.title })
        assertEquals(listOf("alpha", "Gamma", "Beta"), InstalledSort.Size.sort(files).map { it.title })
        assertEquals("Beta", InstalledSort.Enabled.sort(files).last().title)
        assertEquals("alpha", InstalledSort.Updates.sort(files).first().title)
    }
}
