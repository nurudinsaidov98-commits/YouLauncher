package app.youlauncher.ui.screens.accounts

import app.youlauncher.AppGraph
import app.youlauncher.core.isValidNick
import app.youlauncher.core.model.Account
import app.youlauncher.core.repo.AuthResult
import app.youlauncher.core.repo.AuthFailure
import app.youlauncher.core.repo.MicrosoftDeviceCode
import kotlinx.coroutines.Job
import app.youlauncher.resources.Res
import app.youlauncher.resources.account_removed
import app.youlauncher.resources.accounts_session_expired
import app.youlauncher.resources.accounts_session_ok
import app.youlauncher.resources.ely_signed_in
import app.youlauncher.resources.offline_added
import app.youlauncher.ui.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние формы входа Ely.by. Пароль живёт только в памяти формы. */
data class ElyForm(
    val login: String = "",
    val password: String = "",
    val totp: String = "",
    val needsTotp: Boolean = false,
    val busy: Boolean = false,
    val error: ElyError? = null,
)

/** Вход через Microsoft: код на экране, пока пользователь подтверждает вход в браузере. */
sealed interface MsLogin {
    data object Idle : MsLogin
    data object Starting : MsLogin
    data class Code(val code: MicrosoftDeviceCode) : MsLogin
    data class Failed(val reason: AuthFailure?, val message: String) : MsLogin
}

sealed interface ElyError {
    data object Credentials : ElyError
    data class Network(val message: String) : ElyError
}

class AccountsViewModel(
    private val graph: AppGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    val accounts: StateFlow<List<Account>> = graph.accounts.accounts
    val activeId: StateFlow<String?> = graph.accounts.activeId

    private val _ms = MutableStateFlow<MsLogin>(MsLogin.Idle)
    val ms: StateFlow<MsLogin> = _ms.asStateFlow()
    private var msJob: Job? = null
    val microsoftAvailable: Boolean get() = graph.accounts.microsoftAvailable

    private val _ely = MutableStateFlow(ElyForm())
    val ely: StateFlow<ElyForm> = _ely.asStateFlow()

    fun nickError(nick: String): NickError? = when {
        nick.isBlank() -> null
        !isValidNick(nick) -> NickError.Invalid
        accounts.value.any { it.username.equals(nick, ignoreCase = true) && it.type == app.youlauncher.core.model.AccountType.Offline } -> NickError.Taken
        else -> null
    }

    fun addOffline(nick: String) = scope.launch {
        val acc = graph.accounts.addOffline(nick.trim())
        graph.accounts.setActive(acc.id)
        graph.messenger.show(Res.string.offline_added, acc.username)
    }

    fun setActive(id: String) = graph.accounts.setActive(id)

    fun remove(account: Account) = scope.launch {
        graph.accounts.remove(account.id)
        graph.messenger.show(Res.string.account_removed, account.username)
    }

    fun refresh(account: Account) = scope.launch {
        val ok = graph.accounts.refresh(account.id)
        if (ok) graph.messenger.show(Res.string.accounts_session_ok, account.username)
        else graph.messenger.show(Res.string.accounts_session_expired, account.username, error = true)
    }

    fun updateEly(transform: (ElyForm) -> ElyForm) = _ely.update { transform(it).copy(error = null) }

    fun resetEly() = _ely.update { ElyForm() }

    fun signIn(onDone: () -> Unit) {
        val form = _ely.value
        if (form.busy) return
        _ely.update { it.copy(busy = true, error = null) }
        scope.launch {
            val result = runCatching {
                graph.accounts.loginElyBy(form.login.trim(), form.password, form.totp.trim().takeIf { form.needsTotp && it.isNotEmpty() })
            }.getOrElse { AuthResult.Failure(it.message ?: it::class.simpleName.orEmpty()) }
            when (result) {
                is AuthResult.Success -> {
                    graph.accounts.setActive(result.account.id)
                    graph.messenger.show(Res.string.ely_signed_in, result.account.username)
                    _ely.value = ElyForm()
                    onDone()
                }
                AuthResult.TwoFactorRequired -> _ely.update { it.copy(busy = false, needsTotp = true) }
                is AuthResult.InvalidCredentials -> _ely.update {
                    it.copy(busy = false, error = ElyError.Credentials, password = if (it.needsTotp) it.password else "")
                }
                is AuthResult.Failure -> _ely.update { it.copy(busy = false, error = ElyError.Network(result.message)) }
            }
        }
    }

    /** Код с сайта Microsoft и ожидание подтверждения; диалог закрывается сам после входа. */
    fun startMicrosoft(onDone: () -> Unit) {
        msJob?.cancel()
        _ms.value = MsLogin.Starting
        msJob = scope.launch {
            val code = try {
                graph.accounts.microsoftDeviceCode()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ms.value = MsLogin.Failed(null, e.message ?: e::class.simpleName.orEmpty())
                return@launch
            }
            _ms.value = MsLogin.Code(code)
            when (val result = graph.accounts.loginMicrosoft(code)) {
                is AuthResult.Success -> {
                    graph.accounts.setActive(result.account.id)
                    graph.messenger.show(Res.string.ely_signed_in, result.account.username)
                    _ms.value = MsLogin.Idle
                    onDone()
                }
                is AuthResult.Failure -> _ms.value = MsLogin.Failed(result.reason, result.message)
                else -> _ms.value = MsLogin.Failed(null, result.toString())
            }
        }
    }

    fun cancelMicrosoft() {
        msJob?.cancel()
        _ms.value = MsLogin.Idle
    }
}

enum class NickError { Invalid, Taken }
