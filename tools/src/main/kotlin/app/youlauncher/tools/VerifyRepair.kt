package app.youlauncher.tools

import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.download.Downloader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path

/** Реальная проверка починки: портим файл клиента и библиотеку, чиним, сверяем хеши с метаданными Mojang. */
fun main(args: Array<String>) {
    val version = args.firstOrNull() ?: "1.21.1"
    val graph = AppGraph.create(AppDirs(Path.of(System.getProperty("youlauncher.home"))).ensure())
    val ok = runBlocking {
        val inst = graph.instances.instances.value.firstOrNull { it.gameVersion == version && it.loader == LoaderType.Vanilla }
            ?: graph.instances.create(NewInstanceSpec("Починка $version", version))
        val json = graph.mojang.versionJson(version)
        val client = graph.mojang.clientJar(version)
        val lib = json.libraries.mapNotNull { it.downloads?.artifact }.first { it.path != null && Files.exists(graph.dirs.librariesDir.resolve(it.path!!)) }
        val libFile = graph.dirs.librariesDir.resolve(lib.path!!)
        if (Files.exists(client)) Files.writeString(client, "испорчено")
        if (Files.exists(libFile)) Files.writeString(libFile, "испорчено")
        println("Испорчены: ${client.fileName}, ${libFile.fileName}")
        val done = CompletableDeferred<Result<Unit>>()
        val t0 = System.currentTimeMillis()
        graph.launcher.repair(inst.id) { done.complete(it) }
        val result = withTimeout(15 * 60_000) { done.await() }
        val clientOk = Downloader.sha1(client).equals(json.downloads?.client?.sha1, true)
        val libOk = Downloader.sha1(libFile).equals(lib.sha1, true)
        println("Починка за ${(System.currentTimeMillis() - t0) / 1000} с: ${if (result.isSuccess) "успешно" else "ошибка ${result.exceptionOrNull()?.message}"}")
        println("Клиент восстановлен: $clientOk, библиотека восстановлена: $libOk")
        result.isSuccess && clientOk && libOk
    }
    graph.shutdown()
    println(if (ok) "ПРОВЕРКА ПРОЙДЕНА" else "ПРОВЕРКА НЕ ПРОЙДЕНА")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
