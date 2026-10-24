package app.youlauncher.core.mojang

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.launch.GameLogParser
import app.youlauncher.core.launch.LaunchAccount
import app.youlauncher.core.launch.LaunchCommandBuilder
import app.youlauncher.core.launch.LaunchSpec
import app.youlauncher.core.launch.splitArgs
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.settings.JvmPreset
import app.youlauncher.data.java.JavaVersions
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Фикстуры повторяют формат version JSON Mojang (хеши и адреса выдуманы). */
object Fixtures {
    val modern = """
    {
      "id": "26.3", "type": "release", "mainClass": "net.minecraft.client.main.Main",
      "arguments": {
        "default-user-jvm": [{"value": ["-Xms2G", "-Xmx4G", "-XX:+AlwaysPreTouch"]},
          {"rules": [{"action": "allow", "os": {"name": "windows", "versionRange": {"min": "10.0.17134"}}}], "value": ["-XX:+UseZGC"]}],
        "game": ["--username", "${'$'}{auth_player_name}", "--version", "${'$'}{version_name}", "--gameDir", "${'$'}{game_directory}",
          "--assetsDir", "${'$'}{assets_root}", "--assetIndex", "${'$'}{assets_index_name}", "--uuid", "${'$'}{auth_uuid}",
          "--accessToken", "${'$'}{auth_access_token}", "--versionType", "${'$'}{version_type}",
          {"rules": [{"action": "allow", "features": {"is_demo_user": true}}], "value": "--demo"},
          {"rules": [{"action": "allow", "features": {"has_custom_resolution": true}}], "value": ["--width", "${'$'}{resolution_width}", "--height", "${'$'}{resolution_height}"]},
          {"rules": [{"action": "allow", "features": {"has_quick_plays_support": true}}], "value": ["--quickPlayPath", "${'$'}{quickPlayPath}"]},
          {"rules": [{"action": "allow", "features": {"is_quick_play_multiplayer": true}}], "value": ["--quickPlayMultiplayer", "${'$'}{quickPlayMultiplayer}"]}],
        "jvm": [{"rules": [{"action": "allow", "os": {"name": "windows"}}], "value": "-XX:HeapDumpPath=MojangTricks.heapdump"},
          {"rules": [{"action": "allow", "os": {"arch": "x86"}}], "value": "-Xss1M"},
          "-Djava.library.path=${'$'}{natives_directory}/java", "-cp", "${'$'}{classpath}"]
      },
      "assetIndex": {"id": "34", "sha1": "aa", "size": 1, "totalSize": 2, "url": "https://piston-meta.mojang.com/v1/packages/aa/34.json"},
      "downloads": {"client": {"sha1": "bb", "size": 10, "url": "https://piston-data.mojang.com/v1/objects/bb/client.jar"}},
      "javaVersion": {"component": "java-runtime-epsilon", "majorVersion": 25},
      "logging": {"client": {"argument": "-Dlog4j.configurationFile=${'$'}{path}", "file": {"id": "client-1.21.2.xml", "sha1": "cc", "size": 3, "url": "https://piston-data.mojang.com/v1/objects/cc/client-1.21.2.xml"}, "type": "log4j2-xml"}},
      "libraries": [
        {"downloads": {"artifact": {"path": "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar", "sha1": "11", "size": 1, "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar"}}, "name": "org.lwjgl:lwjgl:3.4.3"},
        {"downloads": {"artifact": {"path": "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", "sha1": "12", "size": 1, "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar"}}, "name": "org.lwjgl:lwjgl:3.4.3:natives-windows", "rules": [{"action": "allow", "os": {"name": "windows"}}]},
        {"downloads": {"artifact": {"path": "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-linux.jar", "sha1": "13", "size": 1, "url": "https://libraries.minecraft.net/x.jar"}}, "name": "org.lwjgl:lwjgl:3.4.3:natives-linux", "rules": [{"action": "allow", "os": {"name": "linux"}}]},
        {"downloads": {"artifact": {"path": "com/mojang/text2speech/1.0/t2s.jar", "sha1": "14", "size": 1, "url": "https://libraries.minecraft.net/t2s.jar"}}, "name": "com.mojang:text2speech:1.0", "rules": [{"action": "allow"}, {"action": "disallow", "os": {"name": "windows"}}]}
      ]
    }""".trimIndent()

