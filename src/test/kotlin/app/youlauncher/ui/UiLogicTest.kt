package app.youlauncher.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.model.LogSource
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.MarkdownParser
import app.youlauncher.ui.components.MdBlock
import app.youlauncher.ui.screens.console.LogFilter
import app.youlauncher.ui.screens.console.formatLog
import app.youlauncher.ui.screens.console.highlight
import app.youlauncher.ui.screens.instance.EnvText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiLogicTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Test
    fun `relative time buckets`() {
        val now = 1_000_000_000_000L
        assertEquals(Formats.Ago.Never, Formats.ago(null, now))
        assertEquals(Formats.Ago.JustNow, Formats.ago(now - 30_000, now))
        assertEquals(Formats.Ago.Minutes(5), Formats.ago(now - 5 * min, now))
        assertEquals(Formats.Ago.Hours(3), Formats.ago(now - 3 * hour, now))
        assertEquals(Formats.Ago.Yesterday, Formats.ago(now - 30 * hour, now))
        assertEquals(Formats.Ago.Days(4), Formats.ago(now - 4 * day, now))
        assertEquals(Formats.Ago.Weeks(2), Formats.ago(now - 15 * day, now))
        assertEquals(Formats.Ago.Months(2), Formats.ago(now - 65 * day, now))
        assertEquals(Formats.Ago.JustNow, Formats.ago(now + 10_000, now))
    }

    private fun line(id: Long, level: LogLevel, text: String, source: LogSource = LogSource.Game, thread: String? = null) =
        LogLine(id, 0, level, source, thread, text)

    @Test
    fun `console filter by level, source and query`() {
        val lines = listOf(
            line(1, LogLevel.Info, "Loading world"),
            line(2, LogLevel.Debug, "Stage done", LogSource.Launcher),
            line(3, LogLevel.Error, "Crash in renderer", thread = "Render thread"),
            line(4, LogLevel.Trace, "noise"),
        )
        assertEquals(listOf(1L, 2L, 3L), LogFilter().apply(lines).map { it.id })
        assertEquals(listOf(3L), LogFilter(levels = setOf(LogLevel.Error)).apply(lines).map { it.id })
        assertEquals(listOf(2L), LogFilter(source = LogSource.Launcher).apply(lines).map { it.id })
        assertEquals(listOf(3L), LogFilter(query = "render").apply(lines).map { it.id })
        assertEquals(listOf(1L), LogFilter(query = "WORLD").apply(lines).map { it.id })
    }

    @Test
    fun `log export format`() {
        val text = formatLog(listOf(line(1, LogLevel.Warn, "Hello", thread = "main")))
        assertTrue(text.endsWith("[main/WARN]: Hello"), text)
    }

    @Test
    fun `search highlight marks every match`() {
        val style = SpanStyle()
        val s = highlight("abc ABC abc", "abc", style)
        assertEquals("abc ABC abc", s.text)
        assertEquals(3, s.spanStyles.size)
        assertEquals(0, highlight("text", "", style).spanStyles.size)
    }

    @Test
    fun `markdown blocks`() {
        val md = """
            # Title
            Some **bold** text
            continues here.

            - one
            - two

            1. first
            2. second

            > quote
            ```
            code line
            ```
            ![alt](https://example.org/a.png)
            ---
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertIs<MdBlock.Heading>(blocks[0])
        assertEquals("Some **bold** text continues here.", (blocks[1] as MdBlock.Paragraph).text)
        assertEquals(MdBlock.Bullet(listOf("one", "two"), false), blocks[2])
        assertEquals(MdBlock.Bullet(listOf("first", "second"), true), blocks[3])
        assertEquals(MdBlock.Quote("quote"), blocks[4])
        assertEquals(MdBlock.Code("code line"), blocks[5])
        assertEquals(MdBlock.Image("https://example.org/a.png", "alt"), blocks[6])
        assertEquals(MdBlock.Rule, blocks[7])
    }

    @Test
    fun `markdown tables become table blocks instead of raw pipes`() {
        val md = """
            | 💿 Installer | 📦 Portable |
            |---|:---:|
            | Run **Setup.exe** | Unzip and run |
            | Shortcuts | — |

            After the table.
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        val table = assertIs<MdBlock.Table>(blocks[0])
        assertEquals(listOf("💿 Installer", "📦 Portable"), table.header)
        assertEquals(listOf(listOf("Run **Setup.exe**", "Unzip and run"), listOf("Shortcuts", "—")), table.rows)
        assertEquals(MdBlock.Paragraph("After the table."), blocks[1])
    }

    @Test
    fun `accent shimmer blends along the short hue arc and returns`() {
        val red = androidx.compose.ui.graphics.Color(0xFFD03030.toInt())
        val blue = androidx.compose.ui.graphics.Color(0xFF3050D0.toInt())
        assertEquals(0f, app.youlauncher.ui.theme.AccentShimmer.wave(0f), 1e-4f)
        assertEquals(1f, app.youlauncher.ui.theme.AccentShimmer.wave(0.5f), 1e-4f)
        assertEquals(0f, app.youlauncher.ui.theme.AccentShimmer.wave(1f), 1e-4f)
        val hue = { c: androidx.compose.ui.graphics.Color -> com.materialkolor.hct.Hct.fromInt(c.toArgb()).hue }
        assertEquals(hue(red), hue(app.youlauncher.ui.theme.AccentShimmer.blend(red, blue, 0f)), 1.0)
        assertEquals(hue(blue), hue(app.youlauncher.ui.theme.AccentShimmer.blend(red, blue, 1f)), 1.0)
        // Красный (~27°) и синий (~280°): короткая дуга идёт через пурпурный, а не через зелёный.
        val mid = hue(app.youlauncher.ui.theme.AccentShimmer.blend(red, blue, 0.5f))
        assertTrue(mid > 300 || mid < 30, "mid hue $mid")
    }

    @Test
    fun `custom accent hsv round trip`() {
        val c = androidx.compose.ui.graphics.Color(0xFF2E7D5B.toInt())
        val hsv = app.youlauncher.ui.screens.settings.Hsv.of(c)
        assertEquals(c.toArgb(), hsv.toColor().toArgb())
        val other = app.youlauncher.ui.screens.settings.Hsv.of(app.youlauncher.ui.screens.settings.Hsv.companion(c))
        assertEquals((hsv.hue + 120f) % 360f, other.hue, 1.5f)
    }

    @Test
    fun `html in descriptions is simplified`() {
        val blocks = MarkdownParser.parse("""<p align="center"><img src="https://cdn.example/x.png" width="300"></p><br>Hello <a href="https://x.org">link</a>""")
        assertTrue(blocks.any { it == MdBlock.Image("https://cdn.example/x.png", "") })
        assertTrue(blocks.any { it is MdBlock.Paragraph && it.text == "Hello [link](https://x.org)" })
    }

    @Test
    fun `inline markdown styles and links`() {
        val links = mutableListOf<String>()
        val s = MarkdownParser.inline("a **b** *c* `d` [e](https://e.org)", SpanStyle(), SpanStyle()) { links += it }
        assertEquals("a b c d e", s.text)
        assertEquals(1, s.getLinkAnnotations(0, s.length).size)
    }

    @Test
    fun `environment variables text round trip`() {
        val env = EnvText.parse("A=1\n  B = two=2 \n# comment\nbad\n=x\n")
        assertEquals(mapOf("A" to "1", "B" to " two=2"), env)
        assertEquals("A=1\nB= two=2", EnvText.format(env))
    }
}
