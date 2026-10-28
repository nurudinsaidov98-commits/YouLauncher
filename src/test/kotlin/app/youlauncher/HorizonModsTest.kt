package app.youlauncher

import app.youlauncher.data.horizon.Blake2b
import app.youlauncher.data.horizon.HorizonModFile
import app.youlauncher.data.horizon.HorizonModIndex
import app.youlauncher.data.horizon.HorizonModKind
import app.youlauncher.data.horizon.HorizonModProject
import app.youlauncher.data.horizon.HorizonModRules
import app.youlauncher.data.horizon.HorizonModVersion
import app.youlauncher.data.horizon.HorizonRevoked
import app.youlauncher.data.horizon.Minisign
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HorizonModsTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun `blake2b matches rfc vectors`() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            hex(Blake2b.hash512("abc".toByteArray())),
        )
        assertEquals(
            "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce",
            hex(Blake2b.hash512(ByteArray(0))),
        )
        // Больше одного блока: 128 и 300 байт проходят без ошибок и дают разные хеши.
        assertFalse(hex(Blake2b.hash512(ByteArray(128))) == hex(Blake2b.hash512(ByteArray(300))))
    }

    /** Подпись в формате minisign, сделанная ключом JDK: так проверяется и разбор, и сама криптография. */
    private fun minisign(prehashed: Boolean, data: ByteArray): Pair<String, String> {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val rawPk = pair.public.encoded.takeLast(32).toByteArray()
        val keyId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        fun sign(m: ByteArray) = Signature.getInstance("Ed25519").run { initSign(pair.private); update(m); sign() }
        val alg = if (prehashed) "ED" else "Ed"
        val sig = sign(if (prehashed) Blake2b.hash512(data) else data)
        val trusted = "timestamp:1 file:mod.hmod"
        val global = sign(sig + trusted.toByteArray())
        val b64 = Base64.getEncoder()
        val key = "untrusted comment: minisign public key\n" + b64.encodeToString("Ed".toByteArray() + keyId + rawPk)
        val signature = "untrusted comment: signature\n" + b64.encodeToString(alg.toByteArray() + keyId + sig) + "\ntrusted comment: $trusted\n" + b64.encodeToString(global) + "\n"
        return key to signature
    }

    @Test
    fun `minisign signatures verify and tampering fails`() {
        val data = "horizon mod bytes".toByteArray()
        for (prehashed in listOf(false, true)) {
            val (key, sig) = minisign(prehashed, data)
            assertTrue(Minisign.verify(key, sig, data), "prehashed=$prehashed")
            assertFalse(Minisign.verify(key, sig, data + 1))
            assertFalse(Minisign.verify(key, sig.replace("file:mod.hmod", "file:evil.hmod"), data))
            val (otherKey, _) = minisign(prehashed, data)
            assertFalse(Minisign.verify(otherKey, sig, data))
        }
    }

    private fun version(v: String, date: String, sha: String, game: String = "26.3") =
        HorizonModVersion(v, published = date, game = game, file = HorizonModFile("m-$v.hmod", "https://github.com/x/m-$v.hmod", 10, sha))

    @Test
    fun `newest version skips revoked files and other game versions`() {
        val p = HorizonModProject("minimap", "mod", "Minimap", versions = listOf(version("0.2.0", "2026-10-27", "bb"), version("0.1.0", "2026-10-24", "aa"), version("0.3.0", "2026-10-28", "cc", game = "26.4")))
        assertEquals("0.2.0", HorizonModRules.newest(HorizonModIndex(projects = listOf(p)), p, "26.3")?.version)
        val revokedFile = HorizonModIndex(projects = listOf(p), revoked = listOf(HorizonRevoked("minimap", "BB", "bad")))
        assertEquals("0.1.0", HorizonModRules.newest(revokedFile, p, "26.3")?.version)
        val revokedAll = HorizonModIndex(projects = listOf(p), revoked = listOf(HorizonRevoked("minimap", null, "bad")))
        assertNull(HorizonModRules.newest(revokedAll, p, "26.3"))
    }

    @Test
    fun `install paths are safe`() {
        assertEquals("horizon-mods/zoom.hmod", HorizonModRules.relativePath(HorizonModKind.Mod, "zoom", "zoom-0.1.0.hmod"))
        assertEquals("shaderpacks/sky-1.0.hshader", HorizonModRules.relativePath(HorizonModKind.Shader, "sky", "sky-1.0.hshader"))
        assertNull(HorizonModRules.relativePath(HorizonModKind.ResourcePack, "pack", "../evil.zip"))
        assertNull(HorizonModRules.relativePath(HorizonModKind.ResourcePack, "pack", "a/b.zip"))
        assertNull(HorizonModRules.relativePath(HorizonModKind.Mod, "../x", "x.hmod"))
        assertNull(HorizonModRules.relativePath(HorizonModKind.Mod, "Zoom", "x.hmod"))
    }

    @Test
    fun `mod list keeps other lines`() {
        val start = "mod.zoom:true\n# comment\nmod.minimap:false\n"
        val on = HorizonModRules.setEnabled(start, "minimap", true)
        assertTrue(HorizonModRules.isEnabled(on, "minimap"))
        assertTrue(HorizonModRules.isEnabled(on, "zoom"))
        assertTrue(on.contains("# comment"))
        val off = HorizonModRules.setEnabled(on, "zoom", false)
        assertFalse(HorizonModRules.isEnabled(off, "zoom"))
        assertEquals("", HorizonModRules.setEnabled("", "zoom", false))
    }

    @Test
    fun `real registry index parses`() {
        val text = """{"schema":1,"generated":"x","projects":[{"id":"zoom","kind":"mod","name":"Zoom","authors":["Horizon"],"source":"https://github.com/horizon-rust/horizon","versions":[{"version":"0.1.0","published":"2026-10-24","api":"0.1","side":"client","capabilities":["input","ui"],"api_level":3,"game":"26.3","file":{"name":"zoom-0.1.0.hmod","url":"https://github.com/z.hmod","size":5,"sha256":"ab"}}]},{"id":"n","kind":"native-mod","name":"N","versions":[]}],"revoked":[]}"""
        val idx = HorizonModRules.parse(text)
        assertEquals(2, idx.projects.size)
        assertTrue(idx.projects[0].official)
        assertEquals(HorizonModKind.Mod, HorizonModRules.kindOf(idx.projects[0]))
        assertNull(HorizonModRules.kindOf(idx.projects[1]))
        assertEquals(3, idx.projects[0].versions[0].apiLevel)
    }
}
