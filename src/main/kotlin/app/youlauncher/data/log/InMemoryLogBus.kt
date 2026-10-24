package app.youlauncher.data.log

import app.youlauncher.core.maskSecrets
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.model.LogSource
import app.youlauncher.core.repo.LogBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Журнал консоли в памяти с ограничением по числу строк. Секреты маскируются при записи. */
class InMemoryLogBus(private val capacity: Int = 20_000) : LogBus {
    private val state = MutableStateFlow<List<LogLine>>(emptyList())
    override val lines: StateFlow<List<LogLine>> = state.asStateFlow()
    private val ids = AtomicLong()

    override fun append(line: LogLine) {
        val safe = line.copy(id = ids.incrementAndGet(), text = maskSecrets(line.text))
        state.update { old -> if (old.size >= capacity) old.drop(old.size - capacity + 1) + safe else old + safe }
    }

    fun log(level: LogLevel, text: String, source: LogSource = LogSource.Launcher, thread: String? = null, instanceId: String? = null) =
        append(LogLine(0, System.currentTimeMillis(), level, source, thread, text, instanceId))

    override fun clear() = state.update { emptyList() }

    companion object {
        private val pattern = Regex("""^\[(\d{2}:\d{2}:\d{2})] \[([^/\]]+)/(\w+)]: ?(.*)$""")

        /** Разбор строки вывода игры вида "[12:00:00] [Render thread/INFO]: text". */
        fun parseGameLine(raw: String, fallback: LogLevel = LogLevel.Info): Triple<LogLevel, String?, String> {
            val m = pattern.find(raw) ?: return Triple(guessLevel(raw, fallback), null, raw)
            val level = when (m.groupValues[3].uppercase()) {
                "TRACE" -> LogLevel.Trace
                "DEBUG" -> LogLevel.Debug
                "WARN", "WARNING" -> LogLevel.Warn
                "ERROR" -> LogLevel.Error
                "FATAL" -> LogLevel.Fatal
                else -> LogLevel.Info
            }
            return Triple(level, m.groupValues[2], m.groupValues[4])
        }

        private fun guessLevel(raw: String, fallback: LogLevel): LogLevel = when {
            raw.contains("Exception") || raw.startsWith("\tat ") || raw.startsWith("Caused by") -> LogLevel.Error
            else -> fallback
        }
    }
}
