package app.youlauncher.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

val YouLauncherJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
    coerceInputValues = true
}

/** Атомарная запись: сначала во временный файл рядом, затем перемещение поверх. */
fun Path.writeTextAtomic(text: String) {
    parent?.createDirectories()
    val tmp = resolveSibling("$fileName.tmp")
    tmp.writeText(text, Charsets.UTF_8)
    try {
        Files.move(tmp, this, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: Exception) {
        Files.move(tmp, this, StandardCopyOption.REPLACE_EXISTING)
    }
}

fun <T> Path.readJsonOrNull(serializer: KSerializer<T>, json: Json = YouLauncherJson): T? =
    if (!exists()) null else runCatching { json.decodeFromString(serializer, readText(Charsets.UTF_8)) }.getOrNull()

fun <T> Path.writeJson(serializer: KSerializer<T>, value: T, json: Json = YouLauncherJson) =
    writeTextAtomic(json.encodeToString(serializer, value))
