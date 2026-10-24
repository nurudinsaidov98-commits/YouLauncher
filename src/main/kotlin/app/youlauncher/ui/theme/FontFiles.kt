package app.youlauncher.ui.theme

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/**
 * Шрифты приложения как файлы на диске. Загрузка шрифта из ресурсов Compose держит отдельную копию файла
 * (3,9 МБ у Google Sans Flex) в куче и в памяти Skia на каждое начертание: 12 начертаний давали около 100 МБ.
 * Файловый шрифт Skia отображает в память (общие страницы файла), поэтому все начертания делят одну копию.
 * Файл извлекается один раз в папку данных; имя содержит CRC32, так новая версия шрифта не путается со старой.
 */
object FontFiles {
    private const val RESOURCE_DIR = "composeResources/app.youlauncher.resources/font/"

    @Volatile
    private var dir: Path? = null

    fun init(dir: Path) {
        this.dir = dir
    }

    val flex: File? by lazy { extract("google_sans_flex.ttf") }
    val code: File? by lazy { extract("google_sans_code.ttf") }

    /** Извлекает шрифт, если его ещё нет; null, если папка не задана или запись не удалась (тогда шрифт из ресурсов). */
    internal fun extract(name: String, target: Path? = dir, read: () -> ByteArray? = { readResource(name) }): File? = runCatching {
        val d = target ?: return null
        val bytes = read() ?: return null
        val crc = CRC32().apply { update(bytes) }.value.toString(16)
        val base = name.substringBeforeLast('.')
        val file = d.resolve("$base-$crc.${name.substringAfterLast('.')}")
        if (!Files.exists(file) || Files.size(file) != bytes.size.toLong()) {
            Files.createDirectories(d)
            val tmp = d.resolve("$base-$crc.tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        // Прежние версии того же шрифта; занятый другим окном YouLauncher файл просто останется до следующего раза.
        d.toFile().listFiles { f -> f.name.startsWith("$base-") && f.name != file.fileName.toString() }?.forEach { it.delete() }
        file.toFile()
    }.getOrNull()

    private fun readResource(name: String): ByteArray? =
        FontFiles::class.java.classLoader.getResourceAsStream(RESOURCE_DIR + name)?.use { it.readBytes() }
}
