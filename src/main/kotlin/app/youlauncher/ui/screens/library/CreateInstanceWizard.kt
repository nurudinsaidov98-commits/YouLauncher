package app.youlauncher.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.GameVersion
import app.youlauncher.core.repo.LoaderVersion
import app.youlauncher.core.repo.NewInstanceSpec
import app.youlauncher.core.repo.VersionType
import app.youlauncher.resources.Res
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.resources.action_clear
import app.youlauncher.resources.feature_heading
import app.youlauncher.core.VersionFeatures
import app.youlauncher.ui.components.Emblems
import app.youlauncher.ui.components.VersionEmblem
import app.youlauncher.resources.action_back
import app.youlauncher.resources.action_cancel
import app.youlauncher.resources.action_create
import app.youlauncher.resources.action_next
import app.youlauncher.resources.field_name
import app.youlauncher.resources.memory_gb
import app.youlauncher.resources.wizard_details_title
import app.youlauncher.resources.wizard_group
import app.youlauncher.resources.wizard_icon
import app.youlauncher.resources.wizard_java_auto
import app.youlauncher.resources.wizard_loader_beta
import app.youlauncher.resources.wizard_loader_hint
import app.youlauncher.resources.wizard_loader_latest
import app.youlauncher.resources.wizard_loader_none
import app.youlauncher.resources.wizard_loader_recommended
import app.youlauncher.resources.wizard_loader_title
import app.youlauncher.resources.wizard_loader_unstable
import app.youlauncher.resources.wizard_loader_version
import app.youlauncher.resources.wizard_loading_versions
import app.youlauncher.resources.wizard_memory
import app.youlauncher.resources.wizard_memory_hint
import app.youlauncher.resources.wizard_old
import app.youlauncher.resources.wizard_releases
import app.youlauncher.resources.wizard_resources_title
import app.youlauncher.resources.wizard_snapshots
import app.youlauncher.resources.wizard_step_details
import app.youlauncher.resources.wizard_step_loader
import app.youlauncher.resources.wizard_step_of
import app.youlauncher.resources.wizard_step_resources
import app.youlauncher.resources.wizard_step_summary
import app.youlauncher.resources.wizard_step_version
import app.youlauncher.resources.wizard_summary_loader
import app.youlauncher.resources.wizard_summary_memory
import app.youlauncher.resources.wizard_summary_note
import app.youlauncher.resources.wizard_summary_title
import app.youlauncher.resources.wizard_summary_version
import app.youlauncher.resources.wizard_title
import app.youlauncher.resources.wizard_version_hint
import app.youlauncher.resources.wizard_version_search
import app.youlauncher.resources.wizard_version_title
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import androidx.compose.runtime.DisposableEffect
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.rememberAmbientTime
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.VersionCover
import app.youlauncher.ui.components.symbol
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.onScrimColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class WizardStep(val label: StringResource) {
    Version(Res.string.wizard_step_version),
    Loader(Res.string.wizard_step_loader),
    Details(Res.string.wizard_step_details),
    Resources(Res.string.wizard_step_resources),
    Summary(Res.string.wizard_step_summary),
}

