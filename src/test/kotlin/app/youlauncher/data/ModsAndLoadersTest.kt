package app.youlauncher.data

import app.youlauncher.CONTENT_HOSTS
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.model.DependencyKind
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.ModDependency
import app.youlauncher.core.model.ModFile
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.ModQuery
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.download.DownloaderConfig
import app.youlauncher.data.instances.FileContentRepository
import app.youlauncher.data.instances.FileInstanceRepository
import app.youlauncher.data.loaders.InstallProfile
import app.youlauncher.data.loaders.LoaderParsing
import app.youlauncher.data.loaders.NumericComparator
import app.youlauncher.data.loaders.ProcessorArgs
import app.youlauncher.data.mods.CatalogModInstaller
import app.youlauncher.data.mods.ContentIndex
import app.youlauncher.data.mods.CurseForgeParsing
import app.youlauncher.data.mods.DependencyPlanner
import app.youlauncher.data.mods.IndexedFile
import app.youlauncher.data.mods.ModSet
import app.youlauncher.data.mods.ModSetLogic
import app.youlauncher.data.mods.ModrinthCatalog
import app.youlauncher.data.mods.ModrinthParsing
import app.youlauncher.data.mods.VersionPicker
import app.youlauncher.data.packs.PackExporter
import app.youlauncher.data.packs.PackFormat
import app.youlauncher.data.packs.PackImporter
import app.youlauncher.data.packs.PackParsing
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

class LoaderParsingTest {
    @Test
    fun `fabric and quilt versions with stability`() {
        val fabric = LoaderParsing.fabricLike("""[{"loader":{"version":"0.17.3","stable":true}},{"loader":{"version":"0.17.2","stable":true}}]""", quilt = false)
        assertEquals(listOf("0.17.3", "0.17.2"), fabric.map { it.version })
        assertTrue(fabric[0].latest && fabric[0].recommended)
        val quilt = LoaderParsing.fabricLike("""[{"loader":{"version":"0.29.0-beta.1"}},{"loader":{"version":"0.28.1"}}]""", quilt = true)
        assertEquals(listOf(false, true), quilt.map { it.stable })
    }

    @Test
    fun `forge versions filtered by game and sorted numerically`() {
        val xml = "<versions><version>1.20.1-47.1.99</version><version>1.20.1-47.1.106</version><version>1.21-51.0.33</version></versions>"
        val list = LoaderParsing.forge(xml, """{"promos":{"1.20.1-recommended":"47.1.99","1.20.1-latest":"47.1.106"}}""", "1.20.1")
        assertEquals(listOf("47.1.106", "47.1.99"), list.map { it.version })
        assertTrue(list[0].latest && list[1].recommended)
    }

    @Test
    fun `neoforge prefix covers old and new numbering`() {
        assertEquals("21.10.", LoaderParsing.neoForgePrefix("1.21.10"))
        assertEquals("21.0.", LoaderParsing.neoForgePrefix("1.21"))
        assertEquals("26.3.0.", LoaderParsing.neoForgePrefix("26.3"))
        assertNull(LoaderParsing.neoForgePrefix("b1.7.3"))
        val xml = "<version>21.1.263</version><version>21.1.264</version><version>21.10.1-beta</version><version>21.1.300-beta</version>"
        val list = LoaderParsing.neoForge(xml, "1.21.1")
        assertEquals("21.1.300-beta", list.first().version)
        assertEquals("21.1.264", list.first { it.recommended }.version)
        assertTrue(NumericComparator.compare("47.1.106", "47.1.99") > 0)
    }

    @Test
    fun `processor arguments are substituted`() {
        val lib = Path.of("libs")
        val data = mapOf("MAPPINGS" to "m.txt", "SIDE" to "client")
        assertEquals("--side=client", ProcessorArgs.substitute("--side={SIDE}", data, lib))
        assertEquals(lib.resolve("net/neoforged/x/1.0/x-1.0-mappings.txt").toString(), ProcessorArgs.substitute("[net.neoforged:x:1.0:mappings@txt]", data, lib))
        assertEquals("1.21.1-20240808", ProcessorArgs.resolveValue("'1.21.1-20240808'", lib) { error("no") })
        assertEquals(Path.of("tmp/data/client.lzma").toString(), ProcessorArgs.resolveValue("/data/client.lzma", lib) { Path.of("tmp").resolve(it.removePrefix("/")) })
    }

