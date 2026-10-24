package app.youlauncher.data.loaders

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.LoaderVersion
import app.youlauncher.core.repo.LoaderVersionRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** Чистые функции разбора ответов meta API и maven-metadata (проверяются тестами). */
object LoaderParsing {
    /** Fabric /v2/versions/loader/{game} и Quilt /v3/versions/loader/{game}: [{loader: {version, stable}}]. */
    fun fabricLike(json: String, quilt: Boolean): List<LoaderVersion> {
        val arr = YouLauncherJson.parseToJsonElement(json) as? JsonArray ?: return emptyList()
        val list = arr.mapNotNull { e ->
            val loader = (e as? JsonObject)?.get("loader")?.jsonObject ?: return@mapNotNull null
            val v = loader["version"]?.jsonPrimitive?.content ?: return@mapNotNull null
            // У Quilt нет поля stable: нестабильные помечены суффиксом beta/pre/rc.
            val stable = loader["stable"]?.jsonPrimitive?.booleanOrNull ?: !Regex("(?i)(beta|alpha|pre|rc)").containsMatchIn(v)
            LoaderVersion(v, stable)
        }
        val latestStable = list.firstOrNull { it.stable }?.version
        return list.map { it.copy(latest = it == list.first(), recommended = it.version == latestStable && quilt.not()) }
    }

    fun mavenVersions(xml: String): List<String> = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()

    /** Forge: версии вида "1.20.1-47.1.106", для игры отбираются по префиксу "<игра>-". */
    fun forge(xml: String, promotions: String?, game: String): List<LoaderVersion> {
        val promos = promotions?.let { runCatching { YouLauncherJson.parseToJsonElement(it).jsonObject["promos"]?.jsonObject }.getOrNull() }
        val recommended = promos?.get("$game-recommended")?.jsonPrimitive?.content
        val latest = promos?.get("$game-latest")?.jsonPrimitive?.content
        return mavenVersions(xml).filter { it.startsWith("$game-") }.map { it.removePrefix("$game-") }
            .sortedWith(NumericComparator).reversed()
            .map { LoaderVersion(it, stable = true, recommended = it == recommended, latest = it == latest) }
    }

    /** Префикс версий NeoForge для версии игры: 1.21.10 -> "21.10.", 1.21 -> "21.0.", 26.3 -> "26.3.0.", 26.1.2 -> "26.1.2.". */
    fun neoForgePrefix(game: String): String? {
        val p = game.split('.')
        return when {
            p.size >= 2 && p[0] == "1" -> "${p[1]}.${p.getOrElse(2) { "0" }}."
            p.size >= 2 && (p[0].toIntOrNull() ?: 0) >= 25 -> "${p[0]}.${p[1]}.${p.getOrElse(2) { "0" }}."
            else -> null
        }
    }

    fun neoForge(xml: String, game: String): List<LoaderVersion> {
        val prefix = neoForgePrefix(game) ?: return emptyList()
        val list = mavenVersions(xml).filter { it.startsWith(prefix) }.sortedWith(NumericComparator).reversed()
            .map { LoaderVersion(it, stable = !it.contains("beta") && !it.contains("alpha")) }
        val latestStable = list.firstOrNull { it.stable }?.version
        return list.mapIndexed { i, v -> v.copy(latest = i == 0, recommended = v.version == latestStable) }
    }
}

/** Сравнение версий по числовым частям: 47.1.106 > 47.1.99. */
object NumericComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val pa = Regex("\\d+").findAll(a).map { it.value.toLong() }.toList()
        val pb = Regex("\\d+").findAll(b).map { it.value.toLong() }.toList()
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { -1 }).compareTo(pb.getOrElse(i) { -1 })
            if (c != 0) return c
        }
        return a.compareTo(b)
    }
}

/** Версии загрузчиков из официальных источников (meta.fabricmc.net, meta.quiltmc.org, Maven Forge и NeoForge). */
class RemoteLoaderVersions(private val http: HttpClient) : LoaderVersionRepository {
    private val cache = mutableMapOf<String, List<LoaderVersion>>()

    private suspend fun text(url: String): String {
        val r = http.get(url)
        if (!r.status.isSuccess()) throw IOException("HTTP ${r.status.value}: $url")
        return r.bodyAsText()
    }

    override suspend fun versions(loader: LoaderType, gameVersion: String): List<LoaderVersion> {
        val key = "$loader:$gameVersion"
        cache[key]?.let { return it }
        val list = when (loader) {
            LoaderType.Vanilla -> emptyList()
            LoaderType.Fabric -> runCatching { LoaderParsing.fabricLike(text("$FABRIC_META/v2/versions/loader/$gameVersion"), quilt = false) }.getOrDefault(emptyList())
            LoaderType.Quilt -> runCatching { LoaderParsing.fabricLike(text("$QUILT_META/v3/versions/loader/$gameVersion"), quilt = true) }.getOrDefault(emptyList())
            LoaderType.Forge -> LoaderParsing.forge(text(FORGE_METADATA), runCatching { text(FORGE_PROMOTIONS) }.getOrNull(), gameVersion)
            LoaderType.NeoForge -> LoaderParsing.neoForge(text(NEOFORGE_METADATA), gameVersion)
        }
        cache[key] = list
        return list
    }

    companion object {
        const val FABRIC_META = "https://meta.fabricmc.net"
        const val QUILT_META = "https://meta.quiltmc.org"
        const val FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"
        const val FORGE_METADATA = "$FORGE_MAVEN/maven-metadata.xml"
        const val FORGE_PROMOTIONS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
        const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"
        const val NEOFORGE_METADATA = "$NEOFORGE_MAVEN/maven-metadata.xml"
    }
}
