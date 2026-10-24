package app.youlauncher.ui.window

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/** Небольшая обёртка над dwmapi.dll: скругление углов и тень у окна без системной рамки (Windows 11). */
internal object WindowsDwm {
    private interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUND = 2
    private const val DWMWCP_DONOTROUND = 1

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val api: DwmApi? by lazy {
        if (!isWindows) null else runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
    }

    fun setRoundedCorners(window: Window, rounded: Boolean) {
        val dwm = api ?: return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            val value = IntByReference(if (rounded) DWMWCP_ROUND else DWMWCP_DONOTROUND)
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, value.pointer, 4)
        }
    }
}
