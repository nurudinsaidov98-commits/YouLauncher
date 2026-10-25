package app.youlauncher

import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.LaunchStage
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.settings.DiscordSettings
import app.youlauncher.data.discord.Frames
import app.youlauncher.data.discord.PresenceBuilder
import app.youlauncher.data.discord.PresenceScreen
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscordPresenceTest {
    private val pack = Instance(id = "pack", name = "Simply Optimized", gameVersion = "1.21.10", loader = LoaderType.NeoForge)
    private val vanilla = Instance(id = "van", name = "My world", gameVersion = "1.21.10")

    /** Вместо текстов — ключ строки и аргументы: проверяется выбор, а не перевод. */
    private fun build(screen: PresenceScreen, states: Map<String, LaunchState> = emptyMap(), names: Boolean = true): JsonObject = runBlocking {
        PresenceBuilder.build(PresenceBuilder.Input(DiscordSettings(showNames = names), screen, states, listOf(pack, vanilla), 1_000_000L)) { r, args ->
            (listOf(r.key) + args.map { it.toString() }).joinToString("|")
        }
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `frames are little endian with json body`() {
        val out = ByteArrayOutputStream()
        Frames.write(DataOutputStream(out), Frames.OP_FRAME, buildJsonObject { put("cmd", "X") })
        val bytes = out.toByteArray()
        assertEquals(listOf<Byte>(1, 0, 0, 0), bytes.take(4))
        assertEquals(bytes.size - 8, bytes[4].toInt())
        val (op, body) = Frames.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(Frames.OP_FRAME, op)
        assertEquals("X", body.str("cmd"))
    }

    @Test
    fun `playing a modpack shows its name, loader and session start`() {
        val a = build(PresenceScreen.Library, mapOf("pack" to LaunchState.Running(1, 5_000_000L)))
        assertEquals("discord_playing|Simply Optimized", a.str("details"))
        assertEquals("discord_state_loader|NeoForge|1.21.10", a.str("state"))
        assertEquals(5_000L, a["timestamps"]!!.jsonObject["start"]!!.jsonPrimitive.long)
        assertEquals(PresenceBuilder.Assets.PLAY, a["assets"]!!.jsonObject.str("small_image"))
    }

    @Test
    fun `vanilla and hidden names show only the game version`() {
        assertEquals("discord_playing_vanilla|1.21.10", build(PresenceScreen.Home, mapOf("van" to LaunchState.Running(1, 0))).str("details"))
        val hidden = build(PresenceScreen.Instance("pack"), mapOf("pack" to LaunchState.Running(1, 0)), names = false)
        assertEquals("discord_playing_vanilla|1.21.10", hidden.str("details"))
        assertFalse(hidden.toString().contains("Simply Optimized"))
        assertFalse(build(PresenceScreen.Mods("pack"), names = false).toString().contains("Simply Optimized"))
    }

    @Test
    fun `screens map to their texts`() {
        assertEquals("discord_library", build(PresenceScreen.Library).str("details"))
        assertEquals("discord_library_count|2", build(PresenceScreen.Library).str("state"))
        assertEquals("discord_mods", build(PresenceScreen.Mods(null)).str("details"))
        assertNull(build(PresenceScreen.Mods(null)).str("state"))
        assertEquals("discord_mods_for|Simply Optimized", build(PresenceScreen.Mods("pack")).str("state"))
        assertEquals("discord_preparing|Simply Optimized", build(PresenceScreen.Home, mapOf("pack" to LaunchState.Preparing(LaunchStage.Assets, null))).str("details"))
    }

    @Test
    fun `strings fit discord limits`() {
        assertEquals(32, PresenceBuilder.fit("x".repeat(50), 32).length)
        assertTrue(PresenceBuilder.fit("x".repeat(50), 32).endsWith("…"))
        assertEquals(2, PresenceBuilder.fit("a", 128).length)
    }
}
