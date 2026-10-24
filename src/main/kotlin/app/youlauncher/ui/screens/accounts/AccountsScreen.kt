package app.youlauncher.ui.screens.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Account
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.offlineUuid
import app.youlauncher.resources.Res
import app.youlauncher.resources.account_type_ely
import app.youlauncher.resources.account_type_offline
import app.youlauncher.resources.accounts_active
import app.youlauncher.resources.accounts_add_ely
import app.youlauncher.resources.accounts_add_offline
import app.youlauncher.resources.accounts_copy_uuid
import app.youlauncher.resources.accounts_ely_about
import app.youlauncher.resources.accounts_ely_register
import app.youlauncher.resources.accounts_add_microsoft
import app.youlauncher.resources.accounts_subtitle_ms
import app.youlauncher.resources.accounts_notice_body_ms
import app.youlauncher.resources.account_type_microsoft
import app.youlauncher.resources.ms_code_copied
import app.youlauncher.resources.ms_copy
import app.youlauncher.resources.ms_error_child
import app.youlauncher.resources.ms_error_declined
import app.youlauncher.resources.ms_error_expired
import app.youlauncher.resources.ms_error_no_game
import app.youlauncher.resources.ms_error_no_xbox
import app.youlauncher.resources.ms_error_not_approved
import app.youlauncher.resources.ms_error_region
import app.youlauncher.resources.ms_note
import app.youlauncher.resources.ms_open
import app.youlauncher.resources.ms_retry
import app.youlauncher.resources.ms_starting
import app.youlauncher.resources.ms_step
import app.youlauncher.resources.ms_title
import app.youlauncher.resources.ms_waiting
import app.youlauncher.core.repo.AuthFailure
import app.youlauncher.resources.accounts_empty_body
import app.youlauncher.resources.accounts_empty_title
import app.youlauncher.resources.accounts_last_used
import app.youlauncher.resources.accounts_notice_body
import app.youlauncher.resources.accounts_notice_title
import app.youlauncher.resources.accounts_reauth
import app.youlauncher.resources.accounts_refresh
import app.youlauncher.resources.accounts_remove
import app.youlauncher.resources.accounts_remove_body
import app.youlauncher.resources.accounts_remove_title
import app.youlauncher.resources.accounts_subtitle
import app.youlauncher.resources.accounts_use
import app.youlauncher.resources.accounts_uuid_copied
import app.youlauncher.resources.action_add
import app.youlauncher.resources.action_cancel
import app.youlauncher.resources.action_delete
import app.youlauncher.resources.ely_error_credentials
import app.youlauncher.resources.ely_error_network
import app.youlauncher.resources.ely_hide_password
import app.youlauncher.resources.ely_login
import app.youlauncher.resources.ely_password
import app.youlauncher.resources.ely_password_note
import app.youlauncher.resources.ely_show_password
import app.youlauncher.resources.ely_sign_in
import app.youlauncher.resources.ely_title
import app.youlauncher.resources.ely_totp
import app.youlauncher.resources.ely_totp_body
import app.youlauncher.resources.nav_accounts
import app.youlauncher.resources.offline_body
import app.youlauncher.resources.offline_nick
import app.youlauncher.resources.offline_nick_hint
import app.youlauncher.resources.offline_nick_invalid
import app.youlauncher.resources.offline_nick_taken
import app.youlauncher.resources.offline_title
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.ConfirmDialog
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.Gaps
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.AccountHead
import app.youlauncher.ui.components.PlayerHead
import app.youlauncher.ui.components.ScreenHeader
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.rememberViewModel
import org.jetbrains.compose.resources.stringResource

private enum class AccountDialog { Offline, Ely, Microsoft }

/** Подпись типа аккаунта (офлайн, Ely.by, Microsoft). */
fun accountTypeLabel(type: AccountType): org.jetbrains.compose.resources.StringResource = when (type) {
    AccountType.Offline -> Res.string.account_type_offline
    AccountType.ElyBy -> Res.string.account_type_ely
    AccountType.Microsoft -> Res.string.account_type_microsoft
}

