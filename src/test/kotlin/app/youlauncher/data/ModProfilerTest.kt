package app.youlauncher.data

import app.youlauncher.data.profile.InstanceProfile
import app.youlauncher.data.profile.ModClassIndex
import app.youlauncher.data.profile.ModJar
import app.youlauncher.data.profile.ModProfiler
import app.youlauncher.data.profile.ModUsage
import app.youlauncher.data.profile.ProfileSession
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModProfilerTest {
    /** Компилирует исходники в jar «мода» с fabric.mod.json. */
    private fun modJar(dir: Path, name: String, modId: String, sources: Map<String, String>): Path {
        val src = Files.createDirectories(dir.resolve("$name-src"))
        val out = Files.createDirectories(dir.resolve("$name-classes"))
        val files = sources.map { (cls, code) -> src.resolve(cls.substringAfterLast('.') + ".java").also { Files.writeString(it, code) } }
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "тесты запускаются на JDK")
        assertEquals(0, compiler.run(null, null, null, "-d", out.toString(), *files.map { it.toString() }.toTypedArray()))
        val jar = dir.resolve("$name.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { zip ->
            Files.walk(out).use { s -> s.filter { Files.isRegularFile(it) }.toList() }.forEach { f ->
                zip.putNextEntry(JarEntry(out.relativize(f).toString().replace('\\', '/')))
                Files.copy(f, zip)
                zip.closeEntry()
            }
            zip.putNextEntry(JarEntry("fabric.mod.json"))
            zip.write("""{"schemaVersion": 1, "id": "$modId", "version": "1.0"}""".toByteArray())
            zip.closeEntry()
        }
        return jar
    }

    @Test
    fun `cpu and allocations of a running JVM are attributed to the mod that caused them`() {
        val dir = Files.createTempDirectory("profiler")
        val burner = modJar(dir, "burner", "burner", mapOf("com.example.burner.Main" to """
            package com.example.burner;
            public class Main {
                static volatile long sink;
                public static void main(String[] a) throws Exception {
                    long end = System.currentTimeMillis() + 4500;
                    while (System.currentTimeMillis() < end) {
                        byte[] b = new byte[4096];
                        for (int i = 0; i < b.length; i++) b[i] = (byte) (i * 31 + sink);
                        sink += java.util.Arrays.hashCode(b);
                    }
                }
            }
        """.trimIndent()))
        val idle = modJar(dir, "idle", "idlemod", mapOf("com.example.idle.Unused" to "package com.example.idle; public class Unused {}"))
        val gameDir = Files.createDirectories(dir.resolve("game"))
        val profiler = ModProfiler(dir.resolve("profiles"))
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val args = assertNotNull(profiler.jvmArgs(Runtime.version().feature(), ""))
        profiler.prepare(gameDir)

        val process = ProcessBuilder(listOf(java) + args + listOf("-cp", burner.toString(), "com.example.burner.Main"))
            .directory(gameDir.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue(), output)

        val session = assertNotNull(profiler.finish("inst", gameDir, Runtime.version().feature(), listOf(ModJar(burner, "Burner"), ModJar(idle, "Idle"))))
        val top = session.mods.first()
        assertEquals("Burner", top.title)
        assertTrue(top.cpuShare > 0.5f, "нагрузка засчитана моду: ${session.mods}")
        assertTrue(session.mods.none { it.title == "Idle" }, "мод без работы не попадает в рейтинг")
        assertTrue(session.allocationTracked && top.allocBytesPerSec > 0, "выделение памяти по моду: $session")
        assertFalse(gameDir.resolve(ModProfiler.REPOSITORY_DIR).exists(), "временные файлы записи удалены")
        assertFalse(gameDir.resolve(ModProfiler.SETTINGS_FILE).exists())
        // Сохранено и читается заново.
        assertEquals(session, ModProfiler(dir.resolve("profiles")).profile("inst").sessions.first())
    }

    @Test
    fun `old java and user flight recorder settings are left alone`() {
        val profiler = ModProfiler(Files.createTempDirectory("p"))
        assertNull(profiler.jvmArgs(8, ""), "в Java 8 Mojang JFR нет: запуск бы сломался")
        assertNull(profiler.jvmArgs(21, "-XX:StartFlightRecording=duration=60s"))
        assertNotNull(profiler.jvmArgs(17, "-Xss2m"))
    }

    @Test
    fun `lambdas and mixin handlers are recognized`() {
        val dir = Files.createTempDirectory("index")
        val jar = modJar(dir, "sodium", "sodium", mapOf("me.mod.Render" to "package me.mod; public class Render {}"))
        val index = ModClassIndex.build(listOf(ModJar(jar, "Sodium")))
        assertEquals(0, index.modOf("me.mod.Render"))
        assertEquals(0, index.modOf("me.mod.Render\$\$Lambda/0x000001f0"))
        assertEquals(0, index.modOf("net.minecraft.client.Renderer", "handler\$zza000\$sodium\$onRender"))
        assertNull(index.modOf("net.minecraft.client.Renderer", "render"))
    }

    @Test
    fun `summary weights sessions by duration`() {
        fun s(duration: Long, cpu: Float) = ProfileSession(0, duration, 21, 100, mods = listOf(ModUsage("A", cpuShare = cpu)))
        val summary = assertNotNull(InstanceProfile(listOf(s(300, 0.1f), s(100, 0.5f))).summary())
        assertEquals(0.2f, summary.mods.single().cpuShare, 1e-4f)
        assertEquals(400, summary.durationSec)
    }
}
