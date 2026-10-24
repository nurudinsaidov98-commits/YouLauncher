package app.youlauncher.data

import app.youlauncher.data.java.AdoptiumParsing
import app.youlauncher.data.java.JavaChecks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaToolsTest {
    @Test
    fun `adoptium zip package is picked with its sha256`() {
        val b = AdoptiumParsing.latest("""[{"release_name":"jdk-21.0.12.1+1","binary":{"installer":{"name":"x.msi"},
            "package":{"name":"OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip","link":"https://github.com/adoptium/temurin21-binaries/releases/download/x.zip",
            "checksum":"D35F31E7","size":48999141}}}]""")
        assertEquals("jdk-21.0.12.1+1", b.release)
        assertEquals("d35f31e7", b.sha256)
        assertEquals(48999141, b.size)
        assertFailsWith<java.io.IOException> { AdoptiumParsing.latest("""[{"binary":{"package":{"name":"x.tar.gz","link":"u","checksum":"a"}}}]""") }
        assertFailsWith<java.io.IOException> { AdoptiumParsing.latest("""[{"binary":{"package":{"name":"x.zip","link":"u"}}}]""") }
    }

    @Test
    fun `required java per game version`() {
        mapOf("1.8.9" to 8, "1.12.2" to 8, "1.16.5" to 8, "1.17.1" to 16, "1.18.2" to 17, "1.20.4" to 17, "1.20.5" to 21, "1.21.11" to 21, "26.1" to 25, "26.3-snapshot-2" to 25, "b1.7.3" to 8)
            .forEach { (v, j) -> assertEquals(j, JavaChecks.requiredMajor(v), v) }
    }

    @Test
    fun `first meaningful line of java -version`() {
        assertEquals("""openjdk version "21.0.7" 2025-04-15 LTS""", JavaChecks.firstLine("Picked up JAVA_TOOL_OPTIONS: -Xmx1g\n\nopenjdk version \"21.0.7\" 2025-04-15 LTS\nOpenJDK Runtime"))
    }
}
