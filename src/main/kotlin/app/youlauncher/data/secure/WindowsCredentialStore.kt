package app.youlauncher.data.secure

import app.youlauncher.core.secure.SecretStore
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException

/** CREDENTIALW из wincred.h. */
@Structure.FieldOrder(
    "Flags", "Type", "TargetName", "Comment", "LastWritten", "CredentialBlobSize", "CredentialBlob",
    "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName",
)
class CredentialW : Structure {
    @JvmField var Flags: Int = 0
    @JvmField var Type: Int = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWritten: WinBase.FILETIME = WinBase.FILETIME()
    @JvmField var CredentialBlobSize: Int = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist: Int = 0
    @JvmField var AttributeCount: Int = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null

    constructor() : super()
    constructor(p: Pointer) : super(p) { read() }
}

@Suppress("FunctionName")
private interface CredApi : StdCallLibrary {
    fun CredWriteW(credential: CredentialW, flags: Int): Boolean
    fun CredReadW(target: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
    fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean
    fun CredFree(buffer: Pointer)
}

/**
 * Секреты в диспетчере учётных данных Windows (раздел «Учётные данные Windows», записи YouLauncher:<имя>).
 * Значение хранится в UTF-16LE, как его показывает сама Windows.
 */
class WindowsCredentialStore(
    private val prefix: String = "YouLauncher:",
    private val legacyPrefix: String? = app.youlauncher.core.LegacyNames.NAME + ":",
) : SecretStore {
    private val api: CredApi by lazy { Native.load("Advapi32", CredApi::class.java) }

    /** Запись под прежним именем переносится под новое при первом чтении. */
    override fun read(name: String): String? {
        readTarget(prefix + name)?.let { return it }
        val legacy = legacyPrefix ?: return null
        val old = readTarget(legacy + name) ?: return null
        write(name, old)
        runCatching { deleteTarget(legacy + name) }
        return old
    }

    private fun readTarget(target: String): String? {
        val ref = PointerByReference()
        if (!api.CredReadW(WString(target), CRED_TYPE_GENERIC, 0, ref)) {
            val err = Native.getLastError()
            if (err == ERROR_NOT_FOUND) return null
            throw IOException("Не удалось прочитать учётные данные Windows (код $err)")
        }
        try {
            val cred = CredentialW(ref.value)
            if (cred.CredentialBlobSize == 0 || cred.CredentialBlob == null) return ""
            return String(cred.CredentialBlob!!.getByteArray(0, cred.CredentialBlobSize), Charsets.UTF_16LE)
        } finally {
            api.CredFree(ref.value)
        }
    }

    override fun write(name: String, secret: String) {
        val bytes = secret.toByteArray(Charsets.UTF_16LE)
        val blob = Memory(maxOf(1, bytes.size).toLong()).apply { if (bytes.isNotEmpty()) write(0, bytes, 0, bytes.size) }
        val cred = CredentialW().apply {
            Type = CRED_TYPE_GENERIC
            TargetName = WString(prefix + name)
            UserName = WString("YouLauncher")
            CredentialBlobSize = bytes.size
            CredentialBlob = blob
            Persist = CRED_PERSIST_LOCAL_MACHINE
        }
        if (!api.CredWriteW(cred, 0)) throw IOException("Не удалось сохранить учётные данные Windows (код ${Native.getLastError()})")
    }

    override fun delete(name: String) {
        deleteTarget(prefix + name)
        legacyPrefix?.let { runCatching { deleteTarget(it + name) } }
    }

    private fun deleteTarget(target: String) {
        if (!api.CredDeleteW(WString(target), CRED_TYPE_GENERIC, 0)) {
            val err = Native.getLastError()
            if (err != ERROR_NOT_FOUND) throw IOException("Не удалось удалить учётные данные Windows (код $err)")
        }
    }

    companion object {
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2
        private const val ERROR_NOT_FOUND = 1168

        fun isSupported(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}
