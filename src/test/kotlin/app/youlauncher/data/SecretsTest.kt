package app.youlauncher.data

import app.youlauncher.core.secure.InMemorySecretStore
import app.youlauncher.data.mods.CurseForgeCatalog
import app.youlauncher.data.mods.CurseForgeKey
import app.youlauncher.data.mods.KeySource
import app.youlauncher.data.mods.SwitchableCurseForge
import app.youlauncher.data.mods.isNewerCurseFile
import app.youlauncher.data.secure.BuildSecrets
import app.youlauncher.data.secure.WindowsCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretsTest {
    @Test
    fun `windows credential manager round trip`() {
        if (!WindowsCredentialStore.isSupported()) return
        val store = WindowsCredentialStore(prefix = "YouLauncher-test:")
        val name = "secret-" + UUID.randomUUID()
        try {
            assertNull(store.read(name))
            store.write(name, "ключ-123 с юникодом")
            assertEquals("ключ-123 с юникодом", store.read(name))
            store.write(name, "второй")
            assertEquals("второй", store.read(name))
        } finally {
            store.delete(name)
        }
        assertNull(store.read(name))
        store.delete(name)
    }

    @Test
    fun `key priority is environment, stored, built in`() {
        val store = InMemorySecretStore()
        var env: String? = null
        var builtIn: String? = null
        val key = CurseForgeKey(store, env = { env }, builtIn = { builtIn })
        assertEquals(KeySource.None, key.source.value)
        builtIn = "from-build"
        assertEquals("from-build", key.current())
        assertEquals(KeySource.BuiltIn, key.source.value)
        key.save("  stored  ")
        assertEquals("stored", key.current())
        assertEquals(KeySource.Stored, key.source.value)
        env = "from-env"
        assertEquals("from-env", key.current())
        assertEquals(KeySource.Environment, key.source.value)
        env = null
        key.clear()
        assertEquals("from-build", key.current(), "После удаления своего ключа снова работает встроенный")
        builtIn = null
        assertNull(key.current())
        assertEquals(KeySource.None, key.source.value)
    }

    @Test
    fun `built in key survives encoding`() {
        val encoded = BuildSecrets.encode("\$2a\$10\$abcDEF/xyz.123")
        assertFalse(encoded.contains("abcDEF"))
        assertEquals("\$2a\$10\$abcDEF/xyz.123", BuildSecrets.decode(encoded))
        assertNull(BuildSecrets.decode("не base64"))
    }

    @Test
    fun `curseforge turns on as soon as a key is saved`() {
        val key = CurseForgeKey(InMemorySecretStore(), env = { null }, builtIn = { null })
        val cf = SwitchableCurseForge(HttpClient(MockEngine { respond("") }), key)
        assertFalse(cf.available)
        assertNull(cf.catalog())
        key.save("abc")
        assertTrue(cf.available)
        val first = assertNotNull(cf.catalog())
        assertTrue(first === cf.catalog(), "Каталог переиспользуется, пока ключ тот же")
        key.save("def")
        assertTrue(first !== cf.catalog())
    }

    @Test
    fun `curseforge retries sporadic 403 and gives up on a persistent one`() = kotlinx.coroutines.runBlocking<Unit> {
        var calls = 0
        val flaky = HttpClient(MockEngine {
            calls++
            if (calls <= 2) respond("", io.ktor.http.HttpStatusCode.Forbidden)
            else respond("""{"data":[],"pagination":{"index":0,"totalCount":0}}""", headers = io.ktor.http.headersOf("Content-Type", "application/json"))
        })
        assertEquals(0, CurseForgeCatalog(flaky, "k", retryDelayMs = 1).search(app.youlauncher.core.repo.ModQuery(text = "jei")).total)
        assertEquals(3, calls)

        val denied = HttpClient(MockEngine { respond("", io.ktor.http.HttpStatusCode.Forbidden) })
        kotlin.test.assertFailsWith<java.io.IOException> { CurseForgeCatalog(denied, "k", retryDelayMs = 1).checkKey() }
        val notFound = HttpClient(MockEngine { calls++; respond("", io.ktor.http.HttpStatusCode.NotFound) })
        calls = 0
        kotlin.test.assertFailsWith<java.io.IOException> { CurseForgeCatalog(notFound, "k", retryDelayMs = 1).project("1") }
        assertEquals(1, calls, "404 не повторяется")
    }

    @Test
    fun `newer curseforge file has a larger id`() {
        assertTrue(isNewerCurseFile("5000", "4999"))
        assertFalse(isNewerCurseFile("4999", "5000"))
        assertFalse(isNewerCurseFile("5000", "5000"))
        assertFalse(isNewerCurseFile("x", "1"))
    }
}
