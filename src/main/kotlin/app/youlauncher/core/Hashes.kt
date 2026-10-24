package app.youlauncher.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object Hashes {
    fun sha256(file: Path): String = digest(file, "SHA-256")

    fun digest(file: Path, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
