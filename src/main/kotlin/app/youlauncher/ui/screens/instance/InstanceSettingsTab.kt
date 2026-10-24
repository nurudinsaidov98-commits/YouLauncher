package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.resources.Res
import app.youlauncher.resources.field_name
import app.youlauncher.resources.instance_java
import app.youlauncher.resources.instance_tags
import app.youlauncher.resources.memory_gb
import app.youlauncher.resources.settings_env
import app.youlauncher.resources.settings_env_hint
import app.youlauncher.resources.settings_fullscreen
import app.youlauncher.resources.settings_general
import app.youlauncher.resources.settings_group
import app.youlauncher.resources.settings_height
import app.youlauncher.resources.settings_jvm_args
import app.youlauncher.resources.settings_memory
import app.youlauncher.resources.settings_memory_range
import app.youlauncher.resources.settings_post_exit
import app.youlauncher.resources.settings_pre_launch
import app.youlauncher.resources.settings_quick_join
import app.youlauncher.resources.settings_quick_join_hint
import app.youlauncher.resources.settings_resolution
import app.youlauncher.resources.settings_tags_hint
import app.youlauncher.resources.settings_use_global
import app.youlauncher.resources.settings_width
import app.youlauncher.resources.settings_wrapper
import app.youlauncher.resources.instance_java_auto_for
import app.youlauncher.resources.version_section
import app.youlauncher.resources.instance_look
import app.youlauncher.resources.backup_section
import app.youlauncher.data.java.JavaChecks
import app.youlauncher.ui.screens.settings.JavaPicker
import app.youlauncher.resources.wizard_icon
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.SettingsSection
import app.youlauncher.ui.components.SwitchRow
import app.youlauncher.ui.components.TextFieldRow
import app.youlauncher.ui.icons.Symbols
import org.jetbrains.compose.resources.stringResource

/** Переменные окружения в текстовом виде "ИМЯ=значение" по строкам. */
object EnvText {
    fun parse(text: String): Map<String, String> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && '=' in it && !it.startsWith("#") }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=') }
        .filterKeys { it.isNotEmpty() }

    fun format(env: Map<String, String>): String = env.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

