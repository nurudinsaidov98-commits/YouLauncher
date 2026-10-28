package app.youlauncher

import app.youlauncher.core.launch.LaunchAccount
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.data.horizon.HorizonInfo
import app.youlauncher.data.horizon.HorizonParsing
import app.youlauncher.data.launch.HorizonSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HorizonTest {
    private fun release(tag: String, date: String, asset: String = HorizonInfo.ASSET, draft: Boolean = false) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":false,"published_at":"$date",
         "assets":[{"name":"$asset","browser_download_url":"https://github.com/horizon-rust/horizon/releases/download/$tag/$asset","digest":"sha256:ABCDEF"}]}
    """

    @Test
    fun `only windows game releases, newest first`() {
        val json = "[" + listOf(
            release("v0.2.92", "2026-10-28T01:44:46Z"),
            release("launcher@0.2.21", "2026-10-28T17:01:20Z", "horizon-launcher-windows-x64.zip"),
            release("nightly", "2026-10-28T09:58:01Z"),
            release("v0.2.93", "2026-10-28T07:28:48Z"),
            release("v0.2.94", "2026-10-29T07:28:48Z", draft = true),
            release("v0.2.90", "2026-10-27T18:10:33Z", "horizon-linux-x86_64.tar.gz"),
        ).joinToString(",") + "]"
        val list = HorizonParsing.releases(json)
        assertEquals(listOf("v0.2.93", "v0.2.92"), list.map { it.tag })
        assertEquals("abcdef", list.first().sha256)
        assertTrue(list.first().zipUrl.endsWith("/v0.2.93/horizon-windows-x64.zip"))
    }

    @Test
    fun `game version comes from research lock`() {
        assertEquals("26.3", HorizonParsing.lockedGameVersion("""{"minecraft":{"version":"26.3","java_major":25}}"""))
        assertNull(HorizonParsing.lockedGameVersion("{}"))
        assertNull(HorizonParsing.lockedGameVersion("not json"))
    }

    @Test
    fun `archive paths drop the top folder and refuse escapes`() {
        assertEquals("horizon-client.exe", HorizonParsing.entryPath("horizon-windows-x64/horizon-client.exe"))
        assertEquals("exporters/manifest.json", HorizonParsing.entryPath("horizon-windows-x64/exporters/manifest.json"))
        assertNull(HorizonParsing.entryPath("horizon-windows-x64/"))
        assertNull(HorizonParsing.entryPath("horizon-windows-x64/../../evil.exe"))
        assertNull(HorizonParsing.entryPath("horizon-windows-x64/C:/Windows/evil.dll"))
    }

    @Test
    fun `horizon is not a java mod loader`() {
        val i = Instance(id = "h", name = "Horizon", gameVersion = "26.3", loader = LoaderType.Horizon)
        assertTrue(i.isHorizon)
        assertFalse(i.isModded)
    }

    @Test
    fun `session file holds the account and is created fresh`() {
        val dir = Files.createTempDirectory("hz-session")
        val file = HorizonSession.write(dir, LaunchAccount("Steve", "069a79f4-44e9-4726-a5be-fca90e38aaf5", "token", "msa"))
        val o = Json.parseToJsonElement(Files.readString(file)).jsonObject
        assertEquals("Steve", o["name"]!!.jsonPrimitive.content)
        assertEquals("token", o["access_token"]!!.jsonPrimitive.content)
        assertEquals("msa", o["user_type"]!!.jsonPrimitive.content)
        assertTrue(file.fileName.toString().matches(Regex("session-[0-9a-f]{24}[.]json")))
        dir.toFile().deleteRecursively()
    }
}

class HorizonLockTest {
    @Test
    fun `pinned version file checksum`() {
        assertEquals("11f1", HorizonParsing.pinnedManifestSha1("""{"minecraft":{"version":"26.3","manifest_sha1":"11f1"}}"""))
        assertNull(HorizonParsing.pinnedManifestSha1("""{"minecraft":{}}"""))
    }
}
