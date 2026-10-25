package app.youlauncher.data.fake

import app.youlauncher.core.model.Account
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.model.DependencyKind
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceJava
import app.youlauncher.core.model.LoaderType
import kotlinx.coroutines.flow.first
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogSource
import app.youlauncher.core.model.ModDependency
import app.youlauncher.core.model.ModFile
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.model.Screenshot
import app.youlauncher.core.model.World
import app.youlauncher.core.offlineUuid
import app.youlauncher.core.repo.AccountRepository
import app.youlauncher.core.repo.AccountSession
import app.youlauncher.core.repo.AuthResult
import app.youlauncher.core.repo.CompatStatus
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.ModCompat
import app.youlauncher.core.repo.DependencyInfo
import app.youlauncher.core.repo.InstallPreview
import app.youlauncher.core.repo.GameVersion
import app.youlauncher.core.repo.GameVersionRepository
import app.youlauncher.core.repo.InstallProgress
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.LaunchService
import app.youlauncher.core.repo.LaunchStage
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.repo.LoaderVersion
import app.youlauncher.core.repo.LoaderVersionRepository
import app.youlauncher.core.repo.ModCatalog
import app.youlauncher.core.repo.ModInstaller
import app.youlauncher.core.repo.ModQuery
import app.youlauncher.core.repo.ModSearchPage
import app.youlauncher.core.repo.ModSort
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.log.InMemoryLogBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.UUID

class FakeInstanceRepository(private val root: Path, seed: List<Instance> = FakeData.instances()) : InstanceRepository {
    private val state = MutableStateFlow(seed)
    override val instances: StateFlow<List<Instance>> = state.asStateFlow()

    override suspend fun create(spec: NewInstanceSpec): Instance {
        val instance = Instance(
            id = UUID.randomUUID().toString(),
            name = spec.name,
            gameVersion = spec.gameVersion,
            loader = spec.loader,
            loaderVersion = spec.loaderVersion,
            icon = spec.icon,
            group = spec.group,
            createdAt = System.currentTimeMillis(),
            java = InstanceJava(javaPath = spec.javaPath, minMemoryMb = spec.minMemoryMb, maxMemoryMb = spec.maxMemoryMb),
        )
        state.update { it + instance }
        return instance
    }

    override suspend fun update(id: String, transform: (Instance) -> Instance): Instance? {
        var result: Instance? = null
        state.update { list -> list.map { if (it.id == id) transform(it).also { r -> result = r } else it } }
        return result
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun duplicate(id: String, newName: String): Instance? {
        val source = get(id) ?: return null
        val copy = source.copy(id = UUID.randomUUID().toString(), name = newName, createdAt = System.currentTimeMillis(), lastPlayedAt = null, totalPlayTimeMs = 0, launches = 0, playLog = emptyMap())
        state.update { it + copy }
        return copy
    }

    override fun gameDir(id: String): Path = root.resolve(id).resolve(".minecraft")
}

class FakeContentRepository : InstanceContentRepository {
    private val store = MutableStateFlow<Map<String, List<ContentFile>>>(emptyMap())

    private fun key(id: String, kind: ContentKind) = "$id/${kind.name}"

    private fun seed(id: String, kind: ContentKind): List<ContentFile> {
        val rnd = java.util.Random((id + kind).hashCode().toLong())
        val pool = FakeData.mods().filter {
            when (kind) {
                ContentKind.Mods -> it.type == ProjectType.Mod
                ContentKind.ResourcePacks -> it.type == ProjectType.ResourcePack
                ContentKind.ShaderPacks -> it.type == ProjectType.Shader
                ContentKind.DataPacks -> it.type == ProjectType.DataPack
            }
        }
        if (id == "demo-vanilla" || id == "demo-retro") return if (kind == ContentKind.ResourcePacks) pool.take(1).map { it.toFile(rnd) } else emptyList()
        return pool.filter { rnd.nextFloat() < 0.7f }.map { it.toFile(rnd) }
    }

