package app.youlauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class LoaderType(val title: String) {
    Vanilla("Vanilla"),
    Fabric("Fabric"),
    Quilt("Quilt"),
    Forge("Forge"),
    NeoForge("NeoForge"),
}

/** Иконки экземпляров из набора Material Symbols (имя символа). */
@Serializable
enum class InstanceIcon { Grass, Castle, Cottage, Diamond, Swords, Forest, Rocket, Science, Explore, Pets, Bolt, Water }

@Serializable
data class InstanceJava(
    /** null: глобальная настройка или автоподбор. */
    val javaPath: String? = null,
    val minMemoryMb: Int? = null,
    val maxMemoryMb: Int? = null,
    val jvmArgs: String? = null,
    val env: Map<String, String> = emptyMap(),
    val preLaunchCommand: String? = null,
    val postExitCommand: String? = null,
    val wrapperCommand: String? = null,
)

/** Атмосфера экземпляра: фон, сцена и затемнение. null у поля значит «как в общих настройках». */
@Serializable
data class InstanceAtmosphere(
    val useVersionScene: Boolean = true,
    val wallpaper: String? = null,
    val seed: Long? = null,
    val scrim: Float? = null,
    val presetId: String? = null,
)

@Serializable
data class Instance(
    val id: String,
    val name: String,
    val gameVersion: String,
    val loader: LoaderType = LoaderType.Vanilla,
    val loaderVersion: String? = null,
    val icon: InstanceIcon = InstanceIcon.Grass,
    /** ARGB акцента карточки; null: из темы. */
    val accent: Long? = null,
    val coverPath: String? = null,
    val group: String? = null,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val lastPlayedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val lastSessionMs: Long = 0,
    val launches: Int = 0,
    val java: InstanceJava = InstanceJava(),
    val atmosphere: InstanceAtmosphere = InstanceAtmosphere(),
    val width: Int? = null,
    val height: Int? = null,
    val fullscreen: Boolean? = null,
    val quickJoinServer: String? = null,
    val modProfile: String? = null,
    /** Ежедневная статистика игры: дата ISO -> минуты. */
    val playLog: Map<String, Int> = emptyMap(),
    val backupSchedule: BackupSchedule = BackupSchedule.Off,
    val lastBackupAt: Long? = null,
) {
    val isModded: Boolean get() = loader != LoaderType.Vanilla
}

enum class InstanceSort { LastPlayed, Name, PlayTime, Created, Version }

/** Резервные копии экземпляра по расписанию. */
@Serializable
enum class BackupSchedule(val intervalMs: Long) {
    Off(0),
    Daily(24 * 60 * 60 * 1000L),
    Weekly(7 * 24 * 60 * 60 * 1000L),
}

@Serializable
data class ContentFile(
    val fileName: String,
    val title: String,
    val version: String? = null,
    val enabled: Boolean = true,
    val sizeBytes: Long = 0,
    val sha1: String? = null,
    val projectId: String? = null,
    val source: String? = null,
    val updateAvailable: String? = null,
)

data class World(
    val folder: String,
    val name: String,
    val lastPlayed: Long,
    val sizeBytes: Long,
    val gameMode: String,
)

data class Screenshot(val path: String, val takenAt: Long, val width: Int, val height: Int)
