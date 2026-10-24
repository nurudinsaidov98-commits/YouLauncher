package app.youlauncher.tools

import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.core.maskSecrets
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.repo.NewInstanceSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.writeText

/**
 * Реальная проверка ядра: создаёт экземпляры, скачивает файлы с серверов Mojang, запускает игру,
 * ждёт признаков загрузки клиента в журнале и останавливает игру. Итог пишется в docs/verification.md.
 * Аргументы: путь к отчёту, затем версии.
 */
fun main(args: Array<String>) {
    val ok = runBlocking { verify(args) }
    kotlin.system.exitProcess(if (ok) 0 else 1)
}

private suspend fun verify(args: Array<String>): Boolean {
    val report = Path.of(args[0])
    val versions = args.drop(1).ifEmpty { listOf("latest", "1.8.9") }
    val home = Path.of(System.getProperty("youlauncher.home"))
    val graph = AppGraph.create(AppDirs(home).ensure())
    val out = StringBuilder()
    out.appendLine("# Проверка запуска")
    out.appendLine()
    out.appendLine("Дата: ${LocalDateTime.now().withNano(0)}. Каталог данных: `${home}`. Аккаунт: офлайн. Система: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}.")
    out.appendLine()
    out.appendLine("Файлы скачиваются только с серверов Mojang с проверкой SHA-1, затем игра запускается и останавливается после появления в журнале признаков загрузки клиента.")
    out.appendLine()
    val markers = listOf("Sound engine started", "Created: ", "Backend library", "LWJGL Version", "Reloading ResourceManager")
    var allOk = true

    for (arg in versions) {
        // Формат: версия, загрузчик:версия или загрузчик:версия+мод+мод (слаги Modrinth): fabric:1.21.10+sodium.
        val spec = arg.split('+')
        val mods = spec.drop(1)
        val loader = if (":" in spec[0]) LoaderType.entries.first { it.name.equals(spec[0].substringBefore(':'), true) } else LoaderType.Vanilla
        val v = spec[0].substringAfter(':')
        val gameId = if (v == "latest") graph.versions.latestRelease()?.id ?: error("Нет latest release") else v
        val id = if (loader == LoaderType.Vanilla) gameId else "${loader.title} $gameId"
        println("=== $id ===")
        val inst = graph.instances.create(NewInstanceSpec(name = "Проверка $id", gameVersion = gameId, loader = loader, minMemoryMb = 1024, maxMemoryMb = 3072))
        val installedMods = mutableListOf<String>()
        for (slug in mods) {
            // Префикс cf: берёт мод из CurseForge (поиск по слагу), без префикса из Modrinth.
            val project = if (slug.startsWith("cf:")) {
                val s = slug.removePrefix("cf:")
                graph.curseForge.search(app.youlauncher.core.repo.ModQuery(text = s, gameVersion = gameId, loader = loader)).hits.firstOrNull { it.slug == s }
                    ?: error("В CurseForge не найден $s")
            } else graph.modrinth.project(slug)
            val names = graph.modInstaller.install(inst.id, project).getOrElse { e -> error("Не удалось установить $slug: ${e.message}") }
            installedMods += names
            println("Установлено: ${names.joinToString()}")
        }
        val startLines = graph.log.lines.value.size
        val t0 = System.currentTimeMillis()
        graph.launcher.launch(inst.id)
        var runningAt = 0L
        var verdict = "нет признаков загрузки за отведённое время"
        var ok = false
        while (System.currentTimeMillis() - t0 < 30 * 60_000) {
            delay(1000)
            val st = graph.launcher.state(inst.id)
            if (st is LaunchState.Preparing) print("\r${st.stage} ${((st.progress ?: 0f) * 100).toInt()}% ${st.detail}      ")
            if (st is LaunchState.Running && runningAt == 0L) { runningAt = System.currentTimeMillis(); println("\nЗапущено, PID ${st.pid}") }
            if (st is LaunchState.Failed) { verdict = "ошибка запуска: ${st.message}"; break }
            if (st is LaunchState.Crashed) { verdict = "сбой игры, код ${st.exitCode}: ${st.summary}"; break }
            val lines = graph.log.lines.value.drop(startLines).filter { it.instanceId == inst.id }
            val found = markers.filter { m -> lines.any { it.text.contains(m) } }
            if (runningAt > 0 && found.size >= 2) { verdict = "клиент загрузился (в журнале: ${found.joinToString()})"; ok = true; break }
            if (runningAt > 0 && System.currentTimeMillis() - runningAt > 240_000) break
            if (st is LaunchState.Idle && runningAt > 0) { verdict = "игра закрылась сама"; break }
        }
        val prepareSec = if (runningAt > 0) (runningAt - t0) / 1000 else (System.currentTimeMillis() - t0) / 1000
        delay(3000)
        graph.launcher.stop(inst.id)
        delay(4000)
        val lines = graph.log.lines.value.drop(startLines).filter { it.instanceId == inst.id }
        val seen = mods.associateWith { slug -> lines.any { it.text.contains(slug.removePrefix("cf:"), ignoreCase = true) } }
        if (seen.values.any { !it }) { ok = false; verdict += "; в журнале нет модов: ${seen.filterValues { !it }.keys.joinToString()}" }
        allOk = allOk && ok
        println("\n$id: $verdict")
        out.appendLine("## $id: ${if (ok) "успешно" else "НЕ ПРОЙДЕНО"}")
        out.appendLine()
        out.appendLine("- Итог: $verdict")
        out.appendLine("- Подготовка (загрузка и проверка файлов, Java): $prepareSec с")
        if (installedMods.isNotEmpty()) out.appendLine("- Моды из каталогов (с зависимостями): ${installedMods.joinToString()}")
        out.appendLine("- Строк журнала: ${lines.size}")
        out.appendLine()
        out.appendLine("Начало журнала:")
        out.appendLine("```")
        lines.take(25).forEach { out.appendLine("[${it.level}] ${it.thread?.let { t -> "[$t] " } ?: ""}${maskSecrets(it.text).take(300)}") }
        out.appendLine("```")
        out.appendLine("Конец журнала:")
        out.appendLine("```")
        lines.takeLast(25).forEach { out.appendLine("[${it.level}] ${it.thread?.let { t -> "[$t] " } ?: ""}${maskSecrets(it.text).take(300)}") }
        out.appendLine("```")
        out.appendLine()
    }
    Files.createDirectories(report.parent)
    report.writeText(out.toString())
    graph.shutdown()
    println(if (allOk) "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" else "ЕСТЬ НЕПРОЙДЕННЫЕ ПРОВЕРКИ")
    return allOk
}
