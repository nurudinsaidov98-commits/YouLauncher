package app.youlauncher.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.youlauncher.core.AppDirs
import app.youlauncher.core.settings.AppLanguage
import app.youlauncher.core.settings.ContrastOption
import app.youlauncher.core.settings.FontChoice
import app.youlauncher.core.settings.JvmPreset
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.MotionLevel
import app.youlauncher.core.settings.NavLayout
import app.youlauncher.core.settings.PaletteStyleOption
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.core.settings.UiDensity
import app.youlauncher.resources.Res
import app.youlauncher.resources.app_disclaimer
import app.youlauncher.resources.bg_title
import app.youlauncher.resources.contrast_high
import app.youlauncher.resources.contrast_medium
import app.youlauncher.resources.contrast_reduced
import app.youlauncher.resources.contrast_standard
import app.youlauncher.resources.density_comfortable
import app.youlauncher.resources.density_compact
import app.youlauncher.resources.language_en
import app.youlauncher.resources.language_ru
import app.youlauncher.resources.language_system
import app.youlauncher.resources.memory_gb
import app.youlauncher.resources.motion_full
import app.youlauncher.resources.motion_off
import app.youlauncher.resources.motion_reduced
import app.youlauncher.resources.nav_bottom
import app.youlauncher.resources.nav_settings
import app.youlauncher.resources.nav_side
import app.youlauncher.resources.preset_aikar
import app.youlauncher.resources.preset_fast
import app.youlauncher.resources.preset_none
import app.youlauncher.resources.preset_zgc
import app.youlauncher.resources.settings_about
import app.youlauncher.resources.settings_accent
import app.youlauncher.resources.settings_amoled
import app.youlauncher.resources.settings_amoled_hint
import app.youlauncher.resources.settings_appearance
import app.youlauncher.resources.settings_cache_folder
import app.youlauncher.resources.settings_close_on_start
import app.youlauncher.resources.settings_console_on_start
import app.youlauncher.resources.settings_contrast
import app.youlauncher.resources.settings_corners
import app.youlauncher.resources.settings_data_folder
import app.youlauncher.resources.settings_density
import app.youlauncher.resources.settings_folders
import app.youlauncher.resources.settings_font
import app.youlauncher.resources.settings_font_flex
import app.youlauncher.resources.settings_font_size
import app.youlauncher.resources.settings_font_system
import app.youlauncher.resources.settings_game
import app.youlauncher.resources.settings_interface
import app.youlauncher.resources.settings_java
import app.youlauncher.resources.settings_java_auto_download
import app.youlauncher.resources.settings_java_choose
import app.youlauncher.resources.settings_java_global
import app.youlauncher.resources.settings_java_global_hint
import app.youlauncher.resources.settings_jvm_custom
import app.youlauncher.resources.settings_jvm_preset
import app.youlauncher.resources.settings_language
import app.youlauncher.resources.settings_licenses
import app.youlauncher.resources.settings_logs_folder
import app.youlauncher.resources.settings_memory_global
import app.youlauncher.resources.settings_memory_range
import app.youlauncher.resources.settings_memory_recommended
import app.youlauncher.resources.settings_motion
import app.youlauncher.resources.colors_title
import app.youlauncher.resources.settings_bounce
import app.youlauncher.resources.bounce_bouncy
import app.youlauncher.resources.bounce_jelly
import app.youlauncher.resources.bounce_standard
import app.youlauncher.core.settings.SpringBounce
import app.youlauncher.resources.settings_navigation
import app.youlauncher.resources.settings_network
import app.youlauncher.resources.settings_open_data
import app.youlauncher.resources.settings_palette_style
import app.youlauncher.resources.settings_portable
import app.youlauncher.resources.settings_scale
import app.youlauncher.resources.settings_speed_limit
import app.youlauncher.resources.settings_speed_unlimited
import app.youlauncher.resources.settings_speed_value
import app.youlauncher.resources.settings_subtitle
import app.youlauncher.resources.settings_theme
import app.youlauncher.resources.settings_threads
import app.youlauncher.resources.settings_version
import app.youlauncher.resources.style_content
import app.youlauncher.resources.style_expressive
import app.youlauncher.resources.style_fidelity
import app.youlauncher.resources.style_fruit_salad
import app.youlauncher.resources.style_monochrome
import app.youlauncher.resources.style_neutral
import app.youlauncher.resources.style_rainbow
import app.youlauncher.resources.style_tonal_spot
import app.youlauncher.resources.style_vibrant
import app.youlauncher.resources.theme_dark
import app.youlauncher.resources.theme_light
import app.youlauncher.resources.theme_system
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import androidx.compose.runtime.LaunchedEffect
import app.youlauncher.ui.Platform
import app.youlauncher.ui.brand.YouLauncherLogo
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.Gaps
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.MaxWidth
import app.youlauncher.ui.components.ScreenHeader
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.SettingsSection
import app.youlauncher.ui.components.SliderRow
import app.youlauncher.ui.components.SwitchRow
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.TextFieldRow
import app.youlauncher.ui.components.animatedMorphShape
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.SeedColors
import app.youlauncher.ui.theme.seedColor
import app.youlauncher.ui.theme.toSeedLong
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun PaletteStyleOption.label(): StringResource = when (this) {
    PaletteStyleOption.TonalSpot -> Res.string.style_tonal_spot
    PaletteStyleOption.Neutral -> Res.string.style_neutral
    PaletteStyleOption.Vibrant -> Res.string.style_vibrant
    PaletteStyleOption.Expressive -> Res.string.style_expressive
    PaletteStyleOption.Rainbow -> Res.string.style_rainbow
    PaletteStyleOption.FruitSalad -> Res.string.style_fruit_salad
    PaletteStyleOption.Monochrome -> Res.string.style_monochrome
    PaletteStyleOption.Fidelity -> Res.string.style_fidelity
    PaletteStyleOption.Content -> Res.string.style_content
}

