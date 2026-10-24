package app.youlauncher.core.settings

import app.youlauncher.core.readJsonOrNull
import app.youlauncher.core.writeJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Path
import java.util.UUID

/** Пресет атмосферы: фон, затемнение, размытие и (необязательно) цвет и стиль палитры. */
@Serializable
data class AtmospherePreset(
    val id: String,
    val name: String,
    val background: BackgroundSettings,
    val seed: Long? = null,
    val style: PaletteStyleOption? = null,
    val builtin: Boolean = false,
)

object BuiltinPresets {
    val all: List<AtmospherePreset> = listOf(
        AtmospherePreset("preset-dawn", "dawn", BackgroundSettings(kind = BackgroundKind.Wallpaper, wallpaper = "builtin:dawn-blobs", scrim = 0.3f, blur = 0.7f), builtin = true),
        AtmospherePreset("preset-lagoon", "lagoon", BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.Waves, scrim = 0.35f), style = PaletteStyleOption.Vibrant, builtin = true),
        AtmospherePreset("preset-quiet", "quiet", BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.Calm, scrim = 0.5f, parallax = false), style = PaletteStyleOption.Neutral, builtin = true),
        AtmospherePreset("preset-aurora", "aurora", BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.MeshGradient, scrim = 0.3f, blur = 0.8f), style = PaletteStyleOption.Expressive, builtin = true),
        AtmospherePreset("preset-version", "version", BackgroundSettings(kind = BackgroundKind.VersionScene, scrim = 0.35f), builtin = true),
        AtmospherePreset("preset-playful", "playful", BackgroundSettings(kind = BackgroundKind.Live, live = LiveBackground.DriftingShapes, scrim = 0.4f), style = PaletteStyleOption.FruitSalad, builtin = true),
    )
}

/** Свои пресеты пользователя хранятся в atmospheres.json. */
class AtmospherePresetRepository(private val file: Path) {
    private val serializer = ListSerializer(AtmospherePreset.serializer())
    private val state = MutableStateFlow(file.readJsonOrNull(serializer).orEmpty())
    val presets: StateFlow<List<AtmospherePreset>> = state.asStateFlow()

    fun save(name: String, settings: LauncherSettings): AtmospherePreset {
        val preset = AtmospherePreset(UUID.randomUUID().toString(), name, settings.background, settings.theme.seed, settings.theme.style)
        state.update { it + preset }
        persist()
        return preset
    }

    fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private fun persist() = runCatching { file.writeJson(serializer, state.value) }

    companion object {
        /** Применение пресета к настройкам: фон целиком, цвет и стиль, если они заданы. */
        fun apply(preset: AtmospherePreset, settings: LauncherSettings): LauncherSettings = settings.copy(
            background = preset.background.copy(userWallpaperFolder = settings.background.userWallpaperFolder),
            theme = settings.theme.copy(
                seed = preset.seed ?: settings.theme.seed,
                style = preset.style ?: settings.theme.style,
                presetId = preset.id,
            ),
        )
    }
}
