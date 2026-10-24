package app.youlauncher.data.servers

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.nbt.Nbt
import app.youlauncher.core.nbt.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Hashtable
import javax.naming.directory.InitialDirContext
import kotlin.io.path.exists

/** Сервер из списка игры (servers.dat). [raw] хранит все поля записи, чтобы не потерять их при сохранении. */
data class ServerEntry(val name: String, val address: String, val raw: Map<String, Tag> = emptyMap())

/** Список серверов игры: .minecraft/servers.dat, NBT без сжатия. */
object ServersFile {
    fun read(gameDir: Path): List<ServerEntry> {
        val file = gameDir.resolve("servers.dat")
        if (!file.exists()) return emptyList()
        val root = DataInputStream(Files.newInputStream(file).buffered()).use { Nbt.readRoot(it) }
        return root.list("servers").mapNotNull { t ->
            val c = t as? Tag.Compound ?: return@mapNotNull null
            val ip = c.str("ip") ?: return@mapNotNull null
            ServerEntry(c.str("name") ?: ip, ip, c.map)
        }
    }

    fun write(gameDir: Path, servers: List<ServerEntry>) {
        val file = gameDir.resolve("servers.dat")
        Files.createDirectories(gameDir)
        val list = servers.map { s -> Tag.Compound(LinkedHashMap(s.raw).apply { put("name", Tag.Str(s.name)); put("ip", Tag.Str(s.address)) }) }
        val bytes = ByteArrayOutputStream().also { b -> DataOutputStream(b).use { Nbt.writeRoot(it, Tag.Compound(mapOf("servers" to Tag.ListTag(10, list)))) } }.toByteArray()
        val tmp = file.resolveSibling("servers.dat.tmp")
        Files.write(tmp, bytes)
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
    }
}

data class ServerStatus(
    val motd: String,
    val online: Int,
    val max: Int,
    val version: String,
    val latencyMs: Long,
    val favicon: ByteArray? = null,
)

/**
 * Опрос сервера по протоколу Server List Ping (как в меню серверов игры): рукопожатие, запрос статуса,
 * затем ping/pong для задержки. Учитывает SRV-запись _minecraft._tcp, порт по умолчанию 25565.
 */
object ServerPing {
    const val DEFAULT_PORT = 25565

    fun parseAddress(address: String): Pair<String, Int?> {
        val a = address.trim()
        if (a.startsWith("[")) {
            val host = a.substringAfter('[').substringBefore(']')
            return host to a.substringAfter("]:", "").toIntOrNull()
        }
        val parts = a.split(':')
        return if (parts.size == 2) parts[0] to parts[1].toIntOrNull() else a to null
    }

    /** SRV-запись _minecraft._tcp.<домен>: на неё ссылаются многие серверы без порта в адресе. */
    fun resolveSrv(host: String): Pair<String, Int>? = runCatching {
        val env = Hashtable<String, String>().apply { put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory") }
        val attrs = InitialDirContext(env).getAttributes("dns:///_minecraft._tcp.$host", arrayOf("SRV"))
        val record = attrs.get("SRV")?.get()?.toString() ?: return@runCatching null
        val p = record.trim().split(' ')
        p[3].trimEnd('.') to p[2].toInt()
    }.getOrNull()

    suspend fun ping(address: String, timeoutMs: Int = 4000): ServerStatus = withContext(Dispatchers.IO) {
        val (host, explicitPort) = parseAddress(address)
        val (targetHost, port) = if (explicitPort != null) host to explicitPort else resolveSrv(host) ?: (host to DEFAULT_PORT)
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(targetHost, port), timeoutMs)
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            out.write(packet(0x00) { d ->
                writeVarInt(d, -1)
                writeString(d, host)
                d.writeShort(port)
                writeVarInt(d, 1)
            })
            out.write(packet(0x00) {})
            out.flush()
            readVarInt(input)
            if (readVarInt(input) != 0x00) throw IOException("Сервер ответил не статусом")
            val json = readString(input)
            val sent = System.nanoTime()
            out.write(packet(0x01) { d -> d.writeLong(sent) })
            out.flush()
            val latency = runCatching {
                readVarInt(input); readVarInt(input); input.readLong()
                (System.nanoTime() - sent) / 1_000_000
            }.getOrDefault(-1L)
            parseStatus(json, latency)
        }
    }

    fun parseStatus(json: String, latency: Long): ServerStatus {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        val players = o["players"] as? JsonObject
        val version = o["version"] as? JsonObject
        val favicon = (o["favicon"] as? JsonPrimitive)?.contentOrNull?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Base64.getMimeDecoder().decode(it) }.getOrNull() }
        return ServerStatus(
            motd = stripFormatting(chatText(o["description"])).trim(),
            online = (players?.get("online") as? JsonPrimitive)?.intOrNull ?: 0,
            max = (players?.get("max") as? JsonPrimitive)?.intOrNull ?: 0,
            version = stripFormatting((version?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty()),
            latencyMs = latency,
            favicon = favicon,
        )
    }

    /** Текст из JSON-компонента чата: строка или объект с text и extra. */
    fun chatText(e: JsonElement?): String = when (e) {
        null -> ""
        is JsonPrimitive -> e.contentOrNull.orEmpty()
        is JsonArray -> e.joinToString("") { chatText(it) }
        is JsonObject -> chatText(e["text"]) + (e["extra"] as? JsonArray)?.joinToString("") { chatText(it) }.orEmpty()
    }

    /** Убирает коды форматирования вида §a. */
    fun stripFormatting(s: String): String = s.replace(Regex("§."), "")

    private inline fun packet(id: Int, body: (DataOutputStream) -> Unit): ByteArray {
        val payload = ByteArrayOutputStream().also { b -> DataOutputStream(b).use { d -> writeVarInt(d, id); body(d) } }.toByteArray()
        return ByteArrayOutputStream().also { b -> DataOutputStream(b).use { d -> writeVarInt(d, payload.size); d.write(payload) } }.toByteArray()
    }

    fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) { out.writeByte(v); return }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    fun readVarInt(input: InputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("Соединение закрыто")
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 35) throw IOException("Слишком длинное VarInt")
        }
    }

    private fun writeString(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val n = readVarInt(input)
        if (n !in 0..(1 shl 21)) throw IOException("Неверная длина строки")
        return String(ByteArray(n).also { input.readFully(it) }, Charsets.UTF_8)
    }
}
