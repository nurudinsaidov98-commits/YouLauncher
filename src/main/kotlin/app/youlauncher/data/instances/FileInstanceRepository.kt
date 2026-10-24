package app.youlauncher.data.instances

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceJava
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.core.writeTextAtomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/** Файл экземпляра: номер схемы и сами данные. */
@Serializable
data class InstanceFile(val schema: Int = InstanceMigrations.CURRENT, val instance: Instance)

/** Миграции instance.json между версиями схемы. */
object InstanceMigrations {
    const val CURRENT = 1

    fun migrate(root: JsonObject): JsonObject {
        var json = root
        val schema = json["schema"]?.jsonPrimitive?.int ?: 0
        if (schema < 1) {
            // Схема 0: данные экземпляра лежали прямо в корне файла.
            json = if (json.containsKey("instance")) json else JsonObject(mapOf("schema" to JsonPrimitive(1), "instance" to json))
            json = JsonObject(json + ("schema" to JsonPrimitive(1)))
        }
        return json
    }
}

/**
 * Экземпляры на диске: instances/<id>/instance.json и изолированная папка игры instances/<id>/.minecraft.
 * Общие библиотеки, ассеты и версии лежат в общем кэше лаунчера.
 */
class FileInstanceRepository(private val root: Path) : InstanceRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow(loadAll())
    override val instances: StateFlow<List<Instance>> = state.asStateFlow()

    private fun dir(id: String): Path = root.resolve(id)
    private fun file(id: String): Path = dir(id).resolve("instance.json")
    override fun gameDir(id: String): Path = dir(id).resolve(".minecraft")

    private fun loadAll(): List<Instance> {
        if (!root.isDirectory()) return emptyList()
        return root.listDirectoryEntries().filter { it.resolve("instance.json").exists() }.mapNotNull { d ->
            runCatching { load(d.resolve("instance.json")) }.getOrNull()
        }.sortedBy { it.createdAt }
    }

    fun load(path: Path): Instance {
        val element: JsonElement = YouLauncherJson.parseToJsonElement(path.readText())
        val migrated = InstanceMigrations.migrate(element.jsonObject)
        return YouLauncherJson.decodeFromJsonElement<InstanceFile>(migrated).instance
    }

    private fun save(instance: Instance) {
        Files.createDirectories(gameDir(instance.id))
        file(instance.id).writeTextAtomic(YouLauncherJson.encodeToString(InstanceFile.serializer(), InstanceFile(instance = instance)))
    }

    override suspend fun create(spec: NewInstanceSpec): Instance = mutex.withLock {
        val instance = Instance(
            id = newId(spec.name),
            name = spec.name,
            gameVersion = spec.gameVersion,
            loader = spec.loader,
            loaderVersion = spec.loaderVersion,
            icon = spec.icon,
            group = spec.group,
            createdAt = System.currentTimeMillis(),
            java = InstanceJava(javaPath = spec.javaPath, minMemoryMb = spec.minMemoryMb, maxMemoryMb = spec.maxMemoryMb),
        )
        withContext(Dispatchers.IO) { save(instance) }
        state.update { it + instance }
        instance
    }

    /** Читаемый и уникальный id папки: латиница из имени плюс короткий суффикс. */
    private fun newId(name: String): String {
        val slug = name.lowercase().map { c -> if (c.isLetterOrDigit() && c.code < 128) c else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-').take(24).ifBlank { "instance" }
        var id: String
        do { id = "$slug-${UUID.randomUUID().toString().take(6)}" } while (dir(id).exists())
        return id
    }

    override suspend fun update(id: String, transform: (Instance) -> Instance): Instance? = mutex.withLock {
        val current = state.value.firstOrNull { it.id == id } ?: return null
        val updated = transform(current).copy(id = id)
        if (updated == current) return current
        withContext(Dispatchers.IO) { save(updated) }
        state.update { list -> list.map { if (it.id == id) updated else it } }
        updated
    }

    /** Удаление: папка уходит в корзину Windows, если это возможно, иначе удаляется. */
    override suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val d = dir(id).toFile()
            val trashed = runCatching {
                java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) &&
                    java.awt.Desktop.getDesktop().moveToTrash(d)
            }.getOrDefault(false)
            if (!trashed && d.exists()) d.deleteRecursively()
        }
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun duplicate(id: String, newName: String): Instance? = mutex.withLock {
        val source = state.value.firstOrNull { it.id == id } ?: return null
        val copy = source.copy(
            id = newId(newName), name = newName, createdAt = System.currentTimeMillis(),
            lastPlayedAt = null, totalPlayTimeMs = 0, lastSessionMs = 0, launches = 0, playLog = emptyMap(), favorite = false,
        )
        withContext(Dispatchers.IO) {
            val from = gameDir(id)
            val to = gameDir(copy.id)
            if (from.exists()) from.toFile().copyRecursively(to.toFile(), overwrite = false) { file, e ->
                // Пропускаем занятые файлы (например, лог запущенной игры).
                if (e is java.nio.file.FileSystemException || e is kotlin.io.FileAlreadyExistsException) kotlin.io.OnErrorAction.SKIP else kotlin.io.OnErrorAction.TERMINATE
            }
            save(copy)
        }
        state.update { it + copy }
        copy
    }

    /** Импорт готового экземпляра (из архива или другого лаунчера): папка игры уже подготовлена. */
    suspend fun register(instance: Instance) = mutex.withLock {
        withContext(Dispatchers.IO) { save(instance) }
        state.update { list -> list.filterNot { it.id == instance.id } + instance }
    }

    fun newInstanceId(name: String): String = newId(name)
}
