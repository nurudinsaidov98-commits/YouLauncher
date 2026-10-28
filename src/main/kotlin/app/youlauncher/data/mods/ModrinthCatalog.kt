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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.time.Instant

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0
internal fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
internal fun parseTime(s: String?): Long = s?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0

/** Имя загрузчика в API Modrinth и в фасетах. */
val LoaderType.modrinthName: String get() = when (this) {
    // Horizon: Java-моды не работают, а ресурспаки и датапаки — обычные, как у ванильной игры.
    LoaderType.Vanilla, LoaderType.Horizon -> "minecraft"
    LoaderType.Fabric -> "fabric"
    LoaderType.Quilt -> "quilt"
    LoaderType.Forge -> "forge"
    LoaderType.NeoForge -> "neoforge"
}

/** Чистые функции разбора ответов Modrinth API v2 (проверяются тестами на фикстурах). */
object ModrinthParsing {
    fun projectType(s: String?): ProjectType = when (s) {
        "resourcepack" -> ProjectType.ResourcePack
        "shader" -> ProjectType.Shader
        "datapack" -> ProjectType.DataPack
        "modpack" -> ProjectType.Modpack
        else -> ProjectType.Mod
    }

    fun typeFacet(t: ProjectType): String = when (t) {
        ProjectType.Mod -> "mod"
        ProjectType.ResourcePack -> "resourcepack"
        ProjectType.Shader -> "shader"
        ProjectType.DataPack -> "datapack"
        ProjectType.Modpack -> "modpack"
    }

    fun sortIndex(s: ModSort): String = when (s) {
        ModSort.Relevance -> "relevance"
        ModSort.Downloads -> "downloads"
        ModSort.Follows -> "follows"
        ModSort.Newest -> "newest"
        ModSort.Updated -> "updated"
    }

    /**
     * Фасеты поиска: внешний список связан через И, внутренний через ИЛИ.
     * Загрузчик фильтрует только моды и модпаки: у ресурспаков и шейдеров свои «загрузчики» (minecraft, iris).
     */
    fun facets(q: ModQuery): String {
        val groups = mutableListOf(listOf("project_type:${typeFacet(q.type)}"))
        q.gameVersion?.let { groups += listOf("versions:$it") }
        if (q.loader != null && q.loader != LoaderType.Vanilla && q.type in setOf(ProjectType.Mod, ProjectType.Modpack)) {
            groups += listOf("categories:${q.loader.modrinthName}")
        }
        q.categories.forEach { groups += listOf("categories:$it") }
        return JsonArray(groups.map { g -> JsonArray(g.map(::JsonPrimitive)) }).toString()
    }

    fun searchHit(o: JsonObject): ModProject = ModProject(
        id = o.str("project_id") ?: o.str("id").orEmpty(),
        slug = o.str("slug").orEmpty(),
        source = ContentSource.Modrinth,
        type = projectType(o.str("project_type")),
        title = o.str("title").orEmpty(),
        author = o.str("author").orEmpty(),
        summary = o.str("description").orEmpty(),
        downloads = o.long("downloads"),
        follows = o.long("follows"),
        iconUrl = o.str("icon_url")?.takeIf { it.isNotBlank() },
        categories = (o.strings("display_categories").ifEmpty { o.strings("categories") }).filterNot { it in LOADER_TAGS },
        gameVersions = o.strings("versions"),
        loaders = o.strings("categories").filter { it in LOADER_TAGS },
        gallery = o.strings("gallery"),
        updatedAt = parseTime(o.str("date_modified")),
        license = o.str("license"),
    )

    fun search(json: String, offset: Int): ModSearchPage {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        val hits = o["hits"]?.jsonArray.orEmpty().map { searchHit(it.jsonObject) }
        return ModSearchPage(hits, o.long("total_hits").toInt(), o.long("offset").toInt().takeIf { it > 0 } ?: offset)
    }

    fun project(json: String, author: String): ModProject {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        val gallery = o["gallery"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.str("url") }
        return ModProject(
            id = o.str("id").orEmpty(),
            slug = o.str("slug").orEmpty(),
            source = ContentSource.Modrinth,
            type = projectType(o.str("project_type")),
            title = o.str("title").orEmpty(),
            author = author,
            summary = o.str("description").orEmpty(),
            description = o.str("body").orEmpty(),
            downloads = o.long("downloads"),
            follows = o.long("followers"),
            iconUrl = o.str("icon_url")?.takeIf { it.isNotBlank() },
            categories = o.strings("categories") + o.strings("additional_categories"),
            gameVersions = o.strings("game_versions"),
            loaders = o.strings("loaders"),
            gallery = gallery,
            updatedAt = parseTime(o.str("updated")),
            license = (o["license"] as? JsonObject)?.str("id"),
            sourceUrl = o.str("source_url"),
        )
    }

    /** Владелец проекта из /project/{id}/members. */
    fun owner(json: String): String {
        val arr = YouLauncherJson.parseToJsonElement(json) as? JsonArray ?: return ""
        val members = arr.mapNotNull { it as? JsonObject }
        val m = members.firstOrNull { it.str("role").equals("Owner", true) } ?: members.firstOrNull()
        return (m?.get("user") as? JsonObject)?.str("username").orEmpty()
    }

    fun dependencyKind(s: String?): DependencyKind = when (s) {
        "required" -> DependencyKind.Required
        "incompatible" -> DependencyKind.Incompatible
        "embedded" -> DependencyKind.Embedded
        else -> DependencyKind.Optional
    }

