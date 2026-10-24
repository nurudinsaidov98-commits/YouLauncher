package app.youlauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { Offline, ElyBy, Microsoft }

/** Аккаунт без секретов: токены лежат только в Windows Credential Manager. */
@Serializable
data class Account(
    val id: String,
    val type: AccountType,
    val username: String,
    val uuid: String,
    /** Логин Ely.by (email или ник) для повторного входа. */
    val login: String? = null,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val slimSkin: Boolean = false,
    val addedAt: Long = 0,
    val lastUsedAt: Long? = null,
    /** Сессия Ely.by требует повторного входа. */
    val needsReauth: Boolean = false,
)

enum class LogLevel { Trace, Debug, Info, Warn, Error, Fatal }

enum class LogSource { Launcher, Game }

data class LogLine(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val source: LogSource,
    val thread: String?,
    val text: String,
    val instanceId: String? = null,
)

enum class ProjectType { Mod, ResourcePack, Shader, DataPack, Modpack }

enum class ContentSource { Modrinth, CurseForge, Local }

data class ModProject(
    val id: String,
    val slug: String,
    val source: ContentSource,
    val type: ProjectType,
    val title: String,
    val author: String,
    val summary: String,
    val description: String = "",
    val downloads: Long,
    val follows: Long = 0,
    val iconUrl: String? = null,
    val categories: List<String> = emptyList(),
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val gallery: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val license: String? = null,
    val sourceUrl: String? = null,
)

data class ModVersion(
    val id: String,
    val projectId: String,
    val name: String,
    val versionNumber: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val type: String,
    val publishedAt: Long,
    val downloads: Long,
    val files: List<ModFile>,
    val dependencies: List<ModDependency>,
)

data class ModFile(val url: String, val fileName: String, val sha1: String?, val sha512: String?, val size: Long, val primary: Boolean)

data class ModDependency(val projectId: String?, val versionId: String?, val kind: DependencyKind)

enum class DependencyKind { Required, Optional, Incompatible, Embedded }

enum class InstallState { NotInstalled, Installing, Installed, UpdateAvailable, Incompatible }
