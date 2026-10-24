package app.youlauncher

import app.youlauncher.core.AppDirs
import app.youlauncher.core.model.LoaderType
import app.youlauncher.ui.screens.instance.VersionChangeViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionChangeTest {
    private lateinit var graph: AppGraph

    @BeforeTest
    fun setUp() {
        System.setProperty("youlauncher.demo", "true")
        graph = AppGraph(AppDirs(Files.createTempDirectory("graph")).ensure(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    @AfterTest
    fun tearDown() = System.clearProperty("youlauncher.demo").let { }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(20) }

    @Test
    fun `older version warns, mods are checked and the instance changes`() = runBlocking<Unit> {
        val inst = graph.instances.instances.value.first { it.isModded }
        val vm = VersionChangeViewModel(graph, inst.id, Dispatchers.Unconfined)
        until { vm.state.value.versions != null && vm.state.value.compat != null }
        assertFalse(vm.state.value.changed, "Пока ничего не выбрано, применять нечего")

        val older = vm.state.value.versions!!.last().id
        vm.selectGame(older)
        until { vm.state.value.compat != null && !vm.state.value.checking }
        assertTrue(vm.state.value.downgrade)
        assertTrue(vm.state.value.changed)

        vm.selectLoader(LoaderType.Vanilla)
        until { vm.state.value.compat != null && !vm.state.value.checking }
        assertTrue(vm.state.value.compat!!.none { it.status == app.youlauncher.core.repo.CompatStatus.Update })
        assertTrue(vm.state.value.canApply)

        var done = false
        vm.apply { done = true }
        until { done }
        val after = graph.instances.get(inst.id)!!
        assertEquals(older, after.gameVersion)
        assertEquals(LoaderType.Vanilla, after.loader)
        assertEquals(null, after.loaderVersion)
    }
}
