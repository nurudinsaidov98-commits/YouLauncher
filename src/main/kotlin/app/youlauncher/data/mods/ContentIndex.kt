package app.youlauncher.data.mods

import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.readJsonOrNull
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.writeJson
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** Запись об установленном из каталога файле: по ней находятся обновления и проект в каталоге. */
@Serializable
data class IndexedFile(
    val kind: ContentKind,
    /** Имя файла без суффикса .disabled. */
    val fileName: String,
    val source: ContentSource,
    val projectId: String,
    val versionId: String,
    val versionNumber: String = "",
    val title: String = "",
    val sha1: String? = null,
    /** Адрес файла в CDN каталога (для экспорта в .mrpack). */
    val url: String? = null,
    /** Номер более новой версии, если проверка обновлений её нашла. */
    val update: String? = null,
    val updateVersionId: String? = null,
)

@Serializable
data class ContentIndexFile(val schema: Int = 1, val files: List<IndexedFile> = emptyList())

/** Индекс содержимого экземпляра: instances/<id>/content-index.json (рядом с .minecraft). */
class ContentIndex(private val gameDir: (String) -> Path) {
    private val lock = Any()
    private fun file(id: String): Path = gameDir(id).resolveSibling("content-index.json")

    fun read(id: String): List<IndexedFile> = synchronized(lock) {
        file(id).readJsonOrNull(ContentIndexFile.serializer())?.files.orEmpty()
    }

    fun find(id: String, kind: ContentKind, fileName: String): IndexedFile? =
        read(id).firstOrNull { it.kind == kind && it.fileName == fileName.removeSuffix(".disabled") }

    fun update(id: String, transform: (List<IndexedFile>) -> List<IndexedFile>) = synchronized(lock) {
        val next = transform(read(id))
        file(id).writeJson(ContentIndexFile.serializer(), ContentIndexFile(files = next))
    }

    /** Новая запись заменяет прежние записи того же проекта и того же файла. */
    fun put(id: String, entry: IndexedFile) = update(id) { list ->
        list.filterNot { (it.kind == entry.kind && it.fileName == entry.fileName) || (it.source == entry.source && it.projectId == entry.projectId) } + entry
    }

    fun removeFile(id: String, kind: ContentKind, fileName: String) = update(id) { list ->
        list.filterNot { it.kind == kind && it.fileName == fileName.removeSuffix(".disabled") }
    }
}
