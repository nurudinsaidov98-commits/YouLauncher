package app.youlauncher.data

import app.youlauncher.core.model.BackupSchedule
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.LaunchService
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.data.instances.BackupLogic
import app.youlauncher.data.instances.BackupScheduler
import app.youlauncher.data.instances.FileInstanceRepository
import app.youlauncher.data.instances.InstanceBackups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupsTest {
    private class StubLauncher : LaunchService {
        override val states = MutableStateFlow<Map<String, LaunchState>>(emptyMap())
        override fun launch(instanceId: String, quickJoinServer: String?) = Unit
        override fun stop(instanceId: String) = Unit
        override fun dismiss(instanceId: String) = Unit
    }

    @Test
    fun `backup skips logs and restores into a new instance`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("bk")
        val repo = FileInstanceRepository(root.resolve("instances"))
        val backups = InstanceBackups(repo, root.resolve("backups"))
        val inst = repo.create(NewInstanceSpec("Мир", "1.21.1", LoaderType.Fabric, "0.16.5"))
        repo.update(inst.id) { it.copy(tags = listOf("t"), accent = 0xFF3F7D4EL) }
        val game = repo.gameDir(inst.id)
        Files.createDirectories(game.resolve("saves/w1")); Files.writeString(game.resolve("saves/w1/level.dat"), "lvl")
        Files.writeString(game.resolve("saves/w1/session.lock"), "lock")
        Files.createDirectories(game.resolve("logs")); Files.writeString(game.resolve("logs/latest.log"), "log")
        Files.writeString(game.resolve("options.txt"), "fov:90")

        val zip = backups.backup(inst.id)
        assertNotNull(repo.get(inst.id)!!.lastBackupAt)
        ZipFile(zip.toFile()).use { z ->
            assertNotNull(z.getEntry(InstanceBackups.MARKER))
            assertNotNull(z.getEntry(".minecraft/saves/w1/level.dat"))
            assertNull(z.getEntry(".minecraft/logs/latest.log"))
            assertNull(z.getEntry(".minecraft/saves/w1/session.lock"))
        }
        assertEquals(1, backups.list(inst.id).size)

        val restored = backups.restore(zip, "Мир (копия)")
        assertNotEquals(inst.id, restored.id)
        assertEquals(LoaderType.Fabric to "0.16.5", restored.loader to restored.loaderVersion)
        assertEquals(listOf("t"), restored.tags)
        assertEquals("fov:90", repo.gameDir(restored.id).resolve("options.txt").readText())
        assertTrue(repo.gameDir(restored.id).resolve("saves/w1/level.dat").exists())
        assertTrue(repo.gameDir(inst.id).resolve("options.txt").exists(), "Исходный экземпляр не тронут")
    }

    @Test
    fun `schedule makes due backups, skips running games and keeps the last five`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("bk")
        val repo = FileInstanceRepository(root.resolve("instances"))
        val backups = InstanceBackups(repo, root.resolve("backups"))
        val launcher = StubLauncher()
        var now = 10L * 24 * 3600 * 1000
        val scheduler = BackupScheduler(CoroutineScope(Dispatchers.Unconfined), repo, launcher, backups) { now }
        val a = repo.create(NewInstanceSpec("A", "1.21.1"))
        val b = repo.create(NewInstanceSpec("B", "1.21.1"))
        repo.update(a.id) { it.copy(backupSchedule = BackupSchedule.Daily) }
        repo.update(b.id) { it.copy(backupSchedule = BackupSchedule.Daily) }
        launcher.states.value = mapOf(b.id to LaunchState.Running(1234, startedAt = 0))

        assertEquals(1, scheduler.runOnce(), "Запущенная игра не копируется")
        assertFalse(BackupLogic.due(repo.get(a.id)!!.copy(lastBackupAt = now), now))
        repeat(6) { backups.backup(a.id, scheduled = true); Thread.sleep(1100) }
        backups.prune(a.id)
        assertEquals(5, backups.list(a.id).count { it.scheduled })
        assertTrue(BackupLogic.due(repo.get(a.id)!!.copy(lastBackupAt = now - BackupSchedule.Daily.intervalMs), now))
        assertFalse(BackupLogic.due(repo.get(a.id)!!.copy(backupSchedule = BackupSchedule.Off, lastBackupAt = null), now))
    }
}
