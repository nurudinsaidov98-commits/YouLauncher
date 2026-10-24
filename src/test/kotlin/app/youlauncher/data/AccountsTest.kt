package app.youlauncher.data

import app.youlauncher.INJECTOR_HOSTS
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.maskSecrets
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.repo.AuthResult
import app.youlauncher.core.repo.ReauthRequiredException
import app.youlauncher.core.secure.InMemorySecretStore
import app.youlauncher.data.accounts.AuthlibInjector
import app.youlauncher.data.accounts.ElyAuthException
import app.youlauncher.data.accounts.ElyByClient
import app.youlauncher.data.accounts.FileAccountRepository
import app.youlauncher.data.accounts.InjectorParsing
import app.youlauncher.data.accounts.dashedUuid
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.download.DownloaderConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JSON = headersOf("Content-Type", "application/json")
private const val PROFILE_ID = "ffc8fdc95824509e8a57c99b940fb996"

private fun HttpRequestData.json(): JsonObject =
    YouLauncherJson.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

private fun MockRequestHandleScope.session(token: String, clientToken: String, name: String = "ErickSkrauch"): HttpResponseData =
    respond("""{"accessToken":"$token","clientToken":"$clientToken","selectedProfile":{"id":"$PROFILE_ID","name":"$name"},"user":{"id":"$PROFILE_ID","username":"$name"}}""", headers = JSON)

private fun MockRequestHandleScope.forbidden(message: String): HttpResponseData =
    respond("""{"error":"ForbiddenOperationException","errorMessage":"$message"}""", HttpStatusCode.Unauthorized, JSON)

/** Подставной Ely.by: пароль «secret», двухфакторный код «123456», один действующий токен. */
private class FakeEly {
    val requests = mutableListOf<Pair<String, JsonObject>>()
    var validToken = "token-1"
    var refreshAllowed = true
    var twoFactor = false

    val engine = MockEngine { req ->
        val path = req.url.encodedPath
        if (req.url.host == "skinsystem.ely.by") {
            return@MockEngine respond("""{"SKIN":{"url":"http://ely.by/storage/skins/x.png","metadata":{"model":"slim"}}}""", headers = JSON)
        }
        val body = req.json()
        requests += path to body
        when (path) {
            "/auth/authenticate" -> {
                val pass = body.str("password")
                when {
                    twoFactor && pass == "secret" -> forbidden("Account protected with two factor auth.")
                    pass == "secret" || (twoFactor && pass == "secret:123456") -> session(validToken, body.str("clientToken"))
                    else -> forbidden("Invalid credentials. Invalid username or password.")
                }
            }
            "/auth/validate" -> if (body.str("accessToken") == validToken) respond("", HttpStatusCode.OK) else forbidden("Token expired.")
            "/auth/refresh" -> if (refreshAllowed) { validToken = "token-2"; session(validToken, body.str("clientToken")) } else forbidden("Token expired.")
            "/auth/invalidate", "/auth/signout" -> respond("", HttpStatusCode.OK)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }
    val client = ElyByClient(HttpClient(engine))
}

class ElyByClientTest {
    @Test
    fun `authenticate sends yggdrasil request and parses the profile`() = runBlocking<Unit> {
        val ely = FakeEly()
        val s = ely.client.authenticate("erick@skrauch.net", "secret", "client-1")
        assertEquals("token-1", s.accessToken)
        assertEquals("ErickSkrauch", s.profile.name)
        val (path, body) = ely.requests.single()
        assertEquals("/auth/authenticate", path)
        assertEquals("erick@skrauch.net", body.str("username"))
        assertEquals("client-1", body.str("clientToken"))
        assertEquals("true", body.str("requestUser"))
    }

    @Test
    fun `two factor code is sent as password colon code`() = runBlocking<Unit> {
        val ely = FakeEly().apply { twoFactor = true }
        assertFailsWith<ElyAuthException.TwoFactorRequired> { ely.client.authenticate("erick", "secret", "c") }
        val s = ely.client.authenticate("erick", "secret", "c", totp = " 123456 ")
        assertEquals("token-1", s.accessToken)
        assertEquals("secret:123456", ely.requests.last().second.str("password"))
    }

