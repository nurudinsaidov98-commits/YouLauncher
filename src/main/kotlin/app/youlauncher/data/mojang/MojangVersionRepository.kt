package app.youlauncher.data.mojang

import app.youlauncher.core.repo.GameVersion
import app.youlauncher.core.repo.GameVersionRepository
import app.youlauncher.core.repo.VersionType
import java.time.OffsetDateTime

/** Список версий игры из официального манифеста Mojang (с кэшем на диске для работы без сети). */
class MojangVersionRepository(private val api: MojangApi) : GameVersionRepository {
    override suspend fun versions(forceRefresh: Boolean): List<GameVersion> =
        api.manifest(forceRefresh).versions.map { e ->
            GameVersion(
                id = e.id,
                type = when (e.type) {
                    "release" -> VersionType.Release
                    "old_beta" -> VersionType.OldBeta
                    "old_alpha" -> VersionType.OldAlpha
                    else -> VersionType.Snapshot
                },
                releaseTime = runCatching { OffsetDateTime.parse(e.releaseTime).toInstant().toEpochMilli() }.getOrDefault(0),
            )
        }
}
