package app.youlauncher.data.secure

import java.util.Base64

/**
 * Секреты, встроенные в сборку задачей generateBuildSecrets (build.gradle.kts): ключ CurseForge лаунчера.
 * В репозитории их нет; маска только прячет значение от поиска по тексту, это не шифрование.
 */
object BuildSecrets {
    private val MASK = "youlauncher-build".toByteArray()

    fun encode(value: String): String = Base64.getEncoder().encodeToString(xor(value.toByteArray()))

    fun decode(encoded: String): String? = runCatching { String(xor(Base64.getDecoder().decode(encoded.trim()))) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun xor(bytes: ByteArray) = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor MASK[i % MASK.size].toInt()).toByte() }

    val curseForgeKey: String? by lazy { app.youlauncher.core.BuildInfo.property("curseforge")?.let(::decode) }
}