    fun version(o: JsonObject): ModVersion = ModVersion(
        id = o.str("id").orEmpty(),
        projectId = o.str("project_id").orEmpty(),
        name = o.str("name").orEmpty(),
        versionNumber = o.str("version_number").orEmpty(),
        gameVersions = o.strings("game_versions"),
        loaders = o.strings("loaders"),
        type = o.str("version_type") ?: "release",
        publishedAt = parseTime(o.str("date_published")),
        downloads = o.long("downloads"),
        files = o["files"]?.jsonArray.orEmpty().map { f ->
            val fo = f.jsonObject
            val hashes = fo["hashes"] as? JsonObject
            ModFile(
                url = fo.str("url").orEmpty(),
                fileName = fo.str("filename").orEmpty(),
                sha1 = hashes?.str("sha1"),
                sha512 = hashes?.str("sha512"),
                size = fo.long("size"),
                primary = (fo["primary"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        },
        dependencies = o["dependencies"]?.jsonArray.orEmpty().map { d ->
            val dobj = d.jsonObject
            ModDependency(dobj.str("project_id"), dobj.str("version_id"), dependencyKind(dobj.str("dependency_type")))
        },
    )

    fun versions(json: String): List<ModVersion> =
        (YouLauncherJson.parseToJsonElement(json) as? JsonArray).orEmpty().map { version(it.jsonObject) }

    /** Ответ /version_files и /version_files/update: хеш -> версия. */
    fun versionsByHash(json: String): Map<String, ModVersion> =
        (YouLauncherJson.parseToJsonElement(json) as? JsonObject).orEmpty().mapValues { version(it.value.jsonObject) }

    /** /tag/category: имена категорий для типа проекта (без заголовков-ресурсов вроде «resolutions»). */
    fun categories(json: String, type: ProjectType): List<String> {
        val facet = typeFacet(type)
        return (YouLauncherJson.parseToJsonElement(json) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("project_type") == facet && it.str("header") == "categories" }
            .mapNotNull { it.str("name") }.distinct().sorted()
    }

    val LOADER_TAGS = setOf("fabric", "quilt", "forge", "neoforge", "liteloader", "modloader", "rift", "minecraft", "iris", "optifine", "canvas", "vanilla", "datapack", "bukkit", "paper", "spigot", "velocity", "bungeecord", "folia", "purpur", "sponge", "waterfall")
}

/** Каталог Modrinth: https://api.modrinth.com/v2, ключ не нужен. */
class ModrinthCatalog(private val http: HttpClient) : ModCatalog {
    override val name = "Modrinth"
    private val categoryCache = mutableMapOf<ProjectType, List<String>>()

    private suspend fun ok(r: HttpResponse): String {
        if (!r.status.isSuccess()) throw IOException("Modrinth: HTTP ${r.status.value}")
        return r.bodyAsText()
    }

    override suspend fun search(query: ModQuery): ModSearchPage {
        val body = ok(http.get("$API/search") {
            if (query.text.isNotBlank()) parameter("query", query.text)
            parameter("facets", ModrinthParsing.facets(query))
            parameter("index", ModrinthParsing.sortIndex(query.sort))
            parameter("offset", query.offset)
            parameter("limit", query.limit)
        })
        return ModrinthParsing.search(body, query.offset)
    }

    override suspend fun project(id: String): ModProject {
        val body = ok(http.get("$API/project/$id"))
        val author = runCatching { ModrinthParsing.owner(ok(http.get("$API/project/$id/members"))) }.getOrDefault("")
        return ModrinthParsing.project(body, author)
    }

    override suspend fun versions(projectId: String, gameVersion: String?, loader: LoaderType?): List<ModVersion> {
        val body = ok(http.get("$API/project/$projectId/version") {
            if (loader != null && loader != LoaderType.Vanilla) parameter("loaders", JsonArray(listOf(JsonPrimitive(loader.modrinthName))).toString())
            if (gameVersion != null) parameter("game_versions", JsonArray(listOf(JsonPrimitive(gameVersion))).toString())
        })
        return ModrinthParsing.versions(body)
    }

    suspend fun version(versionId: String): ModVersion =
        ModrinthParsing.version(YouLauncherJson.parseToJsonElement(ok(http.get("$API/version/$versionId"))).jsonObject)

    override suspend fun categories(type: ProjectType): List<String> = categoryCache.getOrPut(type) {
        ModrinthParsing.categories(ok(http.get("$API/tag/category")), type)
    }

    /** Определяет проекты установленных файлов по SHA-1. */
    suspend fun byHashes(sha1: Collection<String>): Map<String, ModVersion> {
        if (sha1.isEmpty()) return emptyMap()
        val body = buildJsonObject {
            putJsonArray("hashes") { sha1.forEach { add(JsonPrimitive(it)) } }
            put("algorithm", "sha1")
        }
        return ModrinthParsing.versionsByHash(ok(http.post("$API/version_files") { contentType(ContentType.Application.Json); setBody(body.toString()) }))
    }

    /** Последние совместимые версии для установленных файлов (проверка обновлений). */
    suspend fun latestByHashes(sha1: Collection<String>, loader: LoaderType?, gameVersion: String): Map<String, ModVersion> {
        if (sha1.isEmpty()) return emptyMap()
        val body = buildJsonObject {
            putJsonArray("hashes") { sha1.forEach { add(JsonPrimitive(it)) } }
            put("algorithm", "sha1")
            if (loader != null && loader != LoaderType.Vanilla) putJsonArray("loaders") { add(JsonPrimitive(loader.modrinthName)) }
            putJsonArray("game_versions") { add(JsonPrimitive(gameVersion)) }
        }
        return ModrinthParsing.versionsByHash(ok(http.post("$API/version_files/update") { contentType(ContentType.Application.Json); setBody(body.toString()) }))
    }

    companion object {
        const val API = "https://api.modrinth.com/v2"
    }
}

