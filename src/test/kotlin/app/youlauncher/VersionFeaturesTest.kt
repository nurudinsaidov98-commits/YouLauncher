package app.youlauncher

import app.youlauncher.core.VersionFeature
import app.youlauncher.core.VersionFeatures
import app.youlauncher.ui.components.Emblems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionFeaturesTest {
    @Test
    fun `key feature per version`() {
        val cases = mapOf(
            "1.21.11" to VersionFeature.Spears,
            "1.21.11-pre3" to VersionFeature.Spears,
            "1.21.10" to VersionFeature.Copper,
            "1.21.9" to VersionFeature.Copper,
            "1.21.8" to VersionFeature.HappyGhast,
            "1.21.5" to VersionFeature.Variants,
            "1.21.4" to VersionFeature.PaleGarden,
            "1.21.3" to VersionFeature.Bundles,
            "1.21.1" to VersionFeature.Trials,
            "1.21" to VersionFeature.Trials,
            "1.20.1" to VersionFeature.Cherry,
            "1.19.4" to VersionFeature.DeepDark,
            "1.16.5" to VersionFeature.Nether,
            "1.12.2" to VersionFeature.Colors,
            "1.8.9" to VersionFeature.Monuments,
            "1.7.10" to VersionFeature.Biomes,
            "1.0" to VersionFeature.TheEnd,
            "26.1" to VersionFeature.Dandelion,
            "26.1.2" to VersionFeature.Dandelion,
            "26.2" to VersionFeature.SulfurCube,
            "26.3" to VersionFeature.AutumnForest,
            "26.3-snapshot-8" to VersionFeature.AutumnForest,
            "24w14a" to VersionFeature.Snapshot,
            "b1.7.3" to VersionFeature.Classic,
            "a1.2.6" to VersionFeature.Classic,
            "rd-132211" to VersionFeature.Classic,
            "" to VersionFeature.Classic,
        )
        cases.forEach { (id, expected) -> assertEquals(expected, VersionFeatures.of(id), id) }
    }

    @Test
    fun `every feature has an emblem inside the canvas`() {
        VersionFeature.entries.forEach { f ->
            val e = Emblems.of(f)
            assertTrue(e.parts.size in 3..9, "$f: ${e.parts.size} фигур")
            assertTrue(e.hue in 0.0..360.0)
            e.parts.forEach { p -> assertTrue(p.x in 0f..1f && p.y in 0f..1f && p.size in 0.04f..1f, "$f выходит за рамки") }
        }
    }
}
