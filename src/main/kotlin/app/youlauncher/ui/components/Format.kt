package app.youlauncher.ui.components

import androidx.compose.runtime.Composable
import app.youlauncher.resources.Res
import app.youlauncher.resources.duration_h
import app.youlauncher.resources.duration_hm
import app.youlauncher.resources.duration_m
import app.youlauncher.resources.duration_zero
import app.youlauncher.resources.number_millions
import app.youlauncher.resources.number_thousands
import app.youlauncher.resources.size_gb
import app.youlauncher.resources.size_kb
import app.youlauncher.resources.size_mb
import app.youlauncher.resources.time_days_ago
import app.youlauncher.resources.time_hours_ago
import app.youlauncher.resources.time_just_now
import app.youlauncher.resources.time_minutes_ago
import app.youlauncher.resources.time_months_ago
import app.youlauncher.resources.time_never
import app.youlauncher.resources.time_weeks_ago
import app.youlauncher.resources.time_yesterday
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Чистые функции форматирования, которые проверяются тестами. */
object Formats {
    sealed interface Ago {
        data object Never : Ago
        data object JustNow : Ago
        data class Minutes(val n: Int) : Ago
        data class Hours(val n: Int) : Ago
        data object Yesterday : Ago
        data class Days(val n: Int) : Ago
        data class Weeks(val n: Int) : Ago
        data class Months(val n: Int) : Ago
    }

    fun ago(time: Long?, now: Long = System.currentTimeMillis()): Ago {
        if (time == null || time <= 0) return Ago.Never
        val diff = (now - time).coerceAtLeast(0)
        val minutes = diff / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> Ago.JustNow
            minutes < 60 -> Ago.Minutes(minutes.toInt())
            hours < 24 -> Ago.Hours(hours.toInt())
            days < 2 -> Ago.Yesterday
            days < 7 -> Ago.Days(days.toInt())
            days < 30 -> Ago.Weeks((days / 7).toInt())
            else -> Ago.Months((days / 30).toInt().coerceAtLeast(1))
        }
    }

    fun decimal(value: Double, digits: Int = 1): String {
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val pattern = if (digits == 0) "#" else "#." + "#".repeat(digits)
        return DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    fun date(ms: Long, locale: Locale = Locale.getDefault()): String =
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
            .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))

    fun dateTime(ms: Long, locale: Locale = Locale.getDefault()): String =
        java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT).withLocale(locale)
            .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))
}

@Composable
fun timeAgo(time: Long?, now: Long = System.currentTimeMillis()): String = when (val a = Formats.ago(time, now)) {
    Formats.Ago.Never -> stringResource(Res.string.time_never)
    Formats.Ago.JustNow -> stringResource(Res.string.time_just_now)
    is Formats.Ago.Minutes -> pluralStringResource(Res.plurals.time_minutes_ago, a.n, a.n)
    is Formats.Ago.Hours -> pluralStringResource(Res.plurals.time_hours_ago, a.n, a.n)
    Formats.Ago.Yesterday -> stringResource(Res.string.time_yesterday)
    is Formats.Ago.Days -> pluralStringResource(Res.plurals.time_days_ago, a.n, a.n)
    is Formats.Ago.Weeks -> pluralStringResource(Res.plurals.time_weeks_ago, a.n, a.n)
    is Formats.Ago.Months -> pluralStringResource(Res.plurals.time_months_ago, a.n, a.n)
}

@Composable
fun duration(ms: Long): String {
    val totalMinutes = (ms / 60_000).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        totalMinutes == 0 -> stringResource(Res.string.duration_zero)
        h == 0 -> stringResource(Res.string.duration_m, m)
        m == 0 -> stringResource(Res.string.duration_h, h)
        else -> stringResource(Res.string.duration_hm, h, m)
    }
}

/** Размер файла вне композиции (для сообщений из корутин). */
suspend fun fileSizeText(bytes: Long): String = when {
    bytes >= 1L shl 30 -> org.jetbrains.compose.resources.getString(Res.string.size_gb, Formats.decimal(bytes / (1L shl 30).toDouble()))
    bytes >= 1L shl 20 -> org.jetbrains.compose.resources.getString(Res.string.size_mb, Formats.decimal(bytes / (1L shl 20).toDouble()))
    else -> org.jetbrains.compose.resources.getString(Res.string.size_kb, Formats.decimal(bytes / 1024.0, 0))
}

@Composable
fun fileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> stringResource(Res.string.size_gb, Formats.decimal(bytes / (1L shl 30).toDouble()))
    bytes >= 1L shl 20 -> stringResource(Res.string.size_mb, Formats.decimal(bytes / (1L shl 20).toDouble()))
    else -> stringResource(Res.string.size_kb, Formats.decimal(bytes / 1024.0, 0))
}

@Composable
fun compactNumber(n: Long): String = when {
    n >= 1_000_000 -> stringResource(Res.string.number_millions, Formats.decimal(n / 1_000_000.0))
    n >= 1_000 -> stringResource(Res.string.number_thousands, Formats.decimal(n / 1_000.0))
    else -> n.toString()
}
