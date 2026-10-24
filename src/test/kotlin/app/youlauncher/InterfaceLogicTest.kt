package app.youlauncher

import app.youlauncher.core.settings.HomeWidget
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.SettingsRepository
import app.youlauncher.ui.screens.settings.InterfaceLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterfaceLogicTest {
    @Test
    fun `hotkey conflicts are taken from the other action`() {
        val map = mapOf("home" to "Ctrl+1", "library" to "Ctrl+2")
        assertEquals("library", InterfaceLogic.conflict(map, "home", "ctrl+2"))
        assertEquals(mapOf("home" to "Ctrl+2"), InterfaceLogic.assignHotkey(map, "home", "Ctrl+2"))
    }

    @Test
    fun `widgets toggle and move`() {
        val list = listOf(HomeWidget.ContinueCard, HomeWidget.Stats)
        assertEquals(listOf(HomeWidget.Stats, HomeWidget.ContinueCard), InterfaceLogic.moveWidget(list, HomeWidget.Stats, -1))
        assertEquals(list, InterfaceLogic.moveWidget(list, HomeWidget.ContinueCard, -1))
        assertEquals(list + HomeWidget.Shortcuts, InterfaceLogic.toggleWidget(list, HomeWidget.Shortcuts, true))
        assertEquals(listOf(HomeWidget.Stats), InterfaceLogic.toggleWidget(list, HomeWidget.ContinueCard, false))
    }

    @Test
    fun `settings export drops local paths and imports back`() {
        val s = LauncherSettings().let { it.copy(java = it.java.copy(globalJavaPath = "C:\\java\\bin\\javaw.exe"), lastInstanceId = "x") }
        val text = SettingsRepository.exportText(s)
        val back = SettingsRepository.decode(text)!!
        assertNull(back.java.globalJavaPath)
        assertNull(back.lastInstanceId)
        assertEquals(s.ui, back.ui)
        assertNull(SettingsRepository.decode("not json"))
    }
}
