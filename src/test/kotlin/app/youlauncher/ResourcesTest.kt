package app.youlauncher

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue

/** Строки интерфейса: одинаковые ключи в ru и en, только позиционные подстановки (%1$s), совпадающие по языкам. */
class ResourcesTest {
    private val root = Path.of("src/main/composeResources")

    private fun load(dir: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        for (file in root.resolve(dir).listDirectoryEntries("*.xml")) {
            val doc = factory.newDocumentBuilder().parse(Files.newInputStream(file))
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i)
                result[e.attributes.getNamedItem("name").nodeValue] = e.textContent
            }
            val plurals = doc.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val p = plurals.item(i)
                val name = p.attributes.getNamedItem("name").nodeValue
                val items = p.childNodes
                for (j in 0 until items.length) {
                    val item = items.item(j)
                    if (item.nodeName == "item") result["$name#${item.attributes.getNamedItem("quantity").nodeValue}"] = item.textContent
                }
            }
        }
        return result
    }

    private val placeholder = Regex("""%(\d+\$)?[sd]""")

    @Test
    fun `russian and english have the same keys`() {
        val en = load("values").keys.map { it.substringBefore('#') }.toSet()
        val ru = load("values-ru").keys.map { it.substringBefore('#') }.toSet()
        assertTrue((en - ru).isEmpty(), "Нет перевода на русский: ${en - ru}")
        assertTrue((ru - en).isEmpty(), "Нет английской строки: ${ru - en}")
    }

    @Test
    fun `placeholders are positional and consistent`() {
        val en = load("values")
        val ru = load("values-ru")
        val bad = (en + ru.mapKeys { "ru:" + it.key }).filterValues { v -> placeholder.findAll(v).any { it.groupValues[1].isEmpty() } }
        assertTrue(bad.isEmpty(), "Непозиционные подстановки (нужно %1\$s): ${bad.keys}")
        val mismatch = en.keys.map { it.substringBefore('#') }.distinct().filter { key ->
            val e = en.filterKeys { it.substringBefore('#') == key }.values.flatMap { placeholder.findAll(it).map { m -> m.value }.toList() }.toSet()
            val r = ru.filterKeys { it.substringBefore('#') == key }.values.flatMap { placeholder.findAll(it).map { m -> m.value }.toList() }.toSet()
            e != r
        }
        assertTrue(mismatch.isEmpty(), "Разные подстановки в ru/en: $mismatch")
    }

    @Test
    fun `no hardcoded colors outside the seed list`() {
        val src = Path.of("src/main/kotlin")
        val offenders = Files.walk(src).use { paths ->
            paths.filter { it.name.endsWith(".kt") && it.name != "Theme.kt" }.toList().flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { i, line ->
                    if (Regex("""Color\(0x[0-9A-Fa-f]{6,8}""").containsMatchIn(line) || Regex("""Color\.(Red|Green|Blue|White|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b""").containsMatchIn(line)) "$file:${i + 1}" else null
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Цвета вне Theme.kt: $offenders")
    }
}
