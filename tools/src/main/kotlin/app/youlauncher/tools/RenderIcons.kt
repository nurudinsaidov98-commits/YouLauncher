package app.youlauncher.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.Canvas
import app.youlauncher.ui.brand.brandLogoColors
import app.youlauncher.ui.brand.drawYouLauncherLogo
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Рисует логотип YouLauncher кодом (тот же DrawScope, что и в приложении) и сохраняет:
 * packaging/youlauncher.ico (16-256 px, PNG внутри ICO), packaging/youlauncher.png (512 px)
 * и drawable/youlauncher_logo.png для README.
 */
fun main(args: Array<String>) {
    val packaging = Path.of(args[0]).createDirectories()
    val drawable = Path.of(args[1]).createDirectories()
    val colors = brandLogoColors(dark = false)

    fun render(size: Int): ByteArray {
        val scene = ImageComposeScene(size, size, Density(1f)) {
            Canvas(Modifier.fillMaxSize()) { drawYouLauncherLogo(colors) }
        }
        val image = scene.render()
        scene.close()
        return image.encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    val sizes = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)
    val pngs = sizes.associateWith(::render)
    packaging.resolve("youlauncher.ico").writeBytes(buildIco(pngs))
    packaging.resolve("youlauncher.png").writeBytes(render(512))
    drawable.resolve("youlauncher_logo.png").writeBytes(render(256))
    println("Иконки сохранены в $packaging")
}

/** ICO с PNG-кадрами (поддерживается Windows Vista и новее). */
private fun buildIco(pngs: Map<Int, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    val le = LittleEndian(DataOutputStream(out))
    le.short(0)
    le.short(1)
    le.short(pngs.size)
    var offset = 6 + 16 * pngs.size
    for ((size, data) in pngs) {
        le.byte(if (size >= 256) 0 else size)
        le.byte(if (size >= 256) 0 else size)
        le.byte(0)
        le.byte(0)
        le.short(1)
        le.short(32)
        le.int(data.size)
        le.int(offset)
        offset += data.size
    }
    for (data in pngs.values) out.write(data)
    return out.toByteArray()
}

private class LittleEndian(private val out: DataOutputStream) {
    fun byte(v: Int) = out.writeByte(v)
    fun short(v: Int) { out.writeByte(v and 0xFF); out.writeByte((v shr 8) and 0xFF) }
    fun int(v: Int) { short(v and 0xFFFF); short((v shr 16) and 0xFFFF) }
}

@Suppress("unused")
private fun Path.ensureParent() = parent?.let { Files.createDirectories(it) }
