package app.youlauncher.tools

import app.youlauncher.data.servers.ServerPing
import kotlinx.coroutines.runBlocking

/** Реальная проверка опроса серверов: печатает описание, игроков, версию и задержку. */
fun main(args: Array<String>) {
    var ok = true
    for (address in args.ifEmpty { arrayOf("mc.hypixel.net") }) {
        val srv = ServerPing.resolveSrv(ServerPing.parseAddress(address).first)
        val result = runCatching { runBlocking { ServerPing.ping(address) } }
        result.onSuccess { s ->
            println("$address (SRV: ${srv ?: "нет"}): ${s.online}/${s.max}, версия «${s.version}», ${s.latencyMs} мс, значок ${s.favicon?.size ?: 0} байт, описание «${s.motd.lines().first().take(60)}»")
        }.onFailure { e ->
            ok = false
            println("$address: ошибка ${e.message}")
        }
    }
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
