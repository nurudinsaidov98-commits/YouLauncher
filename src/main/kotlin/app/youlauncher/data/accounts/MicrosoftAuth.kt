package app.youlauncher.data.accounts

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.repo.MicrosoftDeviceCode
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/** Сессия Minecraft через Microsoft: хранится только в диспетчере учётных данных Windows. */
@Serializable
data class MicrosoftSession(
    val refreshToken: String,
    val accessToken: String,
    val expiresAt: Long,
    val uuid: String,
    val name: String,
    val skinUrl: String? = null,
    val slim: Boolean = false,
    val capeUrl: String? = null,
)

sealed class MicrosoftAuthException(message: String) : IOException(message) {
    class Declined : MicrosoftAuthException("Вход отменён в окне Microsoft")
    class Expired : MicrosoftAuthException("Код входа устарел, начните заново")
    /** Токен обновления больше не действует: нужен повторный вход. */
    class InvalidGrant : MicrosoftAuthException("Сессия Microsoft истекла, войдите заново")
    class NoXboxAccount : MicrosoftAuthException("У этого аккаунта Microsoft нет профиля Xbox")
    class ChildAccount : MicrosoftAuthException("Детский аккаунт: вход нужно разрешить в семейной группе Microsoft")
    class Region : MicrosoftAuthException("Xbox Live недоступен в стране этого аккаунта")
    class NoGame : MicrosoftAuthException("На этом аккаунте не куплена Minecraft: Java Edition")
    /** Mojang ещё не одобрила это приложение Azure для Minecraft API. */
    class AppNotApproved : MicrosoftAuthException("Приложение для входа ещё не одобрено Mojang")
    class Server(val status: Int, message: String) : MicrosoftAuthException(message)
}

/**
 * Вход в Minecraft через Microsoft: код устройства (пользователь вводит его на microsoft.com/link),
 * затем Xbox Live, XSTS, вход в Minecraft и профиль. Пароль Microsoft лаунчер не видит вообще.
 * [clientId] — ID приложения Azure (общедоступный клиент, без секрета).
 */
