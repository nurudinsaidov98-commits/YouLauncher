package app.youlauncher.ui.screens.accounts

import app.youlauncher.AppGraph
import app.youlauncher.core.isValidNick
import app.youlauncher.core.model.Account
import app.youlauncher.core.repo.AuthResult
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
}

enum class NickError { Invalid, Taken }
