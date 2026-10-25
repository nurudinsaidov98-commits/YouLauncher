package app.youlauncher.data.instances

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.BackupSchedule
import app.youlauncher.core.model.Instance
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.LaunchService
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.repo.NewInstanceSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

data class InstanceBackup(val path: Path, val createdAt: Long, val sizeBytes: Long, val scheduled: Boolean)

/**
 * Резервные копии целого экземпляра: zip в backups/<id>/ с файлом экземпляра (youlauncher-instance.json),
 * индексом содержимого, наборами модов и .minecraft без журналов и отчётов о сбоях.
 * Восстановление создаёт новый экземпляр, не трогая текущий.
 */
class InstanceBackups(
    private val instances: InstanceRepository,
    private val backupsDir: Path,
) {
    private val mutex = Mutex()

    private fun dirFor(id: String) = backupsDir.resolve(id)

    suspend fun backup(instanceId: String, scheduled: Boolean = false): Path = mutex.withLock {
        val inst = instances.get(instanceId) ?: throw IOException("Экземпляр не найден")
        withContext(Dispatchers.IO) {
            val instanceDir = instances.gameDir(instanceId).parent
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val target = dirFor(instanceId).resolve("${if (scheduled) "auto" else "instance"}-$stamp.zip")
            Files.createDirectories(target.parent)
            val tmp = target.resolveSibling(target.name + ".part")
            ZipOutputStream(Files.newOutputStream(tmp)).use { zip ->
                zip.putNextEntry(ZipEntry(MARKER))
                zip.write(YouLauncherJson.encodeToString(Instance.serializer(), inst).toByteArray())
                zip.closeEntry()
                Files.walk(instanceDir).use { s ->
                    s.filter { it.isRegularFile() }.forEach { f ->
                        val rel = instanceDir.relativize(f).toString().replace('\\', '/')
                        if (BackupLogic.include(rel)) {
                            zip.putNextEntry(ZipEntry(rel))
                            runCatching { Files.copy(f, zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            target
        }.also { instances.update(instanceId) { it.copy(lastBackupAt = System.currentTimeMillis()) } }
    }

    suspend fun list(instanceId: String): List<InstanceBackup> = withContext(Dispatchers.IO) {
        val dir = dirFor(instanceId)
        if (!dir.isDirectory()) return@withContext emptyList()
        dir.listDirectoryEntries("*.zip").filter { it.name.startsWith("instance-") || it.name.startsWith("auto-") }
            .map { InstanceBackup(it, it.getLastModifiedTime().toMillis(), it.fileSize(), it.name.startsWith("auto-")) }
            .sortedByDescending { it.createdAt }
    }

    /** Восстановление в новый экземпляр с тем же содержимым и настройками; имя получает пометку. */
    suspend fun restore(backup: Path, newName: String): Instance = withContext(Dispatchers.IO) {
        ZipFile(backup.toFile()).use { zip ->
            val meta = zip.getEntry(MARKER) ?: zip.getEntry(LEGACY_MARKER) ?: throw IOException("Это не резервная копия YouLauncher")
            val saved = YouLauncherJson.decodeFromString(Instance.serializer(), zip.getInputStream(meta).use { it.readBytes().decodeToString() })
            val created = instances.create(NewInstanceSpec(newName, saved.gameVersion, saved.loader, saved.loaderVersion, saved.icon, saved.group))
            val instanceDir = instances.gameDir(created.id).parent
            zip.entries().asSequence().filter { !it.isDirectory && it.name != MARKER && it.name != LEGACY_MARKER && it.name != "instance.json" }.forEach { e ->
                val out = instanceDir.resolve(e.name).normalize()
                if (!out.startsWith(instanceDir)) return@forEach
                Files.createDirectories(out.parent)
                zip.getInputStream(e).use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
            }
            instances.update(created.id) {
                it.copy(java = saved.java, atmosphere = saved.atmosphere, tags = saved.tags, accent = saved.accent, width = saved.width, height = saved.height, fullscreen = saved.fullscreen)
            } ?: created
        }
    }

    suspend fun delete(backup: Path) = withContext(Dispatchers.IO) { Files.deleteIfExists(backup) }

    /** Автоматические копии: остаются только последние [keep]. */
    suspend fun prune(instanceId: String, keep: Int = KEEP_AUTO) {
        list(instanceId).filter { it.scheduled }.drop(keep).forEach { delete(it.path) }
    }

    companion object {
        const val MARKER = "youlauncher-instance.json"
        // Резервные копии прежних версий.
        const val LEGACY_MARKER = app.youlauncher.core.LegacyNames.ID + "-instance.json"
        const val KEEP_AUTO = 5
    }
}

object BackupLogic {
    private val skipped = listOf(".minecraft/logs/", ".minecraft/crash-reports/", ".minecraft/.fabric/", ".minecraft/.cache/", ".minecraft/.youlauncher-removed/", ".minecraft/youlauncher-jfr/")

    /** Что входит в копию: всё, кроме журналов, отчётов о сбоях, кэшей загрузчиков и файлов блокировки мира. */
    fun include(relative: String): Boolean = skipped.none { relative.startsWith(it) } && !relative.endsWith("session.lock")

    /** Нужна ли копия по расписанию сейчас. */
    fun due(inst: Instance, now: Long): Boolean =
        inst.backupSchedule != BackupSchedule.Off && now - (inst.lastBackupAt ?: 0L) >= inst.backupSchedule.intervalMs
}

/** Планировщик: раз в полчаса делает копии экземпляров, которым пора, если игра в них не запущена. */
class BackupScheduler(
    private val scope: CoroutineScope,
    private val instances: InstanceRepository,
    private val launcher: LaunchService,
    private val backups: InstanceBackups,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun start() {
        scope.launch(Dispatchers.IO) {
            delay(START_DELAY_MS)
            while (true) {
                runOnce()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    suspend fun runOnce(): Int {
        var made = 0
        for (inst in instances.instances.value) {
            if (!BackupLogic.due(inst, now())) continue
            val state = launcher.state(inst.id)
            if (state is LaunchState.Running || state is LaunchState.Preparing) continue
            runCatching { backups.backup(inst.id, scheduled = true); backups.prune(inst.id) }.onSuccess { made++ }
        }
        return made
    }

    companion object {
        private const val START_DELAY_MS = 60_000L
        private const val CHECK_INTERVAL_MS = 30 * 60_000L
    }
}
