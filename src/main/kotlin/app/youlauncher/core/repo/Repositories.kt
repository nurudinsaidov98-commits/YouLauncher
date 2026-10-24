package app.youlauncher.core.repo

import app.youlauncher.core.model.Account
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.model.Screenshot
import app.youlauncher.core.model.World
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Параметры нового экземпляра из мастера создания. */
data class NewInstanceSpec(
    val name: String,
    val gameVersion: String,
    val loader: LoaderType = LoaderType.Vanilla,
    val loaderVersion: String? = null,
    val icon: InstanceIcon = InstanceIcon.Grass,
    val group: String? = null,
    val minMemoryMb: Int? = null,
    val maxMemoryMb: Int? = null,
    val javaPath: String? = null,
)

interface InstanceRepository {
    val instances: StateFlow<List<Instance>>
    fun get(id: String): Instance? = instances.value.firstOrNull { it.id == id }
    suspend fun create(spec: NewInstanceSpec): Instance
    suspend fun update(id: String, transform: (Instance) -> Instance): Instance?
    suspend fun delete(id: String)
    suspend fun duplicate(id: String, newName: String): Instance?
    /** Каталог экземпляра (.minecraft внутри). */
    fun gameDir(id: String): java.nio.file.Path
}

enum class ContentKind(val folder: String) {
    Mods("mods"), ResourcePacks("resourcepacks"), ShaderPacks("shaderpacks"), DataPacks("datapacks")
}

interface InstanceContentRepository {
    fun content(instanceId: String, kind: ContentKind): Flow<List<ContentFile>>
    fun worlds(instanceId: String): Flow<List<World>>
    fun screenshots(instanceId: String): Flow<List<Screenshot>>
    suspend fun setEnabled(instanceId: String, kind: ContentKind, fileName: String, enabled: Boolean)
    suspend fun remove(instanceId: String, kind: ContentKind, fileName: String)
    suspend fun addFiles(instanceId: String, kind: ContentKind, files: List<java.nio.file.Path>)
    suspend fun refresh(instanceId: String)
    suspend fun deleteWorld(instanceId: String, folder: String)
    /** Архивирует мир в каталог резервных копий и возвращает путь к архиву. */
    suspend fun backupWorld(instanceId: String, folder: String): java.nio.file.Path?
}

enum class VersionType { Release, Snapshot, OldBeta, OldAlpha }

data class GameVersion(val id: String, val type: VersionType, val releaseTime: Long, val javaMajor: Int? = null)

interface GameVersionRepository {
    suspend fun versions(forceRefresh: Boolean = false): List<GameVersion>
    suspend fun latestRelease(): GameVersion? = versions().firstOrNull { it.type == VersionType.Release }
}

data class LoaderVersion(val version: String, val stable: Boolean, val recommended: Boolean = false, val latest: Boolean = false)

interface LoaderVersionRepository {
    suspend fun versions(loader: LoaderType, gameVersion: String): List<LoaderVersion>
    /** Для каких версий игры доступен загрузчик (для фильтрации в мастере). */
    suspend fun supportedGameVersions(loader: LoaderType): Set<String>? = null
}

/** Результат входа в Ely.by. */
sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data object TwoFactorRequired : AuthResult
    data class InvalidCredentials(val message: String) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/** Данные для запуска игры. Токен живёт только в памяти на время запуска. */
data class AccountSession(val username: String, val uuid: String, val accessToken: String, val type: AccountType)

class ReauthRequiredException(val username: String) : Exception("Сессия Ely.by для $username истекла: войдите заново")

interface AccountRepository {
    val accounts: StateFlow<List<Account>>
    val activeId: StateFlow<String?>
    suspend fun addOffline(nick: String): Account
    suspend fun loginElyBy(login: String, password: String, totp: String? = null): AuthResult
    suspend fun refresh(id: String): Boolean
    suspend fun remove(id: String)
    fun setActive(id: String)
    /** Сессия для запуска: у Ely.by токен проверяется и при необходимости обновляется. */
    suspend fun session(id: String): AccountSession
}

enum class LaunchStage { Preparing, Java, Libraries, Assets, Loader, Starting, Running }

sealed interface LaunchState {
    data object Idle : LaunchState
    data class Preparing(val stage: LaunchStage, val progress: Float?, val detail: String = "") : LaunchState
    data class Running(val pid: Long, val startedAt: Long) : LaunchState
    data class Crashed(val exitCode: Int, val reportPath: String?, val summary: String) : LaunchState
    data class Failed(val message: String) : LaunchState
}