/** Разделы настроек. Другие этапы добавляют сюда свои разделы. */
enum class SettingsPage(val title: StringResource, val icon: () -> ImageVector) {
    Appearance(Res.string.settings_appearance, { Symbols.Palette }),
    Colors(Res.string.colors_title, { Symbols.Colorize }),
    Background(Res.string.bg_title, { Symbols.Wallpaper }),
    Interface(Res.string.settings_interface, { Symbols.Dashboard }),
    Game(Res.string.settings_game, { Symbols.SportsEsports }),
    Java(Res.string.settings_java, { Symbols.Coffee }),
    Network(Res.string.settings_network, { Symbols.Download }),
    Folders(Res.string.settings_folders, { Symbols.Folder }),
    Language(Res.string.settings_language, { Symbols.Language }),
    About(Res.string.settings_about, { Symbols.Info }),
}

@Composable
fun SettingsScreen() {
    val graph = LocalAppGraph.current
    val settings by graph.settings.settings.collectAsState()
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { graph.settings.update(it) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pages = SettingsPage.entries
    val ui = LocalAppUi.current
    // Команда вида "settings:Background" открывает нужный раздел.
    LaunchedEffect(ui.pendingAction) {
        val action = ui.pendingAction ?: return@LaunchedEffect
        if (!action.startsWith("settings:")) return@LaunchedEffect
        val page = pages.indexOfFirst { it.name == action.removePrefix("settings:") }
        // Прокрутка только после того, как список разложен: иначе она попадает в середину раскладки.
        // Команда сбрасывается после прокрутки: сброс перезапускает этот эффект и отменил бы её.
        if (page >= 0) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > page }
            listState.animateScrollToItem(page)
        }
        ui.pendingAction = null
    }

    Column(Modifier.fillMaxSize().padding(start = Gaps.screen, end = Gaps.screen, top = Gaps.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(stringResource(Res.string.nav_settings), subtitle = stringResource(Res.string.settings_subtitle))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pages) { index, page ->
                AssistChip(
                    onClick = { scope.launch { listState.animateScrollToItem(index) } },
                    label = { Text(stringResource(page.title)) },
                    leadingIcon = { Icon(page.icon(), null, Modifier.size(18.dp)) },
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
        }
        MaxWidth(1100.dp) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Gaps.section), contentPadding = PaddingValues(bottom = 48.dp)) {
                itemsIndexed(pages) { _, page ->
                    SettingsSection(stringResource(page.title), icon = page.icon()) {
                        when (page) {
                            SettingsPage.Appearance -> AppearanceSection(settings, update)
                            SettingsPage.Colors -> ColorsSection(settings, update)
                            SettingsPage.Background -> BackgroundSection(settings, update)
                            SettingsPage.Interface -> InterfaceSection(settings, update)
                            SettingsPage.Game -> GameSection(settings, update)
                            SettingsPage.Java -> JavaSection(settings, update)
                            SettingsPage.Network -> NetworkSection(settings, update)
                            SettingsPage.Folders -> FoldersSection()
                            SettingsPage.Language -> LanguageSection(settings, update)
                            SettingsPage.About -> AboutSection()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SettingLabel(stringResource(Res.string.settings_theme))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(ThemeMode.System, stringResource(Res.string.theme_system), Symbols.Contrast),
            ChoiceOption(ThemeMode.Light, stringResource(Res.string.theme_light), Symbols.LightMode),
            ChoiceOption(ThemeMode.Dark, stringResource(Res.string.theme_dark), Symbols.DarkMode),
        ),
        selected = s.theme.mode,
        onSelect = { mode -> update { it.copy(theme = it.theme.copy(mode = mode)) } },
    )
    SettingLabel(stringResource(Res.string.settings_accent))
    SeedPicker(
        selectedArgb = s.theme.seedColor().toSeedLong(),
        onSelect = { argb -> update { it.copy(theme = it.theme.copy(seed = argb, colorsFromWallpaper = false)) } },
    )
    SettingLabel(stringResource(Res.string.settings_palette_style))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteStyleOption.entries.forEach { style ->
            FilterChip(
                selected = s.theme.style == style,
                onClick = { update { it.copy(theme = it.theme.copy(style = style)) } },
                label = { Text(stringResource(style.label())) },
            )
        }
    }
    SettingLabel(stringResource(Res.string.settings_contrast))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(ContrastOption.Reduced, stringResource(Res.string.contrast_reduced)),
            ChoiceOption(ContrastOption.Standard, stringResource(Res.string.contrast_standard)),
            ChoiceOption(ContrastOption.Medium, stringResource(Res.string.contrast_medium)),
            ChoiceOption(ContrastOption.High, stringResource(Res.string.contrast_high)),
        ),
        selected = s.theme.contrast,
        onSelect = { c -> update { it.copy(theme = it.theme.copy(contrast = c)) } },
    )
    SwitchRow(
        stringResource(Res.string.settings_amoled), s.theme.amoled,
        { on -> update { it.copy(theme = it.theme.copy(amoled = on)) } },
        subtitle = stringResource(Res.string.settings_amoled_hint), icon = Symbols.DarkMode,
    )
}

