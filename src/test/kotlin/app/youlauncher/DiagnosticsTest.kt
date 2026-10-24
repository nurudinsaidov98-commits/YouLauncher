package app.youlauncher

import app.youlauncher.core.AppDirs
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.data.system.Diagnostics
import app.youlauncher.data.system.LauncherCrash
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticsTest {
    @Test
    fun `diagnostics hide tokens and the windows user name`() = runBlocking<Unit> {
        val dirs = AppDirs(Files.createTempDirectory("diag")).ensure()
        val user = System.getProperty("user.name")
        val crash = LauncherCrash(dirs.logsDir, "1.0").write(IllegalStateException("accessToken=abcdef123456"), "интерфейс")
        assertNotNull(crash)
        assertFalse(Files.readString(crash).contains("abcdef123456"))

        val log = listOf(LogLine(0, 0, LogLevel.Info, app.youlauncher.core.model.LogSource.Game, null, "--accessToken secrettoken99 C:\\Users\\$user\\AppData", instanceId = "x"))
        val zip = Diagnostics(dirs, "1.0").write(Files.createTempDirectory("out").resolve("d.zip"), LauncherSettings(), emptyList(), log)
        ZipFile(zip.toFile()).use { z ->
            val text = z.getInputStream(z.getEntry("launcher-log.txt")).readBytes().decodeToString()
            assertFalse(text.contains("secrettoken99"))
            if (user.length >= 2) assertFalse(text.contains("\\Users\\$user\\"), "Имя пользователя скрыто")
            assertNotNull(z.getEntry("system.txt"))
            assertTrue(z.entries().asSequence().any { it.name.startsWith("crashes/launcher-crash-") })
        }
    }
}
