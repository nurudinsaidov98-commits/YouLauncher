package app.youlauncher.data.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Связь с запущенным клиентом Discord по его локальному каналу (Rich Presence), без SDK и библиотек.
 * Кадр: код операции и длина (по 4 байта, little-endian), затем JSON. Ввод-вывод синхронный: на каждую
 * команду Discord отвечает одним кадром, и ответ читается сразу, поэтому канал не переполняется.
 */
class DiscordIpc private constructor(private val pipe: RandomAccessFile) : Closeable {

    /** Ставит активность в профиле или убирает её (null). */
    fun setActivity(activity: JsonObject?) {
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", buildJsonObject {
                put("pid", ProcessHandle.current().pid())
                put("activity", activity ?: JsonNull)
            })
            put("nonce", UUID.randomUUID().toString())
        }
        Frames.write(pipe, Frames.OP_FRAME, payload)
        val reply = readReply()
        if (reply["evt"]?.jsonPrimitive?.contentOrNull == "ERROR") throw IOException("Discord: ${reply["data"]}")
    }

    /** Ответ на команду; на проверку связи (PING) отвечаем сразу и ждём дальше. */
    private fun readReply(): JsonObject {
        while (true) {
            val (op, body) = Frames.read(pipe)
            when (op) {
                Frames.OP_PING -> Frames.write(pipe, Frames.OP_PONG, body)
                Frames.OP_CLOSE -> throw IOException("Discord закрыл соединение: $body")
                else -> return body
            }
        }
    }

    override fun close() = runCatching { pipe.close() }.let { }

    companion object {
        /** Подключается к первому доступному каналу Discord и представляется приложением [appId]. */
        fun connect(appId: String): DiscordIpc {
            var last: Exception? = null
            for (i in 0..9) {
                val pipe = try {
                    RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw")
                } catch (e: Exception) {
                    last = e; continue
                }
                val ipc = DiscordIpc(pipe)
                try {
                    Frames.write(pipe, Frames.OP_HANDSHAKE, buildJsonObject { put("v", 1); put("client_id", appId) })
                    val ready = ipc.readReply()
                    if (ready["evt"]?.jsonPrimitive?.contentOrNull == "READY") return ipc
                    throw IOException("Discord не принял подключение: $ready")
                } catch (e: Exception) {
                    ipc.close(); last = e
                }
            }
            throw IOException("Discord не запущен", last)
        }
    }
}

/** Кадры протокола Discord IPC; отдельно, чтобы проверять кодирование тестами без Discord. */
internal object Frames {
    const val OP_HANDSHAKE = 0
    const val OP_FRAME = 1
    const val OP_CLOSE = 2
    const val OP_PING = 3
    const val OP_PONG = 4

    fun write(out: DataOutput, op: Int, body: JsonElement) {
        val bytes = Json.encodeToString(JsonElement.serializer(), body).encodeToByteArray()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(bytes.size).array()
        out.write(header + bytes)
    }

    fun read(input: DataInput): Pair<Int, JsonObject> {
        val header = ByteArray(8).also { input.readFully(it) }
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val op = buf.int
        val length = buf.int
        if (length !in 0..1_048_576) throw IOException("Странная длина кадра Discord: $length")
        val body = ByteArray(length).also { input.readFully(it) }
        return op to (Json.parseToJsonElement(body.decodeToString()) as? JsonObject ?: JsonObject(emptyMap()))
    }
}