    @Test
    fun `install profile with server-only processors`() {
        val p = YouLauncherJson.decodeFromString(InstallProfile.serializer(), """
            {"spec":1,"minecraft":"1.21.1","json":"/version.json","data":{"MCP_VERSION":{"client":"'1'","server":"'1'"}},
             "processors":[{"sides":["server"],"jar":"a:b:1","args":[]},{"jar":"c:d:1","args":["--x"],"outputs":{"{A}":"'abc'"}}],
             "libraries":[{"name":"c:d:1","downloads":{"artifact":{"path":"c/d/1/d-1.jar","url":"","sha1":"x","size":1}}}]}
        """.trimIndent())
        assertEquals(1, p.processors.count { it.sides == null || "client" in it.sides })
        assertEquals("'1'", p.data.getValue("MCP_VERSION").client)
    }
}

class CatalogParsingTest {
    @Test
    fun `modrinth facets filter loader only for mods`() {
        val mods = YouLauncherJson.parseToJsonElement(ModrinthParsing.facets(ModQuery(gameVersion = "1.21.1", loader = LoaderType.Fabric, categories = setOf("magic")))).jsonArray
        assertEquals(listOf("project_type:mod", "versions:1.21.1", "categories:fabric", "categories:magic"), mods.map { it.jsonArray.single().jsonPrimitive.content })
        val shaders = ModrinthParsing.facets(ModQuery(type = ProjectType.Shader, loader = LoaderType.Fabric))
        assertFalse("fabric" in shaders)
    }

    @Test
    fun `modrinth search and versions`() {
        val page = ModrinthParsing.search("""{"hits":[{"project_id":"AANobbMI","slug":"sodium","project_type":"mod","title":"Sodium","author":"jellysquid3",
            "description":"Fast","downloads":10,"follows":2,"categories":["fabric","optimization"],"display_categories":["optimization"],"versions":["1.21.1"]}],
            "offset":0,"limit":20,"total_hits":1}""", 0)
        val p = page.hits.single()
        assertEquals(listOf("optimization"), p.categories)
        assertEquals(listOf("fabric"), p.loaders)
        val versions = ModrinthParsing.versions("""[{"id":"v1","project_id":"AANobbMI","name":"Sodium 0.6","version_number":"0.6.0","game_versions":["1.21.1"],
            "loaders":["fabric"],"version_type":"release","date_published":"2024-08-01T00:00:00Z","downloads":5,
            "files":[{"url":"https://cdn.modrinth.com/data/AANobbMI/versions/v1/sodium.jar","filename":"sodium.jar","hashes":{"sha1":"aa","sha512":"bb"},"size":3,"primary":true}],
            "dependencies":[{"project_id":"P7dR8mSH","version_id":null,"dependency_type":"required"},{"project_id":"x","dependency_type":"incompatible"}]}]""")
        val v = versions.single()
        assertTrue(v.files.single().primary)
        assertEquals(listOf(DependencyKind.Required, DependencyKind.Incompatible), v.dependencies.map { it.kind })
        assertTrue(v.publishedAt > 0)
        val cats = ModrinthParsing.categories("""[{"name":"magic","project_type":"mod","header":"categories"},{"name":"16x","project_type":"resourcepack","header":"resolutions"},
            {"name":"cartoon","project_type":"resourcepack","header":"categories"}]""", ProjectType.ResourcePack)
        assertEquals(listOf("cartoon"), cats)
    }