    val legacy = """
    {
      "id": "1.8.9", "type": "release", "mainClass": "net.minecraft.client.main.Main", "assets": "1.8",
      "minecraftArguments": "--username ${'$'}{auth_player_name} --version ${'$'}{version_name} --gameDir ${'$'}{game_directory} --assetsDir ${'$'}{assets_root} --assetIndex ${'$'}{assets_index_name} --uuid ${'$'}{auth_uuid} --accessToken ${'$'}{auth_access_token} --userProperties ${'$'}{user_properties} --userType ${'$'}{user_type}",
      "assetIndex": {"id": "1.8", "sha1": "dd", "size": 1, "totalSize": 2, "url": "https://launchermeta.mojang.com/v1/packages/dd/1.8.json"},
      "downloads": {"client": {"sha1": "ee", "size": 10, "url": "https://launcher.mojang.com/client.jar"}},
      "javaVersion": {"component": "jre-legacy", "majorVersion": 8},
      "libraries": [
        {"downloads": {"artifact": {"path": "org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4.jar", "sha1": "21", "size": 22, "url": "https://libraries.minecraft.net/p.jar"},
          "classifiers": {"natives-windows": {"path": "org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows.jar", "sha1": "22", "size": 5, "url": "https://libraries.minecraft.net/pw.jar"},
                          "natives-osx": {"path": "osx.jar", "sha1": "23", "size": 5, "url": "https://libraries.minecraft.net/osx.jar"}}},
          "extract": {"exclude": ["META-INF/"]}, "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4", "natives": {"linux": "natives-linux", "osx": "natives-osx", "windows": "natives-windows"}},
        {"downloads": {"classifiers": {"natives-windows-64": {"path": "tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar", "sha1": "31", "size": 5, "url": "https://libraries.minecraft.net/tw64.jar"}}},
          "name": "tv.twitch:twitch-platform:6.5", "natives": {"windows": "natives-windows-${'$'}{arch}"}},
        {"downloads": {"artifact": {"path": "com/google/guava/guava/17.0/guava-17.0.jar", "sha1": "41", "size": 1, "url": "https://libraries.minecraft.net/guava.jar"}}, "name": "com.google.guava:guava:17.0"}
      ]
    }""".trimIndent()

    val fabricProfile = """
    {
      "id": "fabric-loader-0.18.1-26.3", "inheritsFrom": "26.3", "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
      "arguments": {"game": [], "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "]},
      "libraries": [
        {"name": "net.fabricmc:fabric-loader:0.18.1", "url": "https://maven.fabricmc.net/", "sha1": "f1", "size": 100},
        {"name": "org.lwjgl:lwjgl:3.4.4", "url": "https://maven.fabricmc.net/"}
      ]
    }""".trimIndent()

    fun parse(json: String) = YouLauncherJson.decodeFromString(VersionJson.serializer(), json)
}

class MojangCoreTest {
    private val win = RuleEnv(osName = "windows", osVersion = "10.0.26100", arch = "x86_64")

    @Test
    fun `rules follow the official algorithm`() {
        assertTrue(Rules.allowed(null, win))
        assertTrue(Rules.allowed(listOf(Rule("allow", OsRule(name = "windows"))), win))
        assertFalse(Rules.allowed(listOf(Rule("allow", OsRule(name = "osx"))), win))
        assertFalse(Rules.allowed(listOf(Rule("allow"), Rule("disallow", OsRule(name = "windows"))), win))
        assertTrue(Rules.allowed(listOf(Rule("allow"), Rule("disallow", OsRule(name = "osx"))), win))
        assertFalse(Rules.allowed(listOf(Rule("allow", OsRule(arch = "x86"))), win))
        assertTrue(Rules.allowed(listOf(Rule("allow", OsRule(arch = "x86"))), win.copy(arch = "x86")))
        assertTrue(Rules.allowed(listOf(Rule("allow", OsRule(name = "windows", versionRange = VersionRange(min = "10.0.17134")))), win))
        assertFalse(Rules.allowed(listOf(Rule("allow", OsRule(name = "windows", versionRange = VersionRange(min = "10.0.17134")))), win.copy(osVersion = "10.0.16299")))
        assertTrue(Rules.allowed(listOf(Rule("allow", OsRule(name = "windows", versionRange = VersionRange(max = "10.0.17134")))), win.copy(osVersion = "10.0.16299")))
        assertTrue(Rules.allowed(listOf(Rule("allow", OsRule(version = "^10\\."))), win))
        val demo = listOf(Rule("allow", features = mapOf("is_demo_user" to true)))
        assertFalse(Rules.allowed(demo, win))
        assertTrue(Rules.allowed(demo, win.copy(features = setOf("is_demo_user"))))
    }

