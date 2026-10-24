package app.youlauncher

import app.youlauncher.data.system.InstallPlan
import app.youlauncher.data.system.LocationProblem
import app.youlauncher.data.system.RegistryStore
import app.youlauncher.ui.navigation.LaunchArgs
import app.youlauncher.ui.setup.BackdropLayout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallerTest {
    private class MemoryRegistry : RegistryStore {
        val keys = mutableMapOf<String, MutableMap<String, Any>>()
        override fun read(key: String, name: String) = keys[key]?.get(name)?.toString()
        override fun write(key: String, values: Map<String, Any>) { keys.getOrPut(key) { mutableMapOf() }.putAll(values) }
        override fun delete(key: String) { keys.remove(key) }
    }

    /** Распакованная программа как в установщике: exe, app с jar, runtime, плюс файлы, которые не копируются. */
    private fun fakeApp(): Path {
        val dir = Files.createTempDirectory("setup-src")
        Files.writeString(dir.resolve("YouLauncher.exe"), "exe")
        Files.createDirectories(dir.resolve("app"))
        Files.writeString(dir.resolve("app/youlauncher-1.2.0-new.jar"), "jar".repeat(100))
        Files.writeString(dir.resolve("app/classes.lst"), "java/lang/Object id: 0")
        Files.writeString(dir.resolve("app/youlauncher.jsa"), "archive with absolute paths")
        Files.writeString(dir.resolve("app/youlauncher.jsa.stamp"), "stamp")
        Files.createDirectories(dir.resolve("runtime/bin"))
        Files.writeString(dir.resolve("runtime/bin/jvm.dll"), "dll")
        Files.createDirectories(dir.resolve("data"))
        Files.writeString(dir.resolve("data/settings.json"), "{}")
        return dir
    }

    @Test
    fun `install folder is checked before installing`() {
        val src = fakeApp()
        val base = Files.createTempDirectory("target")
        assertEquals(LocationProblem.NotAbsolute, InstallPlan.check(Path.of("Games", "YouLauncher"), src, 1))
        assertEquals(LocationProblem.InsideSource, InstallPlan.check(src.resolve("x"), src, 1))
        assertEquals(LocationProblem.NotEnoughSpace, InstallPlan.check(base.resolve("YL"), src, 1000) { 10 })
        Files.writeString(base.resolve("foreign.txt"), "чужой файл")
        assertEquals(LocationProblem.NotEmpty, InstallPlan.check(base, src, 1))
        assertNull(InstallPlan.check(base.resolve("YouLauncher"), src, 1))
        assertFalse(base.resolve("YouLauncher").exists(), "проверка записи убирает за собой созданную папку")
        assertNull(InstallPlan.check(base.resolve("Games/YouLauncher"), src, 1))
        assertFalse(base.resolve("Games").exists(), "и недостающие родительские папки тоже")
    }

    @Test
    fun `copy installs the program, updates over an old version and skips local files`() {
        val src = fakeApp()
        val target = Files.createTempDirectory("target").resolve("YouLauncher")
        Files.createDirectories(target.resolve("app"))
        Files.writeString(target.resolve("app/youlauncher-1.1.0-old.jar"), "old")
        Files.writeString(target.resolve("YouLauncher.exe"), "old exe")
        Files.createDirectories(target.resolve("data"))
        Files.writeString(target.resolve("data/keep.txt"), "данные пользователя")
        var last = 0f
        InstallPlan.copy(src, target) { p, _ -> assertTrue(p >= last); last = p }
        assertEquals(1f, last)
        assertEquals("exe", Files.readString(target.resolve("YouLauncher.exe")))
        assertTrue(target.resolve("app/youlauncher-1.2.0-new.jar").exists())
        assertTrue(target.resolve("app/classes.lst").exists())
        assertTrue(target.resolve("runtime/bin/jvm.dll").exists())
        assertFalse(target.resolve("app/youlauncher-1.1.0-old.jar").exists(), "старые jar удаляются")
        assertFalse(target.resolve("app/youlauncher.jsa").exists(), "кэш классов собирается заново на новом месте")
        assertTrue(target.resolve("data/keep.txt").exists(), "данные в папке программы не трогаются")
        assertFalse(target.resolve("data/settings.json").exists())
        assertTrue(InstallPlan.isOurs(target))
    }

    @Test
    fun `registration and uninstall`() {
        val reg = MemoryRegistry()
        val target = Files.createTempDirectory("installed").also { Files.writeString(it.resolve("YouLauncher.exe"), "") }
        InstallPlan.register(reg, target, "1.2.0", 230L * 1024 * 1024)
        val values = reg.keys.getValue(InstallPlan.UNINSTALL_KEY)
        assertEquals("YouLauncher", values["DisplayName"])
        assertEquals("1.2.0", values["DisplayVersion"])
        assertEquals("\"${target.resolve("YouLauncher.exe")}\" --uninstall", values["UninstallString"])
        assertEquals(230 * 1024, values["EstimatedSize"])
        assertEquals(target, InstallPlan.installedLocation(reg))
        InstallPlan.refreshVersion(reg, Files.createTempDirectory("portable"), "1.3.0")
        assertEquals("1.2.0", values["DisplayVersion"], "портативная копия запись установленной не трогает")
        InstallPlan.refreshVersion(reg, target, "1.3.0")
        assertEquals("1.3.0", values["DisplayVersion"])

        val links = Files.createTempDirectory("links")
        val desktop = Files.writeString(links.resolve("YouLauncher.lnk"), "")
        val data = Files.createTempDirectory("appdata").also { Files.writeString(it.resolve("settings.json"), "{}") }
        InstallPlan.uninstall(reg, listOf(desktop), data)
        assertFalse(desktop.exists())
        assertFalse(data.exists())
        assertNull(InstallPlan.installedLocation(reg))
    }

    @Test
    fun `setup and uninstall launch arguments`() {
        assertTrue(LaunchArgs.parse(arrayOf("--setup")).setup)
        assertTrue(LaunchArgs.parse(arrayOf("--uninstall")).uninstall)
        assertFalse(LaunchArgs.parse(arrayOf("--open", "mods")).setup)
    }

    @Test
    fun `backdrop shapes stay on the right and gears keep their shape`() {
        for (step in 0..4) for (slot in BackdropLayout.base.indices) {
            val p = BackdropLayout.placement(slot, step, 15)
            assertTrue(p.x in 0.45f..1.07f && p.y in -0.07f..1.07f, "фигура $slot на шаге $step в пределах окна")
            if (slot == BackdropLayout.GEAR_BIG || slot == BackdropLayout.GEAR_SMALL) assertEquals(0, p.poly)
            else assertTrue(p.poly in 1 until 15)
        }
        assertEquals(BackdropLayout.base[3], BackdropLayout.placement(3, 0, 15))
    }
}