    @Test
    fun `curseforge files and projects`() {
        val files = CurseForgeParsing.files("""{"data":[{"id":5,"modId":238222,"displayName":"JEI 19","fileName":"jei.jar","releaseType":1,"fileDate":"2024-01-01T00:00:00Z",
            "downloadUrl":"https://edge.forgecdn.net/files/5/jei.jar","fileLength":10,"gameVersions":["1.21.1","NeoForge"],
            "hashes":[{"value":"md5","algo":2},{"value":"abc","algo":1}],"dependencies":[{"modId":1,"relationType":3},{"modId":2,"relationType":2}]}]}""")
        val f = files.single()
        assertEquals("abc", f.files.single().sha1)
        assertEquals(listOf("1.21.1"), f.gameVersions)
        assertEquals(listOf("neoforge"), f.loaders)
        assertEquals(DependencyKind.Required, f.dependencies.first().kind)
        val page = CurseForgeParsing.search("""{"data":[{"id":238222,"slug":"jei","classId":6,"name":"JEI","summary":"Items","downloadCount":9,
            "authors":[{"name":"mezz"}],"logo":{"thumbnailUrl":"https://x/y.png"},"categories":[{"name":"Utility"}]}],"pagination":{"index":0,"totalCount":1}}""")
        assertEquals("mezz", page.hits.single().author)
        assertEquals(ContentSource.CurseForge, page.hits.single().source)
        assertEquals(6, CurseForgeParsing.classId(ProjectType.Mod))
    }
}

class DependencyTest {
    private fun v(project: String, type: String = "release", time: Long = 0, game: List<String> = listOf("1.21.1"), loaders: List<String> = listOf("fabric"), deps: List<ModDependency> = emptyList()) =
        ModVersion("$project-$type-$time", project, project, "1", game, loaders, type, time, 0, listOf(ModFile("https://cdn.modrinth.com/$project.jar", "$project.jar", null, null, 0, true)), deps)

    @Test
    fun `picker prefers compatible releases then newest`() {
        val list = listOf(v("a", "beta", 30), v("a", "release", 10), v("a", "release", 20), v("a", "release", 40, game = listOf("1.20.1")))
        assertEquals("a-release-20", VersionPicker.best(list, "1.21.1", LoaderType.Fabric)?.id)
        assertEquals("a-release-20", VersionPicker.best(list, "1.21.1", LoaderType.Quilt)?.id, "Quilt принимает моды Fabric")
        assertNull(VersionPicker.best(list, "1.21.1", LoaderType.Forge))
    }

    @Test
    fun `required dependencies are resolved transitively once`() = runBlocking<Unit> {
        val c = v("c", deps = listOf(ModDependency("a", null, DependencyKind.Required)))
        val b = v("b", deps = listOf(ModDependency("c", null, DependencyKind.Required), ModDependency("opt", null, DependencyKind.Optional)))
        val a = v("a", deps = listOf(ModDependency("b", null, DependencyKind.Required), ModDependency("api", null, DependencyKind.Required)))
        val all = mapOf("b" to b, "c" to c, "api" to v("api"))
        val plan = DependencyPlanner { all[it.projectId] }.plan(a, installed = setOf("api"))
        assertEquals(listOf("a", "b", "c"), plan.items.map { it.version.projectId })
        assertTrue(plan.missing.isEmpty() && plan.conflicts.isEmpty())
    }

    @Test
    fun `missing and incompatible dependencies are reported`() = runBlocking<Unit> {
        val a = v("a", deps = listOf(ModDependency("gone", null, DependencyKind.Required), ModDependency("optifine", null, DependencyKind.Incompatible)))
        val plan = DependencyPlanner { null }.plan(a, installed = setOf("optifine"))
        assertEquals(listOf("gone"), plan.missing)
        assertEquals(listOf("optifine"), plan.conflicts)
    }

    @Test
    fun `mod sets capture and apply`() {
        val files = listOf(ContentFile("a.jar", "A"), ContentFile("b.jar.disabled", "B", enabled = false), ContentFile("c.jar", "C"))
        val set = ModSetLogic.capture("pvp", files)
        assertEquals(listOf("a.jar", "c.jar"), set.enabled)
        assertTrue(ModSetLogic.matches(files, set))
        val other = ModSet("build", listOf("b.jar"))
        assertEquals(listOf("a.jar" to false, "b.jar.disabled" to true, "c.jar" to false), ModSetLogic.changes(files, other))
    }
}

