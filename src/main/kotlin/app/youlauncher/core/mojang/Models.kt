package app.youlauncher.core.mojang

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** piston-meta.mojang.com/mc/game/version_manifest_v2.json */
@Serializable
data class VersionManifest(val latest: Latest, val versions: List<ManifestEntry>) {
    @Serializable
    data class Latest(val release: String, val snapshot: String)
}

@Serializable
data class ManifestEntry(
    val id: String,
    val type: String,
    val url: String,
    val time: String = "",
    val releaseTime: String = "",
    val sha1: String? = null,
    val complianceLevel: Int = 0,
)

@Serializable
data class Download(val sha1: String? = null, val size: Long = 0, val url: String, val path: String? = null)

@Serializable
data class Downloads(val client: Download? = null, val server: Download? = null)

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String? = null,
    val size: Long = 0,
    val totalSize: Long = 0,
    val url: String,
)

@Serializable
data class JavaVersionRef(val component: String = "jre-legacy", val majorVersion: Int = 8)

@Serializable
data class OsRule(
    val name: String? = null,
    val version: String? = null,
    val arch: String? = null,
    /** Новый формат (26.x): диапазон версии ОС, например {"min": "10.0.17134"}. */
    val versionRange: VersionRange? = null,
)

@Serializable
data class VersionRange(val min: String? = null, val max: String? = null)

@Serializable
data class Rule(
    val action: String,
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null,
)

/** Элемент arguments.game / arguments.jvm: строка или объект {rules, value}. */
@Serializable(with = ArgumentSerializer::class)
sealed interface Argument {
    data class Plain(val value: String) : Argument
    data class Conditional(val rules: List<Rule>, val values: List<String>) : Argument
}

object ArgumentSerializer : KSerializer<Argument> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): Argument {
        val json = (decoder as JsonDecoder).json
        return when (val e = decoder.decodeJsonElement()) {
            is JsonPrimitive -> Argument.Plain(e.content)
            is JsonObject -> {
                val rules = e["rules"]?.let { json.decodeFromJsonElement<List<Rule>>(it) }.orEmpty()
                val values = when (val v = e["value"]) {
                    is JsonArray -> v.map { it.jsonPrimitive.content }
                    is JsonPrimitive -> listOf(v.content)
                    else -> emptyList()
                }
                Argument.Conditional(rules, values)
            }
            else -> Argument.Plain(e.toString())
        }
    }

    override fun serialize(encoder: Encoder, value: Argument) {
        val json = (encoder as JsonEncoder).json
        val element = when (value) {
            is Argument.Plain -> JsonPrimitive(value.value)
            is Argument.Conditional -> buildJsonObject {
                put("rules", json.encodeToJsonElement(value.rules))
                put("value", JsonArray(value.values.map { JsonPrimitive(it) }))
            }
        }
        encoder.encodeJsonElement(element)
    }
}

@Serializable
data class Arguments(
    val game: List<Argument> = emptyList(),
    val jvm: List<Argument> = emptyList(),
    /** Рекомендованные Mojang аргументы JVM, которые пользователь может переопределить (26.x). */
    @SerialName("default-user-jvm") val defaultUserJvm: List<Argument> = emptyList(),
)

@Serializable
data class LibraryDownloads(
    val artifact: Download? = null,
    val classifiers: Map<String, Download>? = null,
)

@Serializable
data class Extract(val exclude: List<String> = emptyList())

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    /** Базовый адрес Maven (у библиотек загрузчиков модов), если нет downloads. */
    val url: String? = null,
    val natives: Map<String, String>? = null,
    val rules: List<Rule>? = null,
    val extract: Extract? = null,
    /** Хеш у библиотек загрузчиков в формате Fabric/Quilt. */
    val sha1: String? = null,
    val size: Long? = null,
)

@Serializable
data class LoggingFile(val id: String, val sha1: String? = null, val size: Long = 0, val url: String)

@Serializable
data class LoggingConfig(val argument: String, val file: LoggingFile, val type: String = "log4j2-xml")

@Serializable
data class Logging(val client: LoggingConfig? = null)

/** Полный version JSON (vanilla или профиль загрузчика с inheritsFrom). */
@Serializable
data class VersionJson(
    val id: String,
    val inheritsFrom: String? = null,
    val type: String? = null,
    val mainClass: String? = null,
    val minecraftArguments: String? = null,
    val arguments: Arguments? = null,
    val assetIndex: AssetIndexRef? = null,
    val assets: String? = null,
    val downloads: Downloads? = null,
    val libraries: List<Library> = emptyList(),
    val logging: Logging? = null,
    val javaVersion: JavaVersionRef? = null,
    val releaseTime: String? = null,
    val time: String? = null,
    val complianceLevel: Int? = null,
)

@Serializable
data class AssetObject(val hash: String, val size: Long)

@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject>,
    /** Версии до 1.6: ассеты копируются в папку resources экземпляра. */
    @SerialName("map_to_resources") val mapToResources: Boolean = false,
    /** Версии 1.6–1.7: ассеты раскладываются в assets/virtual/<id>. */
    val virtual: Boolean = false,
)

/** Реестр Java от Mojang (all.json): платформа -> компонент -> список сборок. */
typealias JavaRuntimeIndex = Map<String, Map<String, List<JavaRuntimeEntry>>>

@Serializable
data class JavaRuntimeEntry(
    val manifest: Download,
    val version: JavaRuntimeVersion,
)

@Serializable
data class JavaRuntimeVersion(val name: String, val released: String = "")

@Serializable
data class JavaRuntimeManifest(val files: Map<String, JavaRuntimeFile>)

@Serializable
data class JavaRuntimeFile(
    val type: String,
    val executable: Boolean = false,
    val downloads: JavaFileDownloads? = null,
    val target: String? = null,
)

@Serializable
data class JavaFileDownloads(val raw: Download? = null, val lzma: Download? = null)
