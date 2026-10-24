package app.youlauncher.data

import app.youlauncher.core.model.AccountType
import app.youlauncher.core.repo.AuthFailure
import app.youlauncher.core.repo.AuthResult
import app.youlauncher.core.repo.ReauthRequiredException
import app.youlauncher.core.secure.InMemorySecretStore
import app.youlauncher.data.accounts.ElyByClient
import app.youlauncher.data.accounts.FileAccountRepository
import app.youlauncher.data.accounts.MicrosoftAuthClient
import app.youlauncher.data.accounts.MicrosoftAuthException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val JSON = headersOf("Content-Type", "application/json")

/**
 * Подставные серверы Microsoft, Xbox Live и Minecraft. Пользователь «подтверждает» вход
 * после [pendingPolls] опросов; отдельные шаги можно сломать флагами.
 */
private class FakeMicrosoft {
    var pendingPolls = 1
    var slowDown = false
    var declined = false
    var xErr: Long? = null
    var notApproved = false
    var noGame = false
    var refreshRevoked = false
    var clock = 1_000_000L
    val paths = mutableListOf<String>()

    private fun form(req: io.ktor.client.request.HttpRequestData): Map<String, String> =
        (req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString().split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            java.net.URLDecoder.decode(k, Charsets.UTF_8) to java.net.URLDecoder.decode(v, Charsets.UTF_8)
        }

    val engine = MockEngine { req ->
        val url = req.url.toString()
        paths += req.url.host + req.url.encodedPath
        when {
            url.endsWith("/devicecode") -> respond(
                """{"device_code":"dev-1","user_code":"ABCD-1234","verification_uri":"https://www.microsoft.com/link","expires_in":900,"interval":5}""",
                headers = JSON,
            )
            url.endsWith("/token") -> {
                val f = form(req)
                when {
                    f["grant_type"] == "refresh_token" ->
                        if (refreshRevoked) respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, JSON)
                        else respond("""{"access_token":"ms-2","refresh_token":"rt-2"}""", headers = JSON)
                    declined -> respond("""{"error":"authorization_declined"}""", HttpStatusCode.BadRequest, JSON)
                    slowDown -> { slowDown = false; respond("""{"error":"slow_down"}""", HttpStatusCode.BadRequest, JSON) }
                    pendingPolls > 0 -> { pendingPolls--; respond("""{"error":"authorization_pending"}""", HttpStatusCode.BadRequest, JSON) }
                    else -> respond("""{"access_token":"ms-1","refresh_token":"rt-1"}""", headers = JSON)
                }
            }
            req.url.host == "user.auth.xboxlive.com" ->
                respond("""{"Token":"xbl-token","DisplayClaims":{"xui":[{"uhs":"uhs-1"}]}}""", headers = JSON)
            req.url.host == "xsts.auth.xboxlive.com" ->
                xErr?.let { respond("""{"XErr":$it}""", HttpStatusCode.Unauthorized, JSON) }
                    ?: respond("""{"Token":"xsts-token","DisplayClaims":{"xui":[{"uhs":"uhs-1"}]}}""", headers = JSON)
            url.endsWith("/authentication/login_with_xbox") ->
                if (notApproved) respond("""{"error":"Invalid app registration"}""", HttpStatusCode.Forbidden, JSON)
                else respond("""{"access_token":"mc-token","expires_in":86400}""", headers = JSON)
            url.endsWith("/minecraft/profile") ->
                if (noGame) respond("""{"error":"NOT_FOUND"}""", HttpStatusCode.NotFound, JSON)
                else respond(
                    """{"id":"069a79f444e94726a5befca90e38aaf5","name":"Notch","skins":[{"state":"ACTIVE","url":"http://textures.minecraft.net/texture/abc","variant":"SLIM"}],"capes":[]}""",
                    headers = JSON,
                )
            else -> respond("", HttpStatusCode.NotFound)
        }
    }
    val client = MicrosoftAuthClient(HttpClient(engine), "00000000-0000-0000-0000-000000000000", now = { clock }, pause = { clock += it })
}