class PackTest {
    @Test
    fun `mrpack manifest is parsed and unsafe paths rejected`() {
        val m = PackParsing.mrpack("""{"formatVersion":1,"game":"minecraft","versionId":"1","name":"Pack",
            "files":[{"path":"mods/a.jar","hashes":{"sha1":"aa"},"downloads":["https://cdn.modrinth.com/data/P/versions/V/a.jar"],"fileSize":3},
                     {"path":"mods/server.jar","env":{"client":"unsupported","server":"required"},"hashes":{},"downloads":[],"fileSize":1}],
            "dependencies":{"minecraft":"1.21.1","fabric-loader":"0.16.5"}}""")
        assertEquals(LoaderType.Fabric to "0.16.5", m.loader to m.loaderVersion)
        assertEquals(listOf("mods/a.jar"), m.files.map { it.path })
        assertNull(PackParsing.safePath("../evil.jar"))
        assertNull(PackParsing.safePath("C:/Windows/x.dll"))
        assertEquals("config/a.toml", PackParsing.safePath("/config/a.toml"))
        assertFailsWith<java.io.IOException> {
            PackParsing.mrpack("""{"game":"minecraft","files":[{"path":"../../x","downloads":[]}],"dependencies":{"minecraft":"1.21.1"}}""")
        }
    }

    @Test
    fun `curseforge and multimc manifests`() {
        val cf = PackParsing.curseForge("""{"minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.2.0","primary":true}]},"name":"CF",
            "files":[{"projectID":1,"fileID":2,"required":true},{"projectID":3,"fileID":4,"required":false}],"overrides":"overrides"}""")
        assertEquals(LoaderType.Forge to "47.2.0", cf.loader to cf.loaderVersion)
        assertEquals(2, cf.curseFiles.size)
        val mmc = PackParsing.multiMc("""{"components":[{"uid":"net.minecraft","version":"1.21.1"},{"uid":"net.neoforged","version":"21.1.264"}]}""", "InstanceType=OneSix\nname=My pack\n", ".minecraft")
        assertEquals(Triple("My pack", LoaderType.NeoForge, "21.1.264"), Triple(mmc.name, mmc.loader, mmc.loaderVersion))
        assertEquals(PackFormat.MultiMC to "Inst/mmc-pack.json", PackParsing.detect(listOf("Inst/instance.cfg", "Inst/mmc-pack.json")))
        assertEquals("47.2.0", PackParsing.forgeShort("1.20.1", "1.20.1-47.2.0"))
    }

    @Test
    fun `prism export imports back with files and loader`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("packs")
        val repo = FileInstanceRepository(root.resolve("instances"))
        val index = ContentIndex(repo::gameDir)
        val content = FileContentRepository(repo::gameDir, root.resolve("bk"), index)
        val inst = repo.create(NewInstanceSpec("Сборка", "1.21.1", LoaderType.Fabric, "0.16.5"))
        val game = repo.gameDir(inst.id)
        Files.createDirectories(game.resolve("mods")); Files.writeString(game.resolve("mods/a.jar"), "jar")
        Files.createDirectories(game.resolve("config")); Files.writeString(game.resolve("config/a.toml"), "x=1")
        Files.createDirectories(game.resolve("logs")); Files.writeString(game.resolve("logs/latest.log"), "log")
        val exporter = PackExporter(repo, index)
        val zip = exporter.exportPrism(inst.id, root.resolve("out/pack.zip"))
        ZipFile(zip.toFile()).use { z -> assertTrue(z.getEntry(".minecraft/logs/latest.log") == null && z.getEntry("mmc-pack.json") != null) }

        val importer = PackImporter(repo, content, index, { null }, { error("сеть не нужна") })
        val copy = importer.import(zip, "Копия")
        assertEquals(LoaderType.Fabric to "0.16.5", copy.loader to copy.loaderVersion)
        assertEquals("x=1", repo.gameDir(copy.id).resolve("config/a.toml").readText())
        assertTrue(repo.gameDir(copy.id).resolve("mods/a.jar").exists())
    }

    @Test
    fun `mrpack export links modrinth files and keeps the rest in overrides`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("packs")
        val repo = FileInstanceRepository(root.resolve("instances"))
        val index = ContentIndex(repo::gameDir)
        val inst = repo.create(NewInstanceSpec("P", "1.21.1", LoaderType.NeoForge, "21.1.264"))
        val game = repo.gameDir(inst.id)
        Files.createDirectories(game.resolve("mods"))
        Files.writeString(game.resolve("mods/linked.jar"), "a"); Files.writeString(game.resolve("mods/local.jar"), "b")
        index.put(inst.id, IndexedFile(ContentKind.Mods, "linked.jar", ContentSource.Modrinth, "P", "V", sha1 = sha1("a".toByteArray()), url = "https://cdn.modrinth.com/data/P/versions/V/linked.jar"))
        val zip = PackExporter(repo, index).exportMrpack(inst.id, root.resolve("p.mrpack"))
        ZipFile(zip.toFile()).use { z ->
            val m = YouLauncherJson.parseToJsonElement(z.getInputStream(z.getEntry("modrinth.index.json")).readBytes().decodeToString()).jsonObject
            assertEquals("21.1.264", m["dependencies"]!!.jsonObject["neoforge"]!!.jsonPrimitive.content)
            assertEquals("mods/linked.jar", m["files"]!!.jsonArray.single().jsonObject["path"]!!.jsonPrimitive.content)
            assertTrue(z.getEntry("overrides/mods/local.jar") != null && z.getEntry("overrides/mods/linked.jar") == null)
        }
    }
}

