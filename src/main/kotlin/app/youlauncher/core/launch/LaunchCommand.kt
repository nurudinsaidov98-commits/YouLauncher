package app.youlauncher.core.launch

import app.youlauncher.core.mojang.Features
import app.youlauncher.core.mojang.RuleEnv
import app.youlauncher.core.mojang.VersionJson
import app.youlauncher.core.mojang.VersionResolver
import app.youlauncher.core.settings.JvmPreset

/** Данные аккаунта для запуска (токен только в памяти). */
data class LaunchAccount(
    val username: String,
    val uuid: String,
    val accessToken: String,
    /** legacy для офлайн, mojang для Yggdrasil (Ely.by через authlib-injector). */
    val userType: String,
)

data class LaunchSpec(
    val version: VersionJson,
    val versionName: String,
    val javaExecutable: String,
    val javaMajor: Int,
    val classpath: String,
    val nativesDir: String,
    val librariesDir: String,
    val gameDir: String,
    val assetsRoot: String,
    val assetIndexName: String,
    val gameAssets: String,
    val account: LaunchAccount,
    val minMemoryMb: Int,
    val maxMemoryMb: Int,
    val preset: JvmPreset = JvmPreset.None,
    val customJvmArgs: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val fullscreen: Boolean = false,
    val quickJoinServer: String? = null,
    val loggingArgument: String? = null,
    /** -javaagent:...=ely.by для authlib-injector. */
    val javaAgent: String? = null,
    /** Запись JFR для статистики нагрузки модов (см. ModProfiler). */
    val profilerArgs: List<String> = emptyList(),
    val wrapperCommand: String = "",
    val launcherName: String = "YouLauncher",
    val launcherVersion: String = app.youlauncher.core.BuildInfo.version,
    val osVersion: String = System.getProperty("os.version").orEmpty(),
)

object JvmPresets {
    val aikar = listOf(
        "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC", "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4", "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
    )

    fun args(preset: JvmPreset, javaMajor: Int): List<String> = when (preset) {
        JvmPreset.None -> emptyList()
        JvmPreset.Aikar -> aikar
        // ZGC есть с Java 15, поколенческий режим включается флагом только в 21–22 (в 23+ он по умолчанию).
        JvmPreset.Zgc -> when {
            javaMajor < 15 -> aikar
            javaMajor in 21..22 -> listOf("-XX:+UseZGC", "-XX:+ZGenerational")
            else -> listOf("-XX:+UseZGC")
        }
        JvmPreset.FastStart -> listOf("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-Xshare:auto", "-XX:+UseStringDeduplication")
    }
}

/** Разбор строки аргументов с учётом кавычек: -Da="b c" -Xss2M. */
fun splitArgs(text: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quote: Char? = null
    for (c in text) {
        when {
            quote != null && c == quote -> quote = null
            quote == null && (c == '"' || c == '\'') -> quote = c
            quote == null && c.isWhitespace() -> { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
            else -> cur.append(c)
        }
    }
    if (cur.isNotEmpty()) out += cur.toString()
    return out
}

object LaunchCommandBuilder {
    fun features(spec: LaunchSpec): Set<String> = buildSet {
        if (spec.width != null && spec.height != null && !spec.fullscreen) add(Features.CUSTOM_RESOLUTION)
        if (spec.quickJoinServer != null) { add(Features.QUICK_PLAY); add(Features.QUICK_PLAY_MULTIPLAYER) }
    }

    fun placeholders(spec: LaunchSpec): Map<String, String> = mapOf(
        "auth_player_name" to spec.account.username,
        "auth_uuid" to spec.account.uuid.replace("-", ""),
        "auth_access_token" to spec.account.accessToken,
        "auth_session" to "token:${spec.account.accessToken}:${spec.account.uuid.replace("-", "")}",
        "auth_xuid" to "0",
        "clientid" to "0",
        "user_type" to spec.account.userType,
        "user_properties" to "{}",
        "version_name" to spec.versionName,
        "version_type" to (spec.version.type ?: "release"),
        "game_directory" to spec.gameDir,
        "assets_root" to spec.assetsRoot,
        "game_assets" to spec.gameAssets,
        "assets_index_name" to spec.assetIndexName,
        "natives_directory" to spec.nativesDir,
        "library_directory" to spec.librariesDir,
        "classpath" to spec.classpath,
        "classpath_separator" to java.io.File.pathSeparator,
        "launcher_name" to spec.launcherName,
        "launcher_version" to spec.launcherVersion,
        "resolution_width" to (spec.width ?: 854).toString(),
        "resolution_height" to (spec.height ?: 480).toString(),
        "quickPlayMultiplayer" to spec.quickJoinServer.orEmpty(),
    ).filterValues { it.isNotEmpty() }

    /** Полная команда запуска (без вывода токена в журнал: см. [masked]). */
    fun build(spec: LaunchSpec): List<String> {
        val env = RuleEnv(osVersion = spec.osVersion, features = features(spec))
        val values = placeholders(spec)
        val version = spec.version
        val cmd = mutableListOf<String>()
        cmd += splitArgs(spec.wrapperCommand)
        cmd += spec.javaExecutable
        cmd += "-Xms${spec.minMemoryMb.coerceAtMost(spec.maxMemoryMb)}M"
        cmd += "-Xmx${spec.maxMemoryMb}M"
        val presetArgs = if (spec.preset == JvmPreset.None) VersionResolver.defaultUserJvm(version, env) else JvmPresets.args(spec.preset, spec.javaMajor)
        cmd += presetArgs
        cmd += splitArgs(spec.customJvmArgs)
        cmd += "-Dfile.encoding=UTF-8"
        // Защита от Log4Shell для старых версий плюс официальный конфиг логирования.
        cmd += "-Dlog4j2.formatMsgNoLookups=true"
        spec.loggingArgument?.let { cmd += it }
        spec.javaAgent?.let { cmd += it }
        cmd += spec.profilerArgs
        cmd += VersionResolver.jvmArguments(version, env, values)
        cmd += version.mainClass ?: error("Нет mainClass")
        val game = VersionResolver.gameArguments(version, env, values).toMutableList()
        if (spec.fullscreen) game += "--fullscreen"
        // Старые версии без quick play: --server/--port.
        val server = spec.quickJoinServer
        if (server != null && Features.QUICK_PLAY_MULTIPLAYER !in usedFeatures(version, env)) {
            val host = server.substringBefore(':')
            val port = server.substringAfter(':', "25565")
            game += listOf("--server", host, "--port", port)
        }
        cmd += game
        return cmd
    }

    private fun usedFeatures(version: VersionJson, env: RuleEnv): Set<String> =
        version.arguments?.game.orEmpty().filterIsInstance<app.youlauncher.core.mojang.Argument.Conditional>()
            .flatMap { it.rules }.flatMap { it.features?.keys.orEmpty() }.toSet().intersect(env.features)

    /** Команда для журнала: токен доступа заменён звёздочками. */
    fun masked(command: List<String>, token: String): String =
        command.joinToString(" ") { arg -> if (token.length > 4 && arg.contains(token)) arg.replace(token, "****") else arg }
}
