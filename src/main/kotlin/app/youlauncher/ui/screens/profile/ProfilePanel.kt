package app.youlauncher.ui.screens.profile

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.AccountType
import app.youlauncher.core.profile.Profile
import app.youlauncher.core.profile.ProfileStats
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.resources.Res
import app.youlauncher.resources.account_type_ely
import app.youlauncher.resources.account_type_offline
import app.youlauncher.resources.nav_settings
import app.youlauncher.resources.profile_menu_accounts
import app.youlauncher.resources.profile_menu_add_account
import app.youlauncher.resources.profile_menu_no_accounts
import app.youlauncher.resources.profile_menu_play_as
import app.youlauncher.resources.profile_change_avatar
import app.youlauncher.resources.profile_change_banner
import app.youlauncher.resources.profile_copy_nick
import app.youlauncher.resources.profile_hours
import app.youlauncher.resources.profile_nick_copied
import app.youlauncher.resources.profile_stat_instances
import app.youlauncher.resources.profile_stat_mods
import app.youlauncher.resources.profile_stat_play
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.UserWallpapers
import app.youlauncher.ui.background.rememberAmbientTime
import app.youlauncher.ui.components.AccountHead
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.PlayerHead
import app.youlauncher.ui.components.ProfileAvatar
import app.youlauncher.ui.components.rememberActiveSkinUrl
import app.youlauncher.resources.profile_avatar_skin
import app.youlauncher.ui.components.WithTooltip
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.images.rememberRemoteImage
import app.youlauncher.ui.navigation.TopLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.sin

/** Статистика профиля из локальных данных: время в игре, экземпляры, моды. */
object ProfileLogic {
    fun stats(totalPlayMs: Long, instances: Int, mods: Int) = ProfileStats(totalPlayMs / 60_000, instances, mods)
}

/**
 * Содержимое выпадающей панели профиля: баннер, аватар, имя и ник (редактируются на месте),
 * статистика, выбор аккаунта для игры и быстрые переходы. Каждый блок появляется с пружинной задержкой.
 */
@Composable
fun ProfilePanel(onNavigate: (TopLevel) -> Unit) {
    val graph = LocalAppGraph.current
    val profile by graph.profile.profile.collectAsState()
    val instances by graph.instances.instances.collectAsState()
    val mods by produceState(0, instances) {
        value = instances.sumOf { runCatching { graph.content.content(it.id, ContentKind.Mods).first().size }.getOrDefault(0) }
    }
    val stats = ProfileLogic.stats(instances.sumOf { it.totalPlayTimeMs }, instances.size, mods)
    Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
        ProfileHeader(profile, stats)
        Spacer(Modifier.height(12.dp))
        AccountSwitcher(onManage = { onNavigate(TopLevel.Accounts) }, modifier = Modifier.staggered(2))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).staggered(3), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onNavigate(TopLevel.Accounts) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                IconLabel(Symbols.ManageAccounts, stringResource(Res.string.profile_menu_accounts))
            }
            FilledTonalButton(onClick = { onNavigate(TopLevel.Settings) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                IconLabel(Symbols.Settings, stringResource(Res.string.nav_settings))
            }
        }
    }
}