    private fun ModProject.toFile(rnd: java.util.Random) = ContentFile(
        fileName = "$slug-${1 + rnd.nextInt(4)}.${rnd.nextInt(10)}.jar",
        title = title,
        version = "${1 + rnd.nextInt(4)}.${rnd.nextInt(10)}.${rnd.nextInt(10)}",
        enabled = rnd.nextFloat() > 0.15f,
        sizeBytes = 80_000L + rnd.nextInt(4_000_000),
        projectId = id,
        source = "modrinth",
        updateAvailable = if (rnd.nextFloat() < 0.2f) "${2 + rnd.nextInt(3)}.0.0" else null,
    )

    private fun ensure(id: String, kind: ContentKind) {
        if (store.value.containsKey(key(id, kind))) return
        store.update { it + (key(id, kind) to seed(id, kind)) }
    }

    override fun content(instanceId: String, kind: ContentKind): Flow<List<ContentFile>> {
        ensure(instanceId, kind)
        return store.map { it[key(instanceId, kind)].orEmpty() }
    }

    fun add(instanceId: String, kind: ContentKind, file: ContentFile) {
        ensure(instanceId, kind)
        store.update { it + (key(instanceId, kind) to (it[key(instanceId, kind)].orEmpty().filterNot { f -> f.projectId == file.projectId } + file)) }
    }

    private val worldStore = mutableMapOf<String, MutableStateFlow<List<World>>>()

    override fun worlds(instanceId: String): Flow<List<World>> = worldStore.getOrPut(instanceId) {
        val now = System.currentTimeMillis()
        val list = if (instanceId == "demo-sky") emptyList() else listOf(
            World("New World", FakeData.t("Долина у реки", "River valley"), now - 7_200_000, 48_000_000, "survival"),
            World("Creative Test", FakeData.t("Творческая площадка", "Creative plot"), now - 5 * 86_400_000L, 12_000_000, "creative"),
            World("Hardcore", FakeData.t("Хардкор", "Hardcore run"), now - 20 * 86_400_000L, 96_000_000, "hardcore"),
        )
        MutableStateFlow(list)
    }

    override suspend fun deleteWorld(instanceId: String, folder: String) {
        worldStore[instanceId]?.update { list -> list.filterNot { it.folder == folder } }
    }

    override suspend fun backupWorld(instanceId: String, folder: String): Path? {
        delay(600)
        return Path.of("backups", "$folder.zip")
    }

    override fun screenshots(instanceId: String): Flow<List<Screenshot>> {
        val now = System.currentTimeMillis()
        val list = if (instanceId == "demo-sky") emptyList() else (0 until 8).map {
            Screenshot("demo://$instanceId/$it", now - it * 86_400_000L, 1920, 1080)
        }
        return MutableStateFlow(list)
    }

    override suspend fun setEnabled(instanceId: String, kind: ContentKind, fileName: String, enabled: Boolean) {
        store.update { it + (key(instanceId, kind) to it[key(instanceId, kind)].orEmpty().map { f -> if (f.fileName == fileName) f.copy(enabled = enabled) else f }) }
    }

    override suspend fun remove(instanceId: String, kind: ContentKind, fileName: String) {
        store.update { it + (key(instanceId, kind) to it[key(instanceId, kind)].orEmpty().filterNot { f -> f.fileName == fileName }) }
    }

    override suspend fun addFiles(instanceId: String, kind: ContentKind, files: List<Path>) {
        files.forEach { p ->
            add(instanceId, kind, ContentFile(fileName = p.fileName.toString(), title = p.fileName.toString().substringBeforeLast('.'), sizeBytes = 0, source = "local"))
        }
    }

    override suspend fun refresh(instanceId: String) = delay(300)
}

class FakeGameVersionRepository : GameVersionRepository {
    override suspend fun versions(forceRefresh: Boolean): List<GameVersion> {
        delay(250)
        return FakeData.versions()
    }
}

class FakeLoaderVersionRepository : LoaderVersionRepository {
    override suspend fun versions(loader: LoaderType, gameVersion: String): List<LoaderVersion> {
        delay(200)
        return when (loader) {
            LoaderType.Vanilla -> emptyList()
            LoaderType.Fabric -> listOf(LoaderVersion("0.18.1", true, latest = true), LoaderVersion("0.17.3", true), LoaderVersion("0.17.2", true), LoaderVersion("0.18.2-beta.1", false))
            LoaderType.Quilt -> listOf(LoaderVersion("0.29.2", true, latest = true), LoaderVersion("0.29.1", true), LoaderVersion("0.30.0-beta.3", false))
            LoaderType.Forge -> listOf(LoaderVersion("59.0.14", true, latest = true), LoaderVersion("59.0.10", true, recommended = true), LoaderVersion("59.0.2", true))
            LoaderType.NeoForge -> listOf(LoaderVersion("21.10.38-beta", false, latest = true), LoaderVersion("21.10.20", true), LoaderVersion("21.10.11", true))
        }
    }
}

class FakeAccountRepository : AccountRepository {
    private val state = MutableStateFlow(FakeData.accounts())
    override val accounts: StateFlow<List<Account>> = state.asStateFlow()
    private val active = MutableStateFlow<String?>(state.value.firstOrNull()?.id)
    override val activeId: StateFlow<String?> = active.asStateFlow()

