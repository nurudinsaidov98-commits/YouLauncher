package app.youlauncher.data.accounts

import app.youlauncher.core.YouLauncherJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException

@Serializable
data class ElyProfile(val id: String, val name: String)

/** Сессия Ely.by: токены хранятся только в диспетчере учётных данных Windows. */
@Serializable
data class ElySession(val accessToken: String, val clientToken: String, val profile: ElyProfile)

/** Текстуры игрока из skinsystem.ely.by. */
data class ElyTextures(val skinUrl: String?, val slim: Boolean, val capeUrl: String?)

sealed class ElyAuthException(message: String) : IOException(message) {
    /** Аккаунт защищён двухфакторной аутентификацией: нужен код, пароль отправляется как «пароль:код». */
    class TwoFactorRequired : ElyAuthException("Нужен код двухфакторной защиты")
    class InvalidCredentials(message: String) : ElyAuthException(message)
    /** Токен недействителен или отозван: нужен повторный вход. */
    class InvalidToken(message: String) : ElyAuthException(message)
    class Server(val status: Int, message: String) : ElyAuthException(message)
}

/**
 * Клиент Yggdrasil API Ely.by (https://authserver.ely.by): authenticate, refresh, validate, invalidate, signout.
 * Пароль используется только в запросе authenticate или signout и нигде не сохраняется.
 */
class ElyByClient(private val http: HttpClient, private val base: String = AUTH_SERVER) {
    private suspend fun post(path: String, body: JsonObject): HttpResponse =
        http.post("$base$path") { contentType(ContentType.Application.Json); setBody(body.toString()) }

    private suspend fun fail(r: HttpResponse, passwordRequest: Boolean): Nothing {
        val text = runCatching { r.bodyAsText() }.getOrDefault("")
        val o = runCatching { YouLauncherJson.parseToJsonElement(text).jsonObject }.getOrNull()
        val error = o?.s("error").orEmpty()
        val message = o?.s("errorMessage") ?: "HTTP ${r.status.value}"
        if (r.status.value == 401 && error == "ForbiddenOperationException") {
            if (passwordRequest && message.contains("two factor", ignoreCase = true)) throw ElyAuthException.TwoFactorRequired()
            if (passwordRequest) throw ElyAuthException.InvalidCredentials(message)
            throw ElyAuthException.InvalidToken(message)
        }
        throw ElyAuthException.Server(r.status.value, message)
    }

    private fun session(text: String, fallbackClientToken: String): ElySession {
        val o = YouLauncherJson.parseToJsonElement(text).jsonObject
        val profile = (o["selectedProfile"] as? JsonObject) ?: throw IOException("Ely.by не вернул профиль игрока")
        return ElySession(
            accessToken = o.s("accessToken") ?: throw IOException("Ely.by не вернул токен"),
            clientToken = o.s("clientToken") ?: fallbackClientToken,
            profile = ElyProfile(profile.s("id").orEmpty(), profile.s("name").orEmpty()),
        )
    }

    /** Вход по логину (нику или e-mail) и паролю; при включённой двухфакторной защите передаётся [totp]. */
    suspend fun authenticate(login: String, password: String, clientToken: String, totp: String? = null): ElySession {
        val secret = if (totp.isNullOrBlank()) password else "$password:${totp.trim()}"
        val r = post("/auth/authenticate", buildJsonObject {
            put("username", login)
            put("password", secret)
            put("clientToken", clientToken)
            put("requestUser", true)
        })
        if (!r.status.isSuccess()) fail(r, passwordRequest = true)
        return session(r.bodyAsText(), clientToken)
    }

    suspend fun refresh(accessToken: String, clientToken: String): ElySession {
        val r = post("/auth/refresh", buildJsonObject {
            put("accessToken", accessToken)
            put("clientToken", clientToken)
            put("requestUser", true)
        })
        if (!r.status.isSuccess()) fail(r, passwordRequest = false)
        return session(r.bodyAsText(), clientToken)
    }

    /** true, если токен ещё действует. */
    suspend fun validate(accessToken: String): Boolean {
        val r = post("/auth/validate", buildJsonObject { put("accessToken", accessToken) })
        if (r.status.isSuccess()) return true
        if (r.status.value == 401) return false
        fail(r, passwordRequest = false)
    }

    /** Отзывает один токен (выход из аккаунта на этом устройстве). */
    suspend fun invalidate(accessToken: String, clientToken: String) {
        val r = post("/auth/invalidate", buildJsonObject {
            put("accessToken", accessToken)
            put("clientToken", clientToken)
        })
        if (!r.status.isSuccess() && r.status.value != 401) fail(r, passwordRequest = false)
    }

    /** Отзывает все токены аккаунта (выход на всех устройствах), требует пароль. */
    suspend fun signout(login: String, password: String) {
        val r = post("/auth/signout", buildJsonObject {
            put("username", login)
            put("password", password)
        })
        if (!r.status.isSuccess()) fail(r, passwordRequest = true)
    }

    /** Скин и плащ игрока: https://skinsystem.ely.by/textures/<ник>; 204 или 404, если скина нет. */
    suspend fun textures(nick: String): ElyTextures {
        val r = http.get("$SKIN_SYSTEM/textures/$nick")
        if (!r.status.isSuccess() || r.status.value == 204) return ElyTextures(null, false, null)
        val text = r.bodyAsText().ifBlank { return ElyTextures(null, false, null) }
        val o = YouLauncherJson.parseToJsonElement(text).jsonObject
        val skin = o["SKIN"] as? JsonObject
        val slim = (skin?.get("metadata") as? JsonObject)?.s("model") == "slim"
        return ElyTextures(secureTextureUrl(skin?.s("url")), slim, secureTextureUrl((o["CAPE"] as? JsonObject)?.s("url")))
    }

    companion object {
        /**
         * Ely.by отдаёт ссылки на текстуры по http (http://ely.by/storage/skins/...), а картинки грузятся только по https;
         * ely.by отдаёт те же файлы по https.
         */
        fun secureTextureUrl(url: String?): String? = url?.let {
            val host = it.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
            if (it.startsWith("http://") && (host == "ely.by" || host.endsWith(".ely.by"))) "https://" + it.removePrefix("http://") else it
        }

        const val AUTH_SERVER = "https://authserver.ely.by"
        const val SKIN_SYSTEM = "https://skinsystem.ely.by"
    }
}

private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