class MicrosoftAuthTest {
    @Test
    fun `device code login goes through xbox and minecraft`() = runBlocking<Unit> {
        val ms = FakeMicrosoft().apply { pendingPolls = 2; slowDown = true }
        val code = ms.client.deviceCode()
        assertEquals("ABCD-1234", code.userCode)
        assertEquals("https://www.microsoft.com/link", code.verificationUri)
        val session = ms.client.awaitLogin(code)
        assertEquals("Notch", session.name)
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", session.uuid)
        assertEquals("mc-token", session.accessToken)
        assertEquals("rt-1", session.refreshToken)
        assertEquals("https://textures.minecraft.net/texture/abc", session.skinUrl)
        assertTrue(session.slim)
        assertTrue(ms.paths.containsAll(listOf("user.auth.xboxlive.com/user/authenticate", "xsts.auth.xboxlive.com/xsts/authorize", "api.minecraftservices.com/minecraft/profile")))
    }

    @Test
    fun `clear reasons for refusals`() = runBlocking<Unit> {
        assertFailsWith<MicrosoftAuthException.Declined> { FakeMicrosoft().apply { declined = true }.let { it.client.awaitLogin(it.client.deviceCode()) } }
        assertFailsWith<MicrosoftAuthException.ChildAccount> { FakeMicrosoft().apply { xErr = 2148916238 }.let { it.client.awaitLogin(it.client.deviceCode()) } }
        assertFailsWith<MicrosoftAuthException.NoXboxAccount> { FakeMicrosoft().apply { xErr = 2148916233 }.let { it.client.awaitLogin(it.client.deviceCode()) } }
        assertFailsWith<MicrosoftAuthException.AppNotApproved> { FakeMicrosoft().apply { notApproved = true }.let { it.client.awaitLogin(it.client.deviceCode()) } }
        assertFailsWith<MicrosoftAuthException.NoGame> { FakeMicrosoft().apply { noGame = true }.let { it.client.awaitLogin(it.client.deviceCode()) } }
        // Пользователь так и не подтвердил вход: код устаревает.
        assertFailsWith<MicrosoftAuthException.Expired> { FakeMicrosoft().apply { pendingPolls = 1000 }.let { it.client.awaitLogin(it.client.deviceCode()) } }
    }

    @Test
    fun `repository adds the account, launches with msa token and refreshes it`() = runBlocking<Unit> {
        val ms = FakeMicrosoft()
        val secrets = InMemorySecretStore()
        val ely = ElyByClient(HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }))
        val repo = FileAccountRepository(Files.createTempDirectory("acc").resolve("accounts.json"), secrets, ely, CoroutineScope(Dispatchers.IO), ms.client, now = { ms.clock })
        assertTrue(repo.microsoftAvailable)
        val result = repo.loginMicrosoft(repo.microsoftDeviceCode())
        assertIs<AuthResult.Success>(result)
        val acc = result.account
        assertEquals(AccountType.Microsoft, acc.type)
        assertEquals("https://textures.minecraft.net/texture/abc", acc.skinUrl)
        // Токены только в хранилище секретов, в файле аккаунтов их нет.
        assertTrue(secrets.read("ms-session:${acc.id}")!!.contains("rt-1"))
        assertEquals("mc-token", repo.session(acc.id).accessToken)
        // Сессия близка к концу: обновляется по токену обновления Microsoft.
        ms.clock += 86_400_000
        assertTrue(repo.refresh(acc.id))
        assertTrue(secrets.read("ms-session:${acc.id}")!!.contains("rt-2"))
        // Microsoft отозвал доступ: нужен новый вход.
        ms.refreshRevoked = true
        ms.clock += 2 * 86_400_000L
        assertFalse(repo.refresh(acc.id))
        assertTrue(repo.accounts.value.first().needsReauth)
        assertFailsWith<ReauthRequiredException> { repo.session(acc.id) }
        repo.remove(acc.id)
        assertEquals(null, secrets.read("ms-session:${acc.id}"))
    }

    @Test
    fun `failure reason reaches the ui and login is hidden without a client id`() = runBlocking<Unit> {
        val ms = FakeMicrosoft().apply { noGame = true }
        val ely = ElyByClient(HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }))
        val repo = FileAccountRepository(Files.createTempDirectory("acc").resolve("accounts.json"), InMemorySecretStore(), ely, CoroutineScope(Dispatchers.IO), ms.client)
        val result = repo.loginMicrosoft(repo.microsoftDeviceCode())
        assertEquals(AuthFailure.NoGame, (result as AuthResult.Failure).reason)
        val plain = FileAccountRepository(Files.createTempDirectory("acc").resolve("accounts.json"), InMemorySecretStore(), ely, CoroutineScope(Dispatchers.IO))
        assertFalse(plain.microsoftAvailable)
    }
}