    // Демо: вход через Microsoft показывает код и ждёт, пока его не отменят (настоящего сервера нет).
    override val microsoftAvailable: Boolean get() = true
    override suspend fun microsoftDeviceCode() = app.youlauncher.core.repo.MicrosoftDeviceCode("F7KQ-29MX", "https://www.microsoft.com/link", "demo", 5, Long.MAX_VALUE)
    override suspend fun loginMicrosoft(code: app.youlauncher.core.repo.MicrosoftDeviceCode): AuthResult = kotlinx.coroutines.awaitCancellation()

    override suspend fun addOffline(nick: String): Account {
        val acc = Account(UUID.randomUUID().toString(), AccountType.Offline, nick, offlineUuid(nick), addedAt = System.currentTimeMillis())
        state.update { it + acc }
        if (active.value == null) active.value = acc.id
        return acc
    }

    override suspend fun loginElyBy(login: String, password: String, totp: String?): AuthResult {
        delay(900)
        return when {
            password.isBlank() -> AuthResult.InvalidCredentials("Invalid credentials. Invalid username or password.")
            password.contains("2fa") && totp.isNullOrBlank() -> AuthResult.TwoFactorRequired
            password == "wrong" -> AuthResult.InvalidCredentials("Invalid credentials. Invalid username or password.")
            else -> {
                val name = login.substringBefore('@').filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "ElyPlayer" }.take(16)
                val acc = Account(UUID.randomUUID().toString(), AccountType.ElyBy, name, UUID.randomUUID().toString(), login = login, addedAt = System.currentTimeMillis())
                state.update { it + acc }
                AuthResult.Success(acc)
            }
        }
    }

    override suspend fun refresh(id: String): Boolean = true

    override suspend fun session(id: String): AccountSession {
        val acc = state.value.first { it.id == id }
        return AccountSession(acc.username, acc.uuid, UUID.randomUUID().toString().replace("-", ""), acc.type)
    }

    override suspend fun remove(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
        if (active.value == id) active.value = state.value.firstOrNull()?.id
    }

    override fun setActive(id: String) {
        active.value = id
        state.update { list -> list.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it } }
    }
}

