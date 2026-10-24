package app.youlauncher.ui

/** Сочетание клавиш в виде "Ctrl+Shift+K". Код клавиши: AWT VK_*, так его отдаёт Compose на десктопе. */
data class Hotkey(val ctrl: Boolean, val shift: Boolean, val alt: Boolean, val keyCode: Int, val keyName: String) {
    override fun toString(): String = buildList {
        if (ctrl) add("Ctrl")
        if (shift) add("Shift")
        if (alt) add("Alt")
        add(keyName)
    }.joinToString("+")

    fun matches(ctrl: Boolean, shift: Boolean, alt: Boolean, keyCode: Int): Boolean =
        this.ctrl == ctrl && this.shift == shift && this.alt == alt && this.keyCode == keyCode

    companion object {
        fun parse(text: String): Hotkey? {
            val parts = text.split('+').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            var ctrl = false
            var shift = false
            var alt = false
            for (mod in parts.dropLast(1)) {
                when (mod.lowercase()) {
                    "ctrl", "control" -> ctrl = true
                    "shift" -> shift = true
                    "alt" -> alt = true
                    else -> return null
                }
            }
            val name = parts.last()
            val code = keyCode(name) ?: return null
            return Hotkey(ctrl, shift, alt, code, name.replaceFirstChar { it.uppercaseChar() })
        }

        fun keyCode(name: String): Int? {
            val field = when (name.lowercase()) {
                "enter", "return" -> "VK_ENTER"
                "esc", "escape" -> "VK_ESCAPE"
                "," -> "VK_COMMA"
                "." -> "VK_PERIOD"
                "/" -> "VK_SLASH"
                "space" -> "VK_SPACE"
                else -> "VK_" + name.uppercase()
            }
            return runCatching { java.awt.event.KeyEvent::class.java.getField(field).getInt(null) }.getOrNull()
        }

        /** Название клавиши по коду для записи нового сочетания. */
        fun keyName(code: Int): String? = when (code) {
            java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_SHIFT, java.awt.event.KeyEvent.VK_ALT -> null
            java.awt.event.KeyEvent.VK_COMMA -> "Comma"
            java.awt.event.KeyEvent.VK_PERIOD -> "Period"
            java.awt.event.KeyEvent.VK_ENTER -> "Enter"
            else -> java.awt.event.KeyEvent.getKeyText(code).takeIf { it.isNotBlank() && !it.contains(' ') }
        }
    }
}
