package app.youlauncher.core

/**
 * Ключевая новинка версии игры для эмблемы в мастере создания. Описания свои, без официальных
 * названий обновлений; сведения о версиях 26.x проверены по журналам изменений (docs/DECISIONS.md).
 */
enum class VersionFeature {
    Classic, Snapshot,
    TheEnd, Jungle, Trading, Wither, Redstone, Horses, Biomes, Monuments, Elytra, Frost, Mansions,
    Colors, Ocean, Villages, Bees, Nether, Amethyst, Mountains, DeepDark, Cherry, Trials, Bundles,
    PaleGarden, Variants, HappyGhast, Copper, Spears, Dandelion, SulfurCube, AutumnForest,
}

object VersionFeatures {
    /** Новинка для id версии: релизы, пре-релизы и снапшоты нового формата по базовой версии. */
    fun of(versionId: String): VersionFeature {
        val id = versionId.trim().lowercase()
        if (id.isEmpty()) return VersionFeature.Classic
        if (Regex("""^\d{2}w\d{2}[a-z]$""").matches(id)) return VersionFeature.Snapshot
        if (id.startsWith("a") || id.startsWith("b") || id.startsWith("c") || id.startsWith("rd-") || id.startsWith("inf-")) return VersionFeature.Classic
        val base = id.substringBefore('-').substringBefore(' ')
        val parts = base.split('.').map { it.toIntOrNull() ?: return VersionFeature.Classic }
        if (parts.isEmpty()) return VersionFeature.Classic
        // Новая нумерация с 2026 года: год.выпуск[.исправление].
        if (parts[0] >= 26) return when (parts.getOrElse(1) { 1 }) {
            1 -> VersionFeature.Dandelion
            2 -> VersionFeature.SulfurCube
            3 -> VersionFeature.AutumnForest
            else -> VersionFeature.Snapshot
        }
        if (parts[0] != 1) return VersionFeature.Classic
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return when (minor) {
            0, 1 -> VersionFeature.TheEnd
            2 -> VersionFeature.Jungle
            3 -> VersionFeature.Trading
            4 -> VersionFeature.Wither
            5 -> VersionFeature.Redstone
            6 -> VersionFeature.Horses
            7 -> VersionFeature.Biomes
            8 -> VersionFeature.Monuments
            9 -> VersionFeature.Elytra
            10 -> VersionFeature.Frost
            11 -> VersionFeature.Mansions
            12 -> VersionFeature.Colors
            13 -> VersionFeature.Ocean
            14 -> VersionFeature.Villages
            15 -> VersionFeature.Bees
            16 -> VersionFeature.Nether
            17 -> VersionFeature.Amethyst
            18 -> VersionFeature.Mountains
            19 -> VersionFeature.DeepDark
            20 -> VersionFeature.Cherry
            21 -> when {
                patch <= 1 -> VersionFeature.Trials
                patch <= 3 -> VersionFeature.Bundles
                patch == 4 -> VersionFeature.PaleGarden
                patch == 5 -> VersionFeature.Variants
                patch <= 8 -> VersionFeature.HappyGhast
                patch <= 10 -> VersionFeature.Copper
                else -> VersionFeature.Spears
            }
            else -> VersionFeature.Snapshot
        }
    }
}
