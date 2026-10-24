package app.youlauncher.core.mojang

import java.nio.file.Path

/** Файл для загрузки: адрес, путь назначения, ожидаемые размер и SHA-1. */
data class FileTask(
    val url: String,
    val target: Path,
    val sha1: String?,
    val size: Long = 0,
    /** Для файлов, адресуемых хешем (ассеты): при совпадении размера повторно хеш не считается. */
    val trustExisting: Boolean = false,
)

/** Библиотека после вычисления правил: файл в classpath или архив natives для распаковки. */
data class ResolvedLibrary(
    val coordinate: String,
    val path: String,
    val url: String,
    val sha1: String?,
    val size: Long,
    val native: Boolean,
    val extractExclude: List<String> = emptyList(),
)

/** Maven-координаты group:artifact:version[:classifier][@ext]. */
data class MavenCoordinate(val group: String, val artifact: String, val version: String, val classifier: String?, val extension: String) {
    /** Ключ без версии: по нему убираются дубликаты (версия из профиля загрузчика важнее ванильной). */
    val key: String get() = "$group:$artifact" + (classifier?.let { ":$it" } ?: "")

    fun path(): String {
        val file = "$artifact-$version" + (classifier?.let { "-$it" } ?: "") + ".$extension"
        return group.replace('.', '/') + "/$artifact/$version/$file"
    }

    companion object {
        fun parse(name: String): MavenCoordinate {
            val ext = name.substringAfter('@', "jar")
            val parts = name.substringBefore('@').split(':')
            require(parts.size >= 3) { "Некорректные координаты: $name" }
            return MavenCoordinate(parts[0], parts[1], parts[2], parts.getOrNull(3), ext)
        }
    }
}

object VersionResolver {
    const val MOJANG_LIBRARIES = "https://libraries.minecraft.net/"

    /**
     * Слияние профиля с родителем (inheritsFrom): поля потомка важнее, аргументы складываются,
     * библиотеки потомка идут первыми, дубликаты по group:artifact[:classifier] отбрасываются.
     */
    fun merge(child: VersionJson, parent: VersionJson): VersionJson {
        val libraries = (child.libraries + parent.libraries).distinctBy { lib ->
            runCatching { MavenCoordinate.parse(lib.name).key }.getOrDefault(lib.name)
        }
        val arguments = if (child.arguments == null && parent.arguments == null) null else Arguments(
            game = parent.arguments?.game.orEmpty() + child.arguments?.game.orEmpty(),
            jvm = parent.arguments?.jvm.orEmpty() + child.arguments?.jvm.orEmpty(),
            defaultUserJvm = child.arguments?.defaultUserJvm?.takeIf { it.isNotEmpty() } ?: parent.arguments?.defaultUserJvm.orEmpty(),
        )
        return VersionJson(
            id = child.id,
            inheritsFrom = null,
            type = child.type ?: parent.type,
            mainClass = child.mainClass ?: parent.mainClass,
            minecraftArguments = child.minecraftArguments ?: parent.minecraftArguments,
            arguments = arguments,
            assetIndex = child.assetIndex ?: parent.assetIndex,
            assets = child.assets ?: parent.assets,
            downloads = child.downloads ?: parent.downloads,
            libraries = libraries,
            logging = child.logging ?: parent.logging,
            javaVersion = child.javaVersion ?: parent.javaVersion,
            releaseTime = child.releaseTime ?: parent.releaseTime,
            time = child.time ?: parent.time,
            complianceLevel = child.complianceLevel ?: parent.complianceLevel,
        )
    }

