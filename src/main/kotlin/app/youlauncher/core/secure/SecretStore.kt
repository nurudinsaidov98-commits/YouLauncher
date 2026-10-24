package app.youlauncher.core.secure

/** Хранилище секретов (ключи API, токены). На Windows это диспетчер учётных данных, в тестах память. */
interface SecretStore {
    fun read(name: String): String?
    fun write(name: String, secret: String)
    fun delete(name: String)
}

class InMemorySecretStore : SecretStore {
    private val map = mutableMapOf<String, String>()
    override fun read(name: String): String? = synchronized(map) { map[name] }
    override fun write(name: String, secret: String) = synchronized(map) { map[name] = secret }
    override fun delete(name: String) { synchronized(map) { map.remove(name) } }
}