    @Test
    fun `modern and legacy manifests are parsed`() {
        val m = Fixtures.parse(Fixtures.modern)
        assertEquals(25, m.javaVersion?.majorVersion)
        assertEquals("client-1.21.2.xml", m.logging?.client?.file?.id)
        assertTrue(m.arguments!!.game.any { it is Argument.Conditional && it.values == listOf("--demo") })
        assertEquals(2, m.arguments!!.defaultUserJvm.size)
        val l = Fixtures.parse(Fixtures.legacy)
        assertTrue(l.arguments == null && l.minecraftArguments!!.contains("--userType"))
        assertEquals("natives-windows-\${arch}", l.libraries[1].natives?.get("windows"))
    }

    @Test
    fun `modern libraries respect rules and keep natives on the classpath`() {
        val libs = VersionResolver.libraries(Fixtures.parse(Fixtures.modern), win)
        assertEquals(listOf("org.lwjgl:lwjgl:3.4.3", "org.lwjgl:lwjgl:3.4.3:natives-windows"), libs.map { it.coordinate })
        assertTrue(libs.none { it.native })
        val cp = VersionResolver.classpath(libs, Path.of("L"), Path.of("client.jar"), ";")
        assertEquals(listOf(Path.of("L", "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar").toString(), Path.of("L", "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar").toString(), "client.jar"), cp.split(';'))
    }

    @Test
    fun `legacy natives use classifiers and arch substitution`() {
        val libs = VersionResolver.libraries(Fixtures.parse(Fixtures.legacy), win)
        val natives = libs.filter { it.native }
        assertEquals(listOf("org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows.jar", "tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar"), natives.map { it.path })
        assertEquals(listOf("META-INF/"), natives.first().extractExclude)
        // У lwjgl-platform есть заглушка-артефакт, у twitch-platform основного jar нет.
        assertEquals(listOf("org.lwjgl.lwjgl:lwjgl-platform:2.9.4", "com.google.guava:guava:17.0"), libs.filterNot { it.native }.map { it.coordinate })
    }

    @Test
    fun `maven coordinates map to paths`() {
        assertEquals("net/fabricmc/fabric-loader/0.18.1/fabric-loader-0.18.1.jar", MavenCoordinate.parse("net.fabricmc:fabric-loader:0.18.1").path())
        assertEquals("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", MavenCoordinate.parse("org.lwjgl:lwjgl:3.4.3:natives-windows").path())
        assertEquals("de/oceanlabs/mcp/mcp_config/1.20/mcp_config-1.20.zip", MavenCoordinate.parse("de.oceanlabs.mcp:mcp_config:1.20@zip").path())
    }

    @Test
    fun `loader profile merges with its parent`() {
        val merged = VersionResolver.merge(Fixtures.parse(Fixtures.fabricProfile), Fixtures.parse(Fixtures.modern))
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", merged.mainClass)
        assertEquals(25, merged.javaVersion?.majorVersion)
        assertEquals("34", merged.assetIndex?.id)
        // Библиотека потомка (lwjgl 3.4.4) вытесняет ту же библиотеку родителя.
        val libs = VersionResolver.libraries(merged, win)
        assertEquals("org.lwjgl:lwjgl:3.4.4", libs.first { it.coordinate.startsWith("org.lwjgl:lwjgl:3") && !it.coordinate.contains("natives") }.coordinate)
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.18.1/fabric-loader-0.18.1.jar", libs.first { it.coordinate.startsWith("net.fabricmc") }.url)
        assertTrue(merged.arguments!!.jvm.last() is Argument.Plain && (merged.arguments!!.jvm.last() as Argument.Plain).value.contains("FabricMcEmu"))
        assertTrue(merged.arguments!!.game.isNotEmpty())
    }

    private fun spec(version: VersionJson, server: String? = null, w: Int? = null) = LaunchSpec(
        version = version, versionName = version.id, javaExecutable = "javaw.exe", javaMajor = 25, classpath = "a.jar;b.jar",
        nativesDir = "N", librariesDir = "L", gameDir = "G", assetsRoot = "A", assetIndexName = "34", gameAssets = "A",
        account = LaunchAccount("Steve", "b50ad385-829d-3141-a216-7e7d7539ba7f", "SECRETTOKEN123", "legacy"),
        minMemoryMb = 1024, maxMemoryMb = 4096, preset = JvmPreset.None, width = w, height = w?.let { 720 },
        quickJoinServer = server, loggingArgument = "-Dlog4j.configurationFile=cfg.xml", wrapperCommand = "", osVersion = "10.0.26100",
    )