interface LaunchService {
    val states: StateFlow<Map<String, LaunchState>>
    fun state(instanceId: String): LaunchState = states.value[instanceId] ?: LaunchState.Idle
    fun launch(instanceId: String, quickJoinServer: String? = null)
    fun stop(instanceId: String)
    fun dismiss(instanceId: String)
    /** Починка: все файлы игры и загрузчика проверяются по хешам и перекачиваются при расхождении. */
    fun repair(instanceId: String, onDone: (Result<Unit>) -> Unit = {}) = onDone(Result.success(Unit))
}

interface LogBus {
    val lines: StateFlow<List<LogLine>>
    fun append(line: LogLine)
    fun clear()
}

data class ModQuery(
    val text: String = "",
    val type: ProjectType = ProjectType.Mod,
    val gameVersion: String? = null,
    val loader: LoaderType? = null,
    val categories: Set<String> = emptySet(),
    val sort: ModSort = ModSort.Relevance,
    val offset: Int = 0,
    val limit: Int = 20,
)

enum class ModSort { Relevance, Downloads, Follows, Newest, Updated }

data class ModSearchPage(val hits: List<ModProject>, val total: Int, val offset: Int)

interface ModCatalog {
    val name: String
    val available: Boolean get() = true
    suspend fun search(query: ModQuery): ModSearchPage
    suspend fun project(id: String): ModProject
    suspend fun versions(projectId: String, gameVersion: String? = null, loader: LoaderType? = null): List<ModVersion>
    suspend fun categories(type: ProjectType): List<String>
}

data class InstallProgress(val projectId: String, val progress: Float?, val done: Boolean = false, val error: String? = null)

/** Обязательная зависимость в предпросмотре установки. */
data class DependencyInfo(
    val projectId: String,
    val title: String,
    val iconUrl: String?,
    val versionNumber: String?,
    /** Уже стоит в экземпляре: качать не нужно. */
    val installed: Boolean,
)

/** Что будет установлено: выбранная версия и все обязательные зависимости (с вложенными). */
data class InstallPreview(
    val project: ModProject,
    val version: ModVersion?,
    val dependencies: List<DependencyInfo> = emptyList(),
    /** Зависимости без подходящей версии (названия). */
    val missing: List<String> = emptyList(),
    /** Установленные проекты, с которыми мод несовместим (названия). */
    val conflicts: List<String> = emptyList(),
    val error: String? = null,
) {
    val toDownload: List<DependencyInfo> get() = dependencies.filterNot { it.installed }
    val canInstall: Boolean get() = error == null && version != null && missing.isEmpty() && conflicts.isEmpty()
}

enum class CompatStatus {
    /** Есть более подходящая версия под новую игру и загрузчик: файл будет заменён. */
    Update,
    /** Текущая версия подходит и так. */
    Same,
    /** Под новую версию ничего нет: файл будет выключен (не удалён). */
    Incompatible,
    /** Файл добавлен вручную, каталог о нём не знает: проверить нельзя. */
    Unknown,
}

data class ModCompat(
    val kind: ContentKind,
    val fileName: String,
    val title: String,
    val status: CompatStatus,
    val newVersion: ModVersion? = null,
    val projectId: String? = null,
)

interface ModInstaller {
    val progress: StateFlow<Map<String, InstallProgress>>
    /** Устанавливает версию проекта в экземпляр; с [withDependencies] вместе с обязательными зависимостями. */
    suspend fun install(instanceId: String, project: ModProject, version: ModVersion? = null, withDependencies: Boolean = true): Result<List<String>>
    /** Предпросмотр установки без загрузки файлов. */
    suspend fun preview(instanceId: String, project: ModProject, version: ModVersion? = null): InstallPreview =
        InstallPreview(project, version)
    suspend fun installedProjectIds(instanceId: String): Set<String>
    /** Совместимость установленного содержимого с другой версией игры и загрузчиком (без изменений на диске). */
    suspend fun compatibility(instanceId: String, gameVersion: String, loader: LoaderType): List<ModCompat> = emptyList()
    /** Применяет план после смены версии экземпляра: обновляет подходящее, выключает несовместимое. */
    suspend fun migrate(instanceId: String, plan: List<ModCompat>): Int = 0
    /** Проверка файлов из каталогов по SHA-1 из индекса: недостающие и повреждённые скачиваются заново. */
    suspend fun repairFiles(instanceId: String): Int = 0
    /** Ищет обновления установленных файлов; возвращает число найденных. */
    suspend fun checkUpdates(instanceId: String): Int = 0
    /** Обновляет файл до найденной версии. */
    suspend fun update(instanceId: String, kind: ContentKind, fileName: String): Result<List<String>> = Result.success(emptyList())
}
