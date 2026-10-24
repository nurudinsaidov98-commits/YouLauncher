package app.youlauncher.data.accounts

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.Account
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.offlineUuid
import app.youlauncher.core.readJsonOrNull
import app.youlauncher.core.repo.AccountRepository
import app.youlauncher.core.repo.AccountSession
import app.youlauncher.core.repo.AuthResult
import app.youlauncher.core.repo.ReauthRequiredException
import app.youlauncher.core.secure.SecretStore
import app.youlauncher.core.writeJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.UUID

@Serializable
data class AccountsFile(val schema: Int = 1, val accounts: List<Account> = emptyList(), val activeId: String? = null)

/** UUID Ely.by приходит без дефисов: 32 hex-символа превращаются в обычную запись UUID. */
fun dashedUuid(raw: String): String =
    if (raw.length == 32 && !raw.contains('-')) "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}" else raw

/**
 * Аккаунты: список без секретов в accounts.json, токены Ely.by только в хранилище секретов
 * (диспетчер учётных данных Windows) под именем ely-session:<id>. Пароль нигде не хранится.
 */
class FileAccountRepository(
    private val file: Path,
    private val secrets: SecretStore,
    private val ely: ElyByClient,
    private val scope: CoroutineScope,
) : AccountRepository {
    private val stored = file.readJsonOrNull(AccountsFile.serializer()) ?: AccountsFile()
    // Ссылки на скины из прежних версий были по http: переводим на https, иначе голова скина не загрузится.
    private val state = MutableStateFlow(stored.accounts.map { it.copy(skinUrl = ElyByClient.secureTextureUrl(it.skinUrl), capeUrl = ElyByClient.secureTextureUrl(it.capeUrl)) })
    override val accounts: StateFlow<List<Account>> = state.asStateFlow()
    private val active = MutableStateFlow(stored.activeId ?: stored.accounts.firstOrNull()?.id)
    override val activeId: StateFlow<String?> = active.asStateFlow()
    private val mutex = Mutex()

    private fun persist() = file.writeJson(AccountsFile.serializer(), AccountsFile(accounts = state.value, activeId = active.value))

    private fun secretName(id: String) = "ely-session:$id"

    private fun readSession(id: String): ElySession? = runCatching {
        secrets.read(secretName(id))?.let { YouLauncherJson.decodeFromString(ElySession.serializer(), it) }
    }.getOrNull()

    private fun writeSession(id: String, s: ElySession) = secrets.write(secretName(id), YouLauncherJson.encodeToString(ElySession.serializer(), s))

    /** Один clientToken на установку лаунчера, как требует Yggdrasil. */
    private val clientToken: String by lazy {
        runCatching { secrets.read(CLIENT_TOKEN) }.getOrNull() ?: UUID.randomUUID().toString().replace("-", "").also { t ->
            runCatching { secrets.write(CLIENT_TOKEN, t) }
        }
    }

    private fun change(transform: (List<Account>) -> List<Account>) {
        state.update(transform)
        persist()
    }

    override suspend fun addOffline(nick: String): Account = mutex.withLock {
        val acc = Account(UUID.randomUUID().toString(), AccountType.Offline, nick, offlineUuid(nick), addedAt = System.currentTimeMillis())
        change { it + acc }
        if (active.value == null) active.value = acc.id
        persist()
        acc
    }

    override suspend fun loginElyBy(login: String, password: String, totp: String?): AuthResult {
        val session = try {
            ely.authenticate(login, password, clientToken, totp)
        } catch (e: ElyAuthException.TwoFactorRequired) {
            return AuthResult.TwoFactorRequired
        } catch (e: ElyAuthException.InvalidCredentials) {
            return AuthResult.InvalidCredentials(e.message.orEmpty())
        } catch (e: Exception) {
            return AuthResult.Failure(e.message ?: e::class.simpleName.orEmpty())
        }
        val uuid = dashedUuid(session.profile.id)
        val textures = runCatching { ely.textures(session.profile.name) }.getOrNull()
        val account = mutex.withLock {
            // Повторный вход в тот же аккаунт обновляет запись, а не создаёт вторую.
            val existing = state.value.firstOrNull { it.type == AccountType.ElyBy && it.uuid == uuid }
            val acc = (existing ?: Account(UUID.randomUUID().toString(), AccountType.ElyBy, session.profile.name, uuid, addedAt = System.currentTimeMillis())).copy(
                username = session.profile.name,
                login = login,
                needsReauth = false,
                skinUrl = textures?.skinUrl,
                slimSkin = textures?.slim ?: false,
                capeUrl = textures?.capeUrl,
            )
            writeSession(acc.id, session)
            change { list -> if (existing != null) list.map { if (it.id == acc.id) acc else it } else list + acc }
            acc
        }
        return AuthResult.Success(account)
    }

    /**
     * Проверка и обновление сессии Ely.by: validate, при недействительном токене refresh.
     * false и пометка «нужен вход», если Ely.by отклонил обновление; при ошибке сети сессия остаётся как есть.
     */
    override suspend fun refresh(id: String): Boolean {
        val acc = state.value.firstOrNull { it.id == id } ?: return false
        if (acc.type != AccountType.ElyBy) return true
        val session = readSession(id) ?: run { markReauth(id, true); return false }
        return try {
            val current = if (ely.validate(session.accessToken)) session else ely.refresh(session.accessToken, session.clientToken).also { writeSession(id, it) }
            val textures = runCatching { ely.textures(current.profile.name) }.getOrNull()
            mutex.withLock {
                change { list ->
                    list.map {
                        if (it.id != id) it else it.copy(
                            username = current.profile.name, needsReauth = false,
                            // Ответ Ely.by без скина значит, что скин сняли; при ошибке сети прежний скин остаётся.
                            skinUrl = if (textures != null) textures.skinUrl else it.skinUrl,
                            slimSkin = textures?.slim ?: it.slimSkin,
                            capeUrl = if (textures != null) textures.capeUrl else it.capeUrl,
                        )
                    }
                }
            }
            true
        } catch (e: ElyAuthException.InvalidToken) {
            markReauth(id, true)
            false
        } catch (e: Exception) {
            true
        }
    }

    private suspend fun markReauth(id: String, value: Boolean) = mutex.withLock {
        change { list -> list.map { if (it.id == id) it.copy(needsReauth = value) else it } }
    }

    /** Фоновое обновление всех сессий Ely.by при старте лаунчера. */
    fun refreshAllInBackground() {
        scope.launch(Dispatchers.IO) {
            state.value.filter { it.type == AccountType.ElyBy }.forEach { refresh(it.id) }
        }
    }

    /** Удаление: токен Ely.by отзывается на сервере (если сеть есть) и стирается из хранилища. */
    override suspend fun remove(id: String) {
        val acc = state.value.firstOrNull { it.id == id } ?: return
        if (acc.type == AccountType.ElyBy) {
            readSession(id)?.let { s -> runCatching { ely.invalidate(s.accessToken, s.clientToken) } }
            runCatching { secrets.delete(secretName(id)) }
        }
        mutex.withLock {
            state.update { list -> list.filterNot { it.id == id } }
            if (active.value == id) active.value = state.value.firstOrNull()?.id
            persist()
        }
    }

    override fun setActive(id: String) {
        active.value = id
        change { list -> list.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it } }
    }

    override suspend fun session(id: String): AccountSession {
        val acc = state.value.firstOrNull { it.id == id } ?: error("Аккаунт не найден")
        if (acc.type == AccountType.Offline) {
            // Офлайн: сервер в офлайн-режиме токен не проверяет, он случайный на каждый запуск.
            return AccountSession(acc.username, acc.uuid, UUID.randomUUID().toString().replace("-", ""), AccountType.Offline)
        }
        if (!refresh(id)) throw ReauthRequiredException(acc.username)
        val s = readSession(id) ?: throw ReauthRequiredException(acc.username)
        val fresh = state.value.first { it.id == id }
        return AccountSession(fresh.username, fresh.uuid, s.accessToken, AccountType.ElyBy)
    }

    companion object {
        const val CLIENT_TOKEN = "ely-client-token"
    }
}