/** Чистая модель мастера (проверяется тестами). */
data class WizardModel(
    val step: WizardStep = WizardStep.Version,
    val version: String? = null,
    val versionFilter: VersionType = VersionType.Release,
    val versionQuery: String = "",
    val loader: LoaderType = LoaderType.Vanilla,
    val loaderVersion: String? = null,
    val showUnstable: Boolean = false,
    val name: String = "",
    val nameEdited: Boolean = false,
    val icon: InstanceIcon = InstanceIcon.Grass,
    val group: String = "",
    val maxMemoryMb: Int = 4096,
) {
    fun canProceed(): Boolean = when (step) {
        WizardStep.Version -> version != null
        WizardStep.Loader -> loader == LoaderType.Vanilla || loaderVersion != null
        WizardStep.Details -> name.isNotBlank()
        else -> true
    }

    fun next(): WizardModel = copy(step = WizardStep.entries.getOrElse(step.ordinal + 1) { step })
    fun back(): WizardModel = copy(step = WizardStep.entries.getOrElse(step.ordinal - 1) { step })

    fun withVersion(v: String): WizardModel = copy(version = v, name = if (nameEdited) name else autoName(v, loader))
    fun withLoader(l: LoaderType): WizardModel = copy(loader = l, loaderVersion = null, name = if (nameEdited) name else autoName(version, l))

    fun toSpec(): NewInstanceSpec = NewInstanceSpec(
        name = name.trim(),
        gameVersion = requireNotNull(version),
        loader = loader,
        loaderVersion = loaderVersion.takeIf { loader != LoaderType.Vanilla },
        icon = icon,
        group = group.trim().ifBlank { null },
        minMemoryMb = (maxMemoryMb / 4).coerceAtLeast(512),
        maxMemoryMb = maxMemoryMb,
    )

    companion object {
        fun autoName(version: String?, loader: LoaderType): String =
            listOfNotNull(version, loader.title.takeIf { loader != LoaderType.Vanilla }).joinToString(" ")

        fun filterVersions(all: List<GameVersion>, type: VersionType, query: String): List<GameVersion> = all.filter {
            (if (type == VersionType.OldBeta) it.type == VersionType.OldBeta || it.type == VersionType.OldAlpha else it.type == type) &&
                (query.isBlank() || it.id.contains(query.trim(), ignoreCase = true))
        }

        /** Рекомендация памяти: половина ОЗУ, но не больше 8 ГБ и не меньше 2 ГБ. */
        fun recommendedMaxMb(totalMb: Long): Int = (totalMb / 2).coerceIn(2048, 8192).toInt() / 512 * 512
    }
}