    @Test
    fun `wrong password and expired token are told apart`() = runBlocking<Unit> {
        val ely = FakeEly().apply { refreshAllowed = false }
        assertFailsWith<ElyAuthException.InvalidCredentials> { ely.client.authenticate("erick", "nope", "c") }
        assertFalse(ely.client.validate("old"))
        assertTrue(ely.client.validate("token-1"))
        assertFailsWith<ElyAuthException.InvalidToken> { ely.client.refresh("old", "c") }
    }

    @Test
    fun `refresh invalidate signout and textures`() = runBlocking<Unit> {
        val ely = FakeEly()
        assertEquals("token-2", ely.client.refresh("token-1", "c").accessToken)
        ely.client.invalidate("token-2", "c")
        ely.client.signout("erick", "secret")
        assertEquals(listOf("/auth/refresh", "/auth/invalidate", "/auth/signout"), ely.requests.map { it.first })
        assertEquals("c", ely.requests[1].second.str("clientToken"))
        val t = ely.client.textures("ErickSkrauch")
        assertTrue(t.slim)
        // Ely.by отдаёт ссылку по http, лаунчер переводит её на https (картинки грузятся только по https).
        assertEquals("https://ely.by/storage/skins/x.png", t.skinUrl)
    }

    @Test
    fun `server errors become readable failures`() = runBlocking<Unit> {
        val down = ElyByClient(HttpClient(MockEngine { respond("<html>", HttpStatusCode.BadGateway) }))
        val e = assertFailsWith<ElyAuthException.Server> { down.authenticate("a", "b", "c") }
        assertEquals(502, e.status)
    }
}

class AccountRepositoryTest {
    private fun repo(ely: FakeEly, secrets: InMemorySecretStore = InMemorySecretStore(), dir: java.nio.file.Path = Files.createTempDirectory("acc")) =
        FileAccountRepository(dir.resolve("accounts.json"), secrets, ely.client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `tokens live only in the secret store and the password nowhere`() = runBlocking<Unit> {
        val ely = FakeEly()
        val secrets = InMemorySecretStore()
        val dir = Files.createTempDirectory("acc")
        val r = repo(ely, secrets, dir)
        val result = assertIs<AuthResult.Success>(r.loginElyBy("erick@skrauch.net", "secret"))
        assertEquals(dashedUuid(PROFILE_ID), result.account.uuid)
        assertTrue(result.account.slimSkin)
        val file = dir.resolve("accounts.json").readText()
        assertFalse(file.contains("token-1") || file.contains("secret"), "В accounts.json нет токена и пароля")
        assertTrue(secrets.read("ely-session:${result.account.id}")!!.contains("token-1"))
        assertFalse(secrets.read("ely-session:${result.account.id}")!!.contains("secret"))

        // Повторный вход в тот же аккаунт не создаёт дубликат, аккаунты переживают перезапуск.
        r.loginElyBy("ErickSkrauch", "secret")
        assertEquals(1, r.accounts.value.size)
        assertEquals(1, repo(ely, secrets, dir).accounts.value.size)
    }

    @Test
    fun `two factor flow through the repository`() = runBlocking<Unit> {
        val ely = FakeEly().apply { twoFactor = true }
        val r = repo(ely)
        assertEquals(AuthResult.TwoFactorRequired, r.loginElyBy("erick", "secret"))
        assertIs<AuthResult.InvalidCredentials>(r.loginElyBy("erick", "bad"))
        assertIs<AuthResult.Success>(r.loginElyBy("erick", "secret", "123456"))
    }

    @Test
    fun `expired token is refreshed before launch, rejected refresh asks to sign in`() = runBlocking<Unit> {
        val ely = FakeEly()
        val r = repo(ely)
        val acc = (r.loginElyBy("erick", "secret") as AuthResult.Success).account
        ely.validToken = "server-rotated"
        val session = r.session(acc.id)
        assertEquals("token-2", session.accessToken)
        assertEquals(AccountType.ElyBy, session.type)

        ely.validToken = "rotated-again"
        ely.refreshAllowed = false
        assertFailsWith<ReauthRequiredException> { r.session(acc.id) }
        assertTrue(r.accounts.value.single().needsReauth)
        ely.refreshAllowed = true
        assertIs<AuthResult.Success>(r.loginElyBy("erick", "secret"))
        assertFalse(r.accounts.value.single().needsReauth)
    }

    @Test
    fun `network failure keeps the session and offline accounts need no server`() = runBlocking<Unit> {
        val secrets = InMemorySecretStore()
        val dir = Files.createTempDirectory("acc")
        val acc = (repo(FakeEly(), secrets, dir).loginElyBy("erick", "secret") as AuthResult.Success).account
        val offline = FileAccountRepository(dir.resolve("accounts.json"), secrets, ElyByClient(HttpClient(MockEngine { throw java.io.IOException("нет сети") })), CoroutineScope(Dispatchers.Unconfined))
        assertTrue(offline.refresh(acc.id))
        assertFalse(offline.accounts.value.single().needsReauth)
        val nick = offline.addOffline("Steve_2")
        val s = offline.session(nick.id)
        assertEquals(AccountType.Offline, s.type)
        assertEquals(32, s.accessToken.length)
    }

    @Test
    fun `removing an ely account revokes and deletes its token`() = runBlocking<Unit> {
        val ely = FakeEly()
        val secrets = InMemorySecretStore()
        val r = repo(ely, secrets)
        val acc = (r.loginElyBy("erick", "secret") as AuthResult.Success).account
        r.remove(acc.id)
        assertEquals("/auth/invalidate", ely.requests.last().first)
        assertNull(secrets.read("ely-session:${acc.id}"))
        assertTrue(r.accounts.value.isEmpty())
        assertNull(r.activeId.value)
    }

    @Test
    fun `secrets are masked in logs`() {
        val line = """{"accessToken":"abcdef123456","clientToken":"zz9988","password":"secret:123456"} --accessToken abcdef123456"""
        val masked = maskSecrets(line)
        assertFalse(masked.contains("abcdef123456") || masked.contains("secret:123456") || masked.contains("zz9988"))
        assertEquals("ffc8fdc9-5824-509e-8a57-c99b940fb996", dashedUuid(PROFILE_ID))
    }
}

class AuthlibInjectorTest {
    private val jar = "authlib-injector jar bytes".toByteArray()
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(jar).joinToString("") { "%02x".format(it) }