@Composable
private fun InterfaceSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SliderRow(stringResource(Res.string.settings_scale), s.ui.scale, { v -> update { it.copy(ui = it.ui.copy(scale = (v * 20).toInt() / 20f)) } }, "${(s.ui.scale * 100).toInt()}%", 0.8f..1.4f, steps = 11)
    SliderRow(stringResource(Res.string.settings_font_size), s.ui.fontScale, { v -> update { it.copy(ui = it.ui.copy(fontScale = (v * 20).toInt() / 20f)) } }, "${(s.ui.fontScale * 100).toInt()}%", 0.85f..1.3f, steps = 8)
    SliderRow(stringResource(Res.string.settings_corners), s.ui.cornerScale, { v -> update { it.copy(ui = it.ui.copy(cornerScale = (v * 10).toInt() / 10f)) } }, "${(s.ui.cornerScale * 100).toInt()}%", 0.3f..1.5f, steps = 11)
    SettingLabel(stringResource(Res.string.settings_font))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(FontChoice.GoogleSansFlex, stringResource(Res.string.settings_font_flex), Symbols.TextFields),
            ChoiceOption(FontChoice.System, stringResource(Res.string.settings_font_system), Symbols.FormatSize),
        ),
        selected = s.ui.font,
        onSelect = { f -> update { it.copy(ui = it.ui.copy(font = f)) } },
    )
    SettingLabel(stringResource(Res.string.settings_density))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(UiDensity.Comfortable, stringResource(Res.string.density_comfortable)),
            ChoiceOption(UiDensity.Compact, stringResource(Res.string.density_compact)),
        ),
        selected = s.ui.density,
        onSelect = { d -> update { it.copy(ui = it.ui.copy(density = d)) } },
    )
    SettingLabel(stringResource(Res.string.settings_motion))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(MotionLevel.Full, stringResource(Res.string.motion_full), Symbols.Animation),
            ChoiceOption(MotionLevel.Reduced, stringResource(Res.string.motion_reduced), Symbols.SlowMotionVideo),
            ChoiceOption(MotionLevel.Off, stringResource(Res.string.motion_off), Symbols.Block),
        ),
        selected = s.ui.motion,
        onSelect = { m -> update { it.copy(ui = it.ui.copy(motion = m)) } },
    )
    if (s.ui.motion == MotionLevel.Full) {
        SettingLabel(stringResource(Res.string.settings_bounce))
        ConnectedChoice(
            options = listOf(
                ChoiceOption(SpringBounce.Standard, stringResource(Res.string.bounce_standard), Symbols.MotionMode),
                ChoiceOption(SpringBounce.Bouncy, stringResource(Res.string.bounce_bouncy), Symbols.Animation),
                ChoiceOption(SpringBounce.Jelly, stringResource(Res.string.bounce_jelly), Symbols.Waves),
            ),
            selected = s.ui.bounce,
            onSelect = { b -> update { it.copy(ui = it.ui.copy(bounce = b)) } },
        )
    }
    SettingLabel(stringResource(Res.string.settings_navigation))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(NavLayout.Side, stringResource(Res.string.nav_side), Symbols.ViewSidebar),
            ChoiceOption(NavLayout.Bottom, stringResource(Res.string.nav_bottom), Symbols.BottomNavigation),
        ),
        selected = s.ui.navigation,
        onSelect = { n -> update { it.copy(ui = it.ui.copy(navigation = n)) } },
    )
    WindowModeChoice(s, update)
    HorizontalDivider()
    NotificationsAndSounds(s, update)
    HorizontalDivider()
    HomeWidgetsEditor(s, update)
    HorizontalDivider()
    HotkeysEditor(s, update)
    HorizontalDivider()
    SettingsTransfer(update)
}

