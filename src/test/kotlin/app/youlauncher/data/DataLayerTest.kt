package app.youlauncher.data

import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.download.DownloadException
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.download.DownloaderConfig
import app.youlauncher.data.instances.FileContentRepository
import app.youlauncher.data.instances.FileInstanceRepository
import app.youlauncher.data.instances.LevelDat
import app.youlauncher.data.net.createHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloaderTest {
    private val payload = Random(1).nextBytes(300_000)
    private val sha1 = MessageDigest.getInstance("SHA-1").digest(payload).joinToString("") { "%02x".format(it) }

    private fun <T> withServer(failFirst: Int = 0, block: (String, AtomicInteger) -> T): T {
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.bin") { ex ->
            val n = hits.incrementAndGet()
            if (n <= failFirst) { ex.sendResponseHeaders(500, -1); ex.close(); return@createContext }
            val range = ex.requestHeaders.getFirst("Range")
            val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
            val body = payload.copyOfRange(from, payload.size)
            ex.sendResponseHeaders(if (from > 0) 206 else 200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.start()
        try { return block("http://127.0.0.1:${server.address.port}", hits) } finally { server.stop(0) }
    }

    private fun downloader() = Downloader(createHttpClient(), DownloaderConfig(parallelism = 4, retries = 3, initialBackoffMs = 10, requireHttps = false))

    @Test
    fun `downloads and verifies sha1`() = withServer { base, _ ->
        val target = Files.createTempDirectory("dl").resolve("a/b/file.bin")
        val d = downloader()
        runBlocking { d.downloadAll(listOf(FileTask("$base/file.bin", target, sha1, payload.size.toLong()))) }
        assertContentEquals(payload, target.readBytes())
        assertEquals(1, d.progress.value.doneFiles)
        assertEquals(payload.size.toLong(), d.progress.value.doneBytes)
        assertFalse(target.resolveSibling("file.bin.part").exists())
    }

    @Test
    fun `valid files are not downloaded again`() = withServer { base, hits ->
        val target = Files.createTempDirectory("dl").resolve("file.bin").also { Files.write(it, payload) }
        runBlocking { downloader().downloadAll(listOf(FileTask("$base/file.bin", target, sha1, payload.size.toLong()))) }
        assertEquals(0, hits.get())
    }

    @Test
    fun `wrong hash is rejected and nothing is left`() = withServer { base, _ ->
        val target = Files.createTempDirectory("dl").resolve("file.bin")
        assertFailsWith<DownloadException> {
            runBlocking { downloader().downloadAll(listOf(FileTask("$base/file.bin", target, "0".repeat(40), payload.size.toLong()))) }
        }
        assertFalse(target.exists())
    }

    @Test
    fun `resumes a partial download with Range`() = withServer { base, _ ->
        val target = Files.createTempDirectory("dl").resolve("file.bin")
        Files.write(target.resolveSibling("file.bin.part"), payload.copyOfRange(0, 100_000))
        runBlocking { downloader().downloadAll(listOf(FileTask("$base/file.bin", target, sha1, payload.size.toLong()))) }
        assertContentEquals(payload, target.readBytes())
    }

    @Test
    fun `retries temporary server errors`() = withServer(failFirst = 2) { base, hits ->
        val target = Files.createTempDirectory("dl").resolve("file.bin")
        runBlocking { downloader().downloadAll(listOf(FileTask("$base/file.bin", target, sha1, payload.size.toLong()))) }
        assertTrue(hits.get() >= 3)
        assertTrue(target.exists())
    }

    @Test
    fun `404 fails fast and foreign hosts are refused`() = withServer { base, hits ->
        val dir = Files.createTempDirectory("dl")
        assertFailsWith<DownloadException> { runBlocking { downloader().downloadAll(listOf(FileTask("$base/missing", dir.resolve("x"), null))) } }
        val strict = Downloader(createHttpClient(), DownloaderConfig(allowedHosts = Downloader.MOJANG_HOSTS))
        assertFailsWith<DownloadException> { runBlocking { strict.downloadAll(listOf(FileTask("https://example.org/x.jar", dir.resolve("y"), null))) } }
        assertFailsWith<DownloadException> { runBlocking { strict.downloadAll(listOf(FileTask("$base/file.bin", dir.resolve("z"), null))) } }
        assertEquals(0, hits.get(), "Чужие хосты и http отклоняются до запроса")
    }
}

class StorageTest {
    @Test
    fun `instances are stored in json and survive reload`() = runTest {
        val root = Files.createTempDirectory("inst")
        val repo = FileInstanceRepository(root)
        val inst = repo.create(NewInstanceSpec("Мой мир!", "1.21.10", LoaderType.Vanilla, icon = InstanceIcon.Castle, maxMemoryMb = 3072))
        assertTrue(inst.id.startsWith("my-mir") || inst.id.isNotBlank())
        assertTrue(repo.gameDir(inst.id).exists())
        repo.update(inst.id) { it.copy(favorite = true, tags = listOf("t")) }
        val reloaded = FileInstanceRepository(root).instances.value.single()
        assertEquals(true, reloaded.favorite)
        assertEquals(3072, reloaded.java.maxMemoryMb)
        assertEquals(InstanceIcon.Castle, reloaded.icon)

        Files.writeString(repo.gameDir(inst.id).resolve("options.txt"), "fov:90")
        val copy = assertNotNull(repo.duplicate(inst.id, "Копия"))
        assertEquals("fov:90", Files.readString(repo.gameDir(copy.id).resolve("options.txt")))
        assertEquals(0, copy.launches)
    }

    @Test
    fun `schema 0 instance file is migrated`() {
        val root = Files.createTempDirectory("inst")
        Files.createDirectories(root.resolve("old"))
        root.resolve("old/instance.json").writeText("""{"id":"old","name":"Old","gameVersion":"1.12.2"}""")
        val inst = FileInstanceRepository(root).instances.value.single()
        assertEquals("1.12.2", inst.gameVersion)
    }

    private fun jar(path: Path, entries: Map<String, String>) {
        Files.createDirectories(path.parent)
        ZipOutputStream(Files.newOutputStream(path)).use { z -> entries.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }

    @Test
    fun `mods metadata, toggling and removal`() = runTest {
        val game = Files.createTempDirectory("game")
        val repo = FileContentRepository({ game }, Files.createTempDirectory("bk"))
        jar(game.resolve("mods/sodium.jar"), mapOf("fabric.mod.json" to """{"id":"sodium","name":"Sodium","version":"0.6.0"}"""))
        jar(game.resolve("mods/jei.jar"), mapOf("META-INF/mods.toml" to "[[mods]]\nmodId=\"jei\"\nversion=\"19.1\"\ndisplayName=\"Just Enough Items\""))
        jar(game.resolve("mods/old.jar"), mapOf("mcmod.info" to """[{"modid":"x","name":"Old Mod","version":"1.0"}]"""))
        Files.writeString(game.resolve("mods/readme.txt"), "not a mod")
        val mods = repo.content("i", ContentKind.Mods).first()
        assertEquals(listOf("Just Enough Items" to "19.1", "Old Mod" to "1.0", "Sodium" to "0.6.0"), mods.map { it.title to it.version })
        repo.setEnabled("i", ContentKind.Mods, "sodium.jar", false)
        assertTrue(game.resolve("mods/sodium.jar.disabled").exists())
        assertFalse(repo.content("i", ContentKind.Mods).first().first { it.title == "Sodium" }.enabled)
        repo.setEnabled("i", ContentKind.Mods, "sodium.jar.disabled", true)
        assertTrue(game.resolve("mods/sodium.jar").exists())
    }

    private fun writeLevelDat(file: Path, name: String, gameType: Int, lastPlayed: Long) {
        Files.createDirectories(file.parent)
        DataOutputStream(GZIPOutputStream(Files.newOutputStream(file))).use { o ->
            o.writeByte(10); o.writeUTF("")
            o.writeByte(10); o.writeUTF("Data")
            o.writeByte(8); o.writeUTF("LevelName"); o.writeUTF(name)
            o.writeByte(3); o.writeUTF("GameType"); o.writeInt(gameType)
            o.writeByte(4); o.writeUTF("LastPlayed"); o.writeLong(lastPlayed)
            o.writeByte(9); o.writeUTF("Players"); o.writeByte(8); o.writeInt(1); o.writeUTF("x")
            o.writeByte(10); o.writeUTF("Nested"); o.writeByte(8); o.writeUTF("LevelName"); o.writeUTF("не то имя"); o.writeByte(0)
            o.writeByte(0)
            o.writeByte(0)
        }
    }

    @Test
    fun `worlds are read from level dat and backed up`() = runTest {
        val game = Files.createTempDirectory("game")
        val backups = Files.createTempDirectory("bk")
        writeLevelDat(game.resolve("saves/w1/level.dat"), "Долина", 1, 1_700_000_000_000)
        Files.writeString(game.resolve("saves/w1/region.mca"), "data")
        assertEquals("Долина", LevelDat.read(game.resolve("saves/w1/level.dat"))?.name)
        val repo = FileContentRepository({ game }, backups)
        val world = repo.worlds("i").first().single()
        assertEquals("Долина" to "creative", world.name to world.gameMode)
        assertEquals(1_700_000_000_000, world.lastPlayed)
        val zip = assertNotNull(repo.backupWorld("i", "w1"))
        assertTrue(zip.startsWith(backups) && Files.size(zip) > 0)
    }
}