/** Установка из Modrinth целиком на подставном HTTP: API, зависимости, загрузка с CDN и индекс. */
class ModInstallerTest {
    private val jarA = "mod a".toByteArray()
    private val jarB = "fabric api".toByteArray()

    private fun version(id: String, project: String, file: String, bytes: ByteArray, deps: String = "[]") = """
        {"id":"$id","project_id":"$project","name":"$project","version_number":"1.0","game_versions":["1.21.1"],"loaders":["fabric"],
         "version_type":"release","date_published":"2024-01-01T00:00:00Z","downloads":1,
         "files":[{"url":"https://cdn.modrinth.com/data/$project/versions/$id/$file","filename":"$file","hashes":{"sha1":"${sha1(bytes)}"},"size":${bytes.size},"primary":true}],
         "dependencies":$deps}
    """.trimIndent()

    private val engine = MockEngine { req ->
        val path = req.url.encodedPath
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            req.url.host == "cdn.modrinth.com" && path.endsWith("a.jar") -> respond(jarA, HttpStatusCode.OK)
            req.url.host == "cdn.modrinth.com" && path.endsWith("api.jar") -> respond(jarB, HttpStatusCode.OK)
            path == "/v2/project/A/version" -> respond("[${version("va", "A", "a.jar", jarA, """[{"project_id":"B","dependency_type":"required"}]""")}]", headers = json)
            path == "/v2/project/B/version" -> respond("[${version("vb", "B", "api.jar", jarB)}]", headers = json)
            path == "/v2/project/B" -> respond("""{"id":"B","slug":"fabric-api","project_type":"mod","title":"Fabric API"}""", headers = json)
            path == "/v2/project/B/members" -> respond("[]", headers = json)
            path == "/v2/version_files" -> respond("{}", headers = json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `installs a mod with its required dependency`() = runBlocking<Unit> {
        val http = HttpClient(engine)
        val root = Files.createTempDirectory("mods")
        val repo = FileInstanceRepository(root)
        val index = ContentIndex(repo::gameDir)
        val content = FileContentRepository(repo::gameDir, root.resolve("bk"), index)
        val inst = repo.create(NewInstanceSpec("M", "1.21.1", LoaderType.Fabric))
        val installer = CatalogModInstaller(repo, content, index, ModrinthCatalog(http), { null }) {
            Downloader(http, DownloaderConfig(retries = 1, initialBackoffMs = 1, allowedHosts = CONTENT_HOSTS))
        }
        val project = ModrinthParsing.searchHit(YouLauncherJson.parseToJsonElement("""{"project_id":"A","slug":"a","project_type":"mod","title":"Mod A","author":"x"}""").jsonObject)
        val result = installer.install(inst.id, project).getOrThrow()
        assertEquals(listOf("Mod A", "Fabric API"), result)
        val mods = repo.gameDir(inst.id).resolve("mods")
        assertTrue(mods.resolve("a.jar").exists() && mods.resolve("api.jar").exists())
        assertEquals(setOf("A", "B"), installer.installedProjectIds(inst.id))
        val files = content.scanContent(inst.id, ContentKind.Mods)
        assertEquals(setOf("A", "B"), files.mapNotNull { it.projectId }.toSet())

        // Повторная установка не дублирует зависимость, а ваниль для модов отклоняется.
        val vanilla = repo.create(NewInstanceSpec("V", "1.21.1"))
        assertTrue(installer.install(vanilla.id, project).isFailure)
    }
}