/** Быстрый выбор аккаунта, с которым запускается игра. */
@Composable
private fun AccountSwitcher(onManage: () -> Unit, modifier: Modifier) {
    val graph = LocalAppGraph.current
    val accounts by graph.accounts.accounts.collectAsState()
    val activeId by graph.accounts.activeId.collectAsState()
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(Res.string.profile_menu_play_as),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (accounts.isEmpty()) {
                Text(
                    stringResource(Res.string.profile_menu_no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                TextButton(onClick = onManage, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(horizontal = 8.dp)) {
                    IconLabel(Symbols.PersonAdd, stringResource(Res.string.profile_menu_add_account))
                }
            }
            accounts.forEach { acc ->
                val active = acc.id == activeId
                val container by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    MaterialTheme.motionScheme.defaultEffectsSpec(),
                )
                ListItem(
                    headlineContent = { Text(acc.username, style = MaterialTheme.typography.titleSmallEmphasized) },
                    supportingContent = {
                        Text(stringResource(app.youlauncher.ui.screens.accounts.accountTypeLabel(acc.type)))
                    },
                    leadingContent = { AccountHead(acc, size = 36.dp) },
                    trailingContent = { RadioButton(selected = active, onClick = { graph.accounts.setActive(acc.id) }) },
                    colors = ListItemDefaults.colors(containerColor = container),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { graph.accounts.setActive(acc.id) },
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: Profile, stats: ProfileStats) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val time = rememberAmbientTime(saver = graph.settings.settings.collectAsState().value.background.saver)
    val avatarTitle = stringResource(Res.string.profile_change_avatar)
    val bannerTitle = stringResource(Res.string.profile_change_banner)
    val skinTitle = stringResource(Res.string.profile_avatar_skin)
    val skinUrl = rememberActiveSkinUrl()

    fun pick(title: String, kind: String, apply: (Profile, String) -> Profile) {
        val file = Platform.chooseFiles(title, UserWallpapers.extensions.toList()).firstOrNull() ?: return
        scope.launch { graph.profile.importImage(file, kind)?.let { graph.profile.save(apply(graph.profile.profile.value, it)) } }
    }

    // Имя и ник редактируются прямо в карточке, сохранение с небольшой задержкой.
    var name by remember(profile.id) { mutableStateOf(profile.displayName) }
    var nick by remember(profile.id) { mutableStateOf(profile.nickname) }
    LaunchedEffect(name, nick) {
        delay(500)
        val current = graph.profile.profile.value
        if (name.isNotBlank() && (name != current.displayName || nick != current.nickname)) graph.profile.save(current.copy(displayName = name.trim(), nickname = nick.trim()))
    }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().staggered(0)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.large)) {
                val banner = profile.bannerImage?.let { rememberRemoteImage(it, 900) }
                if (banner != null) Image(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else SoftGradient(time, Modifier.fillMaxSize())
                SmallAction(Symbols.Wallpaper, bannerTitle, { pick(bannerTitle, "banner") { p, path -> p.copy(bannerImage = path) } }, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            Box(Modifier.pullUp(48.dp).padding(start = 4.dp).height(96.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(5.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    ProfileAvatar(profile, 86.dp)
                }
                // Своя картинка выключает голову скина; кнопка со значком лица возвращает её, если у аккаунта Ely.by есть скин.
                SmallAction(Symbols.PhotoCamera, avatarTitle, { pick(avatarTitle, "avatar") { p, path -> p.copy(avatarImage = path, avatarFromSkin = false) } }, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 2.dp))
                if (skinUrl != null && !profile.avatarFromSkin) {
                    SmallAction(Symbols.Face, skinTitle, { scope.launch { graph.profile.save(graph.profile.profile.value.copy(avatarFromSkin = true)) } }, Modifier.align(Alignment.BottomStart).offset(x = (-2).dp, y = 2.dp))
                }
            }
            Column(Modifier.padding(top = 10.dp).staggered(1)) {
                InlineText(name, { name = it.take(32) }, MaterialTheme.typography.headlineSmallEmphasized.copy(color = MaterialTheme.colorScheme.onSurface), focus)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InlineText(nick, { v -> nick = v.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(24) },
                        MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), focus, Modifier.width(IntrinsicWidth(nick)))
                    val copyLabel = stringResource(Res.string.profile_copy_nick)
                    WithTooltip(copyLabel) {
                        IconButton(
                            onClick = { Platform.copy(nick); graph.messenger.show(Res.string.profile_nick_copied) },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(28.dp),
                        ) { Icon(Symbols.ContentCopy, copyLabel, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    val hours = Formats.decimal(stats.playMinutes / 60.0, if (stats.playMinutes < 600) 1 else 0)
                    Stat(stringResource(Res.string.profile_hours, hours), stringResource(Res.string.profile_stat_play), Modifier.weight(1f))
                    Stat(stats.instances.toString(), stringResource(Res.string.profile_stat_instances), Modifier.weight(1f))
                    Stat(stats.mods.toString(), stringResource(Res.string.profile_stat_mods), Modifier.weight(1f))
                }
            }
        }
    }
}

/** Ширина поля ника по длине текста, чтобы кнопка «скопировать» стояла сразу за ником. */
@Composable
private fun IntrinsicWidth(text: String) = (text.length.coerceAtLeast(3) * 9 + 6).dp

/** Текст, который редактируется по клику; Enter или Esc завершают ввод. */
@Composable
private fun InlineText(value: String, onChange: (String) -> Unit, style: TextStyle, focus: FocusManager, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        // Esc тоже завершает ввод (Enter — через onDone).
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Escape) { focus.clearFocus(); true } else false
        },
    )
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        WithTooltip(label) {
            FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(32.dp)) {
                Icon(icon, label, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Мягкий градиент баннера из цветов темы, медленно дрейфует. */
@Composable
private fun SoftGradient(time: State<Float>, modifier: Modifier) {
    val s = MaterialTheme.colorScheme
    val colors = listOf(s.primaryContainer, s.tertiaryContainer, s.secondaryContainer, s.primary.copy(alpha = 0.6f))
    Canvas(modifier) {
        drawRect(s.surfaceContainerHigh)
        val t = time.value
        val anchors = listOf(Offset(0.85f, 0.15f), Offset(0.15f, 0.85f), Offset(0.5f, 0.5f), Offset(0.1f, 0.1f))
        colors.forEachIndexed { i, c ->
            val a = anchors[i]
            val center = Offset(size.width * (a.x + 0.07f * sin(t * 0.15f + i * 1.7f)), size.height * (a.y + 0.07f * cos(t * 0.12f + i * 2.3f)))
            val r = size.width * 0.7f
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, r), r, center)
        }
    }
}

/** Сдвиг вверх с уменьшением занимаемой высоты: аватар наезжает на баннер без пустого места ниже. */
private fun Modifier.pullUp(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}
