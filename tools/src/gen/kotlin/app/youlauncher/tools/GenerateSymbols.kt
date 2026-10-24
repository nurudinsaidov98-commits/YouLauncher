package app.youlauncher.tools

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.readLines
import kotlin.io.path.writeText

private const val BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web"

/**
 * Скачивает SVG Material Symbols Rounded из официального репозитория Google и генерирует
 * Kotlin-файл с ImageVector. Иконки не перерисовываются: путь берётся из SVG как есть.
 * Использование: gradlew.bat :tools:generateSymbols
 */
fun main(args: Array<String>) {
    val listFile = Path.of(args[0])
    val output = Path.of(args[1])
    val entries = listFile.readLines(Charsets.UTF_8)
        .map { it.removePrefix("﻿").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { if (it.endsWith("+")) it.dropLast(1) to true else it to false }
        .distinctBy { it.first }

    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .build()
    val pool = Executors.newFixedThreadPool(12) { r -> Thread(r).apply { isDaemon = true } }

    fun fetch(name: String, filled: Boolean): String? {
        val suffix = if (filled) "_fill1_24px" else "_24px"
        val url = "$BASE/$name/materialsymbolsrounded/$name$suffix.svg"
        repeat(3) {
            runCatching {
                val request = HttpRequest.newBuilder(URI(url)).timeout(java.time.Duration.ofSeconds(20)).build()
                val res = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (res.statusCode() == 404) return null
                if (res.statusCode() == 200) return res.body()
            }
        }
        error("Не удалось скачать $url")
    }

    data class Item(val prop: String, val name: String, val paths: List<String>, val minY: Float, val size: Float)

    val futures = entries.flatMap { (name, needFill) ->
        val base = pascal(name)
        buildList {
            add(Triple(base, name, false))
            if (needFill) add(Triple(base + "Filled", name, true))
        }
    }.map { (prop, name, filled) ->
        pool.submit<Item?> {
            val svg = fetch(name, filled) ?: run {
                System.err.println("Нет иконки: $name (fill=$filled)")
                return@submit null
            }
            val paths = Regex("""<path[^>]*\sd="([^"]+)"""").findAll(svg).map { it.groupValues[1] }.toList()
            // Новые символы нарисованы в сетке "0 -960 960 960", часть старых в "0 0 24 24" без viewBox.
            val box = Regex("viewBox=\"([^\"]+)\"").find(svg)?.groupValues?.get(1)?.split(' ', ',')?.filter { it.isNotBlank() }?.map { it.toFloat() }
            val minY = box?.getOrNull(1) ?: 0f
            val size = box?.getOrNull(2) ?: (Regex("width=\"(\\d+)\"").find(svg)?.groupValues?.get(1)?.toFloat() ?: 24f)
            Item(prop, name, paths, minY, size)
        }
    }
    val items = futures.mapNotNull { it.get() }.sortedBy { it.prop }
    pool.shutdown()

    val sb = StringBuilder()
    sb.appendLine("// Сгенерировано: gradlew.bat :tools:generateSymbols. Не редактировать вручную.")
    sb.appendLine("// Material Symbols Rounded, Google, Apache License 2.0 (см. NOTICE).")
    sb.appendLine("// Источник: https://github.com/google/material-design-icons/tree/master/symbols/web")
    sb.appendLine("@file:Suppress(\"unused\", \"MaxLineLength\")")
    sb.appendLine()
    sb.appendLine("package app.youlauncher.ui.icons")
    sb.appendLine()
    sb.appendLine("import androidx.compose.ui.graphics.vector.ImageVector")
    sb.appendLine()
    sb.appendLine("/** Официальные иконки Google Material Symbols, стиль Rounded, вес 400, оптический размер 24. */")
    sb.appendLine("object Symbols {")
    for (item in items) {
        val joined = item.paths.joinToString(", ") { "\"$it\"" }
        sb.appendLine("    val ${item.prop}: ImageVector by lazy { symbol(\"${item.name}\", ${item.size}f, ${item.minY}f, $joined) }")
    }
    sb.appendLine("}")
    Files.createDirectories(output.parent)
    output.writeText(sb.toString())
    println("Сгенерировано иконок: ${items.size} -> $output")
}

private fun pascal(snake: String): String =
    snake.split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        .let { if (it.first().isDigit()) "S$it" else it }