@Composable
private fun GameSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val total = Platform.totalMemoryMb()
    val top = (total * 3 / 4).toFloat().coerceAtLeast(4096f)
    val gb = { mb: Number -> Formats.decimal(mb.toDouble() / 1024.0) }
    SettingLabel(stringResource(Res.string.settings_memory_global))
    Text(
        stringResource(Res.string.settings_memory_range, stringResource(Res.string.memory_gb, gb(s.game.minMemoryMb)), stringResource(Res.string.memory_gb, gb(s.game.maxMemoryMb))),
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
    RangeSlider(
        value = s.game.minMemoryMb.toFloat()..s.game.maxMemoryMb.toFloat(),
        onValueChange = { r -> update { it.copy(game = it.game.copy(minMemoryMb = (r.start / 256).toInt() * 256, maxMemoryMb = (r.endInclusive / 256).toInt() * 256)) } },
        valueRange = 512f..top,
    )
    Text(
        stringResource(Res.string.settings_memory_recommended, stringResource(Res.string.memory_gb, gb((total / 2).coerceIn(2048, 8192))), stringResource(Res.string.memory_gb, gb(total))),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingLabel(stringResource(Res.string.settings_jvm_preset))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(JvmPreset.None, stringResource(Res.string.preset_none)),
            ChoiceOption(JvmPreset.Aikar, stringResource(Res.string.preset_aikar)),
            ChoiceOption(JvmPreset.Zgc, stringResource(Res.string.preset_zgc)),
            ChoiceOption(JvmPreset.FastStart, stringResource(Res.string.preset_fast)),
        ),
        selected = s.game.jvmPreset,
        onSelect = { p -> update { it.copy(game = it.game.copy(jvmPreset = p)) } },
        showIcons = false,
    )
    TextFieldRow(stringResource(Res.string.settings_jvm_custom), s.game.jvmArgs, { v -> update { it.copy(game = it.game.copy(jvmArgs = v)) } })
    SwitchRow(stringResource(Res.string.settings_close_on_start), s.game.closeLauncherOnStart, { on -> update { it.copy(game = it.game.copy(closeLauncherOnStart = on)) } }, icon = Symbols.ExitToApp)
    SwitchRow(stringResource(Res.string.settings_console_on_start), s.game.showConsoleOnStart, { on -> update { it.copy(game = it.game.copy(showConsoleOnStart = on)) } }, icon = Symbols.Terminal)
}

