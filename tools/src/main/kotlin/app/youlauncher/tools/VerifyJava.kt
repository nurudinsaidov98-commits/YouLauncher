package app.youlauncher.tools

import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.data.java.JavaSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/** Реальная проверка загрузки Temurin: скачать, сверить sha256, распаковать, запустить java -version, найти поиском. */
fun main(args: Array<String>) {
    val major = args.firstOrNull()?.toIntOrNull() ?: 8
    val graph = AppGraph.create(AppDirs(Path.of(System.getProperty("youlauncher.home"))).ensure())
    val ok = runBlocking {
        val build = graph.adoptium.latest(major)
        println("Сборка: ${build.release}, ${build.fileName}, ${build.size / 1_000_000} МБ, sha256 ${build.sha256.take(12)}…")
        val t0 = System.currentTimeMillis()
        val install = graph.adoptium.install(major)
        println("Установлена за ${(System.currentTimeMillis() - t0) / 1000} с: ${install.version} в ${install.home}")
        val check = graph.java.verify(install)
        println("java -version: ${if (check.ok) "работает" else "ОШИБКА"} (${check.output})")
        val found = graph.java.detect().any { it.home == install.home && it.source == JavaSource.Temurin }
        println("Поиск находит как Temurin: $found")
        check.ok && found && install.major == major
    }
    graph.shutdown()
    println(if (ok) "ПРОВЕРКА ПРОЙДЕНА" else "ПРОВЕРКА НЕ ПРОЙДЕНА")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
