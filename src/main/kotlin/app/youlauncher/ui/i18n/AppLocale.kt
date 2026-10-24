package app.youlauncher.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import app.youlauncher.core.settings.AppLanguage
import java.util.Locale

/**
 * Смена языка без перезапуска: Compose Resources берёт локаль из Locale.getDefault(),
 * поэтому подменяем её и пересоздаём дерево через key().
 */
object AppLocale {
    private val systemDefault: Locale = Locale.getDefault()
    val LocalLanguageTag = staticCompositionLocalOf { systemDefault.toLanguageTag() }

    fun resolve(language: AppLanguage): Locale = language.tag?.let { Locale.forLanguageTag(it) } ?: systemDefault

    /** Язык интерфейса, который реально будет показан (ru или en). */
    fun effectiveTag(language: AppLanguage): String =
        if (resolve(language).language == "ru") "ru" else "en"

    @Composable
    fun Provide(language: AppLanguage, content: @Composable () -> Unit) {
        val locale = resolve(language)
        Locale.setDefault(locale)
        CompositionLocalProvider(LocalLanguageTag provides locale.toLanguageTag()) {
            key(locale.toLanguageTag()) { content() }
        }
    }
}
