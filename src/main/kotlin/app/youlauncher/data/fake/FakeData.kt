package app.youlauncher.data.fake

import app.youlauncher.core.model.Account
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.offlineUuid
import app.youlauncher.core.repo.GameVersion
import app.youlauncher.core.repo.VersionType
import java.time.LocalDate
import java.util.Locale

/**
 * Демо-данные для интерфейса до подключения ядра и для скриншотов. Все названия выдуманы,
 * никаких отсылок к ассетам и брендам игры.
 */
object FakeData {
    private val ru: Boolean get() = Locale.getDefault().language == "ru"
    fun t(ru: String, en: String) = if (this.ru) ru else en

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    fun instances(now: Long = System.currentTimeMillis()): List<Instance> = listOf(
        Instance(
            id = "demo-survival", name = t("Выживание", "Survival"), gameVersion = "1.21.10",
            loader = LoaderType.Fabric, loaderVersion = "0.17.3", icon = InstanceIcon.Forest,
            favorite = true, createdAt = now - 60 * DAY, lastPlayedAt = now - 2 * HOUR,
            totalPlayTimeMs = 42 * HOUR, lastSessionMs = 2 * HOUR + 1_200_000, launches = 57,
            group = t("Основные", "Main"), tags = listOf(t("с друзьями", "friends")),
            playLog = playLog(now, seed = 1),
        ),
        Instance(
            id = "demo-vanilla", name = t("Чистая ваниль", "Pure vanilla"), gameVersion = "1.21.10",
            icon = InstanceIcon.Grass, createdAt = now - 40 * DAY, lastPlayedAt = now - DAY,
            totalPlayTimeMs = 12 * HOUR, lastSessionMs = 3_000_000, launches = 18,
            group = t("Основные", "Main"), playLog = playLog(now, seed = 2),
        ),
        Instance(
            id = "demo-tech", name = t("Техно-сборка", "Tech pack"), gameVersion = "1.20.1",
            loader = LoaderType.NeoForge, loaderVersion = "47.1.106", icon = InstanceIcon.Science,
            createdAt = now - 120 * DAY, lastPlayedAt = now - 3 * DAY, totalPlayTimeMs = 87 * HOUR,
            lastSessionMs = 4 * HOUR, launches = 64, group = t("Сборки", "Modpacks"),
            tags = listOf(t("техника", "tech"), t("долгая игра", "long run")), playLog = playLog(now, seed = 3),
        ),
        Instance(
            id = "demo-retro", name = t("Ретро", "Retro"), gameVersion = "1.8.9", icon = InstanceIcon.Castle,
            createdAt = now - 200 * DAY, lastPlayedAt = now - 14 * DAY, totalPlayTimeMs = 6 * HOUR,
            lastSessionMs = 1_800_000, launches = 9, playLog = playLog(now, seed = 4),
        ),
        Instance(
            id = "demo-light", name = t("Лёгкий клиент", "Light client"), gameVersion = "1.21.1",
            loader = LoaderType.Quilt, loaderVersion = "0.29.2", icon = InstanceIcon.Bolt,
            createdAt = now - 30 * DAY, lastPlayedAt = now - 30 * DAY, totalPlayTimeMs = 3 * HOUR,
            lastSessionMs = 900_000, launches = 4, favorite = true, playLog = playLog(now, seed = 5),
        ),
        Instance(
            id = "demo-sky", name = t("Небесные острова", "Sky islands"), gameVersion = "26.1",
            loader = LoaderType.Fabric, loaderVersion = "0.18.1", icon = InstanceIcon.Rocket,
            createdAt = now - 5 * DAY, lastPlayedAt = null, totalPlayTimeMs = 0, launches = 0,
            group = t("Сборки", "Modpacks"),
        ),
    )

    private fun playLog(now: Long, seed: Int): Map<String, Int> {
        val rnd = java.util.Random(seed.toLong())
        val today = LocalDate.ofEpochDay(now / DAY)
        return (0 until 30).associate { d ->
            val minutes = if (rnd.nextFloat() < 0.45f) 0 else 15 + rnd.nextInt(180)
            today.minusDays(d.toLong()).toString() to minutes
        }.filterValues { it > 0 }
    }

    fun accounts(now: Long = System.currentTimeMillis()): List<Account> = listOf(
        Account("acc-offline", AccountType.Offline, "Wanderer", offlineUuid("Wanderer"), addedAt = now - 50 * DAY, lastUsedAt = now - 2 * HOUR),
        Account("acc-ely", AccountType.ElyBy, "Stonecutter", "6f1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", login = "stonecutter@example.org", addedAt = now - 20 * DAY, lastUsedAt = now - 5 * DAY),
        Account("acc-offline-2", AccountType.Offline, "Lumen", offlineUuid("Lumen"), addedAt = now - 10 * DAY),
    )

