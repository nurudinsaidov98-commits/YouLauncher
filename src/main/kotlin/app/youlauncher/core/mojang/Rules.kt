package app.youlauncher.core.mojang

/** Окружение для вычисления rules: ОС в терминах Mojang (windows/osx/linux), версия ОС, архитектура и фичи. */
data class RuleEnv(
    val osName: String = currentOsName(),
    val osVersion: String = System.getProperty("os.version").orEmpty(),
    val arch: String = currentArch(),
    val features: Set<String> = emptySet(),
) {
    companion object {
        fun currentOsName(): String {
            val n = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                n.startsWith("windows") -> "windows"
                n.startsWith("mac") -> "osx"
                else -> "linux"
            }
        }

        /** x86_64, x86 или arm64, как в rules и classifier natives. */
        fun currentArch(): String = when (System.getProperty("os.arch").orEmpty().lowercase()) {
            "amd64", "x86_64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            "aarch64", "arm64" -> "arm64"
            else -> System.getProperty("os.arch").orEmpty()
        }
    }

    /** Разрядность для подстановки ${arch} в natives старых версий. */
    val bits: String get() = if (arch == "x86") "32" else "64"
}

object Rules {
    /**
     * Алгоритм официального лаунчера: без правил разрешено; иначе идём по правилам по порядку,
     * каждое подходящее правило задаёт результат (allow или disallow), последнее подходящее побеждает.
     */
    fun allowed(rules: List<Rule>?, env: RuleEnv): Boolean {
        if (rules.isNullOrEmpty()) return true
        var result = false
        for (rule in rules) {
            if (matches(rule, env)) result = rule.action == "allow"
        }
        return result
    }

    fun matches(rule: Rule, env: RuleEnv): Boolean {
        rule.os?.let { os ->
            if (os.name != null && os.name != env.osName) return false
            if (os.arch != null && os.arch != env.arch) return false
            if (os.version != null && !Regex(os.version).containsMatchIn(env.osVersion)) return false
            os.versionRange?.let { range ->
                if (range.min != null && compareVersions(env.osVersion, range.min) < 0) return false
                if (range.max != null && compareVersions(env.osVersion, range.max) >= 0) return false
            }
        }
        rule.features?.let { required ->
            for ((name, value) in required) {
                if ((name in env.features) != value) return false
            }
        }
        return true
    }

    /** Сравнение версий вида 10.0.17134 по числам. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** Версия Windows в формате 10.0.<build> (os.version у JVM даёт только "10.0"). */
    fun windowsVersion(): String {
        val base = System.getProperty("os.version").orEmpty()
        val build = runCatching {
            val p = ProcessBuilder("cmd.exe", "/c", "ver").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Regex("""(\d+\.\d+\.\d+)""").find(out)?.groupValues?.get(1)
        }.getOrNull()
        return build ?: base
    }
}

object Features {
    const val DEMO = "is_demo_user"
    const val CUSTOM_RESOLUTION = "has_custom_resolution"
    const val QUICK_PLAY = "has_quick_plays_support"
    const val QUICK_PLAY_SINGLEPLAYER = "is_quick_play_singleplayer"
    const val QUICK_PLAY_MULTIPLAYER = "is_quick_play_multiplayer"
    const val QUICK_PLAY_REALMS = "is_quick_play_realms"
}
