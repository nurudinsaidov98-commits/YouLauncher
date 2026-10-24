package app.youlauncher.data.profile

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.profile.Profile
import app.youlauncher.core.profile.ProfileRepository
import app.youlauncher.core.writeJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readText

/** Локальное хранилище: profile.json и папка images с копиями выбранных картинок. */
class LocalProfileRepository(private val root: Path) : ProfileRepository {
    private val profileFile = root.resolve("profile.json")
    private val imagesDir = root.resolve("images")
    private val state = MutableStateFlow(loadProfile())
    override val profile: StateFlow<Profile> = state.asStateFlow()

    private fun loadProfile(): Profile {
        // Новый профиль сразу записывается на диск, иначе сгенерированный аватар менялся бы при каждом запуске.
        if (!profileFile.exists()) return newProfile().also { p -> runCatching { profileFile.writeJson(Profile.serializer(), p) } }
        return runCatching {
            val obj = YouLauncherJson.parseToJsonElement(profileFile.readText()).jsonObject
            val schema = obj["schema"]?.jsonPrimitive?.int ?: 0
            val migrated = if (schema < 1) JsonObject(obj + ("schema" to JsonPrimitive(1))) else obj
            YouLauncherJson.decodeFromJsonElement<Profile>(migrated)
        }.getOrElse { newProfile() }
    }

    private fun newProfile(): Profile {
        val id = UUID.randomUUID().toString()
        return Profile(id = id, displayName = "Player", nickname = "player", avatarSeed = id)
    }

    override suspend fun save(profile: Profile) = withContext(Dispatchers.IO) {
        val p = profile.copy(schema = Profile.SCHEMA, updatedAt = System.currentTimeMillis())
        profileFile.writeJson(Profile.serializer(), p)
        state.value = p
    }

    override suspend fun importImage(source: Path, kind: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Files.createDirectories(imagesDir)
            val target = imagesDir.resolve("$kind-${UUID.randomUUID().toString().take(8)}.${source.extension.lowercase()}")
            Files.copy(source, target)
            target.toString()
        }.getOrNull()
    }
}
