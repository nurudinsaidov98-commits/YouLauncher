package app.youlauncher.data

import app.youlauncher.CONTENT_HOSTS
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.CompatStatus
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.download.DownloaderConfig
import app.youlauncher.data.instances.FileContentRepository
import app.youlauncher.data.instances.FileInstanceRepository
import app.youlauncher.data.mods.CatalogModInstaller
import app.youlauncher.data.mods.ContentIndex
import app.youlauncher.data.mods.ModrinthCatalog
import app.youlauncher.data.mods.ModrinthParsing
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Смена версии экземпляра: мод с версией под новую игру обновляется, без неё выключается. */
class VersionMigrationTest {
    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }
    private val old = "a for 1.21.1".toByteArray()
    private val new = "a for 1.21.4".toByteArray()
    private val other = "b for 1.21.1".toByteArray()

    private fun v(id: String, project: String, file: String, bytes: ByteArray, game: String) = """
        {"id":"$id","project_id":"$project","name":"$project","version_number":"$game-1","game_versions":["$game"],"loaders":["fabric"],
         "version_type":"release","date_published":"2024-0${if (game.endsWith("4")) 2 else 1}-01T00:00:00Z","downloads":1,
         "files":[{"url":"https://cdn.modrinth.com/data/$project/versions/$id/$file","filename":"$file","hashes":{"sha1":"${sha1(bytes)}"},"size":${bytes.size},"primary":true}],
         "dependencies":[]}"""

    private val engine = MockEngine { req ->
        val path = req.url.encodedPath
        val json = headersOf("Content-Type", "application/json")
        when {
            req.url.host == "cdn.modrinth.com" -> respond(when { path.endsWith("a-old.jar") -> old; path.endsWith("a-new.jar") -> new; else -> other })
            path == "/v2/project/A/version" -> respond("[${v("a1", "A", "a-old.jar", old, "1.21.1")},${v("a4", "A", "a-new.jar", new, "1.21.4")}]", headers = json)
            path == "/v2/project/B/version" -> respond("[${v("b1", "B", "b.jar", other, "1.21.1")}]", headers = json)
            path.startsWith("/v2/project/") && path.endsWith("/members") -> respond("[]", headers = json)
            path.startsWith("/v2/project/") -> respond("""{"id":"${path.substringAfterLast('/')}","slug":"x","project_type":"mod","title":"Mod ${path.substringAfterLast('/')}"}""", headers = json)
            path == "/v2/version_files" -> respond("{}", headers = json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `repair re-downloads damaged and missing catalog files`() = runBlocking<Unit> {
        val http = HttpClient(engine)
        val root = Files.createTempDirectory("rep")
        val repo = FileInstanceRepository(root)
        val index = ContentIndex(repo::gameDir)
        val content = FileContentRepository(repo::gameDir, root.resolve("bk"), index)
        val installer = CatalogModInstaller(repo, content, index, ModrinthCatalog(http), { null }) {
            Downloader(http, DownloaderConfig(retries = 1, initialBackoffMs = 1, allowedHosts = CONTENT_HOSTS))
        }
        val inst = repo.create(NewInstanceSpec("R", "1.21.1", LoaderType.Fabric))
        fun project(id: String) = ModrinthParsing.searchHit(YouLauncherJson.parseToJsonElement("""{"project_id":"$id","slug":"$id","project_type":"mod","title":"Mod $id"}""").jsonObject)
        installer.install(inst.id, project("A")).getOrThrow()
        installer.install(inst.id, project("B")).getOrThrow()
        val mods = repo.gameDir(inst.id).resolve("mods")
        assertEquals(0, installer.repairFiles(inst.id), "Целые файлы не трогаются")
        Files.writeString(mods.resolve("a-old.jar"), "повреждён")
        content.setEnabled(inst.id, ContentKind.Mods, "b.jar", false)
        Files.delete(mods.resolve("b.jar.disabled"))
        assertEquals(2, installer.repairFiles(inst.id))
        assertEquals(sha1(old), app.youlauncher.data.download.Downloader.sha1(mods.resolve("a-old.jar")))
        assertTrue(mods.resolve("b.jar").exists())
    }

    @Test
    fun `compatible mods update, others are turned off, manual files stay`() = runBlocking<Unit> {
        val http = HttpClient(engine)
        val root = Files.createTempDirectory("mig")
        val repo = FileInstanceRepository(root)
        val index = ContentIndex(repo::gameDir)
        val content = FileContentRepository(repo::gameDir, root.resolve("bk"), index)
        val installer = CatalogModInstaller(repo, content, index, ModrinthCatalog(http), { null }) {
            Downloader(http, DownloaderConfig(retries = 1, initialBackoffMs = 1, allowedHosts = CONTENT_HOSTS))
        }
        val inst = repo.create(NewInstanceSpec("M", "1.21.1", LoaderType.Fabric))
        fun project(id: String) = ModrinthParsing.searchHit(YouLauncherJson.parseToJsonElement("""{"project_id":"$id","slug":"$id","project_type":"mod","title":"Mod $id"}""").jsonObject)
        installer.install(inst.id, project("A")).getOrThrow()
        installer.install(inst.id, project("B")).getOrThrow()
        val mods = repo.gameDir(inst.id).resolve("mods")
        Files.writeString(mods.resolve("manual.jar"), "jar")
        assertTrue(mods.resolve("a-old.jar").exists())

        val plan = installer.compatibility(inst.id, "1.21.4", LoaderType.Fabric)
        val byFile = plan.associateBy { it.fileName }
        assertEquals(CompatStatus.Update, byFile.getValue("a-old.jar").status)
        assertEquals("1.21.4-1", byFile.getValue("a-old.jar").newVersion?.versionNumber)
        assertEquals(CompatStatus.Incompatible, byFile.getValue("b.jar").status)
        assertEquals(CompatStatus.Unknown, byFile.getValue("manual.jar").status)

        repo.update(inst.id) { it.copy(gameVersion = "1.21.4") }
        assertEquals(2, installer.migrate(inst.id, plan))
        assertTrue(mods.resolve("a-new.jar").exists(), "Совместимый мод обновлён")
        assertTrue(mods.resolve("b.jar.disabled").exists(), "Несовместимый выключен, а не удалён")
        assertTrue(mods.resolve("manual.jar").exists(), "Свой файл не тронут")

        val vanilla = installer.compatibility(inst.id, "1.21.4", LoaderType.Vanilla)
        assertTrue(vanilla.filter { it.status != CompatStatus.Unknown }.all { it.status == CompatStatus.Incompatible }, "Без загрузчика моды не работают")
        assertEquals(ContentKind.Mods, vanilla.first().kind)
    }
}
