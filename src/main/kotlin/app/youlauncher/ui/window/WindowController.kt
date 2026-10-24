package app.youlauncher.ui.window

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.youlauncher.core.settings.WindowMode
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

enum class ResizeEdge(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    Left(true, false, false, false),
    Right(false, false, true, false),
    Top(false, true, false, false),
    Bottom(false, false, false, true),
    TopLeft(true, true, false, false),
    TopRight(false, true, true, false),
    BottomLeft(true, false, false, true),
    BottomRight(false, false, true, true),
}

/**
 * Управление окном без системной рамки: свернуть, развернуть на рабочую область (с учётом панели
 * задач), перетаскивание за заголовок, изменение размера за края, компактный и мини-режим.
 */
@Stable
class WindowController(
    val window: Window?,
    private val onClose: () -> Unit,
) {
    var isMaximized by mutableStateOf(false)
        private set
    var isMinimized by mutableStateOf(false)
        private set
    var isFocused by mutableStateOf(true)
        private set

    private var restoreBounds: Rectangle? = null
    private var dragStartMouse: Point? = null
    private var dragStartBounds: Rectangle? = null

    val minWidth = 900
    val minHeight = 600

    init {
        window?.addWindowListener(object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent) { isMinimized = true }
            override fun windowDeiconified(e: WindowEvent) { isMinimized = false }
            override fun windowActivated(e: WindowEvent) { isFocused = true }
            override fun windowDeactivated(e: WindowEvent) { isFocused = false }
        })
        window?.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (isMaximized && window.bounds != workArea()) isMaximized = false
            }
        })
    }

    fun minimize() {
        (window as? Frame)?.extendedState = Frame.ICONIFIED
    }

    fun close() = onClose()

    fun toggleMaximize() {
        val w = window ?: return
        if (isMaximized) {
            restoreBounds?.let { w.bounds = it }
            isMaximized = false
        } else {
            restoreBounds = w.bounds
            w.bounds = workArea()
            isMaximized = true
        }
    }

    fun applyMode(mode: WindowMode) {
        val w = window ?: return
        if (isMaximized) toggleMaximize()
        when (mode) {
            WindowMode.Normal -> if (w.width < 1100) w.setSize(1280, 820)
            WindowMode.Compact -> w.setSize(minWidth, 640)
            WindowMode.Mini -> w.setSize(minWidth, minHeight)
        }
    }

    private fun workArea(): Rectangle {
        val w = window ?: return Rectangle(0, 0, 1280, 820)
        val gc = w.graphicsConfiguration
        val screen = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(
            screen.x + insets.left,
            screen.y + insets.top,
            screen.width - insets.left - insets.right,
            screen.height - insets.top - insets.bottom,
        )
    }

    fun startDrag() {
        val w = window ?: return
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        if (isMaximized) {
            // Как в системных окнах: при перетаскивании развёрнутого окна оно восстанавливается под курсором.
            val restore = restoreBounds ?: Rectangle(0, 0, 1280, 820)
            val ratio = (mouse.x - w.x).toDouble() / w.width
            restore.x = mouse.x - (restore.width * ratio).toInt()
            restore.y = mouse.y - 20
            w.bounds = restore
            isMaximized = false
        }
        dragStartMouse = mouse
        dragStartBounds = w.bounds
    }

    fun dragMove() {
        val w = window ?: return
        val start = dragStartMouse ?: return
        val bounds = dragStartBounds ?: return
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        w.setLocation(bounds.x + mouse.x - start.x, bounds.y + mouse.y - start.y)
    }

    fun endDrag() {
        val w = window
        // Перетаскивание к верхнему краю экрана разворачивает окно.
        if (w != null && dragStartMouse != null) {
            val mouse = MouseInfo.getPointerInfo()?.location
            if (mouse != null && mouse.y <= workArea().y + 1) toggleMaximize()
        }
        dragStartMouse = null
        dragStartBounds = null
    }

    fun startResize() {
        dragStartMouse = MouseInfo.getPointerInfo()?.location
        dragStartBounds = window?.bounds
    }

    fun resize(edge: ResizeEdge) {
        val w = window ?: return
        val start = dragStartMouse ?: return
        val b = dragStartBounds ?: return
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        val dx = mouse.x - start.x
        val dy = mouse.y - start.y
        var x = b.x
        var y = b.y
        var width = b.width
        var height = b.height
        if (edge.right) width = (b.width + dx).coerceAtLeast(minWidth)
        if (edge.bottom) height = (b.height + dy).coerceAtLeast(minHeight)
        if (edge.left) {
            width = (b.width - dx).coerceAtLeast(minWidth)
            x = b.x + b.width - width
        }
        if (edge.top) {
            height = (b.height - dy).coerceAtLeast(minHeight)
            y = b.y + b.height - height
        }
        w.setBounds(x, y, width, height)
    }

    fun endResize() {
        dragStartMouse = null
        dragStartBounds = null
    }
}

val LocalWindowController = staticCompositionLocalOf { WindowController(null) {} }
