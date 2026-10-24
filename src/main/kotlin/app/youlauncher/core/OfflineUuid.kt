package app.youlauncher.core

import java.util.UUID

/**
 * Стабильный UUID офлайн-игрока: UUID v3 из строки "OfflinePlayer:<ник>", как это делает сервер
 * в офлайн-режиме. Один и тот же ник всегда даёт один и тот же UUID.
 */
fun offlineUuid(nick: String): String =
    UUID.nameUUIDFromBytes("OfflinePlayer:$nick".toByteArray(Charsets.UTF_8)).toString()

/** Ник офлайн-аккаунта: 3–16 символов, латиница, цифры и подчёркивание. */
fun isValidNick(nick: String): Boolean = nick.length in 3..16 && nick.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }

/** Маскирует секреты в строке для логов: токены, пароли, ключи. */
fun maskSecrets(text: String): String {
    var out = text
    val patterns = listOf(
        Regex("""("?(?:accessToken|clientToken|password|token|apiKey|x-api-key)"?\s*[:=]\s*"?)([^"\s,}&]+)""", RegexOption.IGNORE_CASE),
        Regex("""(--accessToken\s+)(\S+)"""),
        Regex("""(Bearer\s+)([A-Za-z0-9._\-]+)"""),
    )
    for (p in patterns) out = p.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) }
    return out
}

private fun mask(secret: String): String = if (secret.length <= 4) "****" else secret.take(2) + "****" + secret.takeLast(2)
