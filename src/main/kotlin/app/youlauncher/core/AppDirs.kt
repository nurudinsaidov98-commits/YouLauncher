package app.youlauncher.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Каталоги данных лаунчера. По умолчанию %APPDATA%\YouLauncher. Портативный режим: если рядом с exe
 * лежит файл `portable.txt`, всё хранится в папке `data` рядом с приложением.
 * Для тестов и скриншотов корень переопределяется системным свойством `youlauncher.home`.
 */
class AppDirs(val root: Path) {
    val settingsFile: Path get() = root.resolve("settings.json")
    val instancesDir: Path get() = root.resolve("instances")
    val accountsFile: Path get() = root.resolve("accounts.json")
    val atmosphereFile: Path get() = root.resolve("atmospheres.json")
    val cacheDir: Path get() = root.resolve("cache")
    val versionsDir: Path get() = root.resolve("versions")
    val librariesDir: Path get() = root.resolve("libraries")
    val assetsDir: Path get() = root.resolve("assets")
    val runtimesDir: Path get() = root.resolve("runtimes")
    val wallpapersDir: Path get() = root.resolve("wallpapers")
    val logsDir: Path get() = root.resolve("logs")
    val crashReportsDir: Path get() = root.resolve("crash-reports")
    val backupsDir: Path get() = root.resolve("backups")
    val toolsDir: Path get() = root.resolve("tools")
    val iconsCacheDir: Path get() = cacheDir.resolve("images")

    fun ensure(): AppDirs = apply {
        listOf(root, instancesDir, cacheDir, versionsDir, librariesDir, assetsDir, runtimesDir, wallpapersDir, logsDir, backupsDir, toolsDir)
            .forEach { it.createDirectories() }
    }

    companion object {
        const val PORTABLE_MARKER = "portable.txt"

        fun default(): AppDirs {
            System.getProperty("youlauncher.home")?.let { return AppDirs(Path.of(it)) }
            appDirectory()?.let { appDir ->
                if (appDir.resolve(PORTABLE_MARKER).exists()) return AppDirs(appDir.resolve("data"))
            }
            val appData = System.getenv("APPDATA")?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming")
            return AppDirs(migrateLegacy(appData.resolve(LegacyNames.NAME), appData.resolve("YouLauncher")))
        }

        /**
         * Папка, где лежит YouLauncher.exe. В сборке jpackage ресурсы лежат в `<папка>\app\resources`, jar в `<папка>\app`.
         * Берётся первый существующий вариант: `jpackage.app-path` в не-ASCII пути может прийти искажённым.
         */
        fun appDirectory(): Path? = sequenceOf(
            { System.getProperty("compose.application.resources.dir")?.let { Path.of(it).parent?.parent } },
            { System.getProperty("jpackage.app-path")?.let { Path.of(it).parent } },
            {
                Path.of(AppDirs::class.java.protectionDomain.codeSource.location.toURI()).parent?.let { dir ->
                    if (dir.fileName?.toString().equals("app", ignoreCase = true) && dir.parent != null) dir.parent else dir
                }
            },
        ).mapNotNull { runCatching(it).getOrNull() }.firstOrNull { Files.isDirectory(it) }

        /** Папка данных прежней версии переносится под новое имя; если перенести нельзя, используется как есть. */
        fun migrateLegacy(old: Path, target: Path): Path {
            if (target.exists() || !old.exists()) return target
            return runCatching { Files.move(old, target); target }.getOrDefault(old)
        }

        fun isPortable(): Boolean = appDirectory()?.resolve(PORTABLE_MARKER)?.exists() == true
    }
}
