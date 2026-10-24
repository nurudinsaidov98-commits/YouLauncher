package app.youlauncher.data.net

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.settings.ProxySettings
import app.youlauncher.core.settings.ProxyType
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json

val USER_AGENT: String get() = "YouLauncher-Launcher/${app.youlauncher.core.BuildInfo.version} (unofficial; not affiliated with Mojang or Microsoft)"

/** Общий HTTP-клиент: таймауты, повторы для временных ошибок, свой User-Agent (его требует Modrinth). */
fun createHttpClient(proxy: ProxySettings = ProxySettings()): HttpClient = HttpClient(Java) {
    engine {
        when (proxy.type) {
            ProxyType.None -> Unit
            ProxyType.Http -> this.proxy = ProxyBuilder.http(URLBuilder("http://${proxy.host}:${proxy.port}").build())
            ProxyType.Socks -> this.proxy = ProxyBuilder.socks(proxy.host, proxy.port)
        }
    }
    install(ContentNegotiation) { json(YouLauncherJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
        requestTimeoutMillis = 10 * 60_000
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        retryOnException(maxRetries = 3, retryOnTimeout = true)
        exponentialDelay()
    }
    defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
    expectSuccess = false
}
