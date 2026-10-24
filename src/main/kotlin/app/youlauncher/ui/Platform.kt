package app.youlauncher.ui

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Действия операционной системы: открыть папку или ссылку, буфер обмена. */
object Platform {
    fun openFolder(path: Path): Boolean = runCatching {
        Files.createDirectories(path)
        if (System.getProperty("os.name").startsWith("Windows")) {
            ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start()
        } else {
            Desktop.getDesktop().open(path.toFile())
        }
        true
    }.getOrDefault(false)

    fun openUrl(url: String): Boolean = runCatching {
        require(url.startsWith("https://") || url.startsWith("http://"))
        Desktop.getDesktop().browse(URI(url))
        true
    }.getOrDefault(false)

    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    /** Путь к исполняемому файлу лаунчера (YouLauncher.exe в сборке, java.exe при запуске из Gradle). */
    fun launcherExecutable(): String? = ProcessHandle.current().info().command().orElse(null)

    /**
     * Ярлык на рабочем столе, который запускает экземпляр сразу: YouLauncher.exe --launch <id>.
     * Создаётся через WScript.Shell (штатный способ Windows), путь к рабочему столу берётся у системы,
     * поэтому работает и с рабочим столом в OneDrive.
     */
    fun createDesktopShortcut(name: String, arguments: String): Boolean = runCatching {
        if (!System.getProperty("os.name").startsWith("Windows")) return false
        val exe = launcherExecutable() ?: return false
        val safeName = name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60)
        fun ps(s: String) = s.replace("'", "''")
        val script = "\$d=[Environment]::GetFolderPath('Desktop');" +
            "\$s=(New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path \$d '${ps(safeName)}.lnk'));" +
            "\$s.TargetPath='${ps(exe)}';\$s.Arguments='${ps(arguments)}';\$s.IconLocation='${ps(exe)},0';\$s.Save()"
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start()
        p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    /** Системный диалог выбора файлов. Возвращает пустой список при отмене. */
    fun chooseFiles(title: String, extensions: List<String> = emptyList(), multiple: Boolean = false): List<Path> {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
        dialog.isMultipleMode = multiple
        if (extensions.isNotEmpty()) {
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, n -> extensions.any { n.endsWith(".$it", ignoreCase = true) } }
        }
        dialog.isVisible = true
        return dialog.files.map { it.toPath() }
    }

    /** Системный диалог сохранения файла. */
    fun chooseSaveFile(title: String, defaultName: String): Path? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val ext = defaultName.substringAfterLast('.', "")
        val fixed = if (ext.isNotEmpty() && !name.endsWith(".$ext", ignoreCase = true)) "$name.$ext" else name
        return Path.of(dir, fixed)
    }

    fun chooseFolder(title: String): Path? {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }

    fun totalMemoryMb(): Long = runCatching {
        val os = java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        os.totalMemorySize / (1024 * 1024)
    }.getOrDefault(8192)
}
