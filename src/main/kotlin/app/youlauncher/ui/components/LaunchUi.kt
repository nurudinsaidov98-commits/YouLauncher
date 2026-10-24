package app.youlauncher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.repo.LaunchStage
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_dismiss
import app.youlauncher.resources.action_play
import app.youlauncher.resources.action_stop
import app.youlauncher.resources.action_view_log
import app.youlauncher.resources.home_choose_instance
import app.youlauncher.resources.launch_crashed
import app.youlauncher.resources.launch_failed
import app.youlauncher.resources.launch_running_for
import app.youlauncher.resources.launch_stage_assets
import app.youlauncher.resources.launch_stage_java
import app.youlauncher.resources.launch_stage_libraries
import app.youlauncher.resources.launch_stage_loader
import app.youlauncher.resources.launch_stage_preparing
import app.youlauncher.resources.launch_stage_running
import app.youlauncher.resources.launch_stage_starting
import app.youlauncher.ui.icons.Symbols
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun LaunchStage.label(): StringResource = when (this) {
    LaunchStage.Preparing -> Res.string.launch_stage_preparing
    LaunchStage.Java -> Res.string.launch_stage_java
    LaunchStage.Libraries -> Res.string.launch_stage_libraries
    LaunchStage.Assets -> Res.string.launch_stage_assets
    LaunchStage.Loader -> Res.string.launch_stage_loader
    LaunchStage.Starting -> Res.string.launch_stage_starting
    LaunchStage.Running -> Res.string.launch_stage_running
}

val LaunchState.isBusy: Boolean get() = this is LaunchState.Preparing || this is LaunchState.Running

/**
 * Split-кнопка M3 Expressive: слева «Играть»/«Остановить», справа выбор экземпляра.
 * Стрелка поворачивается на пружине, формы половинок морфят при нажатии.
 */
@Composable
fun PlaySplitButton(
    state: LaunchState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    instances: List<Instance>,
    selectedId: String?,
    onSelect: (Instance) -> Unit,
    height: Dp = SplitButtonDefaults.MediumContainerHeight,
) {
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (menu) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "arrow")
    val busy = state.isBusy
    val chooseLabel = stringResource(Res.string.home_choose_instance)
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { if (busy) onStop() else onPlay() },
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
                    modifier = Modifier.heightIn(min = height),
                    colors = if (busy) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    AnimatedContent(busy, transitionSpec = { fadeIn(fastEffects) togetherWith fadeOut(fastEffects) }, label = "play") { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (b) Symbols.StopFilled else Symbols.PlayArrowFilled,
                                contentDescription = null,
                                modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(height)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (b) Res.string.action_stop else Res.string.action_play), style = ButtonDefaults.textStyleFor(height))
                        }
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menu,
                    onCheckedChange = { menu = it },
                    enabled = instances.size > 1,
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                    modifier = Modifier.heightIn(min = height).semantics { contentDescription = chooseLabel },
                ) {
                    Icon(
                        Symbols.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(height)).graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
            Text(
                chooseLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            instances.forEach { inst ->
                DropdownMenuItem(
                    text = { Text(inst.name) },
                    leadingIcon = { InstanceBadge(inst.icon, inst.gameVersion, size = 28.dp, accent = inst.accent) },
                    trailingIcon = if (inst.id == selectedId) ({ Icon(Symbols.Check, contentDescription = null) }) else null,
                    onClick = { onSelect(inst); menu = false },
                )
            }
        }
    }
}

/** Строка состояния запуска: волнистый прогресс со стадией, время игры или ошибка. */
@Composable
fun LaunchStatus(
    state: LaunchState,
    onOpenLog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = state::class,
        transitionSpec = { fadeIn(effectsIn) togetherWith fadeOut(effectsOut) },
        modifier = modifier,
        label = "launch-status",
    ) { kind ->
        when (kind) {
            LaunchState.Preparing::class -> {
                val p = state as? LaunchState.Preparing ?: return@AnimatedContent
                val progress by animateFloatAsState(p.progress ?: 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "progress")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(p.stage.label()), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.weight(1f))
                        if (p.progress != null) Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    if (p.progress != null) {
                        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            LaunchState.Running::class -> {
                val r = state as? LaunchState.Running ?: return@AnimatedContent
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(r.startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Symbols.SportsEsportsFilled, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(Res.string.launch_running_for, duration(now - r.startedAt + 60_000)),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
            LaunchState.Crashed::class, LaunchState.Failed::class -> {
                val text = when (state) {
                    is LaunchState.Crashed -> stringResource(Res.string.launch_crashed, state.exitCode)
                    is LaunchState.Failed -> stringResource(Res.string.launch_failed, state.message)
                    else -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Symbols.ErrorFilled, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
                    TextButton(onClick = onOpenLog, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_view_log)) }
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_dismiss)) }
                }
            }
            else -> Spacer(Modifier.size(0.dp))
        }
    }
}
