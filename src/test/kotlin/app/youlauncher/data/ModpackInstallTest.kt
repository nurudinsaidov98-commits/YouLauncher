package app.youlauncher.data

import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.repo.ModQuery
import app.youlauncher.data.packs.ModpackNames
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
import kotlin.test.assertTrue

/** Сборки ставятся из каталога одной кнопкой: новый экземпляр без похода в импорт. */
class ModpackInstallTest {
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

    @Test
    fun `install button on a modpack creates a new instance`() = runBlocking<Unit> {
        val vm = ModsViewModel(graph, null, dispatcher = Dispatchers.Unconfined)
        val pack = graph.modrinth.search(ModQuery()).hits.first().copy(type = ProjectType.Modpack)
        val before = graph.instances.instances.value.size
        vm.install(pack)
        withTimeout(15_000) { while (graph.instances.instances.value.size == before) delay(50) }
        assertTrue(graph.instances.instances.value.any { it.name.startsWith(pack.title) })
        withTimeout(5_000) { while (pack.id !in vm.state.value.installed) delay(20) }
        assertTrue(vm.progress.value[pack.id]?.done == true)
    }

    @Test
    fun `modpack instance names do not collide`() {
        assertEquals("Cobbleverse", ModpackNames.unique("Cobbleverse", listOf("Survival")))
        assertEquals("Cobbleverse 3", ModpackNames.unique("Cobbleverse", listOf("Cobbleverse", "Cobbleverse 2")))
    }
}