    /** Библиотеки, разрешённые правилами для окружения, с путями и адресами загрузки. */
    fun libraries(version: VersionJson, env: RuleEnv): List<ResolvedLibrary> {
        val result = mutableListOf<ResolvedLibrary>()
        val seen = mutableSetOf<String>()
        for (lib in version.libraries) {
            if (!Rules.allowed(lib.rules, env)) continue
            val coord = runCatching { MavenCoordinate.parse(lib.name) }.getOrNull() ?: continue
            val nativeKey = lib.natives?.get(env.osName)?.replace("\${arch}", env.bits)
            if (nativeKey != null) {
                val d = lib.downloads?.classifiers?.get(nativeKey)
                val path = d?.path ?: coord.copy(classifier = nativeKey).path()
                val url = d?.url ?: ((lib.url ?: MOJANG_LIBRARIES).ensureSlash() + path)
                if (seen.add("native:" + coord.key)) {
                    result += ResolvedLibrary(lib.name, path, url, d?.sha1, d?.size ?: 0, native = true, extractExclude = lib.extract?.exclude.orEmpty())
                }
            }
            // У старых lwjgl-platform/jinput-platform основного артефакта нет (только natives).
            val artifact = lib.downloads?.artifact
            if (artifact == null && lib.downloads != null && nativeKey != null) continue
            if (!seen.add(coord.key)) continue
            val path = artifact?.path ?: coord.path()
            val url = artifact?.url?.takeIf { it.isNotBlank() } ?: ((lib.url ?: MOJANG_LIBRARIES).ensureSlash() + path)
            result += ResolvedLibrary(lib.name, path, url, artifact?.sha1 ?: lib.sha1, artifact?.size ?: lib.size ?: 0, native = false)
        }
        return result
    }

    private fun String.ensureSlash() = if (endsWith('/')) this else "$this/"

    /** Classpath: библиотеки (без natives) и в конце клиентский jar, разделитель ';' на Windows. */
    fun classpath(libraries: List<ResolvedLibrary>, librariesDir: Path, clientJar: Path, separator: String = java.io.File.pathSeparator): String =
        (libraries.filterNot { it.native }.map { librariesDir.resolve(it.path).toString() } + clientJar.toString())
            .distinct()
            .joinToString(separator)

    /** Аргументы из нового формата с учётом правил. */
    fun expand(args: List<Argument>, env: RuleEnv): List<String> = args.flatMap { a ->
        when (a) {
            is Argument.Plain -> listOf(a.value)
            is Argument.Conditional -> if (Rules.allowed(a.rules, env)) a.values else emptyList()
        }
    }

    /** Подстановка ${плейсхолдеров}. Неизвестные плейсхолдеры остаются как есть. */
    fun substitute(arg: String, values: Map<String, String>): String =
        Regex("""\$\{([a-zA-Z0-9_]+)}""").replace(arg) { m -> values[m.groupValues[1]] ?: m.value }

    /**
     * Аргументы игры. Пары с неподставленными значениями (например, --quickPlayPath без значения)
     * выбрасываются, чтобы игра не получила буквальный "${...}".
     */
    fun gameArguments(version: VersionJson, env: RuleEnv, values: Map<String, String>): List<String> {
        val raw = version.arguments?.game?.takeIf { it.isNotEmpty() }?.let { expand(it, env) }
            ?: version.minecraftArguments?.split(' ')?.filter { it.isNotBlank() }
            ?: emptyList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            val a = substitute(raw[i], values)
            val next = raw.getOrNull(i + 1)
            if (a.startsWith("--") && next != null && !next.startsWith("--")) {
                val v = substitute(next, values)
                if (!v.contains("\${")) { out += a; out += v }
                i += 2
            } else {
                if (!a.contains("\${")) out += a
                i++
            }
        }
        return out
    }

    /** Аргументы JVM из version JSON; для старых версий (без arguments.jvm) стандартный набор. */
    fun jvmArguments(version: VersionJson, env: RuleEnv, values: Map<String, String>): List<String> {
        val raw = version.arguments?.jvm?.takeIf { it.isNotEmpty() }?.let { expand(it, env) } ?: legacyJvm(env)
        return raw.map { substitute(it, values) }
    }

    private fun legacyJvm(env: RuleEnv): List<String> = buildList {
        if (env.osName == "windows") add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump")
        if (env.osName == "osx") add("-XstartOnFirstThread")
        if (env.arch == "x86") add("-Xss1M")
        add("-Djava.library.path=\${natives_directory}")
        add("-Dminecraft.launcher.brand=\${launcher_name}")
        add("-Dminecraft.launcher.version=\${launcher_version}")
        add("-cp")
        add("\${classpath}")
    }

    /** Рекомендованные Mojang аргументы JVM без размеров кучи (память задаёт пользователь). */
    fun defaultUserJvm(version: VersionJson, env: RuleEnv): List<String> =
        expand(version.arguments?.defaultUserJvm.orEmpty(), env).filterNot { it.startsWith("-Xms") || it.startsWith("-Xmx") }
}