@Composable
fun InstanceSettingsTab(inst: Instance, vm: InstanceViewModel) {
    val graph = LocalAppGraph.current
    val global = graph.settings.settings.value.game
    // Ленивый список: при открытии вкладки строятся только видимые разделы (раньше сразу все, вместе с 28 плитками обоев).
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
SettingsSection(stringResource(Res.string.settings_general), icon = Symbols.Tune) {
            TextFieldRow(stringResource(Res.string.field_name), inst.name, { v -> vm.update { it.copy(name = v) } })
            SettingLabel(stringResource(Res.string.wizard_icon))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InstanceIcon.entries.forEach { icon ->
                    val selected = icon == inst.icon
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.large)
                            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
                            .selectable(selected, role = Role.RadioButton) { vm.update { it.copy(icon = icon) } }
                            .padding(6.dp),
                    ) { InstanceBadge(icon, inst.gameVersion, size = 40.dp, accent = inst.accent) }
                }
            }
            TextFieldRow(stringResource(Res.string.settings_group), inst.group.orEmpty(), { v -> vm.update { it.copy(group = v.ifBlank { null }) } })
            TextFieldRow(
                stringResource(Res.string.instance_tags),
                inst.tags.joinToString(", "),
                { v -> vm.update { it.copy(tags = v.split(',').map(String::trim).filter(String::isNotEmpty)) } },
                supporting = stringResource(Res.string.settings_tags_hint),
            )
        }
        }

        item {
SettingsSection(stringResource(Res.string.version_section), icon = Symbols.DeployedCodeUpdate) {
            VersionSection(inst, vm)
        }
        }

        item {
SettingsSection(stringResource(Res.string.instance_look), icon = Symbols.Palette) {
            CardLookSection(inst, vm)
        }
        }

        item {
SettingsSection(stringResource(Res.string.backup_section), icon = Symbols.Backup) {
            val navigator = app.youlauncher.ui.navigation.LocalNavigator.current
            BackupSection(inst, vm, onOpenInstance = { id -> navigator.navigate(app.youlauncher.ui.navigation.Screen.Instance(id)) })
        }
        }

        item { AtmosphereSection(inst, vm) }

        item {
SettingsSection(stringResource(Res.string.settings_memory), icon = Symbols.Memory) {
            val useGlobal = inst.java.maxMemoryMb == null
            SwitchRow(stringResource(Res.string.settings_use_global), useGlobal, { on ->
                vm.update { it.copy(java = it.java.copy(minMemoryMb = if (on) null else global.minMemoryMb, maxMemoryMb = if (on) null else global.maxMemoryMb)) }
            })
            val total = Platform.totalMemoryMb().coerceAtLeast(4096)
            val min = (inst.java.minMemoryMb ?: global.minMemoryMb).toFloat()
            val max = (inst.java.maxMemoryMb ?: global.maxMemoryMb).toFloat()
            Text(
                stringResource(
                    Res.string.settings_memory_range,
                    stringResource(Res.string.memory_gb, Formats.decimal(min / 1024.0)),
                    stringResource(Res.string.memory_gb, Formats.decimal(max / 1024.0)),
                ),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            val top = (total * 3 / 4).toFloat().coerceAtLeast(4096f)
            RangeSlider(
                value = min..max,
                onValueChange = { r ->
                    vm.update { it.copy(java = it.java.copy(minMemoryMb = (r.start / 256).toInt() * 256, maxMemoryMb = (r.endInclusive / 256).toInt() * 256)) }
                },
                valueRange = 512f..top,
                enabled = !useGlobal,
            )
        }
        }

        item {
SettingsSection(stringResource(Res.string.instance_java), icon = Symbols.Coffee) {
            JavaPicker(
                inst.java.javaPath,
                { p -> vm.update { it.copy(java = it.java.copy(javaPath = p)) } },
                stringResource(Res.string.instance_java_auto_for, JavaChecks.requiredMajor(inst.gameVersion)),
            )
            TextFieldRow(
                stringResource(Res.string.settings_jvm_args),
                inst.java.jvmArgs.orEmpty(),
                { v -> vm.update { it.copy(java = it.java.copy(jvmArgs = v.ifBlank { null })) } },
                placeholder = global.jvmArgs.ifBlank { "-XX:+UseG1GC" },
            )
            TextFieldRow(
                stringResource(Res.string.settings_env),
                EnvText.format(inst.java.env),
                { v -> vm.update { it.copy(java = it.java.copy(env = EnvText.parse(v))) } },
                singleLine = false,
                supporting = stringResource(Res.string.settings_env_hint),
            )
        }
        }

        item {
SettingsSection(stringResource(Res.string.settings_resolution), icon = Symbols.DesktopWindows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextFieldRow(stringResource(Res.string.settings_width), (inst.width ?: global.width).toString(), { v -> v.toIntOrNull()?.let { w -> vm.update { it.copy(width = w) } } }, Modifier.weight(1f), numeric = true)
                TextFieldRow(stringResource(Res.string.settings_height), (inst.height ?: global.height).toString(), { v -> v.toIntOrNull()?.let { h -> vm.update { it.copy(height = h) } } }, Modifier.weight(1f), numeric = true)
            }
            SwitchRow(stringResource(Res.string.settings_fullscreen), inst.fullscreen ?: global.fullscreen, { on -> vm.update { it.copy(fullscreen = on) } })
            TextFieldRow(
                stringResource(Res.string.settings_quick_join),
                inst.quickJoinServer.orEmpty(),
                { v -> vm.update { it.copy(quickJoinServer = v.ifBlank { null }) } },
                supporting = stringResource(Res.string.settings_quick_join_hint),
            )
        }
        }

        item {
SettingsSection(stringResource(Res.string.settings_pre_launch), icon = Symbols.Terminal) {
            TextFieldRow(stringResource(Res.string.settings_pre_launch), inst.java.preLaunchCommand.orEmpty(), { v -> vm.update { it.copy(java = it.java.copy(preLaunchCommand = v.ifBlank { null })) } })
            TextFieldRow(stringResource(Res.string.settings_post_exit), inst.java.postExitCommand.orEmpty(), { v -> vm.update { it.copy(java = it.java.copy(postExitCommand = v.ifBlank { null })) } })
            TextFieldRow(stringResource(Res.string.settings_wrapper), inst.java.wrapperCommand.orEmpty(), { v -> vm.update { it.copy(java = it.java.copy(wrapperCommand = v.ifBlank { null })) } })
        }
        }
    }
}