class MicrosoftAuthClient(
    private val http: HttpClient,
    private val clientId: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun deviceCode(): MicrosoftDeviceCode {
        val r = http.submitForm("$LOGIN/devicecode", parameters {
            append("client_id", clientId)
            append("scope", SCOPE)
        })
        val o = json(r)
        if (!r.status.isSuccess()) throw MicrosoftAuthException.Server(r.status.value, o.s("error_description") ?: "HTTP ${r.status.value}")
        return MicrosoftDeviceCode(
            userCode = o.s("user_code") ?: throw IOException("Microsoft не вернул код"),
            verificationUri = o.s("verification_uri") ?: "https://www.microsoft.com/link",
            deviceCode = o.s("device_code") ?: throw IOException("Microsoft не вернул код устройства"),
            intervalSec = o.l("interval") ?: 5,
            expiresAt = now() + (o.l("expires_in") ?: 900) * 1000,
        )
    }

    /** Ждёт, пока пользователь подтвердит вход на сайте Microsoft, и проходит всю цепочку до профиля Minecraft. */
    suspend fun awaitLogin(code: MicrosoftDeviceCode): MicrosoftSession {
        var interval = code.intervalSec.coerceAtLeast(1) * 1000
        while (true) {
            if (now() > code.expiresAt) throw MicrosoftAuthException.Expired()
            pause(interval)
            val r = http.submitForm("$LOGIN/token", parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                append("client_id", clientId)
                append("device_code", code.deviceCode)
            })
            val o = json(r)
            if (r.status.isSuccess()) return minecraft(o.s("access_token")!!, o.s("refresh_token")!!)
            when (o.s("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5000
                "authorization_declined" -> throw MicrosoftAuthException.Declined()
                "expired_token", "bad_verification_code" -> throw MicrosoftAuthException.Expired()
                else -> throw MicrosoftAuthException.Server(r.status.value, o.s("error_description") ?: "HTTP ${r.status.value}")
            }
        }
    }

    /** Новая сессия Minecraft по токену обновления Microsoft (без участия пользователя). */
    suspend fun refresh(refreshToken: String): MicrosoftSession {
        val r = http.submitForm("$LOGIN/token", parameters {
            append("grant_type", "refresh_token")
            append("client_id", clientId)
            append("refresh_token", refreshToken)
            append("scope", SCOPE)
        })
        val o = json(r)
        if (!r.status.isSuccess()) {
            if (o.s("error") == "invalid_grant") throw MicrosoftAuthException.InvalidGrant()
            throw MicrosoftAuthException.Server(r.status.value, o.s("error_description") ?: "HTTP ${r.status.value}")
        }
        return minecraft(o.s("access_token")!!, o.s("refresh_token") ?: refreshToken)
    }

    private suspend fun minecraft(msAccess: String, msRefresh: String): MicrosoftSession {
        // Xbox Live
        val xbl = postJson(XBL, buildJsonObject {
            putJsonObject("Properties") {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$msAccess")
            }
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        })
        val xblJson = json(xbl)
        if (!xbl.status.isSuccess()) throw MicrosoftAuthException.Server(xbl.status.value, "Xbox Live: HTTP ${xbl.status.value}")
        val xblToken = xblJson.s("Token") ?: throw IOException("Xbox Live не вернул токен")
        // XSTS для Minecraft
        val xsts = postJson(XSTS, buildJsonObject {
            putJsonObject("Properties") {
                put("SandboxId", "RETAIL")
                putJsonArray("UserTokens") { add(xblToken) }
            }
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        })
        val xstsJson = json(xsts)
        if (!xsts.status.isSuccess()) throw MicrosoftParsing.xstsError(xsts.status.value, xstsJson.l("XErr"))
        val xstsToken = xstsJson.s("Token") ?: throw IOException("XSTS не вернул токен")
        val uhs = MicrosoftParsing.userHash(xstsJson) ?: MicrosoftParsing.userHash(xblJson) ?: throw IOException("Xbox Live не вернул идентификатор пользователя")
        // Minecraft
        val mc = postJson("$MINECRAFT/authentication/login_with_xbox", buildJsonObject { put("identityToken", "XBL3.0 x=$uhs;$xstsToken") })
        val mcJson = json(mc)
        if (mc.status.value == 403) throw MicrosoftAuthException.AppNotApproved()
        if (!mc.status.isSuccess()) throw MicrosoftAuthException.Server(mc.status.value, "Minecraft: HTTP ${mc.status.value}")
        val mcAccess = mcJson.s("access_token") ?: throw IOException("Minecraft не вернул токен")
        val expiresAt = now() + (mcJson.l("expires_in") ?: 86_400) * 1000
        // Профиль: 404 значит, что игра на аккаунте не куплена.
        val p = http.get("$MINECRAFT/minecraft/profile") { header("Authorization", "Bearer $mcAccess") }
        if (p.status.value == 404) throw MicrosoftAuthException.NoGame()
        if (!p.status.isSuccess()) throw MicrosoftAuthException.Server(p.status.value, "Профиль Minecraft: HTTP ${p.status.value}")
        return MicrosoftParsing.session(json(p), msRefresh, mcAccess, expiresAt)
    }

    private suspend fun postJson(url: String, body: JsonObject): HttpResponse = http.post(url) {
        contentType(ContentType.Application.Json)
        header("Accept", "application/json")
        setBody(body.toString())
    }

    private suspend fun json(r: HttpResponse): JsonObject =
        runCatching { YouLauncherJson.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    companion object {
        const val LOGIN = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        const val XBL = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MINECRAFT = "https://api.minecraftservices.com"
        const val SCOPE = "XboxLive.signin offline_access"
    }
}

object MicrosoftParsing {
    fun reason(e: Throwable): app.youlauncher.core.repo.AuthFailure? = when (e) {
        is MicrosoftAuthException.Declined -> app.youlauncher.core.repo.AuthFailure.Declined
        is MicrosoftAuthException.Expired -> app.youlauncher.core.repo.AuthFailure.Expired
        is MicrosoftAuthException.NoXboxAccount -> app.youlauncher.core.repo.AuthFailure.NoXboxAccount
        is MicrosoftAuthException.ChildAccount -> app.youlauncher.core.repo.AuthFailure.ChildAccount
        is MicrosoftAuthException.Region -> app.youlauncher.core.repo.AuthFailure.Region
        is MicrosoftAuthException.NoGame -> app.youlauncher.core.repo.AuthFailure.NoGame
        is MicrosoftAuthException.AppNotApproved -> app.youlauncher.core.repo.AuthFailure.AppNotApproved
        else -> null
    }

    fun xstsError(status: Int, xErr: Long?): MicrosoftAuthException = when (xErr) {
        2148916233L -> MicrosoftAuthException.NoXboxAccount()
        2148916235L -> MicrosoftAuthException.Region()
        2148916238L, 2148916236L, 2148916237L -> MicrosoftAuthException.ChildAccount()
        else -> MicrosoftAuthException.Server(status, "XSTS: HTTP $status${xErr?.let { " ($it)" } ?: ""}")
    }

    fun userHash(o: JsonObject): String? =
        ((o["DisplayClaims"] as? JsonObject)?.get("xui") as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.s("uhs") }

    /** Профиль Minecraft: UUID с дефисами, активный скин и плащ (ссылки на textures.minecraft.net переводятся на https). */
    fun session(profile: JsonObject, refreshToken: String, accessToken: String, expiresAt: Long): MicrosoftSession {
        fun active(key: String) = (profile[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it.s("state") == "ACTIVE" }
        val skin = active("skins")
        return MicrosoftSession(
            refreshToken = refreshToken,
            accessToken = accessToken,
            expiresAt = expiresAt,
            uuid = dashedUuid(profile.s("id") ?: throw IOException("Minecraft не вернул UUID")),
            name = profile.s("name") ?: throw IOException("Minecraft не вернул ник"),
            skinUrl = secure(skin?.s("url")),
            slim = skin?.s("variant").equals("SLIM", ignoreCase = true),
            capeUrl = secure(active("capes")?.s("url")),
        )
    }

    fun secure(url: String?): String? = url?.let {
        if (it.startsWith("http://textures.minecraft.net/")) "https://" + it.removePrefix("http://") else it
    }
}

private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.l(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