@Composable
fun AccountsScreen() {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { AccountsViewModel(graph) }
    val accounts by vm.accounts.collectAsState()
    val activeId by vm.activeId.collectAsState()
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    // Команда «signInMicrosoft» сразу открывает вход через Microsoft (так делаются снимки экрана).
    val ui = app.youlauncher.ui.LocalAppUi.current
    LaunchedEffect(ui.pendingAction) { if (ui.consume("signInMicrosoft") && vm.microsoftAvailable) dialog = AccountDialog.Microsoft }
    var removing by remember { mutableStateOf<Account?>(null) }

    Column(Modifier.fillMaxSize().padding(start = Gaps.screen, end = Gaps.screen, top = Gaps.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(stringResource(Res.string.nav_accounts), subtitle = stringResource(if (vm.microsoftAvailable) Res.string.accounts_subtitle_ms else Res.string.accounts_subtitle)) {
            FilledTonalButton(onClick = { dialog = AccountDialog.Offline }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.PersonAdd, stringResource(Res.string.accounts_add_offline)) }
            Button(onClick = { vm.resetEly(); dialog = AccountDialog.Ely }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Login, stringResource(Res.string.accounts_add_ely)) }
            if (vm.microsoftAvailable) {
                Button(onClick = { dialog = AccountDialog.Microsoft }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Login, stringResource(Res.string.accounts_add_microsoft)) }
            }
        }
        Notice(vm.microsoftAvailable)
        if (accounts.isEmpty()) {
            EmptyState(Illustration.Accounts, stringResource(Res.string.accounts_empty_title), stringResource(Res.string.accounts_empty_body)) {
                Button(onClick = { dialog = AccountDialog.Offline }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.PersonAdd, stringResource(Res.string.accounts_add_offline)) }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(accounts, key = { _, a -> a.id }) { index, acc ->
                    AccountCard(
                        acc,
                        active = acc.id == activeId,
                        onUse = { vm.setActive(acc.id) },
                        onRemove = { removing = acc },
                        onReauth = {
                            if (acc.type == AccountType.Microsoft) dialog = AccountDialog.Microsoft
                            else { vm.updateEly { it.copy(login = acc.login.orEmpty()) }; dialog = AccountDialog.Ely }
                        },
                        onRefresh = { vm.refresh(acc) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ).staggered(index, acc.id),
                    )
                }
            }
        }
    }

    when (dialog) {
        AccountDialog.Offline -> OfflineDialog(vm, onDismiss = { dialog = null })
        AccountDialog.Ely -> ElyDialog(vm, onDismiss = { dialog = null })
        AccountDialog.Microsoft -> MicrosoftDialog(vm, onDismiss = { vm.cancelMicrosoft(); dialog = null })
        null -> Unit
    }
    removing?.let { acc ->
        ConfirmDialog(
            title = stringResource(Res.string.accounts_remove_title, acc.username),
            text = stringResource(Res.string.accounts_remove_body),
            confirmLabel = stringResource(Res.string.action_delete),
            onConfirm = { vm.remove(acc) },
            onDismiss = { removing = null },
            icon = Symbols.Delete,
            destructive = true,
        )
    }
}

