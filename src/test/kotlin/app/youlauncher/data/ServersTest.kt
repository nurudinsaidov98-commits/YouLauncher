package app.youlauncher.data

import app.youlauncher.core.nbt.Tag
import app.youlauncher.data.servers.ServerEntry
import app.youlauncher.data.servers.ServerPing
import app.youlauncher.data.servers.ServersFile
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServersTest {
    @Test
    fun `servers dat round trip keeps unknown fields`() {
        val dir = Files.createTempDirectory("srv")
        val icon = Tag.Str("iVBORw0KGgo=")
        ServersFile.write(dir, listOf(ServerEntry("Мой сервер", "play.example.org", mapOf("icon" to icon, "acceptTextures" to Tag.Byte(1))), ServerEntry("Local", "127.0.0.1:25566")))
        val back = ServersFile.read(dir)
        assertEquals(listOf("Мой сервер" to "play.example.org", "Local" to "127.0.0.1:25566"), back.map { it.name to it.address })
        assertEquals(icon, back[0].raw["icon"])
        ServersFile.write(dir, back.drop(1))
        assertEquals(1, ServersFile.read(dir).size)
    }

    @Test
    fun `addresses and chat components`() {
        assertEquals("play.example.org" to null, ServerPing.parseAddress("play.example.org"))
        assertEquals("1.2.3.4" to 25570, ServerPing.parseAddress("1.2.3.4:25570"))
        assertEquals("::1" to 25565, ServerPing.parseAddress("[::1]:25565"))
        val s = ServerPing.parseStatus("""{"version":{"name":"§a1.21.1","protocol":767},"players":{"max":100,"online":7},
            "description":{"text":"Привет, ","extra":[{"text":"§lмир"}]}}""", 42)
        assertEquals("Привет, мир", s.motd)
        assertEquals(7 to 100, s.online to s.max)
        assertEquals("1.21.1", s.version)
    }

    @Test
    fun `server list ping over a real socket`() = runBlocking<Unit> {
        val server = ServerSocket(0)
        val t = thread {
            server.accept().use { sock ->
                val input = DataInputStream(sock.getInputStream())
                val out = DataOutputStream(sock.getOutputStream())
                // Рукопожатие и запрос статуса.
                repeat(2) { input.readNBytes(ServerPing.readVarInt(input)) }
                val json = """{"version":{"name":"Test","protocol":767},"players":{"max":20,"online":3},"description":"Hello"}""".toByteArray()
                out.write(frame { d -> ServerPing.writeVarInt(d, 0); ServerPing.writeVarInt(d, json.size); d.write(json) })
                out.flush()
                // Ping: вернуть то же число.
                val len = ServerPing.readVarInt(input)
                val body = DataInputStream(input.readNBytes(len).inputStream())
                ServerPing.readVarInt(body)
                val payload = body.readLong()
                out.write(frame { d -> ServerPing.writeVarInt(d, 1); d.writeLong(payload) })
                out.flush()
            }
        }
        val status = ServerPing.ping("127.0.0.1:${server.localPort}")
        t.join()
        server.close()
        assertEquals("Hello", status.motd)
        assertEquals(3 to 20, status.online to status.max)
        assertTrue(status.latencyMs >= 0)
    }

    private fun frame(body: (DataOutputStream) -> Unit): ByteArray {
        val payload = ByteArrayOutputStream().also { b -> DataOutputStream(b).use(body) }.toByteArray()
        return ByteArrayOutputStream().also { b -> DataOutputStream(b).use { d -> ServerPing.writeVarInt(d, payload.size); d.write(payload) } }.toByteArray()
    }
}
