package app.youlauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.youlauncher.ui.Platform
import app.youlauncher.ui.images.rememberRemoteImage
import app.youlauncher.ui.theme.monoFamily

/** Блоки упрощённого Markdown (то, что встречается в описаниях модов). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val items: List<String>, val ordered: Boolean) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val imageLine = Regex("""^!\[([^\]]*)]\(([^)\s]+)[^)]*\)$""")
    private val htmlImg = Regex("""<img[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    /** Простейшее упрощение HTML: картинки в Markdown, переносы строк, остальные теги убираются. */
    fun normalizeHtml(src: String): String = src
        .replace(htmlImg) { "\n![](${it.groupValues[1]})\n" }
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</?(p|div|center|h\d)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "[${it.groupValues[2]}](${it.groupValues[1]})" }
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    fun parse(markdown: String): List<MdBlock> {
        val lines = normalizeHtml(markdown).lines()
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()
        var list: MutableList<String>? = null
        var ordered = false
        var i = 0
        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
        fun flushList() {
            list?.let { if (it.isNotEmpty()) blocks += MdBlock.Bullet(it.toList(), ordered) }
            list = null
        }
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.startsWith("```") -> {
                    flushParagraph(); flushList()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { code.appendLine(lines[i]); i++ }
                    blocks += MdBlock.Code(code.toString().trimEnd())
                }
                line.isEmpty() -> { flushParagraph(); flushList() }
                line.startsWith("#") -> {
                    flushParagraph(); flushList()
                    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    blocks += MdBlock.Heading(level, line.drop(level).trim())
                }
                line == "---" || line == "***" || line == "___" -> { flushParagraph(); flushList(); blocks += MdBlock.Rule }
                imageLine.matches(line) -> {
                    flushParagraph(); flushList()
                    val m = imageLine.find(line)!!
                    blocks += MdBlock.Image(m.groupValues[2], m.groupValues[1])
                }
                line.startsWith(">") -> { flushParagraph(); flushList(); blocks += MdBlock.Quote(line.trimStart('>', ' ')) }
                Regex("""^[-*+]\s+.*""").matches(line) -> {
                    flushParagraph()
                    if (list == null || ordered) { flushList(); list = mutableListOf(); ordered = false }
                    list!!.add(line.drop(1).trim())
                }
                Regex("""^\d+[.)]\s+.*""").matches(line) -> {
                    flushParagraph()
                    if (list == null || !ordered) { flushList(); list = mutableListOf(); ordered = true }
                    list!!.add(line.substringAfter(' ').trim())
                }
                else -> { flushList(); if (paragraph.isNotEmpty()) paragraph.append(' '); paragraph.append(line) }
            }
            i++
        }
        flushParagraph(); flushList()
        return blocks
    }

    private val inline = Regex("""(\*\*[^*]+\*\*|__[^_]+__|\*[^*]+\*|_[^_]+_|`[^`]+`|\[[^\]]+]\([^)\s]+\))""")

    fun inline(text: String, linkStyle: SpanStyle, codeStyle: SpanStyle, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in inline.findAll(text)) {
            append(text.substring(last, m.range.first))
            val t = m.value
            when {
                t.startsWith("**") || t.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.drop(2).dropLast(2)) }
                t.startsWith("`") -> withStyle(codeStyle) { append(t.drop(1).dropLast(1)) }
                t.startsWith("[") -> {
                    val label = t.substring(1, t.indexOf(']'))
                    val url = t.substring(t.indexOf('(') + 1, t.length - 1)
                    withLink(LinkAnnotation.Clickable(url, TextLinkStyles(linkStyle)) { onLink(url) }) { append(label) }
                }
                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.drop(1).dropLast(1)) }
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val scheme = MaterialTheme.colorScheme
    val mono = monoFamily()
    val link = SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline)
    val code = SpanStyle(fontFamily = mono, background = scheme.surfaceContainerHighest)
    val open: (String) -> Unit = { Platform.openUrl(it) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { b ->
                when (b) {
                    is MdBlock.Heading -> Text(
                        MarkdownParser.inline(b.text, link, code, open),
                        style = when (b.level) {
                            1 -> MaterialTheme.typography.headlineSmallEmphasized
                            2 -> MaterialTheme.typography.titleLargeEmphasized
                            else -> MaterialTheme.typography.titleMediumEmphasized
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    is MdBlock.Paragraph -> Text(MarkdownParser.inline(b.text, link, code, open), style = MaterialTheme.typography.bodyLarge)
                    is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        b.items.forEachIndexed { idx, item ->
                            Row {
                                Text(if (b.ordered) "${idx + 1}." else "•", style = MaterialTheme.typography.bodyLarge, color = scheme.primary, modifier = Modifier.width(24.dp))
                                Text(MarkdownParser.inline(item, link, code, open), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    is MdBlock.Quote -> Row {
                        Box(Modifier.width(4.dp).heightIn(min = 24.dp).background(scheme.primary, MaterialTheme.shapes.extraSmall))
                        Text(MarkdownParser.inline(b.text, link, code, open), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                    }
                    is MdBlock.Code -> Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                        Text(b.text, fontFamily = mono, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                    is MdBlock.Image -> {
                        val image = if (b.url.startsWith("https://")) rememberRemoteImage(b.url, 1200) else null
                        if (image != null) {
                            Image(image, b.alt, contentScale = ContentScale.FillWidth, modifier = Modifier.widthIn(max = 720.dp).clip(MaterialTheme.shapes.large))
                        }
                    }
                    MdBlock.Rule -> HorizontalDivider()
                }
            }
        }
    }
}