@Composable
private fun Notice(microsoft: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Symbols.InfoFilled, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.accounts_notice_title), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(stringResource(if (microsoft) Res.string.accounts_notice_body_ms else Res.string.accounts_notice_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun AccountCard(acc: Account, active: Boolean, onUse: () -> Unit, onRemove: () -> Unit, onReauth: () -> Unit, onRefresh: () -> Unit, modifier: Modifier) {
    val graph = LocalAppGraph.current
    Card(
        onClick = onUse,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountHead(acc, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(acc.username, style = MaterialTheme.typography.titleLargeEmphasized)
                    Surface(shape = MaterialTheme.shapes.small, color = when (acc.type) {
                        AccountType.ElyBy -> MaterialTheme.colorScheme.tertiaryContainer
                        AccountType.Microsoft -> MaterialTheme.colorScheme.secondaryContainer
                        AccountType.Offline -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }) {
                        Text(
                            stringResource(accountTypeLabel(acc.type)),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(acc.uuid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (acc.lastUsedAt != null) {
                    Text(stringResource(Res.string.accounts_last_used, timeAgo(acc.lastUsedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (acc.needsReauth) {
                FilledTonalButton(onClick = onReauth, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Key, stringResource(Res.string.accounts_reauth)) }
            }
            if (active) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Symbols.CheckCircleFilled, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(Res.string.accounts_active), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = onUse, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.accounts_use)) }
            }
            if (acc.type != AccountType.Offline && !acc.needsReauth) {
                TIconButton(Symbols.Sync, stringResource(Res.string.accounts_refresh), onRefresh)
            }
            TIconButton(Symbols.ContentCopy, stringResource(Res.string.accounts_copy_uuid), {
                Platform.copy(acc.uuid)
                graph.messenger.show(Res.string.accounts_uuid_copied)
            })
            TIconButton(Symbols.Delete, stringResource(Res.string.accounts_remove), onRemove)
        }
    }
}

@Composable
private fun OfflineDialog(vm: AccountsViewModel, onDismiss: () -> Unit) {
    var nick by remember { mutableStateOf("") }
    val error = vm.nickError(nick)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canAdd = nick.isNotBlank() && error == null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PlayerHead(offlineUuid(nick.ifBlank { "?" }), size = 56.dp) },
        title = { Text(stringResource(Res.string.offline_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                Text(stringResource(Res.string.offline_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = nick,
                    onValueChange = { nick = it.take(16) },
                    label = { Text(stringResource(Res.string.offline_nick)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(
                            when (error) {
                                NickError.Invalid -> stringResource(Res.string.offline_nick_invalid)
                                NickError.Taken -> stringResource(Res.string.offline_nick_taken)
                                null -> stringResource(Res.string.offline_nick_hint)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canAdd) { vm.addOffline(nick); onDismiss() } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (nick.isNotBlank()) Text("UUID: ${offlineUuid(nick)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { vm.addOffline(nick); onDismiss() }, enabled = canAdd, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ElyDialog(vm: AccountsViewModel, onDismiss: () -> Unit) {
    val form by vm.ely.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val totpFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(form.needsTotp) { if (form.needsTotp) totpFocus.requestFocus() }
    val canSubmit = !form.busy && form.login.isNotBlank() && form.password.isNotEmpty() && (!form.needsTotp || form.totp.length >= 6)
    val submit = { if (canSubmit) vm.signIn(onDismiss) }
    AlertDialog(
        onDismissRequest = { if (!form.busy) onDismiss() },
        icon = { Icon(Symbols.Login, null) },
        title = { Text(stringResource(Res.string.ely_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(min = 380.dp, max = 440.dp)) {
                Text(stringResource(Res.string.accounts_ely_about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = form.login,
                    onValueChange = { v -> vm.updateEly { it.copy(login = v) } },
                    label = { Text(stringResource(Res.string.ely_login)) },
                    singleLine = true,
                    enabled = !form.busy && !form.needsTotp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { v -> vm.updateEly { it.copy(password = v) } },
                    label = { Text(stringResource(Res.string.ely_password)) },
                    singleLine = true,
                    enabled = !form.busy && !form.needsTotp,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TIconButton(
                            if (showPassword) Symbols.VisibilityOff else Symbols.Visibility,
                            stringResource(if (showPassword) Res.string.ely_hide_password else Res.string.ely_show_password),
                            { showPassword = !showPassword },
                        )
                    },
                    supportingText = { Text(stringResource(Res.string.ely_password_note)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(form.needsTotp, enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.ely_totp_body), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = form.totp,
                            onValueChange = { v -> vm.updateEly { it.copy(totp = v.filter(Char::isDigit).take(8)) } },
                            label = { Text(stringResource(Res.string.ely_totp)) },
                            singleLine = true,
                            enabled = !form.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(totpFocus),
                        )
                    }
                }
                form.error?.let { e ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Symbols.ErrorFilled, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                            Text(
                                when (e) {
                                    ElyError.Credentials -> stringResource(Res.string.ely_error_credentials)
                                    is ElyError.Network -> stringResource(Res.string.ely_error_network, e.message)
                                },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                TextButton(onClick = { Platform.openUrl("https://account.ely.by/register") }, shapes = ButtonDefaults.shapes()) {
                    IconLabel(Symbols.OpenInNew, stringResource(Res.string.accounts_ely_register))
                }
            }
        },
        confirmButton = {
            Button(onClick = submit, enabled = canSubmit, shapes = ButtonDefaults.shapes()) {
                if (form.busy) LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(Res.string.ely_sign_in))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !form.busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Вход через Microsoft по коду устройства: код крупно, кнопки «Скопировать» и «Открыть microsoft.com/link»,
 * ожидание подтверждения в браузере. Пароль вводится только на сайте Microsoft.
 */
@Composable
private fun MicrosoftDialog(vm: AccountsViewModel, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val state by vm.ms.collectAsState()
    LaunchedEffect(Unit) { vm.startMicrosoft(onDismiss) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Login, null) },
        title = { Text(stringResource(Res.string.ms_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(min = 380.dp, max = 440.dp)) {
                when (val s = state) {
                    MsLogin.Idle, MsLogin.Starting -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(36.dp))
                        Text(stringResource(Res.string.ms_starting), style = MaterialTheme.typography.bodyLarge)
                    }
                    is MsLogin.Code -> {
                        Text(stringResource(Res.string.ms_step), style = MaterialTheme.typography.bodyMedium)
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    s.code.userCode,
                                    style = MaterialTheme.typography.displaySmallEmphasized,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TIconButton(Symbols.ContentCopy, stringResource(Res.string.ms_copy), {
                                    Platform.copy(s.code.userCode)
                                    graph.messenger.show(Res.string.ms_code_copied)
                                })
                            }
                        }
                        Button(
                            onClick = { Platform.copy(s.code.userCode); Platform.openUrl(s.code.verificationUri) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { IconLabel(Symbols.OpenInNew, stringResource(Res.string.ms_open)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(28.dp))
                            Text(stringResource(Res.string.ms_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(Res.string.ms_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is MsLogin.Failed -> Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Symbols.ErrorFilled, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                            Text(
                                when (s.reason) {
                                    AuthFailure.Declined -> stringResource(Res.string.ms_error_declined)
                                    AuthFailure.Expired -> stringResource(Res.string.ms_error_expired)
                                    AuthFailure.NoXboxAccount -> stringResource(Res.string.ms_error_no_xbox)
                                    AuthFailure.ChildAccount -> stringResource(Res.string.ms_error_child)
                                    AuthFailure.Region -> stringResource(Res.string.ms_error_region)
                                    AuthFailure.NoGame -> stringResource(Res.string.ms_error_no_game)
                                    AuthFailure.AppNotApproved -> stringResource(Res.string.ms_error_not_approved)
                                    null -> stringResource(Res.string.ely_error_network, s.message)
                                },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state is MsLogin.Failed) {
                Button(onClick = { vm.startMicrosoft(onDismiss) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.ms_retry)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
