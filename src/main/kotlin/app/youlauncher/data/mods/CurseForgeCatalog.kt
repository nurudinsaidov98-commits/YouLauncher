package app.youlauncher.data.mods

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.model.DependencyKind
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.model.ModDependency
import app.youlauncher.core.model.ModFile
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.repo.ModCatalog
import app.youlauncher.core.repo.ModQuery
import app.youlauncher.core.repo.ModSearchPage
import app.youlauncher.core.repo.ModSort
import app.youlauncher.core.secure.SecretStore
import app.youlauncher.data.secure.BuildSecrets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonArray
import java.io.IOException

/** Чистые функции разбора ответов CurseForge API v1 (проверяются тестами на фикстурах). */
object CurseForgeParsing {
    const val GAME_ID = 432

    fun classId(t: ProjectType): Int = when (t) {
        ProjectType.Mod -> 6
        ProjectType.ResourcePack -> 12
        ProjectType.Shader -> 6552
        ProjectType.DataPack -> 6945
        ProjectType.Modpack -> 4471
    }

    fun projectType(classId: Long): ProjectType = when (classId.toInt()) {
        12 -> ProjectType.ResourcePack
        6552 -> ProjectType.Shader
        6945 -> ProjectType.DataPack
        4471 -> ProjectType.Modpack
        else -> ProjectType.Mod
    }

    fun loaderType(l: LoaderType): Int? = when (l) {
        LoaderType.Forge -> 1
        LoaderType.Fabric -> 4
        LoaderType.Quilt -> 5
        LoaderType.NeoForge -> 6
        LoaderType.Vanilla, LoaderType.Horizon -> null
    }

    fun sortField(s: ModSort): Int = when (s) {
        ModSort.Relevance -> 1
        ModSort.Downloads -> 6
        ModSort.Follows -> 2
        ModSort.Newest -> 11
        ModSort.Updated -> 3
    }

    fun dependencyKind(relation: Long): DependencyKind = when (relation.toInt()) {
        3 -> DependencyKind.Required
        5 -> DependencyKind.Incompatible
        1 -> DependencyKind.Embedded
        else -> DependencyKind.Optional
    }

    private val LOADER_NAMES = mapOf("forge" to "forge", "fabric" to "fabric", "quilt" to "quilt", "neoforge" to "neoforge")