    @Test
    fun `modern launch command`() {
        val cmd = LaunchCommandBuilder.build(spec(Fixtures.parse(Fixtures.modern)))
        assertEquals("javaw.exe", cmd.first())
        assertTrue("-Xmx4096M" in cmd && "-Xms1024M" in cmd)
        assertTrue("-XX:+UseZGC" in cmd && "-XX:+AlwaysPreTouch" in cmd && "-Xmx4G" !in cmd)
        assertTrue("-Djava.library.path=N/java" in cmd)
        assertEquals("a.jar;b.jar", cmd[cmd.indexOf("-cp") + 1])
        val main = cmd.indexOf("net.minecraft.client.main.Main")
        assertTrue(main > cmd.indexOf("-cp"))
        assertEquals("Steve", cmd[cmd.indexOf("--username") + 1])
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", cmd[cmd.indexOf("--uuid") + 1])
        assertFalse("--demo" in cmd || "--width" in cmd || "--quickPlayPath" in cmd)
        assertTrue(cmd.none { it.contains("\${") })
        assertFalse(LaunchCommandBuilder.masked(cmd, "SECRETTOKEN123").contains("SECRETTOKEN123"))
    }

    @Test
    fun `resolution and quick play`() {
        val cmd = LaunchCommandBuilder.build(spec(Fixtures.parse(Fixtures.modern), server = "play.example.org:25565", w = 1280))
        assertEquals("1280", cmd[cmd.indexOf("--width") + 1])
        assertEquals("play.example.org:25565", cmd[cmd.indexOf("--quickPlayMultiplayer") + 1])
        assertFalse("--server" in cmd)
        val legacy = LaunchCommandBuilder.build(spec(Fixtures.parse(Fixtures.legacy), server = "old.example.org:25570"))
        assertEquals(listOf("--server", "old.example.org", "--port", "25570"), legacy.takeLast(4))
        assertEquals("legacy", legacy[legacy.indexOf("--userType") + 1])
        assertEquals("{}", legacy[legacy.indexOf("--userProperties") + 1])
        assertTrue("-Djava.library.path=N" in legacy)
    }

    @Test
    fun `jvm presets and argument splitting`() {
        val aikar = LaunchCommandBuilder.build(spec(Fixtures.parse(Fixtures.modern)).copy(preset = JvmPreset.Aikar, customJvmArgs = "-Dfoo=\"a b\" -Xss2M"))
        assertTrue("-XX:+UseG1GC" in aikar && "-XX:+UseZGC" !in aikar && "-Dfoo=a b" in aikar && "-Xss2M" in aikar)
        assertEquals(listOf("--a", "b c", "d"), splitArgs("--a 'b c'  d"))
        assertEquals(listOf("cmd", "/c", "wrap"), splitArgs("cmd /c wrap"))
    }

    @Test
    fun `log4j xml events and plain lines are parsed`() {
        val p = GameLogParser()
        val lines = listOf(
            """<log4j:Event logger="dqr" timestamp="1700000000000" level="WARN" thread="Render thread">""",
            """  <log4j:Message><![CDATA[Missing sound for event]]></log4j:Message>""",
            """</log4j:Event>""",
        )
        val out = lines.flatMap { p.feed(it) }
        assertEquals(1, out.size)
        assertEquals(LogLevel.Warn, out[0].level)
        assertEquals("Render thread", out[0].thread)
        assertEquals("Missing sound for event", out[0].text)
        val multi = listOf("""<log4j:Event logger="a" timestamp="1" level="ERROR" thread="main">""", "<log4j:Message><![CDATA[Boom", "second line]]></log4j:Message>",
            "<log4j:Throwable><![CDATA[java.lang.RuntimeException: x", "\tat a.b(C.java:1)]]></log4j:Throwable>", "</log4j:Event>").flatMap { p.feed(it) }
        assertEquals("Boom\nsecond line\njava.lang.RuntimeException: x\n\tat a.b(C.java:1)", multi.single().text)
        val plain = p.feed("[12:00:01] [Server thread/INFO]: Done (1.2s)!").single()
        assertEquals("Server thread" to "Done (1.2s)!", plain.thread to plain.text)
        assertEquals(LogLevel.Error, p.feed("\tat net.example.X(X.java:3)").single().level)
    }

    @Test
    fun `java versions`() {
        assertEquals(8, JavaVersions.major("1.8.0_412"))
        assertEquals(21, JavaVersions.major("\"21.0.7\""))
        assertEquals(25, JavaVersions.major("25"))
        assertTrue(JavaVersions.compatible(8, 8))
        assertFalse(JavaVersions.compatible(8, 17))
        assertTrue(JavaVersions.compatible(21, 25))
        assertFalse(JavaVersions.compatible(25, 21))
    }
}
