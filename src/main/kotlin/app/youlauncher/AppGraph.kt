package app.youlauncher

import app.youlauncher.core.AppDirs
import app.youlauncher.core.repo.AccountRepository
import app.youlauncher.core.repo.GameVersionRepository
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.LaunchService
import app.youlauncher.core.repo.LoaderVersionRepository
import app.youlauncher.core.repo.ModCatalog
import app.youlauncher.core.repo.ModInstaller
import app.youlauncher.core.settings.AtmospherePresetRepository
import app.youlauncher.core.settings.SettingsRepository
import app.youlauncher.data.fake.FakeAccountRepository
import app.youlauncher.data.fake.FakeContentRepository
import app.youlauncher.data.fake.FakeGameVersionRepository
import app.youlauncher.data.fake.FakeInstanceRepository
import app.youlauncher.data.fake.FakeLaunchService
import app.youlauncher.data.fake.FakeLoaderVersionRepository
import app.youlauncher.data.fake.FakeModCatalog
import app.youlauncher.data.fake.FakeModInstaller
import app.youlauncher.data.fake.UnavailableCatalog
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.download.DownloaderConfig
import app.youlauncher.data.instances.FileContentRepository
import app.youlauncher.data.instances.FileInstanceRepository
import app.youlauncher.data.instances.BackupScheduler
import app.youlauncher.data.system.CacheCleaner
import app.youlauncher.data.system.Updater
import app.youlauncher.core.BuildInfo
import app.youlauncher.core.settings.UpdateChannel
import app.youlauncher.resources.update_available_msg
import app.youlauncher.resources.update_open
import app.youlauncher.data.system.SystemNotifier
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.resources.Res
import app.youlauncher.resources.notify_crash_body
import app.youlauncher.resources.notify_crash_title
import app.youlauncher.ui.brand.LogoPainter
import app.youlauncher.ui.brand.brandLogoColors
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import app.youlauncher.data.instances.InstanceBackups
import app.youlauncher.data.java.AdoptiumInstaller
import app.youlauncher.data.java.JavaManager
import app.youlauncher.data.launch.GameLauncher
import app.youlauncher.data.loaders.LoaderInstaller
import app.youlauncher.data.loaders.RemoteLoaderVersions
import app.youlauncher.data.mods.CatalogModInstaller
import app.youlauncher.data.mods.ContentIndex
import app.youlauncher.data.mods.CurseForgeCatalog
import app.youlauncher.data.mods.CurseForgeKey
import app.youlauncher.data.accounts.AuthlibInjector
import app.youlauncher.data.accounts.ElyByClient
import app.youlauncher.data.accounts.FileAccountRepository
import app.youlauncher.data.mods.SwitchableCurseForge
import app.youlauncher.data.secure.WindowsCredentialStore
import app.youlauncher.core.secure.InMemorySecretStore
import app.youlauncher.core.secure.SecretStore
import app.youlauncher.data.mods.ModrinthCatalog
import app.youlauncher.data.mods.ModSetStore
import app.youlauncher.data.packs.PackExporter
import app.youlauncher.data.packs.PackImporter
import app.youlauncher.data.mojang.GameInstaller
import app.youlauncher.data.mojang.MojangApi
import app.youlauncher.data.mojang.MojangVersionRepository
import app.youlauncher.data.log.InMemoryLogBus
import app.youlauncher.ui.Messenger
import app.youlauncher.ui.images.ImageLoader
import app.youlauncher.data.net.createHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ручная сборка зависимостей приложения. Один экземпляр на процесс. */
class AppGraph(
    val dirs: AppDirs,
    val scope: CoroutineScope,
) {
    val settings = SettingsRepository(dirs.settingsFile, scope)
    val presets = AtmospherePresetRepository(dirs.atmosphereFile)
    val profile: app.youlauncher.core.profile.ProfileRepository = app.youlauncher.data.profile.LocalProfileRepository(dirs.root.resolve("profile"))
    val messenger = Messenger()
    val log = InMemoryLogBus()
    val http = createHttpClient(settings.settings.value.network.proxy)
    val images = ImageLoader(http, dirs.iconsCacheDir)

    // Демо-режим (скриншоты, тесты интерфейса): выдуманные экземпляры и имитация запуска.
    val demo: Boolean = System.getProperty("youlauncher.demo") == "true"

    /** Путь для показа на экране: в демо и на скриншотах вместо настоящей папки (в ней имя пользователя) — условная. */
    fun displayPath(path: java.nio.file.Path): String =
        if (demo) path.toString().replace(dirs.root.toString(), DEMO_ROOT) else path.toString()
    val mojang = MojangApi(http, dirs)
    private val downloaderFactory = {
        val n = settings.settings.value.network
        Downloader(http, DownloaderConfig(parallelism = n.maxConcurrentDownloads, retries = n.retries, speedLimit = n.speedLimitKbps * 1024L, allowedHosts = GAME_HOSTS))
    }
    val java = JavaManager(mojang, dirs, downloaderFactory, demo = demo)
    // Temurin качается с GitHub-релизов Adoptium (ссылки отдаёт api.adoptium.net), хеш sha256 из API.
    val adoptium = AdoptiumInstaller(http, dirs, java, {
        val n = settings.settings.value.network
        Downloader(http, DownloaderConfig(parallelism = 1, retries = n.retries, speedLimit = n.speedLimitKbps * 1024L, allowedHosts = JAVA_HOSTS))
    })
    private val gameInstaller = GameInstaller(mojang, dirs, downloaderFactory)
    private val fakeContent = FakeContentRepository()
    val instances: InstanceRepository = if (demo) FakeInstanceRepository(dirs.instancesDir) else FileInstanceRepository(dirs.instancesDir)
    private val contentIndex = ContentIndex(instances::gameDir)
    val content: InstanceContentRepository = if (demo) fakeContent else FileContentRepository(instances::gameDir, dirs.backupsDir, contentIndex)
    val versions: GameVersionRepository = if (demo) FakeGameVersionRepository() else MojangVersionRepository(mojang)
    /** Horizon (Minecraft на Rust): релизы с GitHub, архив проверяется по SHA-256, server.jar — с серверов Mojang. */
    val horizonReleases = app.youlauncher.data.horizon.HorizonReleases(http)
    private val horizonInstaller = app.youlauncher.data.horizon.HorizonInstaller(
        dirs, horizonReleases,
        githubDownloader = {
            val n = settings.settings.value.network
            Downloader(http, DownloaderConfig(parallelism = 1, retries = n.retries, speedLimit = n.speedLimitKbps * 1024L, allowedHosts = HORIZON_HOSTS))
        },
        mojangDownloader = downloaderFactory,
    )
    val loaderVersions: LoaderVersionRepository = if (demo) FakeLoaderVersionRepository() else RemoteLoaderVersions(http, horizonReleases)
    private val loaderInstaller = LoaderInstaller(http, mojang, dirs, downloaderFactory)
    // Секреты (токены Ely.by, ключ CurseForge) только в диспетчере учётных данных Windows.
    val secrets: SecretStore = if (!demo && WindowsCredentialStore.isSupported()) WindowsCredentialStore() else InMemorySecretStore()
    val accounts: AccountRepository = if (demo) FakeAccountRepository()
        else FileAccountRepository(
            dirs.accountsFile, secrets, ElyByClient(http), scope,
            // Вход через Microsoft включается, только если в сборку встроен ID приложения Azure.
            microsoft = BuildInfo.microsoftClientId?.let { app.youlauncher.data.accounts.MicrosoftAuthClient(http, it) },
        ).also { it.refreshAllInBackground() }
    private val injector = AuthlibInjector(http, dirs.toolsDir, {
        Downloader(http, DownloaderConfig(retries = settings.settings.value.network.retries, allowedHosts = INJECTOR_HOSTS))
    })
    /** Статистика нагрузки модов по сессиям (JFR во время игры). */
    val modProfiler = app.youlauncher.data.profile.ModProfiler(dirs.root.resolve("profiles"))
    val launcher: LaunchService = if (demo) FakeLaunchService(scope, instances, log)
        else GameLauncher(scope, dirs, instances, content, accounts, settings, gameInstaller, java, log, loaderInstaller, loaderVersions, injector, profiler = modProfiler, horizon = horizonInstaller, horizonReleases = horizonReleases)
    private val modrinthCatalog = ModrinthCatalog(http)
    val modrinth: ModCatalog = if (demo) FakeModCatalog("Modrinth") else modrinthCatalog
    val curseForgeKey = CurseForgeKey(secrets)
    private val curseForgeLive = SwitchableCurseForge(http, curseForgeKey)
    val curseForge: ModCatalog = if (!demo) curseForgeLive
        else if (System.getenv(CurseForgeCatalog.KEY_ENV).isNullOrBlank()) UnavailableCatalog("CurseForge") else FakeModCatalog("CurseForge")
    // Моды, ресурспаки и шейдеры скачиваются только с CDN каталогов.
    private val contentDownloader = {
        val n = settings.settings.value.network
        Downloader(http, DownloaderConfig(parallelism = n.maxConcurrentDownloads, retries = n.retries, speedLimit = n.speedLimitKbps * 1024L, allowedHosts = CONTENT_HOSTS))
    }
    val modInstaller: ModInstaller = if (demo) FakeModInstaller(scope, fakeContent)
        else CatalogModInstaller(instances, content, contentIndex, modrinthCatalog, { curseForgeLive.catalog() }, contentDownloader)
    private val packDownloader = {
        val n = settings.settings.value.network
        Downloader(http, DownloaderConfig(parallelism = n.maxConcurrentDownloads, retries = n.retries, speedLimit = n.speedLimitKbps * 1024L, allowedHosts = PACK_HOSTS))
    }
    val packImporter = PackImporter(instances, content, contentIndex, { curseForgeLive.catalog() }, packDownloader)
    /** Сборки из каталога одной кнопкой «Установить». */
    val modpackInstaller: app.youlauncher.data.packs.ModpackInstaller = if (demo) app.youlauncher.data.packs.FakeModpackInstaller(instances)
        else app.youlauncher.data.packs.CatalogModpackInstaller(instances, packImporter, packDownloader, dirs.cacheDir)
    val packExporter = PackExporter(instances, contentIndex)
    val modSets = ModSetStore(instances::gameDir)
    val notifier = SystemNotifier({ settings.settings.value.ui.notifications }) {
        // Значок уведомления: логотип YouLauncher, нарисованный тем же кодом, что и в интерфейсе.
        val bitmap = ImageBitmap(32, 32)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(32f, 32f)) {
            with(LogoPainter(brandLogoColors())) { draw(size) }
        }
        bitmap.toAwtImage()
    }
    val cacheCleaner = CacheCleaner(dirs.cacheDir)
    // Обновления лаунчера только из релизов GitHub репозитория, заданного при сборке, с проверкой sha256.
    private val updateDownloader = Downloader(http, DownloaderConfig(parallelism = 1, retries = settings.settings.value.network.retries, allowedHosts = INJECTOR_HOSTS))
    val updaterProgress = kotlinx.coroutines.flow.MutableStateFlow(0f).also { flow ->
        scope.launch { updateDownloader.progress.collect { flow.value = it.fraction } }
    }
    val updater = Updater(http, BuildInfo.updateRepo, dirs.cacheDir) { updateDownloader }
    val updates = app.youlauncher.data.system.UpdateCenter(updater, updaterProgress, scope, BuildInfo.version,
        exit = { shutdown(); kotlin.system.exitProcess(0) }, openPage = { app.youlauncher.ui.Platform.openUrl(it) })

    /** Открыть «О программе» с найденным обновлением; задаёт окно приложения, где есть навигация. */
    @Volatile var openUpdates: (() -> Unit)? = null
    val instanceBackups = InstanceBackups(instances, dirs.backupsDir)

    init {
        // Классы экранов подгружаются заранее в фоне с низким приоритетом: первый переход не ждёт загрузки классов.
        val classCache = app.youlauncher.data.system.ClassCache(AppDirs.appDirectory(), BuildInfo.version)
        Thread({ Thread.sleep(2_000); classCache.preload() }, "youlauncher-class-preload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
        // Демо: виджет нагрузки модов с примерными данными.
        if (demo) scope.launch { runCatching { app.youlauncher.data.fake.FakeProfiles.seed(modProfiler, instances, content) } }
        if (!demo) {
            val sys = settings.settings.value.system
            if (sys.autoCleanCache) scope.launch { runCatching { cacheCleaner.clean(sys.cacheMaxAgeDays) } }
            // Кэш классов Java собирается в фоне через полминуты после запуска, если его нет или он устарел.
            if (classCache.needsBuild()) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(30_000)
                classCache.build()
            }
            // Установленная копия после автообновления: новая версия в «Приложениях» Windows.
            AppDirs.appDirectory()?.let { appDir ->
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.delay(10_000)
                    runCatching { app.youlauncher.data.system.InstallPlan.refreshVersion(app.youlauncher.data.system.WindowsRegistry, appDir, BuildInfo.version) }
                }
            }
            // Проверка обновлений при запуске: уведомление с кнопкой, которая ведёт прямо к найденному обновлению.
            if (updater.configured && sys.updateChannel != UpdateChannel.Off) scope.launch {
                updates.check(sys.updateChannel)?.let { info ->
                    messenger.show(app.youlauncher.ui.UiMessage(Res.string.update_available_msg, listOf(info.version), actionLabel = Res.string.update_open, action = { openUpdates?.invoke() }))
                }
            }
            // Сбой игры, пока пользователь в другом окне: системное уведомление.
            scope.launch {
                var previous = emptyMap<String, LaunchState>()
                launcher.states.collect { now ->
                    now.forEach { (id, st) ->
                        if (st is LaunchState.Crashed && previous[id] !is LaunchState.Crashed) {
                            val name = instances.get(id)?.name ?: id
                            notifier.notify(getString(Res.string.notify_crash_title), getString(Res.string.notify_crash_body, name), error = true)
                        }
                    }
                    previous = now
                }
            }
        }
    }
    /** Активность в профиле Discord: экран лаунчера и запущенная игра. */
    val discord = app.youlauncher.data.discord.DiscordPresence(
        scope, BuildInfo.discordAppId,
        kotlinx.coroutines.flow.MutableStateFlow(settings.settings.value.discord).also { flow -> scope.launch { settings.settings.collect { flow.value = it.discord } } },
        launcher, instances,
    ).also { if (!demo) it.start() }
    private val backupScheduler = BackupScheduler(scope, instances, launcher, instanceBackups).also { if (!demo) it.start() }

    fun shutdown() {
        settings.saveNow()
        notifier.dispose()
        runCatching { http.close() }
    }

    companion object {
        const val DEMO_ROOT = "C:\\Users\\Player\\AppData\\Roaming\\YouLauncher"
        fun create(dirs: AppDirs = AppDirs.default().ensure()): AppGraph =
            AppGraph(dirs, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

/** Откуда разрешено скачивать игровые файлы: серверы Mojang и Maven-репозитории загрузчиков модов. */
val GAME_HOSTS = setOf("mojang.com", "minecraft.net", "fabricmc.net", "quiltmc.org", "minecraftforge.net", "neoforged.net")

/** Откуда разрешено скачивать моды и пакеты: CDN Modrinth и CurseForge. */
val CONTENT_HOSTS = setOf("modrinth.com", "forgecdn.net")

/** Хосты, разрешённые форматом .mrpack для файлов сборок. */
val PACK_HOSTS = CONTENT_HOSTS + setOf("github.com", "raw.githubusercontent.com", "gitlab.com")

/** authlib-injector скачивается только из релизов GitHub (файлы отдаёт release-assets.githubusercontent.com). */
val INJECTOR_HOSTS = setOf("github.com", "githubusercontent.com")

/** Откуда скачивается Java Temurin: API Adoptium и релизы на GitHub. */
val JAVA_HOSTS = setOf("adoptium.net", "github.com", "githubusercontent.com")

/** Откуда качается Horizon: только релизы GitHub его репозитория (сам архив лежит на githubusercontent.com). */
val HORIZON_HOSTS = setOf("github.com", "githubusercontent.com")
