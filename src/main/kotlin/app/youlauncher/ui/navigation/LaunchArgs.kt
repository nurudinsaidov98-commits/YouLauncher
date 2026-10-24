package app.youlauncher.ui.navigation

/**
 * Аргументы запуска YouLauncher.exe:
 * - `--launch <id>` запускает экземпляр (так работает ярлык экземпляра на рабочем столе);
 * - `--open <куда>` открывает экран: home, library, mods, accounts, console, settings,
 *   settings:<раздел> (например settings:Background), instance:<id>, profile.
 */
data class LaunchArgs(val launchInstance: String? = null, val screen: Screen? = null, val action: String? = null) {
    companion object {
        fun parse(args: Array<String>): LaunchArgs {
            var result = LaunchArgs()
            var i = 0
            while (i < args.size) {
                val value = args.getOrNull(i + 1)?.takeIf { !it.startsWith("--") }
                when (args[i]) {
                    "--launch" -> if (value != null) {
                        result = result.copy(launchInstance = value, screen = Screen.Instance(value))
                        i++
                    }
                    "--open" -> if (value != null) {
                        result = open(result, value)
                        i++
                    }
                }
                i++
            }
            return result
        }

        private fun open(base: LaunchArgs, target: String): LaunchArgs {
            val key = target.substringBefore(':').lowercase()
            val rest = target.substringAfter(':', "")
            return when {
                key == "settings" && rest.isNotEmpty() -> base.copy(screen = Screen.Settings, action = "settings:$rest")
                key == "instance" && rest.isNotEmpty() -> base.copy(screen = Screen.Instance(rest))
                key == "profile" -> base.copy(screen = Screen.Home, action = "profile")
                else -> TopLevel.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.let { base.copy(screen = Screen.of(it)) } ?: base
            }
        }
    }
}