/** Имитация запуска: проходит стадии подготовки с прогрессом и пишет правдоподобный журнал. */
class FakeLaunchService(
    private val scope: CoroutineScope,
    private val instances: InstanceRepository,
    private val log: InMemoryLogBus,
) : LaunchService {
    private val state = MutableStateFlow<Map<String, LaunchState>>(emptyMap())
    override val states: StateFlow<Map<String, LaunchState>> = state.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    private fun set(id: String, s: LaunchState) = state.update { it + (id to s) }

    override fun launch(instanceId: String, quickJoinServer: String?) {
        if (jobs[instanceId]?.isActive == true) return
        val inst = instances.get(instanceId) ?: return
        jobs[instanceId] = scope.launch {
            log.log(LogLevel.Info, "${FakeData.t("Подготовка экземпляра", "Preparing instance")} «${inst.name}» (${inst.gameVersion}, ${inst.loader.title})", instanceId = instanceId)
            val stages = listOf(LaunchStage.Preparing to 8, LaunchStage.Java to 10, LaunchStage.Libraries to 24, LaunchStage.Assets to 30, LaunchStage.Loader to 10, LaunchStage.Starting to 8)
            for ((stage, steps) in stages) {
                if (stage == LaunchStage.Loader && inst.loader == LoaderType.Vanilla) continue
                for (i in 0..steps) {
                    set(instanceId, LaunchState.Preparing(stage, i / steps.toFloat(), "${i * 7}/${steps * 7}"))
                    delay(55)
                }
                log.log(LogLevel.Debug, "Stage ${stage.name} done", instanceId = instanceId)
            }
            val started = System.currentTimeMillis()
            set(instanceId, LaunchState.Running(pid = 10_000L + instanceId.hashCode().mod(5000), startedAt = started))
            instances.update(instanceId) { it.copy(lastPlayedAt = started, launches = it.launches + 1) }
            val lines = listOf(
                "[Render thread/INFO]: Setting user: Wanderer",
                "[Render thread/INFO]: Backend library: LWJGL version 3.3.3",
                "[Render thread/WARN]: Missing sound for event: demo.ambient",
                "[Render thread/INFO]: Reloading ResourceManager: vanilla",
                "[Worker-Main-2/INFO]: Loaded 1290 recipes",
                "[Render thread/INFO]: Created: 1024x1024x0 atlas",
                "[Server thread/INFO]: Preparing level \"New World\"",
                "[Server thread/INFO]: Done (2.1s)!",
            )
            for (l in lines) {
                val (level, thread, text) = InMemoryLogBus.parseGameLine("[12:00:00] $l")
                log.log(level, text, LogSource.Game, thread, instanceId)
                delay(120)
            }
        }
    }

    override fun stop(instanceId: String) {
        jobs.remove(instanceId)?.cancel()
        val current = state.value[instanceId]
        if (current is LaunchState.Running) {
            val minutes = ((System.currentTimeMillis() - current.startedAt) / 60_000).toInt()
            scope.launch {
                instances.update(instanceId) { it.copy(totalPlayTimeMs = it.totalPlayTimeMs + minutes * 60_000L, lastSessionMs = minutes * 60_000L) }
            }
            log.log(LogLevel.Info, FakeData.t("Игра остановлена", "Game stopped"), instanceId = instanceId)
        }
        set(instanceId, LaunchState.Idle)
    }

    override fun dismiss(instanceId: String) = set(instanceId, LaunchState.Idle)
}

class FakeModCatalog(override val name: String = "Modrinth") : ModCatalog {
    private val all = FakeData.mods()

    override suspend fun search(query: ModQuery): ModSearchPage {
        delay(350)
        val text = query.text.trim().lowercase()
        val filtered = all.filter { it.type == query.type }
            .filter { text.isEmpty() || it.title.lowercase().contains(text) || it.summary.lowercase().contains(text) }
            .filter { query.categories.isEmpty() || it.categories.any { c -> c in query.categories } }
            .filter { query.loader == null || it.loaders.isEmpty() || query.loader.name.lowercase() in it.loaders }
        val sorted = when (query.sort) {
            ModSort.Relevance, ModSort.Downloads -> filtered.sortedByDescending { it.downloads }
            ModSort.Follows -> filtered.sortedByDescending { it.follows }
            ModSort.Newest, ModSort.Updated -> filtered.sortedByDescending { it.updatedAt }
        }
        return ModSearchPage(sorted.drop(query.offset).take(query.limit), sorted.size, query.offset)
    }

    override suspend fun project(id: String): ModProject {
        delay(200)
        return all.first { it.id == id }
    }

    override suspend fun versions(projectId: String, gameVersion: String?, loader: LoaderType?): List<ModVersion> {
        delay(250)
        val p = all.first { it.id == projectId }
        return (0 until 5).map { i ->
            ModVersion(
                id = "$projectId-v$i", projectId = projectId, name = "${p.title} ${3 - i / 2}.${4 - i}.0",
                versionNumber = "${3 - i / 2}.${4 - i}.0", gameVersions = p.gameVersions.drop(i / 2),
                loaders = p.loaders, type = if (i == 0) "beta" else "release",
                publishedAt = System.currentTimeMillis() - i * 12 * 86_400_000L, downloads = 90_000L / (i + 1),
                files = listOf(ModFile("https://example.invalid/${p.slug}.jar", "${p.slug}-$i.jar", null, null, 1_200_000, true)),
                dependencies = if (p.categories.contains("library") || p.type != ProjectType.Mod) emptyList() else listOf(ModDependency("demo-mod-6", null, DependencyKind.Required)),
            )
        }
    }