    fun project(o: JsonObject, description: String = ""): ModProject {
        val logo = o["logo"] as? JsonObject
        val links = o["links"] as? JsonObject
        return ModProject(
            id = o.long("id").toString(),
            slug = o.str("slug").orEmpty(),
            source = ContentSource.CurseForge,
            type = projectType(o.long("classId")),
            title = o.str("name").orEmpty(),
            author = (o["authors"] as? JsonArray)?.firstOrNull()?.let { (it as JsonObject).str("name") }.orEmpty(),
            summary = o.str("summary").orEmpty(),
            description = description,
            downloads = o.long("downloadCount"),
            follows = o.long("thumbsUpCount"),
            iconUrl = logo?.str("thumbnailUrl") ?: logo?.str("url"),
            categories = (o["categories"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
            gameVersions = (o["latestFilesIndexes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("gameVersion") }.distinct(),
            gallery = (o["screenshots"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("url") },
            updatedAt = parseTime(o.str("dateModified")),
            sourceUrl = links?.str("sourceUrl"),
        )
    }

    fun search(json: String): ModSearchPage {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        val data = o["data"]?.jsonArray.orEmpty().map { project(it.jsonObject) }
        val p = o["pagination"] as? JsonObject
        return ModSearchPage(data, p?.long("totalCount")?.toInt() ?: data.size, p?.long("index")?.toInt() ?: 0)
    }

    fun file(o: JsonObject): ModVersion {
        val hashes = (o["hashes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val sha1 = hashes.firstOrNull { it.long("algo") == 1L }?.str("value")
        val versions = o.strings("gameVersions")
        val fileName = o.str("fileName").orEmpty()
        val id = o.long("id")
        val url = o.str("downloadUrl") ?: ""
        return ModVersion(
            id = id.toString(),
            projectId = o.long("modId").toString(),
            name = o.str("displayName") ?: fileName,
            versionNumber = o.str("displayName") ?: fileName,
            gameVersions = versions.filter { it.firstOrNull()?.isDigit() == true },
            loaders = versions.mapNotNull { LOADER_NAMES[it.lowercase()] },
            type = when (o.long("releaseType").toInt()) { 2 -> "beta"; 3 -> "alpha"; else -> "release" },
            publishedAt = parseTime(o.str("fileDate")),
            downloads = o.long("downloadCount"),
            files = listOf(ModFile(url, fileName, sha1, null, o.long("fileLength"), primary = true)),
            dependencies = (o["dependencies"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
                ModDependency(it.long("modId").toString(), null, dependencyKind(it.long("relationType")))
            },
        )
    }

    fun files(json: String): List<ModVersion> =
        YouLauncherJson.parseToJsonElement(json).jsonObject["data"]?.jsonArray.orEmpty().map { file(it.jsonObject) }

    fun categories(json: String): List<String> =
        YouLauncherJson.parseToJsonElement(json).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }.distinct().sorted()

}

/** Каталог CurseForge: https://api.curseforge.com/v1, ключ из переменной окружения YOULAUNCHER_CURSEFORGE_KEY. */
class CurseForgeCatalog(private val http: HttpClient, private val apiKey: String, private val retryDelayMs: Long = RETRY_DELAY_MS) : ModCatalog {
    override val name = "CurseForge"
    private val categoryCache = mutableMapOf<ProjectType, List<String>>()
    private val categoryIds = mutableMapOf<String, Long>()

    private fun HttpRequestBuilder.auth() { header("x-api-key", apiKey); header("Accept", "application/json") }

    /**
     * Запрос с повторами. CurseForge время от времени отвечает 403 на верный ключ (проверено: тот же запрос
     * через секунду проходит), поэтому 403, 429 и 5xx повторяются с растущей паузой.
     */
    private suspend fun ok(send: suspend () -> HttpResponse): String {
        var last = 0
        repeat(RETRIES) { attempt ->
            val r = send()
            if (r.status.isSuccess()) return r.bodyAsText()
            last = r.status.value
            if (last != 403 && last != 429 && last < 500) throw IOException("CurseForge: HTTP $last")
            if (attempt < RETRIES - 1) delay(retryDelayMs shl attempt)
        }
        if (last == 403) throw IOException("CurseForge отклонил запрос (HTTP 403). Если это повторяется, проверьте ключ API")
        throw IOException("CurseForge: HTTP $last")
    }

    override suspend fun search(query: ModQuery): ModSearchPage {
        if (query.categories.isNotEmpty() && categoryIds.isEmpty()) categories(query.type)
        val body = ok { http.get("$API/mods/search") {
            auth()
            parameter("gameId", CurseForgeParsing.GAME_ID)
            parameter("classId", CurseForgeParsing.classId(query.type))
            if (query.text.isNotBlank()) parameter("searchFilter", query.text)
            query.gameVersion?.let { parameter("gameVersion", it) }
            if (query.type == ProjectType.Mod || query.type == ProjectType.Modpack) {
                query.loader?.let(CurseForgeParsing::loaderType)?.let { parameter("modLoaderType", it) }
            }
            query.categories.mapNotNull { categoryIds[it] }.takeIf { it.isNotEmpty() }?.let { parameter("categoryIds", it.joinToString(",", "[", "]")) }
            parameter("sortField", CurseForgeParsing.sortField(query.sort))
            parameter("sortOrder", "desc")
            parameter("index", query.offset)
            parameter("pageSize", query.limit.coerceAtMost(50))
        } }
        return CurseForgeParsing.search(body)
    }

    override suspend fun project(id: String): ModProject {
        val o = YouLauncherJson.parseToJsonElement(ok { http.get("$API/mods/$id") { auth() } }).jsonObject["data"]!!.jsonObject
        val description = runCatching {
            YouLauncherJson.parseToJsonElement(ok { http.get("$API/mods/$id/description") { auth() } }).jsonObject.str("data").orEmpty()
        }.getOrDefault("")
        return CurseForgeParsing.project(o, description)
    }

    override suspend fun versions(projectId: String, gameVersion: String?, loader: LoaderType?): List<ModVersion> {
        val body = ok { http.get("$API/mods/$projectId/files") {
            auth()
            gameVersion?.let { parameter("gameVersion", it) }
            loader?.let(CurseForgeParsing::loaderType)?.let { parameter("modLoaderType", it) }
            parameter("pageSize", 50)
        } }
        return CurseForgeParsing.files(body)
    }

    /** Проверка ключа: запрос описания игры. Неверный ключ даёт 403 и понятное исключение. */
    suspend fun checkKey() {
        ok { http.get("$API/games/${CurseForgeParsing.GAME_ID}") { auth() } }
    }

    /** Файлы по id (для импорта модпаков CurseForge). */
    suspend fun filesByIds(ids: List<Long>): List<ModVersion> {
        if (ids.isEmpty()) return emptyList()
        val body = buildJsonObject { putJsonArray("fileIds") { ids.forEach { add(JsonPrimitive(it)) } } }
        return CurseForgeParsing.files(ok { http.post("$API/mods/files") { auth(); contentType(ContentType.Application.Json); setBody(body.toString()) } })
    }

    /** Типы проектов по id (в какую папку класть файлы сборки). */
    suspend fun projectTypes(ids: List<Long>): Map<String, ProjectType> {
        if (ids.isEmpty()) return emptyMap()
        val body = buildJsonObject { putJsonArray("modIds") { ids.distinct().forEach { add(JsonPrimitive(it)) } } }
        val text = ok { http.post("$API/mods") { auth(); contentType(ContentType.Application.Json); setBody(body.toString()) } }
        return CurseForgeParsing.search(text).hits.associate { it.id to it.type }
    }

    override suspend fun categories(type: ProjectType): List<String> = categoryCache.getOrPut(type) {
        val body = ok { http.get("$API/categories") { auth(); parameter("gameId", CurseForgeParsing.GAME_ID); parameter("classId", CurseForgeParsing.classId(type)) } }
        YouLauncherJson.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            o.str("name")?.let { categoryIds[it] = o.long("id") }
        }
        CurseForgeParsing.categories(body)
    }

    companion object {
        const val API = "https://api.curseforge.com/v1"
        const val KEY_ENV = "YOULAUNCHER_CURSEFORGE_KEY"
        private const val RETRIES = 5
        private const val RETRY_DELAY_MS = 700L
    }
}

enum class KeySource { None, Environment, Stored, BuiltIn }

/**
 * Ключ CurseForge по приоритету: переменная окружения YOULAUNCHER_CURSEFORGE_KEY, свой ключ игрока из настроек
 * (диспетчер учётных данных Windows), ключ лаунчера, встроенный в сборку. В файлы и журналы ключ не попадает.
 */
class CurseForgeKey(
    private val store: SecretStore,
    private val env: () -> String? = { System.getenv(CurseForgeCatalog.KEY_ENV) },
    private val builtIn: () -> String? = { BuildSecrets.curseForgeKey },
) {
    private val _source = MutableStateFlow(KeySource.None)
    val source: StateFlow<KeySource> = _source.asStateFlow()

    init { current() }

    fun current(): String? {
        fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
        val candidates = listOf(
            KeySource.Environment to { env().clean() },
            KeySource.Stored to { runCatching { store.read(SECRET_NAME) }.getOrNull().clean() },
            KeySource.BuiltIn to { builtIn().clean() },
        )
        for ((source, read) in candidates) {
            val key = read() ?: continue
            _source.value = source
            return key
        }
        _source.value = KeySource.None
        return null
    }

    fun save(key: String) {
        store.write(SECRET_NAME, key.trim())
        current()
    }

    fun clear() {
        store.delete(SECRET_NAME)
        current()
    }

    companion object {
        const val SECRET_NAME = "curseforge-api-key"
    }
}

/** CurseForge, который включается, как только появляется ключ, без перезапуска. */
class SwitchableCurseForge(private val http: HttpClient, private val key: CurseForgeKey) : ModCatalog {
    override val name = "CurseForge"
    private var cached: Pair<String, CurseForgeCatalog>? = null

    override val available: Boolean get() = key.source.value != KeySource.None

    @Synchronized
    fun catalog(): CurseForgeCatalog? {
        val k = key.current() ?: return null
        cached?.let { (ck, c) -> if (ck == k) return c }
        return CurseForgeCatalog(http, k).also { cached = k to it }
    }

    private fun require(): CurseForgeCatalog = catalog() ?: throw IOException("Для CurseForge нужен ключ API: добавьте его в настройках, раздел «Загрузки»")

    override suspend fun search(query: ModQuery) = require().search(query)
    override suspend fun project(id: String) = require().project(id)
    override suspend fun versions(projectId: String, gameVersion: String?, loader: LoaderType?) = require().versions(projectId, gameVersion, loader)
    override suspend fun categories(type: ProjectType) = require().categories(type)
}