@Composable
private fun JavaSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SwitchRow(stringResource(Res.string.settings_java_auto_download), s.java.autoDownload, { on -> update { it.copy(java = it.java.copy(autoDownload = on)) } }, icon = Symbols.CloudDownload)
    JavaManagerSection(s.java.globalJavaPath) { p -> update { it.copy(java = it.java.copy(globalJavaPath = p)) } }
}

@Composable
private fun NetworkSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SliderRow(
        stringResource(Res.string.settings_threads), s.network.maxConcurrentDownloads.toFloat(),
        { v -> update { it.copy(network = it.network.copy(maxConcurrentDownloads = v.toInt())) } },
        s.network.maxConcurrentDownloads.toString(), 1f..32f, steps = 30,
    )
    val limitMb = s.network.speedLimitKbps / 1024f
    SliderRow(
        stringResource(Res.string.settings_speed_limit), limitMb,
        { v -> update { it.copy(network = it.network.copy(speedLimitKbps = (v.toInt() * 1024))) } },
        if (s.network.speedLimitKbps == 0) stringResource(Res.string.settings_speed_unlimited) else stringResource(Res.string.settings_speed_value, limitMb.toInt().toString()),
        0f..100f, steps = 99,
    )
    HorizontalDivider()
    CurseForgeKeySection()
}

@Composable
private fun FoldersSection() {
    val graph = LocalAppGraph.current
    if (AppDirs.isPortable()) Text(stringResource(Res.string.settings_portable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    FolderRow(stringResource(Res.string.settings_data_folder), graph.dirs.root)
    FolderRow(stringResource(Res.string.settings_cache_folder), graph.dirs.cacheDir)
    FolderRow(stringResource(Res.string.settings_logs_folder), graph.dirs.logsDir)
    HorizontalDivider()
    CacheSection(graph.settings.settings.collectAsState().value) { t -> graph.settings.update(t) }
}

@Composable
private fun FolderRow(label: String, path: java.nio.file.Path) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Symbols.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(path.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = { Platform.openFolder(path) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.FolderOpen, stringResource(Res.string.settings_open_data)) }
    }
}

@Composable
private fun LanguageSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    ConnectedChoice(
        options = listOf(
            ChoiceOption(AppLanguage.System, stringResource(Res.string.language_system), Symbols.Language),
            ChoiceOption(AppLanguage.Russian, stringResource(Res.string.language_ru)),
            ChoiceOption(AppLanguage.English, stringResource(Res.string.language_en)),
        ),
        selected = s.language,
        onSelect = { lang -> update { it.copy(language = lang) } },
    )
}

@Composable
private fun AboutSection() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        YouLauncherLogo(size = 64.dp)
        Column(Modifier.weight(1f)) {
            Text("YouLauncher", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(stringResource(Res.string.settings_version, AppInfo.version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.app_disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        FilledTonalButton(onClick = { AppInfo.openLicenses() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Gavel, stringResource(Res.string.settings_licenses)) }
    }
    HorizontalDivider()
    val graph = LocalAppGraph.current
    UpdateSection(graph.settings.settings.collectAsState().value) { t -> graph.settings.update(t) }
    HorizontalDivider()
    DiagnosticsRow()
}

/** Выбор seed-цвета: выбранный образец морфит из круга в «печенье» M3 и поворачивается. */
@Composable
fun SeedPicker(selectedArgb: Long, onSelect: (Long) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SeedColors.forEach { seed ->
            val argb = seed.color.toSeedLong()
            val selected = argb == selectedArgb
            val shape = animatedMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, selected, rotation = 30f)
            val name = stringResource(seed.name)
            Box(
                Modifier
                    .size(52.dp)
                    .clip(shape)
                    .background(seed.color)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(argb) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Surface(shape = MaterialShapes.Circle.toShape(), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(24.dp)) {
                        Icon(Symbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(3.dp))
                    }
                }
            }
        }
    }
}
