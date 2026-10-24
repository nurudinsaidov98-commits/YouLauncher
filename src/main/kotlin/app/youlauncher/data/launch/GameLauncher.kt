package app.youlauncher.data.launch

import app.youlauncher.core.AppDirs
import app.youlauncher.core.launch.GameLogParser
import app.youlauncher.core.launch.LaunchAccount
import app.youlauncher.core.launch.LaunchCommandBuilder
import app.youlauncher.core.launch.LaunchSpec
import app.youlauncher.core.maskSecrets
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogSource
import app.youlauncher.core.mojang.JavaVersionRef
import app.youlauncher.core.mojang.RuleEnv
import app.youlauncher.core.mojang.Rules
import app.youlauncher.core.offlineUuid
import app.youlauncher.core.repo.AccountRepository
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.LaunchService
import app.youlauncher.core.repo.LaunchStage
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.settings.SettingsRepository
import app.youlauncher.core.writeTextAtomic
import app.youlauncher.data.java.JavaInstall
import app.youlauncher.data.java.JavaManager
import app.youlauncher.data.java.JavaVersions
import app.youlauncher.data.log.InMemoryLogBus
import app.youlauncher.data.mojang.GameInstaller
import app.youlauncher.data.loaders.LoaderInstaller
import app.youlauncher.data.accounts.AuthlibInjector
import app.youlauncher.core.repo.LoaderVersionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines

/**
 * Настоящий запуск игры: установка версии (файлы Mojang с проверкой SHA-1), подбор Java,
 * сборка команды, процесс с потоковым выводом в консоль, учёт времени игры и определение сбоя.
 */
