package app.youlauncher

import app.youlauncher.core.AppDirs
import app.youlauncher.core.repo.ModQuery
import app.youlauncher.ui.screens.mods.CatalogSource
import app.youlauncher.ui.screens.mods.EntryStatus
import app.youlauncher.ui.screens.mods.ModsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Окно установки модов на демо-данных: выбор галочками, зависимости под каждым модом, установка всего. */
class ModsBatchTest {
    private lateinit var graph: AppGraph

    @BeforeTest
    fun setUp() {
        System.setProperty("youlauncher.demo", "true")
        graph = AppGraph(AppDirs(Files.createTempDirectory("graph")).ensure(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("youlauncher.demo")
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(15_000) { while (!condition()) delay(20) }

    @Test
    fun `catalog keeps search, results, selection and scroll after visiting a mod page`() = runBlocking<Unit> {
        val target = graph.instances.instances.value.first { it.isModded }
        val vm = ModsViewModel(graph, target.id, dispatcher = Dispatchers.Unconfined)
        vm.setText("swift")
        until { !vm.state.value.loading && vm.state.value.query.text == "swift" && vm.state.value.results.isNotEmpty() }
        vm.toggleSelected(vm.state.value.results.first())
        vm.scrollIndex = 7; vm.scrollOffset = 12
        val memory = vm.memory()
        vm.clear()
        // Вернулись со страницы мода: всё как было, без нового запроса (результаты те же объекты).
        val back = ModsViewModel(graph, target.id, memory, Dispatchers.Unconfined)
        assertEquals("swift", back.state.value.query.text)
        assertTrue(back.state.value.results === memory.state.results)
        assertEquals(1, back.selected.value.size)
        assertEquals(7, back.scrollIndex)
        assertEquals(12, back.scrollOffset)
        assertTrue(!back.state.value.loading)
    }

    @Test
    fun `catalog keeps CurseForge and the search when reopened from the menu or for another instance`() = runBlocking<Unit> {
        val target = graph.instances.instances.value.first { it.isModded }
        val other = graph.instances.instances.value.first { it.id != target.id }
        val vm = ModsViewModel(graph, target.id, dispatcher = Dispatchers.Unconfined)
        vm.setSource(CatalogSource.CurseForge)
        vm.setText("swift")
        until { !vm.state.value.loading && vm.state.value.query.text == "swift" }
        val memory = vm.memory()
        vm.clear()
        // Вернулись через меню «Моды» (экран без экземпляра): всё прежнее, включая цель.
        val fromMenu = ModsViewModel(graph, null, memory, Dispatchers.Unconfined)
        assertEquals(CatalogSource.CurseForge, fromMenu.state.value.source)
        assertEquals("swift", fromMenu.state.value.query.text)
        assertEquals(target.id, fromMenu.state.value.target?.id)
        fromMenu.clear()
        // Открыли каталог из другого экземпляра: источник и поиск прежние, цель новая.
        val forOther = ModsViewModel(graph, other.id, memory, Dispatchers.Unconfined)
        assertEquals(CatalogSource.CurseForge, forOther.state.value.source)
        assertEquals("swift", forOther.state.value.query.text)
        assertEquals(other.id, forOther.state.value.target?.id)
        assertEquals(0, forOther.scrollIndex)
        forOther.clear()
    }

    @Test
    fun `selected mods are previewed with dependencies and installed together`() = runBlocking<Unit> {
        val target = graph.instances.instances.value.first { it.isModded }
        val vm = ModsViewModel(graph, target.id, dispatcher = Dispatchers.Unconfined)
        val projects = graph.modrinth.search(ModQuery()).hits.take(4)
        projects.forEach(vm::toggleSelected)
        assertEquals(4, vm.selected.value.size)
        vm.toggleSelected(projects.last())
        assertEquals(3, vm.selected.value.size)

        vm.installSelected()
        until { vm.sheet.value?.loading == false }
        val sheet = assertNotNull(vm.sheet.value)
        assertTrue(sheet.batch)
        assertEquals(projects.take(3).map { it.id }, sheet.entries.map { it.project.id })
        val withDeps = sheet.entries.count { it.preview!!.dependencies.isNotEmpty() }
        // Общая зависимость нескольких модов считается один раз, уже установленная не считается вовсе.
        assertEquals(if (withDeps > 0) 1 else 0, sheet.dependenciesToDownload)

        vm.confirmInstall(withDependencies = true)
        until { vm.sheet.value?.finished == true }
        assertTrue(vm.sheet.value!!.entries.all { it.status == EntryStatus.Done })
        assertTrue(vm.selected.value.isEmpty(), "Установленное снимается с выделения")
        assertTrue(vm.state.value.installed.containsAll(projects.take(3).map { it.id }))
        vm.dismissSheet()
        assertNull(vm.sheet.value)
    }

    @Test
    fun `single install asks only when dependencies must be downloaded`() = runBlocking<Unit> {
        val target = graph.instances.instances.value.first { it.isModded }
        val vm = ModsViewModel(graph, target.id, dispatcher = Dispatchers.Unconfined)
        val hits = graph.modrinth.search(ModQuery()).hits
        val withDeps = hits.first { graph.modInstaller.preview(target.id, it).toDownload.isNotEmpty() }
        val plain = hits.first { graph.modInstaller.preview(target.id, it).dependencies.isEmpty() }

        vm.install(plain)
        until { plain.id in vm.state.value.installed }
        assertNull(vm.sheet.value, "Без зависимостей окно не показывается")

        vm.install(withDeps)
        until { vm.sheet.value != null }
        assertEquals(withDeps.id, vm.sheet.value!!.entries.single().project.id)
        vm.confirmInstall(withDependencies = false)
        until { vm.sheet.value?.finished == true }
        assertTrue(withDeps.id in vm.state.value.installed)
    }
}
