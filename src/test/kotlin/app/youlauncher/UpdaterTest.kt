package app.youlauncher

import app.youlauncher.core.settings.UpdateChannel
import app.youlauncher.data.system.UpdateParsing
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdaterTest {
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false, digest: String? = "sha256:ABCDEF", asset: String? = null) = """
        {"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"body":"Notes $tag","html_url":"https://github.com/o/r/releases/$tag",
         "assets":[{"name":"${asset ?: UpdateParsing.assetName(tag.removePrefix("v"))}","size":42,
                    "browser_download_url":"https://github.com/o/r/releases/download/$tag/x.zip"${digest?.let { ",\"digest\":\"$it\"" } ?: ""}}]}
    """.trimIndent()

    private fun list(vararg r: String) = r.joinToString(",", "[", "]")

    @Test
    fun `versions compare numerically`() {
        assertTrue(UpdateParsing.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateParsing.isNewer("v1.1.0", "1.0.0"))
        assertFalse(UpdateParsing.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateParsing.isNewer("0.9.0", "1.0.0"))
        assertTrue(UpdateParsing.isNewer("0.1.0", "dev"))
    }

    @Test
    fun `release is newer than its prerelease`() {
        assertTrue(UpdateParsing.isNewer("1.1.0", "1.1.0-beta.2"))
        assertTrue(UpdateParsing.isNewer("1.1.0-beta.10", "1.1.0-beta.2"))
        assertTrue(UpdateParsing.isNewer("1.1.0-beta.1", "1.0.0"))
        assertFalse(UpdateParsing.isNewer("1.1.0-beta.1", "1.1.0"))
        val json = list(release("v1.1.0-beta.1", prerelease = true), release("v1.1.0"))
        assertEquals("1.1.0", UpdateParsing.pick(json, UpdateChannel.Beta, "1.0.0")!!.version)
    }

    @Test
    fun `stable channel skips prereleases and drafts`() {
        val json = list(release("v1.2.0", prerelease = true), release("v1.3.0", draft = true), release("v1.1.0"))
        val info = UpdateParsing.pick(json, UpdateChannel.Stable, "1.0.0")!!
        assertEquals("1.1.0", info.version)
        assertEquals("abcdef", info.sha256)
        assertEquals(42, info.size)
        assertEquals("Notes v1.1.0", info.notes)
        assertEquals("1.2.0", UpdateParsing.pick(json, UpdateChannel.Beta, "1.0.0")!!.version)
        assertNull(UpdateParsing.pick(json, UpdateChannel.Off, "1.0.0"))
    }

    @Test
    fun `release without hash or archive is not offered`() {
        assertNull(UpdateParsing.pick(list(release("v2.0.0", digest = null)), UpdateChannel.Stable, "1.0.0"))
        assertNull(UpdateParsing.pick(list(release("v2.0.0", digest = "md5:123")), UpdateChannel.Stable, "1.0.0"))
        assertNull(UpdateParsing.pick(list(release("v2.0.0", asset = "Other.zip")), UpdateChannel.Stable, "1.0.0"))
        assertNull(UpdateParsing.pick(list(release("v1.0.0")), UpdateChannel.Stable, "1.0.0"))
        assertNull(UpdateParsing.pick("{\"message\":\"Not Found\"}", UpdateChannel.Stable, "1.0.0"))
    }

    @Test
    fun `script waits, copies and restarts without touching portable marker`() {
        val s = UpdateParsing.script(1234, Path.of("C:", "u", "YouLauncher-App"), Path.of("C:", "Apps", "YouLauncher"))
        assertTrue("Wait-Process -Id 1234" in s)
        assertTrue("robocopy" in s && "/XF portable.txt" in s)
        assertTrue("YouLauncher.exe" in s)
        assertTrue(s.indexOf("Wait-Process") < s.indexOf("robocopy") && s.indexOf("robocopy") < s.indexOf("Start-Process"))
    }
}