    private fun release(digest: String) = """{"tag_name":"v1.2.8","assets":[{"name":"authlib-injector-1.2.8.jar",
        "browser_download_url":"https://github.com/yushijinhun/authlib-injector/releases/download/v1.2.8/authlib-injector-1.2.8.jar","digest":"sha256:$digest"}]}"""

    private fun injector(digest: String, calls: MutableList<String>, now: () -> Long = { 1_000L }, online: () -> Boolean = { true }): Pair<AuthlibInjector, java.nio.file.Path> {
        val http = HttpClient(MockEngine { req ->
            calls += req.url.host
            if (!online()) throw java.io.IOException("нет сети")
            if (req.url.host == "api.github.com") respond(release(digest), headers = JSON) else respond(jar)
        })
        val dir = Files.createTempDirectory("tools")
        return AuthlibInjector(http, dir, { Downloader(http, DownloaderConfig(retries = 1, initialBackoffMs = 1, allowedHosts = INJECTOR_HOSTS)) }, now) to dir
    }

    @Test
    fun `release digest is parsed`() {
        val r = InjectorParsing.release(release(sha256))
        assertEquals("1.2.8", r.version)
        assertEquals(sha256, r.sha256)
        assertFailsWith<java.io.IOException> { InjectorParsing.release("""{"tag_name":"v1","assets":[{"name":"authlib-injector-1.jar","browser_download_url":"u"}]}""") }
        assertEquals("-javaagent:x.jar=ely.by", AuthlibInjector.javaAgent(java.nio.file.Path.of("x.jar")))
    }

    @Test
    fun `downloads once, verifies sha256 and works offline later`() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        var time = 1_000L
        var online = true
        val (inj, dir) = injector(sha256, calls, { time }, { online })
        val path = inj.ensure()
        assertTrue(path.exists())
        assertEquals(listOf("api.github.com", "github.com"), calls)
        inj.ensure()
        assertEquals(2, calls.size, "В течение суток GitHub не спрашивается")
        time += 2 * 24 * 3600 * 1000L
        online = false
        assertEquals(path, inj.ensure(), "Без сети используется скачанная версия")
        assertTrue(dir.resolve("authlib-injector.json").exists())
    }

    @Test
    fun `hash mismatch is rejected and the file removed`() = runBlocking<Unit> {
        val (inj, dir) = injector("0".repeat(64), mutableListOf())
        assertFailsWith<java.io.IOException> { inj.ensure() }
        assertFalse(dir.resolve("authlib-injector-1.2.8.jar").exists())
    }
}
