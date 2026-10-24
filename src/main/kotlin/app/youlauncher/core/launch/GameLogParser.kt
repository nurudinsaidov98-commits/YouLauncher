package app.youlauncher.core.launch

import app.youlauncher.core.model.LogLevel

/** Строка журнала игры после разбора. */
data class ParsedLog(val level: LogLevel, val thread: String?, val text: String, val time: Long?)

/**
 * Разбор вывода игры. Официальный конфиг логирования Mojang пишет в консоль XML-события log4j:
 * <log4j:Event logger=".." timestamp=".." level="INFO" thread="Render thread">
 *   <log4j:Message><![CDATA[текст]]></log4j:Message>
 * </log4j:Event>
 * Обычные строки ("[12:00:00] [main/INFO]: текст") и трассировки тоже поддерживаются.
 */
class GameLogParser {
    private val buffer = StringBuilder()
    private var inEvent = false

    fun feed(line: String): List<ParsedLog> {
        val trimmed = line.trim()
        if (!inEvent && trimmed.startsWith("<log4j:Event")) {
            inEvent = true
            buffer.setLength(0)
        }
        if (inEvent) {
            buffer.append(line).append('\n')
            if (trimmed.endsWith("</log4j:Event>")) {
                inEvent = false
                return listOfNotNull(parseEvent(buffer.toString()))
            }
            return emptyList()
        }
        if (trimmed.isEmpty()) return emptyList()
        return listOf(parsePlain(line))
    }

    private val attr = Regex("""(\w+)="([^"]*)"""")
    private val cdata = Regex("""<log4j:(Message|Throwable)><!\[CDATA\[(.*?)]]></log4j:\1>""", RegexOption.DOT_MATCHES_ALL)

    private fun parseEvent(xml: String): ParsedLog? {
        val header = xml.substringBefore('>')
        val attrs = attr.findAll(header).associate { it.groupValues[1] to it.groupValues[2] }
        val parts = cdata.findAll(xml).map { it.groupValues[2].trimEnd() }.toList()
        if (parts.isEmpty()) return null
        return ParsedLog(level(attrs["level"]), attrs["thread"], parts.joinToString("\n"), attrs["timestamp"]?.toLongOrNull())
    }

    private val plain = Regex("""^\[(\d{2}:\d{2}:\d{2}(?:\.\d+)?)] \[([^\]]+?)/(\w+)](?: \[[^\]]*])?: ?(.*)$""")

    fun parsePlain(line: String): ParsedLog {
        val m = plain.find(line)
        if (m != null) return ParsedLog(level(m.groupValues[3]), m.groupValues[2], m.groupValues[4], null)
        val l = when {
            line.startsWith("\tat ") || line.startsWith("Caused by") || line.contains("Exception in thread") -> LogLevel.Error
            line.contains("ERROR") -> LogLevel.Error
            line.contains("WARN") -> LogLevel.Warn
            else -> LogLevel.Info
        }
        return ParsedLog(l, null, line, null)
    }

    private fun level(s: String?): LogLevel = when (s?.uppercase()) {
        "TRACE" -> LogLevel.Trace
        "DEBUG" -> LogLevel.Debug
        "WARN", "WARNING" -> LogLevel.Warn
        "ERROR" -> LogLevel.Error
        "FATAL" -> LogLevel.Fatal
        else -> LogLevel.Info
    }
}