    override suspend fun categories(type: ProjectType): List<String> =
        all.filter { it.type == type }.flatMap { it.categories }.distinct().sorted()
}

class UnavailableCatalog(override val name: String) : ModCatalog {
    override val available: Boolean = false
    override suspend fun search(query: ModQuery) = ModSearchPage(emptyList(), 0, 0)
    override suspend fun project(id: String): ModProject = error("unavailable")
    override suspend fun versions(projectId: String, gameVersion: String?, loader: LoaderType?) = emptyList<ModVersion>()
    override suspend fun categories(type: ProjectType) = emptyList<String>()
}

class FakeModInstaller(private val scope: CoroutineScope, private val content: FakeContentRepository) : ModInstaller {
    private val state = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    override val progress: StateFlow<Map<String, InstallProgress>> = state.asStateFlow()
    private val installed = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Демо: у каждого второго проекта две выдуманные зависимости, одна из них уже «установлена». */
    override suspend fun preview(instanceId: String, project: ModProject, version: ModVersion?): InstallPreview {
        delay(400)
        val deps = if (project.id.hashCode() % 2 == 0) listOf(
            DependencyInfo("demo-core", "Hearth Core", null, "2.4.1", installed = false),
            DependencyInfo("demo-config", "Tidy Config", null, null, installed = true),
        ) else emptyList()
        val v = version ?: ModVersion("${project.id}-demo", project.id, project.title, "1.0.0", emptyList(), emptyList(), "release", 0, 0, emptyList(), emptyList())
        return InstallPreview(project, v, deps)
    }

    override suspend fun install(instanceId: String, project: ModProject, version: ModVersion?, withDependencies: Boolean): Result<List<String>> {
        for (i in 0..20) {
            state.update { it + (project.id to InstallProgress(project.id, i / 20f)) }
            delay(60)
        }
        state.update { it + (project.id to InstallProgress(project.id, 1f, done = true)) }
        installed.update { it + (instanceId to (it[instanceId].orEmpty() + project.id)) }
        val kind = when (project.type) {
            ProjectType.ResourcePack -> ContentKind.ResourcePacks
            ProjectType.Shader -> ContentKind.ShaderPacks
            ProjectType.DataPack -> ContentKind.DataPacks
            else -> ContentKind.Mods
        }
        content.add(instanceId, kind, ContentFile("${project.slug}.jar", project.title, version?.versionNumber ?: "1.0.0", projectId = project.id, source = "modrinth"))
        return Result.success(listOf(project.id))
    }

    // Установленное: поставленное в демо плюс файлы экземпляра, у которых известен проект.
    override suspend fun installedProjectIds(instanceId: String): Set<String> =
        installed.value[instanceId].orEmpty() + ContentKind.entries.flatMap { k -> content.content(instanceId, k).first().mapNotNull { it.projectId } }

    /** Демо: моды поочерёдно получают «обновится», «подходит», «несовместим», файлы без проекта неизвестны. */
    override suspend fun compatibility(instanceId: String, gameVersion: String, loader: LoaderType): List<ModCompat> {
        delay(500)
        return content.content(instanceId, ContentKind.Mods).first().mapIndexed { i, f ->
            val status = when {
                f.projectId == null -> CompatStatus.Unknown
                loader == LoaderType.Vanilla -> CompatStatus.Incompatible
                else -> listOf(CompatStatus.Update, CompatStatus.Same, CompatStatus.Incompatible)[i % 3]
            }
            val v = if (status == CompatStatus.Update) ModVersion("${f.fileName}-new", f.projectId.orEmpty(), f.title, "$gameVersion-1.0", listOf(gameVersion), listOf(loader.name.lowercase()), "release", 0, 0, emptyList(), emptyList()) else null
            ModCompat(ContentKind.Mods, f.fileName, f.title, status, v, f.projectId)
        }
    }

    override suspend fun migrate(instanceId: String, plan: List<ModCompat>): Int {
        plan.filter { it.status == CompatStatus.Incompatible }.forEach { content.setEnabled(instanceId, it.kind, it.fileName, false) }
        return plan.count { it.status == CompatStatus.Update || it.status == CompatStatus.Incompatible }
    }
}