class GameLauncher(
    private val scope: CoroutineScope,
    private val dirs: AppDirs,
    private val instances: InstanceRepository,
    private val content: InstanceContentRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val installer: GameInstaller,
    private val java: JavaManager,
    private val log: InMemoryLogBus,
    private val loaders: LoaderInstaller,
    private val loaderVersions: LoaderVersionRepository,
    private val injector: AuthlibInjector,
    private val launcherVersion: String = app.youlauncher.core.BuildInfo.version,
) : LaunchService {
    private val state = MutableStateFlow<Map<String, LaunchState>>(emptyMap())
    override val states: StateFlow<Map<String, LaunchState>> = state.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val processes = mutableMapOf<String, Process>()
    private val osVersion: String by lazy { if (RuleEnv.currentOsName() == "windows") Rules.windowsVersion() else System.getProperty("os.version") }

    private fun set(id: String, s: LaunchState) = state.update { it + (id to s) }
    private fun info(id: String, text: String, level: LogLevel = LogLevel.Info) = log.log(level, text, instanceId = id)

    override fun launch(instanceId: String, quickJoinServer: String?) {
        if (jobs[instanceId]?.isActive == true) return
        val instance = instances.get(instanceId) ?: return
        jobs[instanceId] = scope.launch(Dispatchers.IO) {
            try {
                run(instance, quickJoinServer ?: instance.quickJoinServer)
            } catch (e: CancellationException) {
                set(instanceId, LaunchState.Idle)
                throw e
            } catch (e: Throwable) {
                info(instanceId, "Запуск не удался: ${e.message ?: e::class.simpleName}", LogLevel.Error)
                set(instanceId, LaunchState.Failed(e.message ?: e::class.simpleName.orEmpty()))
            } finally {
                processes.remove(instanceId)
            }
        }
    }

    override fun repair(instanceId: String, onDone: (Result<Unit>) -> Unit) {
        if (jobs[instanceId]?.isActive == true) return
        val instance = instances.get(instanceId) ?: return
        jobs[instanceId] = scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val id = instance.id
                info(id, "Починка «${instance.name}»: проверка всех файлов по хешам")
                val gameDir = instances.gameDir(id)
                val env = RuleEnv(osVersion = osVersion)
                val progress: (LaunchStage, Float?, String) -> Unit = { stage, p, detail -> set(id, LaunchState.Preparing(stage, p, detail)) }
                val installed = installer.install(instance.gameVersion, gameDir, env, forceVerify = true, onProgress = progress)
                if (instance.loader != LoaderType.Vanilla) {
                    val java = chooseJava(instance, installed.resolved.json.javaVersion ?: JavaVersionRef()) { p -> set(id, LaunchState.Preparing(LaunchStage.Java, p)) }
                    set(id, LaunchState.Preparing(LaunchStage.Loader, null, instance.loader.title))
                    val loaderVersion = instance.loaderVersion ?: pickLoaderVersion(instance)
                    val versionId = loaders.ensure(instance.loader, instance.gameVersion, loaderVersion, { java.executable }, { info(id, it) }, force = true)
                    installer.install(versionId, gameDir, env, forceVerify = true, onProgress = progress)
                }
                info(id, "Починка завершена")
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it; info(instanceId, "Починка не удалась: ${it.message}", LogLevel.Error) }
            set(instanceId, LaunchState.Idle)
            onDone(result)
        }
    }

    private suspend fun run(instance: Instance, server: String?) {
        val id = instance.id
        val s = settings.settings.value
        set(id, LaunchState.Preparing(LaunchStage.Preparing, null))
        info(id, "Подготовка «${instance.name}» (${instance.gameVersion}, ${instance.loader.title})")
        val gameDir = instances.gameDir(id)
        Files.createDirectories(gameDir)

        val width = instance.width ?: s.game.width
        val height = instance.height ?: s.game.height
        val fullscreen = instance.fullscreen ?: s.game.fullscreen
        val env = RuleEnv(osVersion = osVersion)
        val progress: (LaunchStage, Float?, String) -> Unit = { stage, p, detail -> set(id, LaunchState.Preparing(stage, p, detail)) }
        var installed = installer.install(instance.gameVersion, gameDir, env, onProgress = progress)

        // Java: путь экземпляра, затем общий, затем рантайм Mojang или найденная в системе.
        set(id, LaunchState.Preparing(LaunchStage.Java, null))
        val ref = installed.resolved.json.javaVersion ?: JavaVersionRef()
        val javaInstall = chooseJava(instance, ref) { p -> set(id, LaunchState.Preparing(LaunchStage.Java, p)) }

        // Загрузчик модов ставится поверх готовой ванильной версии: его профиль наследует её (inheritsFrom).
        if (instance.loader != LoaderType.Vanilla) {
            set(id, LaunchState.Preparing(LaunchStage.Loader, null, instance.loader.title))
            val loaderVersion = instance.loaderVersion ?: pickLoaderVersion(instance).also { v ->
                instances.update(id) { it.copy(loaderVersion = v) }
            }
            info(id, "${instance.loader.title} $loaderVersion")
            val versionId = loaders.ensure(instance.loader, instance.gameVersion, loaderVersion, { javaInstall.executable }, log = { info(id, it) })
            installed = installer.install(versionId, gameDir, env, onProgress = progress)
        }
        info(id, "Java ${javaInstall.version} (${javaInstall.source}): ${javaInstall.executable}")

        val account = launchAccount()
        // Ely.by: authlib-injector перенаправляет проверку сессий и скины на ely.by.
        val javaAgent = if (account.userType == "mojang") {
            set(id, LaunchState.Preparing(LaunchStage.Preparing, null, "authlib-injector"))
            AuthlibInjector.javaAgent(injector.ensure()).also { info(id, "authlib-injector: ${it.substringAfter(':').substringBeforeLast('=')}") }
        } else null
        val spec = LaunchSpec(
            version = installed.resolved.json,
            versionName = instance.gameVersion,
            javaExecutable = javaInstall.executable.toString(),
            javaMajor = javaInstall.major,
            classpath = app.youlauncher.core.mojang.VersionResolver.classpath(installed.libraries, dirs.librariesDir, installed.clientJar),
            nativesDir = installed.nativesDir.toString(),
            librariesDir = dirs.librariesDir.toString(),
            gameDir = gameDir.toString(),
            assetsRoot = installed.assetsRoot.toString(),
            assetIndexName = installed.assetIndexName,
            gameAssets = installed.gameAssets.toString(),
            account = account,
            minMemoryMb = instance.java.minMemoryMb ?: s.game.minMemoryMb,
            maxMemoryMb = instance.java.maxMemoryMb ?: s.game.maxMemoryMb,
            preset = s.game.jvmPreset,
            customJvmArgs = listOfNotNull(s.game.jvmArgs.takeIf { it.isNotBlank() }, instance.java.jvmArgs).joinToString(" "),
            width = if (fullscreen) null else width,
            height = if (fullscreen) null else height,
            fullscreen = fullscreen,
            quickJoinServer = server?.takeIf { it.isNotBlank() },
            loggingArgument = installed.loggingArgument,
            wrapperCommand = instance.java.wrapperCommand ?: s.game.wrapperCommand,
            launcherVersion = launcherVersion,
            osVersion = osVersion,
            javaAgent = javaAgent,
        )
        val command = LaunchCommandBuilder.build(spec)
        info(id, "Команда: " + LaunchCommandBuilder.masked(command, account.accessToken), LogLevel.Debug)

        runHook(instance.java.preLaunchCommand ?: s.game.preLaunchCommand, instance, gameDir, "до запуска")

        set(id, LaunchState.Preparing(LaunchStage.Starting, null))
        val pb = ProcessBuilder(command).directory(gameDir.toFile())
        pb.environment().putAll(s.game.env)
        pb.environment().putAll(instance.java.env)
        val process = pb.start()
        processes[id] = process
        val started = System.currentTimeMillis()
        set(id, LaunchState.Running(process.pid(), started))
        info(id, "Игра запущена, PID ${process.pid()}")
        instances.update(id) { it.copy(lastPlayedAt = started, launches = it.launches + 1) }

        val tail = ArrayDeque<String>()
        val readers = listOf(process.inputStream to LogLevel.Info, process.errorStream to LogLevel.Error).map { (stream, fallback) ->
            Thread({ pump(stream, id, fallback, tail) }, "youlauncher-game-log").apply { isDaemon = true; start() }
        }
        val exit = withContext(Dispatchers.IO) { process.waitFor() }
        readers.forEach { it.join(2_000) }

        val minutes = ((System.currentTimeMillis() - started) / 60_000).toInt()
        val today = LocalDate.now().toString()
        instances.update(id) {
            it.copy(
                totalPlayTimeMs = it.totalPlayTimeMs + (System.currentTimeMillis() - started),
                lastSessionMs = System.currentTimeMillis() - started,
                playLog = it.playLog + (today to ((it.playLog[today] ?: 0) + minutes)),
            )
        }
        runHook(instance.java.postExitCommand ?: s.game.postExitCommand, instance, gameDir, "после выхода")
        runCatching { content.refresh(id) }

        val stoppedByUser = state.value[id] is LaunchState.Idle
        if (exit == 0 || stoppedByUser) {
            info(id, "Игра завершилась (код $exit)")
            set(id, LaunchState.Idle)
        } else {
            val report = crashReport(gameDir, started) ?: writeOwnReport(instance, exit, tail)
            val summary = summarize(report)
            info(id, "Игра завершилась с ошибкой, код $exit. Отчёт: $report", LogLevel.Error)
            set(id, LaunchState.Crashed(exit, report.toString(), summary))
        }
    }

    private fun pump(stream: InputStream, id: String, fallback: LogLevel, tail: ArrayDeque<String>) {
        val parser = GameLogParser()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                synchronized(tail) { tail.addLast(raw); while (tail.size > 400) tail.removeFirst() }
                parser.feed(raw).forEach { p ->
                    val level = if (fallback == LogLevel.Error && p.level == LogLevel.Info) LogLevel.Warn else p.level
                    log.log(level, p.text, LogSource.Game, p.thread, id)
                }
            }
        }
    }

    /** Версия загрузчика, если в экземпляре не выбрана: рекомендованная, иначе последняя стабильная. */
    private suspend fun pickLoaderVersion(instance: Instance): String {
        val list = loaderVersions.versions(instance.loader, instance.gameVersion)
        return (list.firstOrNull { it.recommended } ?: list.firstOrNull { it.stable } ?: list.firstOrNull())?.version
            ?: error("${instance.loader.title} не поддерживает версию игры ${instance.gameVersion}")
    }

    private suspend fun chooseJava(instance: Instance, ref: JavaVersionRef, onProgress: (Float?) -> Unit): JavaInstall {
        val s = settings.settings.value
        (instance.java.javaPath ?: s.java.globalJavaPath)?.let { path ->
            return java.inspect(Path.of(path)) ?: error("Выбранная Java не найдена или повреждена: $path")
        }
        java.installedMojang(ref.component)?.let { return it }
        if (s.java.autoDownload) {
            return runCatching { java.ensureMojangRuntime(ref) { p, _ -> onProgress(p) } }.getOrElse { e ->
                java.detect(s.java.extraSearchPaths).firstOrNull { JavaVersions.compatible(ref.majorVersion, it.major) }
                    ?: throw IllegalStateException("Не удалось скачать Java ${ref.majorVersion}: ${e.message}")
            }
        }
        return java.detect(s.java.extraSearchPaths).firstOrNull { JavaVersions.compatible(ref.majorVersion, it.major) }
            ?: error("Нужна Java ${ref.majorVersion}. Включите автоматическую загрузку Java или укажите путь в настройках")
    }

    /**
     * Аккаунт для запуска. Офлайн: ник и стабильный UUID, токен случайный (сервер его не проверяет).
     * Ely.by: токен проверяется и при необходимости обновляется перед запуском.
     */
    private suspend fun launchAccount(): LaunchAccount {
        val acc = accounts.accounts.value.firstOrNull { it.id == accounts.activeId.value } ?: accounts.accounts.value.firstOrNull()
            ?: return LaunchAccount("Player", offlineUuid("Player"), UUID.randomUUID().toString().replace("-", ""), "legacy")
        val session = accounts.session(acc.id)
        return when (session.type) {
            AccountType.ElyBy -> LaunchAccount(session.username, session.uuid, session.accessToken, "mojang")
            AccountType.Offline -> LaunchAccount(session.username, session.uuid, session.accessToken, "legacy")
        }
    }

    private fun runHook(command: String?, instance: Instance, gameDir: Path, label: String) {
        if (command.isNullOrBlank()) return
        info(instance.id, "Команда $label: $command")
        runCatching {
            val pb = ProcessBuilder("cmd.exe", "/c", command).directory(gameDir.toFile()).redirectErrorStream(true)
            pb.environment()["INST_NAME"] = instance.name
            pb.environment()["INST_ID"] = instance.id
            pb.environment()["INST_MC_DIR"] = gameDir.toString()
            pb.environment()["INST_MC_VER"] = instance.gameVersion
            val p = pb.start()
            p.inputStream.bufferedReader().forEachLine { info(instance.id, it) }
            if (!p.waitFor(10, TimeUnit.MINUTES)) p.destroyForcibly()
        }.onFailure { info(instance.id, "Команда $label не выполнилась: ${it.message}", LogLevel.Warn) }
    }

    /** Свежий отчёт игры (crash-reports) или файл hs_err JVM, созданный после старта. */
    private fun crashReport(gameDir: Path, since: Long): Path? {
        val candidates = buildList {
            gameDir.resolve("crash-reports").takeIf { it.isDirectory() }?.listDirectoryEntries("*.txt")?.let(::addAll)
            gameDir.takeIf { it.isDirectory() }?.listDirectoryEntries("hs_err_pid*.log")?.let(::addAll)
        }
        return candidates.filter { it.getLastModifiedTime().toMillis() >= since - 2_000 }.maxByOrNull { it.getLastModifiedTime().toMillis() }
    }

    /** Свой отчёт о сбое: последние строки вывода без секретов. */
    private fun writeOwnReport(instance: Instance, exit: Int, tail: ArrayDeque<String>): Path {
        val name = "${instance.id}-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.txt"
        val file = dirs.crashReportsDir.resolve(name)
        val lines = synchronized(tail) { tail.toList() }
        file.writeTextAtomic(buildString {
            appendLine("YouLauncher: отчёт о сбое")
            appendLine("Экземпляр: ${instance.name} (${instance.gameVersion}, ${instance.loader.title})")
            appendLine("Код выхода: $exit")
            appendLine("Время: ${LocalDateTime.now()}")
            appendLine()
            lines.forEach { appendLine(maskSecrets(it)) }
        })
        return file
    }

    private fun summarize(report: Path): String = runCatching {
        val lines = report.readLines()
        lines.firstOrNull { it.startsWith("Description:") }?.removePrefix("Description:")?.trim()
            ?: lines.lastOrNull { it.contains("Exception") || it.contains("Error") }?.trim()?.take(200)
            ?: report.name
    }.getOrDefault(report.name)

    override fun stop(instanceId: String) {
        val p = processes[instanceId]
        set(instanceId, LaunchState.Idle)
        if (p != null) {
            info(instanceId, "Остановка игры")
            p.descendants().forEach { it.destroy() }
            p.destroy()
            scope.launch(Dispatchers.IO) {
                if (!p.waitFor(5, TimeUnit.SECONDS)) { p.descendants().forEach { it.destroyForcibly() }; p.destroyForcibly() }
            }
        } else {
            jobs.remove(instanceId)?.cancel()
        }
    }

    override fun dismiss(instanceId: String) = set(instanceId, LaunchState.Idle)
}