    fun versions(): List<GameVersion> {
        val base = 1_760_000_000_000L
        val releases = listOf(
            "26.3", "26.2", "26.1", "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.1", "1.21",
            "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5", "1.15.2", "1.14.4", "1.13.2",
            "1.12.2", "1.11.2", "1.10.2", "1.9.4", "1.8.9", "1.7.10", "1.6.4", "1.5.2", "1.4.7", "1.2.5",
        )
        val snapshots = listOf("26.4-snapshot-2", "26.4-snapshot-1", "26.3-rc1", "26.3-pre2")
        val list = snapshots.mapIndexed { i, id -> GameVersion(id, VersionType.Snapshot, base - i * DAY) } +
            releases.mapIndexed { i, id -> GameVersion(id, VersionType.Release, base - (i + 5) * 30 * DAY) } +
            listOf(GameVersion("b1.7.3", VersionType.OldBeta, 1_300_000_000_000L), GameVersion("a1.2.6", VersionType.OldAlpha, 1_290_000_000_000L))
        return list
    }

    private val modNames = listOf(
        "Swiftframe" to ("Ускоряет отрисовку и поднимает частоту кадров" to "Faster rendering and higher frame rates"),
        "Atlas Lite" to ("Мини-карта и путевые точки" to "Minimap with waypoints"),
        "Satchel" to ("Сортировка инвентаря и быстрый перенос" to "Inventory sorting and quick transfer"),
        "Lumen Shade" to ("Мягкое освещение и тени" to "Soft lighting and shadows"),
        "Wayfarer's Journal" to ("Заметки о местах и маршрутах" to "Notes about places and routes"),
        "Echo Field" to ("Объёмный звук окружения" to "Spatial ambient sound"),
        "Ironclad API" to ("Общая библиотека для модов" to "Shared library for mods"),
        "Quiet Night" to ("Спокойные ночи без лишнего шума" to "Calmer nights without extra noise"),
        "Harvest Plus" to ("Удобный сбор урожая" to "Convenient crop harvesting"),
        "Skyline Clouds" to ("Объёмные облака" to "Volumetric clouds"),
        "Beacon Tweaks" to ("Настройка маяков и эффектов" to "Beacon and effect tweaks"),
        "Pathfinder HUD" to ("Компактный интерфейс с координатами" to "Compact HUD with coordinates"),
        "Clearwater" to ("Прозрачная вода и подводный обзор" to "Clear water and underwater view"),
        "Forge of Stars" to ("Новые руды и инструменты" to "New ores and tools"),
        "Woodland Tales" to ("Разнообразие лесов" to "More varied forests"),
        "Driftwood" to ("Плоты и речные путешествия" to "Rafts and river travel"),
        "Hearthstone Kitchen" to ("Готовка и новые блюда" to "Cooking and new dishes"),
        "Gearwork" to ("Механизмы и автоматизация" to "Machines and automation"),
        "Pixel Perfect" to ("Ресурспак с чёткими текстурами" to "Crisp texture resource pack"),
        "Pastel Dreams" to ("Пастельный ресурспак" to "Pastel resource pack"),
        "Aurora Shaders" to ("Шейдеры с северным сиянием" to "Shaders with auroras"),
        "Soft Glow" to ("Лёгкие шейдеры для слабых ПК" to "Lightweight shaders for low-end PCs"),
        "Better Villages DP" to ("Датапак с новыми постройками" to "Data pack with new structures"),
        "Starter Kit" to ("Датапак со стартовым набором" to "Data pack with a starter kit"),
    )

    fun mods(): List<ModProject> = modNames.mapIndexed { i, (name, summary) ->
        val type = when {
            i >= 22 -> ProjectType.DataPack
            i >= 20 -> ProjectType.Shader
            i >= 18 -> ProjectType.ResourcePack
            else -> ProjectType.Mod
        }
        val categories = listOf(
            listOf("optimization"), listOf("utility"), listOf("storage", "utility"), listOf("decoration"),
            listOf("adventure"), listOf("audio"), listOf("library"), listOf("utility"), listOf("food"),
            listOf("decoration"), listOf("magic"), listOf("utility"), listOf("decoration"), listOf("equipment"),
            listOf("worldgen"), listOf("transportation"), listOf("food"), listOf("technology"),
        ).getOrElse(i) { listOf("decoration") }
        ModProject(
            id = "demo-mod-$i", slug = name.lowercase().replace(' ', '-').replace("'", ""), source = ContentSource.Modrinth,
            type = type, title = name, author = listOf("Northwind", "Kestrel", "Mosslight", "Quill", "Ferro", "Orchard")[i % 6],
            summary = t(summary.first, summary.second),
            description = t(
                "## $name\n\n${summary.first}.\n\n### Возможности\n- Настраивается в меню модов\n- Совместим с популярными сборками\n- Не требует перезапуска мира\n\n> Демо-описание до подключения каталога Modrinth.",
                "## $name\n\n${summary.second}.\n\n### Features\n- Configurable from the mod menu\n- Compatible with popular packs\n- No world restart needed\n\n> Demo description until the Modrinth catalog is connected.",
            ),
            downloads = (12_400_000L / (i + 1)) + i * 3_100, follows = 40_000L / (i + 1),
            categories = categories, gameVersions = listOf("1.21.10", "1.21.1", "1.20.1"),
            loaders = if (type == ProjectType.Mod) listOf("fabric", "quilt", "neoforge") else emptyList(),
            updatedAt = System.currentTimeMillis() - i * 3 * DAY, license = "MIT",
        )
    }
}
