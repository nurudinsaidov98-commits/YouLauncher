package app.youlauncher.core.profile

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Профиль игрока. Хранится локально; схема версионируется, чтобы позже подключить сервер. */
@Serializable
data class Profile(
    val schema: Int = Profile.SCHEMA,
    val id: String = "",
    val displayName: String = "",
    val nickname: String = "",
    /** Своё изображение аватара; null: аватар генерируется из [avatarSeed]. */
    val avatarImage: String? = null,
    val avatarSeed: String = "",
    /** Показывать голову скина активного аккаунта Ely.by вместо своей картинки и узора. */
    val avatarFromSkin: Boolean = true,
    /** Своё изображение баннера; null: мягкий градиент из цветов темы. */
    val bannerImage: String? = null,
    val updatedAt: Long = 0,
) {
    companion object {
        const val SCHEMA = 1
    }
}

/** Что показывать в аватаре профиля. */
sealed interface AvatarSource {
    data class Skin(val url: String) : AvatarSource
    data class Picture(val path: String) : AvatarSource
    data class Pattern(val seed: String) : AvatarSource

    companion object {
        /**
         * Голова скина Ely.by, если активный аккаунт со скином и показ скина не выключен; затем своя картинка;
         * иначе узор из сида профиля.
         */
        fun choose(profile: Profile, activeSkinUrl: String?): AvatarSource = when {
            profile.avatarFromSkin && activeSkinUrl != null -> Skin(activeSkinUrl)
            profile.avatarImage != null -> Picture(profile.avatarImage)
            else -> Pattern(profile.avatarSeed.ifBlank { profile.id })
        }
    }
}

/** Статистика из локальных данных лаунчера. */
data class ProfileStats(val playMinutes: Long = 0, val instances: Int = 0, val mods: Int = 0)

/**
 * Хранилище профиля. Сейчас реализация локальная (файлы в AppData); позже сюда можно подключить
 * сервер, не трогая экран: он работает только с этим интерфейсом.
 */
interface ProfileRepository {
    val profile: StateFlow<Profile>
    suspend fun save(profile: Profile)
    /** Копирует выбранную картинку в папку профиля и возвращает путь к копии. */
    suspend fun importImage(source: java.nio.file.Path, kind: String): String?
}
