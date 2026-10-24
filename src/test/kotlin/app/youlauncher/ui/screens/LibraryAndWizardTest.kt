package app.youlauncher.ui.screens

import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceSort
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.GameVersion
import app.youlauncher.core.repo.VersionType
import app.youlauncher.ui.screens.home.HomeViewModel
import app.youlauncher.ui.screens.library.LibraryFilter
import app.youlauncher.ui.screens.library.LibraryLogic
import app.youlauncher.ui.screens.library.VersionComparator
import app.youlauncher.ui.screens.library.WizardModel
import app.youlauncher.ui.screens.library.WizardStep
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryAndWizardTest {
    private fun inst(id: String, name: String, version: String = "1.21.10", loader: LoaderType = LoaderType.Vanilla, fav: Boolean = false, played: Long? = null, time: Long = 0, group: String? = null, tags: List<String> = emptyList()) =
        Instance(id, name, version, loader, favorite = fav, lastPlayedAt = played, totalPlayTimeMs = time, group = group, tags = tags, createdAt = id.hashCode().toLong())

    private val list = listOf(
        inst("a", "Alpha", "1.20.1", LoaderType.Forge, played = 100, time = 5),
        inst("b", "beta", "1.8.9", played = 300, time = 50, group = "Old"),
        inst("c", "Gamma", "26.1", LoaderType.Fabric, fav = true, played = 200, time = 1, tags = listOf("pvp")),
        inst("d", "Delta", "1.21.10", LoaderType.Fabric, played = null, time = 0),
    )

    @Test
    fun `filters by query, loader, group and favorites`() {
        assertEquals(listOf("c"), LibraryLogic.apply(list, LibraryFilter(query = "pvp")).map { it.id })
        assertEquals(listOf("c", "d"), LibraryLogic.apply(list, LibraryFilter(loader = LoaderType.Fabric)).map { it.id }.sorted())
        assertEquals(listOf("b"), LibraryLogic.apply(list, LibraryFilter(group = "Old")).map { it.id })
        assertEquals(listOf("c"), LibraryLogic.apply(list, LibraryFilter(favoritesOnly = true)).map { it.id })
        assertEquals(listOf("a"), LibraryLogic.apply(list, LibraryFilter(query = "1.20")).map { it.id })
        assertEquals(listOf("c", "d"), LibraryLogic.apply(list, LibraryFilter(query = "fabric")).map { it.id }.sorted())
    }

    @Test
    fun `favorites first, then sort order`() {
        assertEquals(listOf("c", "b", "a", "d"), LibraryLogic.apply(list, LibraryFilter(sort = InstanceSort.LastPlayed)).map { it.id })
        assertEquals(listOf("c", "a", "b", "d"), LibraryLogic.apply(list, LibraryFilter(sort = InstanceSort.Name)).map { it.id })
        assertEquals(listOf("c", "b", "a", "d"), LibraryLogic.apply(list, LibraryFilter(sort = InstanceSort.PlayTime)).map { it.id })
        assertEquals(listOf("c", "d", "a", "b"), LibraryLogic.apply(list, LibraryFilter(sort = InstanceSort.Version)).map { it.id })
    }

    @Test
    fun `version comparator is numeric`() {
        val sorted = listOf("1.8.9", "1.21.10", "1.21.4", "26.1", "1.20.1", "1.21").sortedWith(VersionComparator)
        assertEquals(listOf("1.8.9", "1.20.1", "1.21", "1.21.4", "1.21.10", "26.1"), sorted)
    }

    @Test
    fun `unique names get a number`() {
        assertEquals("Pack", LibraryLogic.uniqueName("Pack", setOf("Other")))
        assertEquals("Pack 2", LibraryLogic.uniqueName("Pack", setOf("Pack")))
        assertEquals("Pack 3", LibraryLogic.uniqueName("Pack", setOf("Pack", "Pack 2")))
    }

    @Test
    fun `wizard auto name follows version and loader until edited`() {
        var m = WizardModel().withVersion("1.21.10")
        assertEquals("1.21.10", m.name)
        m = m.withLoader(LoaderType.Fabric)
        assertEquals("1.21.10 Fabric", m.name)
        m = m.copy(name = "My pack", nameEdited = true).withVersion("1.20.1")
        assertEquals("My pack", m.name)
        assertEquals(null, m.withLoader(LoaderType.Quilt).loaderVersion)
    }

    @Test
    fun `wizard steps and validation`() {
        var m = WizardModel()
        assertFalse(m.canProceed())
        m = m.withVersion("1.21.10")
        assertTrue(m.canProceed())
        m = m.next().withLoader(LoaderType.Fabric)
        assertEquals(WizardStep.Loader, m.step)
        assertFalse(m.canProceed())
        m = m.copy(loaderVersion = "0.18.1")
        assertTrue(m.canProceed())
        m = m.next().next().next()
        assertEquals(WizardStep.Summary, m.step)
        assertEquals(WizardStep.Summary, m.next().step)
        assertEquals(WizardStep.Resources, m.back().step)
        val spec = m.copy(maxMemoryMb = 4096).toSpec()
        assertEquals(1024, spec.minMemoryMb)
        assertEquals("0.18.1", spec.loaderVersion)
        assertEquals(null, m.withLoader(LoaderType.Vanilla).toSpec().loaderVersion)
    }

    @Test
    fun `version list filters`() {
        val all = listOf(
            GameVersion("1.21.10", VersionType.Release, 3), GameVersion("26.4-snapshot-1", VersionType.Snapshot, 4),
            GameVersion("b1.7.3", VersionType.OldBeta, 1), GameVersion("a1.2.6", VersionType.OldAlpha, 0),
        )
        assertEquals(listOf("1.21.10"), WizardModel.filterVersions(all, VersionType.Release, "").map { it.id })
        assertEquals(listOf("b1.7.3", "a1.2.6"), WizardModel.filterVersions(all, VersionType.OldBeta, "").map { it.id })
        assertEquals(emptyList(), WizardModel.filterVersions(all, VersionType.Release, "1.8"))
    }

    @Test
    fun `memory recommendation is clamped`() {
        assertEquals(2048, WizardModel.recommendedMaxMb(2048))
        assertEquals(8192, WizardModel.recommendedMaxMb(65536))
        assertEquals(8192, WizardModel.recommendedMaxMb(16384))
        assertEquals(4096, WizardModel.recommendedMaxMb(8192))
    }

    @Test
    fun `home statistics sum play time and last 14 days`() {
        val today = LocalDate.of(2026, 10, 23)
        val a = inst("a", "A", time = 3_600_000).copy(launches = 3, playLog = mapOf("2026-10-23" to 30, "2026-10-10" to 15, "2026-01-01" to 999))
        val b = inst("b", "B", time = 1_800_000).copy(launches = 1, playLog = mapOf("2026-10-23" to 10))
        val stats = HomeViewModel.computeStats(listOf(a, b), today)
        assertEquals(5_400_000, stats.totalPlayMs)
        assertEquals(4, stats.launches)
        assertEquals(14, stats.last14.size)
        assertEquals(40, stats.last14.last())
        assertEquals(15, stats.last14.first())
    }
}
