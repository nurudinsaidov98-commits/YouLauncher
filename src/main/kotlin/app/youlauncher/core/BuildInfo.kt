package app.youlauncher.core

import java.util.Properties

/** Сведения о сборке из ресурса youlauncher-build.properties (его пишет задача Gradle generateBuildSecrets). */
object BuildInfo {
    private val props: Properties by lazy {
        Properties().apply { BuildInfo::class.java.getResourceAsStream("/youlauncher-build.properties")?.use { load(it) } }
    }

    /** Версия приложения, одна для jar, установщика и «О программе». */
    val version: String get() = props.getProperty("version") ?: "dev"

    /** Репозиторий GitHub для автообновлений ("владелец/репозиторий"); null: обновления в этой сборке не настроены. */
    val updateRepo: String? get() = props.getProperty("updateRepo")?.takeIf { it.isNotBlank() }
    /** ID приложения Azure для входа через Microsoft (общедоступный клиент, не секрет); null — вход выключен. */
    val microsoftClientId: String? get() = props.getProperty("msClientId")?.takeIf { it.isNotBlank() }

    /** Приложение Discord для активности в профиле (публичный ID, не секрет). */
    val discordAppId: String get() = props.getProperty("discordAppId")?.takeIf { it.isNotBlank() } ?: "1563954573545177099"

    fun property(name: String): String? = props.getProperty(name)
}
