package app.youlauncher.core.settings

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    @Test
    fun `defaults when file missing`() = runTest {
        val dir = Files.createTempDirectory("youlauncher-settings")
        val repo = SettingsRepository(dir.resolve("settings.json"), backgroundScope)
        assertEquals(LauncherSettings(), repo.settings.value)
    }

    @Test
    fun `changes are saved and loaded back`() {
        val dir = Files.createTempDirectory("youlauncher-settings")
        val file = dir.resolve("settings.json")
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val repo = SettingsRepository(file, scope)
        repo.update { it.copy(language = AppLanguage.English, theme = it.theme.copy(mode = ThemeMode.Dark)) }
        scope.advanceUntilIdle()
        assertTrue(file.readText().contains("\"English\""))

        val reloaded = SettingsRepository(file, TestScope(StandardTestDispatcher()))
        assertEquals(AppLanguage.English, reloaded.settings.value.language)
        assertEquals(ThemeMode.Dark, reloaded.settings.value.theme.mode)
    }

    @Test
    fun `broken or old file falls back and keeps known fields`() = runTest {
        val dir = Files.createTempDirectory("youlauncher-settings")
        val file = dir.resolve("settings.json")
        file.writeText("""{"language":"Russian","unknownField":42,"theme":{"mode":"Light"}}""")
        val repo = SettingsRepository(file, backgroundScope)
        assertEquals(AppLanguage.Russian, repo.settings.value.language)
        assertEquals(ThemeMode.Light, repo.settings.value.theme.mode)
        assertEquals(LauncherSettings.CURRENT_SCHEMA, repo.settings.value.schema)

        file.writeText("{not json")
        val broken = SettingsRepository(file, backgroundScope)
        assertEquals(LauncherSettings(), broken.settings.value)
    }
}
