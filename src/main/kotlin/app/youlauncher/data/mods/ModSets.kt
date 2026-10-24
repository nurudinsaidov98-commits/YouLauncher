package app.youlauncher.data.mods

import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.readJsonOrNull
import app.youlauncher.core.writeJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** Набор модов: какие файлы включены (имена без .disabled). Остальные моды при применении выключаются. */
@Serializable
data class ModSet(val name: String, val enabled: List<String>)

@Serializable
data class ModSetsFile(val schema: Int = 1, val sets: List<ModSet> = emptyList())

object ModSetLogic {
    fun capture(name: String, files: List<ContentFile>): ModSet =
        ModSet(name, files.filter { it.enabled }.map { it.fileName.removeSuffix(".disabled") }.sorted())

    /** Какие файлы переключить, чтобы получить набор: имя файла -> включить или выключить. */
    fun changes(files: List<ContentFile>, set: ModSet): List<Pair<String, Boolean>> {
        val want = set.enabled.toSet()
        return files.mapNotNull { f ->
            val should = f.fileName.removeSuffix(".disabled") in want
            if (should != f.enabled) f.fileName to should else null
        }
    }

    /** Совпадает ли текущее состояние с набором (для отметки активного набора). */
    fun matches(files: List<ContentFile>, set: ModSet): Boolean = changes(files, set).isEmpty()
}

/** Наборы модов экземпляра: instances/<id>/mod-sets.json. */
class ModSetStore(private val gameDir: (String) -> Path) {
    private val flows = mutableMapOf<String, MutableStateFlow<List<ModSet>>>()
    private fun file(id: String) = gameDir(id).resolveSibling("mod-sets.json")

    fun sets(id: String): StateFlow<List<ModSet>> = synchronized(flows) {
        flows.getOrPut(id) { MutableStateFlow(file(id).readJsonOrNull(ModSetsFile.serializer())?.sets.orEmpty()) }.asStateFlow()
    }

    private fun write(id: String, transform: (List<ModSet>) -> List<ModSet>) = synchronized(flows) {
        val flow = flows.getOrPut(id) { MutableStateFlow(file(id).readJsonOrNull(ModSetsFile.serializer())?.sets.orEmpty()) }
        val next = transform(flow.value)
        file(id).writeJson(ModSetsFile.serializer(), ModSetsFile(sets = next))
        flow.value = next
    }

    /** Сохраняет набор; набор с тем же именем заменяется. */
    fun save(id: String, set: ModSet) = write(id) { list -> list.filterNot { it.name == set.name } + set }

    fun delete(id: String, name: String) = write(id) { list -> list.filterNot { it.name == name } }
}