@Composable
fun CreateInstanceWizard(
    onDismiss: () -> Unit,
    onCreate: (NewInstanceSpec) -> Unit,
    existingGroups: List<String>,
) {
    val graph = LocalAppGraph.current
    val totalMb = remember { Platform.totalMemoryMb() }
    var model by remember { mutableStateOf(WizardModel(maxMemoryMb = minOf(4096, WizardModel.recommendedMaxMb(totalMb)))) }
    var versions by remember { mutableStateOf<List<GameVersion>?>(null) }
    var loaderVersions by remember { mutableStateOf<List<LoaderVersion>?>(null) }

    val ui = LocalAppUi.current
    // Выбранная версия меняет атмосферу всего окна, а не только превью в мастере.
    LaunchedEffect(model.version) { ui.previewVersion = model.version }
    DisposableEffect(Unit) { onDispose { ui.previewVersion = null } }
    LaunchedEffect(Unit) {
        versions = runCatching { graph.versions.versions() }.getOrDefault(emptyList())
        if (model.version == null) versions?.firstOrNull { it.type == VersionType.Release }?.let { model = model.withVersion(it.id) }
    }
    LaunchedEffect(model.loader, model.version) {
        loaderVersions = null
        val v = model.version ?: return@LaunchedEffect
        if (model.loader == LoaderType.Vanilla) { loaderVersions = emptyList(); return@LaunchedEffect }
        val list = runCatching { graph.loaderVersions.versions(model.loader, v) }.getOrDefault(emptyList())
        loaderVersions = list
        if (model.loaderVersion == null) {
            val pick = list.firstOrNull { it.recommended } ?: list.firstOrNull { it.stable } ?: list.firstOrNull()
            model = model.copy(loaderVersion = pick?.version)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(940.dp).height(640.dp),
        ) {
            Row(Modifier.fillMaxSize()) {
                WizardPreview(model, Modifier.width(320.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
                    StepIndicator(model.step)
                    Spacer(Modifier.height(20.dp))
                    val spatialIn = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                    val spatialOut = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                    val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = model.step,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(spatialIn) { it / 6 * dir } + fadeIn(effectsIn)) togetherWith
                                (slideOutHorizontally(spatialOut) { -it / 6 * dir } + fadeOut(effectsOut))
                        },
                        modifier = Modifier.weight(1f),
                        label = "wizard",
                    ) { step ->
                        when (step) {
                            WizardStep.Version -> VersionStep(model, versions) { model = it }
                            WizardStep.Loader -> LoaderStep(model, loaderVersions) { model = it }
                            WizardStep.Details -> DetailsStep(model, existingGroups) { model = it }
                            WizardStep.Resources -> ResourcesStep(model, totalMb) { model = it }
                            WizardStep.Summary -> SummaryStep(model)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) }
                        Spacer(Modifier.weight(1f))
                        if (model.step != WizardStep.Version) {
                            TextButton(onClick = { model = model.back() }, shapes = ButtonDefaults.shapes()) {
                                IconLabel(Symbols.ArrowBack, stringResource(Res.string.action_back))
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        val last = model.step == WizardStep.Summary
                        val enabled = model.canProceed()
                        Button(
                            onClick = { if (last) { onCreate(model.toSpec()); onDismiss() } else model = model.next() },
                            enabled = enabled,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            if (last) IconLabel(Symbols.Check, stringResource(Res.string.action_create))
                            else IconLabel(Symbols.ArrowForward, stringResource(Res.string.action_next))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardPreview(model: WizardModel, modifier: Modifier) {
    val time = rememberAmbientTime()
    Box(modifier.clip(MaterialTheme.shapes.extraLarge)) {
        Crossfade(model.version ?: "", animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(), label = "atmosphere") { v ->
            VersionCover(v.ifEmpty { "youlauncher" }, Modifier.fillMaxSize(), time = time, blur = 36.dp)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))))
        // Главная новинка версии: эмблема из форм M3, при смене версии собирается заново на пружинах.
        val feature = remember(model.version) { VersionFeatures.of(model.version.orEmpty()) }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            feature,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp).fillMaxWidth(0.7f).aspectRatio(1f),
            label = "emblem",
        ) { f -> VersionEmblem(f, time, Modifier.fillMaxSize()) }
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (model.version != null) {
                AnimatedContent(
                    feature,
                    transitionSpec = { (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec())) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "feature",
                ) { f ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(stringResource(Res.string.feature_heading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(Emblems.label(f)), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            InstanceBadge(model.icon, model.version ?: "youlauncher", size = 72.dp)
            Text(
                model.name.ifBlank { stringResource(Res.string.wizard_title) },
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = onScrimColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(model.version, model.loader.title, model.loaderVersion).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = onScrimColor().copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun StepIndicator(step: WizardStep) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.wizard_step_of, step.ordinal + 1, WizardStep.entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            WizardStep.entries.forEach { s ->
                val active = s.ordinal <= step.ordinal
                val w by animateFloatAsState(if (s == step) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dot")
                Box(
                    Modifier
                        .height(8.dp)
                        .width((8 + 32 * w).dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(step.label), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun StepTitle(title: StringResource, hint: StringResource? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmallEmphasized)
        if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VersionStep(model: WizardModel, versions: List<GameVersion>?, onChange: (WizardModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle(Res.string.wizard_version_title, Res.string.wizard_version_hint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model.versionQuery,
                onValueChange = { onChange(model.copy(versionQuery = it)) },
                placeholder = { Text(stringResource(Res.string.wizard_version_search)) },
                leadingIcon = { Icon(Symbols.Search, null) },
                trailingIcon = if (model.versionQuery.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { onChange(model.copy(versionQuery = "")) }) }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f),
            )
        }
        ConnectedChoice(
            options = listOf(
                ChoiceOption(VersionType.Release, stringResource(Res.string.wizard_releases)),
                ChoiceOption(VersionType.Snapshot, stringResource(Res.string.wizard_snapshots)),
                ChoiceOption(VersionType.OldBeta, stringResource(Res.string.wizard_old)),
            ),
            selected = model.versionFilter,
            onSelect = { onChange(model.copy(versionFilter = it)) },
        )
        if (versions == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingIndicator()
                Text(stringResource(Res.string.wizard_loading_versions))
            }
        } else {
            val shown = remember(versions, model.versionFilter, model.versionQuery) {
                WizardModel.filterVersions(versions, model.versionFilter, model.versionQuery)
            }
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { v ->
                    val selected = v.id == model.version
                    ListItem(
                        headlineContent = { Text(v.id, style = MaterialTheme.typography.titleMedium) },
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        trailingContent = { InstanceBadge(model.icon, v.id, size = 28.dp) },
                        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected, role = Role.RadioButton) { onChange(model.withVersion(v.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoaderStep(model: WizardModel, loaderVersions: List<LoaderVersion>?, onChange: (WizardModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepTitle(Res.string.wizard_loader_title, Res.string.wizard_loader_hint)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LoaderType.entries.forEach { l ->
                val selected = model.loader == l
                Surface(
                    onClick = { onChange(model.withLoader(l)) },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.width(150.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(l.symbol(), null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
                        Text(l.title, style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }
        if (model.loader != LoaderType.Vanilla) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.wizard_loader_version), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(Res.string.wizard_loader_unstable), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = model.showUnstable, onCheckedChange = { onChange(model.copy(showUnstable = it)) })
            }
            when {
                loaderVersions == null -> LoadingIndicator()
                loaderVersions.isEmpty() -> Text(stringResource(Res.string.wizard_loader_none), color = MaterialTheme.colorScheme.error)
                else -> {
                    val shown = loaderVersions.filter { model.showUnstable || it.stable }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { it.version }) { lv ->
                            val selected = lv.version == model.loaderVersion
                            ListItem(
                                headlineContent = { Text(lv.version) },
                                leadingContent = { RadioButton(selected = selected, onClick = null) },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (lv.recommended) Tag(stringResource(Res.string.wizard_loader_recommended), MaterialTheme.colorScheme.primaryContainer)
                                        if (lv.latest) Tag(stringResource(Res.string.wizard_loader_latest), MaterialTheme.colorScheme.secondaryContainer)
                                        if (!lv.stable) Tag(stringResource(Res.string.wizard_loader_beta), MaterialTheme.colorScheme.tertiaryContainer)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                                modifier = Modifier.clip(MaterialTheme.shapes.large).selectable(selected, role = Role.RadioButton) {
                                    onChange(model.copy(loaderVersion = lv.version))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun DetailsStep(model: WizardModel, groups: List<String>, onChange: (WizardModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepTitle(Res.string.wizard_details_title)
        OutlinedTextField(
            value = model.name,
            onValueChange = { onChange(model.copy(name = it, nameEdited = true)) },
            label = { Text(stringResource(Res.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.wizard_icon), style = MaterialTheme.typography.titleSmall)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InstanceIcon.entries.forEach { icon ->
                val selected = icon == model.icon
                Box(
                    Modifier
                        .clip(MaterialTheme.shapes.large)
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
                        .selectable(selected, role = Role.RadioButton) { onChange(model.copy(icon = icon)) }
                        .padding(6.dp),
                ) { InstanceBadge(icon, model.version ?: "youlauncher", size = 44.dp) }
            }
        }
        OutlinedTextField(
            value = model.group,
            onValueChange = { onChange(model.copy(group = it)) },
            label = { Text(stringResource(Res.string.wizard_group)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (groups.isNotEmpty()) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g -> FilterChip(selected = model.group == g, onClick = { onChange(model.copy(group = g)) }, label = { Text(g) }) }
            }
        }
    }
}

@Composable
private fun ResourcesStep(model: WizardModel, totalMb: Long, onChange: (WizardModel) -> Unit) {
    val maxSlider = (totalMb * 3 / 4).coerceIn(2048, 32768).toFloat()
    val recommended = WizardModel.recommendedMaxMb(totalMb)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepTitle(Res.string.wizard_resources_title)
        val gb = Formats.decimal(model.maxMemoryMb / 1024.0)
        Text(stringResource(Res.string.wizard_memory, stringResource(Res.string.memory_gb, gb)), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = model.maxMemoryMb.toFloat(),
            onValueChange = { onChange(model.copy(maxMemoryMb = (it / 512).toInt() * 512)) },
            valueRange = 1024f..maxSlider,
            steps = ((maxSlider - 1024f) / 512f).toInt() - 1,
        )
        Text(
            stringResource(Res.string.wizard_memory_hint, stringResource(Res.string.memory_gb, Formats.decimal(recommended / 1024.0))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.CoffeeFilled, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.wizard_java_auto), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SummaryStep(model: WizardModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepTitle(Res.string.wizard_summary_title)
        SummaryRow(stringResource(Res.string.field_name), model.name)
        SummaryRow(stringResource(Res.string.wizard_summary_version), model.version.orEmpty())
        SummaryRow(stringResource(Res.string.wizard_summary_loader), listOfNotNull(model.loader.title, model.loaderVersion.takeIf { model.loader != LoaderType.Vanilla }).joinToString(" "))
        SummaryRow(stringResource(Res.string.wizard_summary_memory), stringResource(Res.string.memory_gb, Formats.decimal(model.maxMemoryMb / 1024.0)))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.VerifiedUserFilled, null)
                Text(stringResource(Res.string.wizard_summary_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(180.dp))
        Text(value, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}

