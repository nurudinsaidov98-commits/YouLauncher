package app.youlauncher.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.window.WindowDraggableArea
import app.youlauncher.core.settings.AppLanguage
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.core.settings.UiSettings
import app.youlauncher.data.system.DriveInfo
import app.youlauncher.data.system.InstallPlan
import app.youlauncher.data.system.LocationProblem
import app.youlauncher.data.system.RegistryStore
import app.youlauncher.data.system.ShortcutMaker
import app.youlauncher.data.system.ShortcutPlace
import app.youlauncher.data.system.WindowsRegistry
import app.youlauncher.data.system.WindowsShortcuts
import app.youlauncher.resources.*
import app.youlauncher.ui.brand.YouLauncherLogo
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.fileSize
import app.youlauncher.ui.i18n.AppLocale
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.YouLauncherTheme
import app.youlauncher.ui.window.WindowsDwm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

/** Шаги установщика. */
enum class SetupStep { Welcome, Features, Location, Installing, Done }

/** Состояние установщика: выбранная папка, ярлыки, прогресс. Файловые операции в [InstallPlan]. */
class SetupModel(
    val source: Path,
    val version: String,
    private val registry: RegistryStore = WindowsRegistry,
    private val shortcuts: ShortcutMaker = WindowsShortcuts,
    // Подменяются в снимках для README: там не должно быть дисков и имени пользователя этого компьютера.
    val drives: List<DriveInfo> = InstallPlan.drives(),
    val required: Long = InstallPlan.sizeOf(source),
    defaultLocation: Path = InstallPlan.defaultLocation(),
    private val check: (Path) -> LocationProblem? = { InstallPlan.check(it, source, required) },
    /** Папка данных на системном диске (%APPDATA%\YouLauncher); в снимках — условная. */
    val appData: Path = InstallPlan.appDataDir(),
) {
    var step by mutableStateOf(SetupStep.Welcome)
    var forward by mutableStateOf(true)
    val existing: Path? = InstallPlan.installedLocation(registry)
    var location by mutableStateOf((existing ?: defaultLocation).toString())
    var problem by mutableStateOf<LocationProblem?>(null)
    var desktop by mutableStateOf(true)
    var startMenu by mutableStateOf(true)
    var launchAfter by mutableStateOf(true)
    var progress by mutableFloatStateOf(0f)
    var currentFile by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    /** Идёт перенос прежних данных (второй этап установки). */
    var moving by mutableStateOf(false)

    val target: Path? get() = runCatching { Path.of(location.trim()) }.getOrNull()

    /** Игры и данные рядом с программой: по умолчанию да на не системном диске, пока пользователь не выбрал сам. */
    var dataHere by mutableStateOf(target?.let { InstallPlan.suggestLocalData(it) } ?: false)
    private var dataHereTouched = false
    /** Прежние данные на системном диске: при хранении рядом с программой они переносятся. */
    val oldData: Path? = appData.takeIf { InstallPlan.hasData(it) }

    fun chooseDataHere(value: Boolean) {
        dataHereTouched = true
        dataHere = value
    }

    /** Папку сменили: подсказка по месту данных пересчитывается, если пользователь её ещё не трогал. */
    fun onLocationChanged() {
        if (!dataHereTouched) target?.let { dataHere = InstallPlan.suggestLocalData(it) }
    }

    fun go(next: SetupStep) {
        forward = next.ordinal > step.ordinal
        step = next
    }

    suspend fun recheck() {
        val t = target
        problem = if (t == null) LocationProblem.NotAbsolute else withContext(Dispatchers.IO) { check(t) }
    }

    suspend fun install() {
        val dir = target ?: return
        error = null
        go(SetupStep.Installing)
        runCatching {
            withContext(Dispatchers.IO) {
                InstallPlan.copy(source, dir) { p, f -> progress = p; currentFile = f }
                val exe = dir.resolve("${InstallPlan.APP_NAME}.exe")
                if (desktop) shortcuts.folder(ShortcutPlace.Desktop)?.let { shortcuts.create(it, InstallPlan.APP_NAME, exe) }
                if (startMenu) shortcuts.folder(ShortcutPlace.StartMenu)?.let { shortcuts.create(it, InstallPlan.APP_NAME, exe) }
                // Игры рядом с программой: лаунчер увидит portable.txt и будет хранить всё в папке data.
                InstallPlan.setLocalData(dir, dataHere)
                val old = oldData
                if (dataHere && old != null && !InstallPlan.hasData(InstallPlan.localDataDir(dir))) {
                    moving = true
                    progress = 0f
                    InstallPlan.moveData(old, InstallPlan.localDataDir(dir)) { p, f -> progress = p; currentFile = f }
                }
                InstallPlan.register(registry, dir, version, InstallPlan.sizeOf(dir))
            }
        }.onFailure { e ->
            error = if (e is java.nio.file.FileSystemException) "running" else (e.message ?: e::class.simpleName.orEmpty())
        }
        delay(400)
        go(SetupStep.Done)
    }

    fun launchInstalled() {
        val dir = target ?: return
        runCatching { ProcessBuilder(dir.resolve("${InstallPlan.APP_NAME}.exe").toString()).directory(dir.toFile()).start() }
    }
}

/** Точка входа режимов установки и удаления (YouLauncher.exe --setup / --uninstall): без основных данных лаунчера. */
fun runSetup(appDir: Path, version: String, uninstall: Boolean) = application {
    val state = rememberWindowState(size = DpSize(1040.dp, 660.dp), position = WindowPosition(Alignment.Center))
    AppLocale.Provide(AppLanguage.System) {
        val title = stringResource(if (uninstall) Res.string.uninstall_window_title else Res.string.setup_window_title)
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = title,
            undecorated = true,
            resizable = false,
            icon = remember { app.youlauncher.ui.brand.LogoPainter(app.youlauncher.ui.brand.brandLogoColors()) },
        ) {
            LaunchedEffect(window) { WindowsDwm.setRoundedCorners(window, rounded = true) }
            YouLauncherTheme(ThemeSettings(), UiSettings()) {
                val drag: @Composable (Modifier) -> Unit = { m -> WindowDraggableArea(m) { Box(Modifier.fillMaxSize()) } }
                if (uninstall) UninstallScreen(appDir, ::exitApplication, minimize = { state.isMinimized = true }, dragHandle = drag)
                else SetupScreen(remember { SetupModel(appDir, version) }, ::exitApplication, minimize = { state.isMinimized = true }, dragHandle = drag)
            }
        }
    }
}

/** Общая рамка: фон с фигурами, затемнение под текстом, заголовок окна с перетаскиванием. */
@Composable
private fun SetupFrame(
    step: Int,
    dragHandle: @Composable (Modifier) -> Unit,
    busy: Boolean,
    closeEnabled: Boolean,
    onClose: () -> Unit,
    minimize: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Surface(color = s.surface, contentColor = s.onSurface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            SetupBackdrop(step, busy, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to s.surface, 0.42f to s.surface.copy(alpha = 0.94f), 0.62f to s.surface.copy(alpha = 0.35f), 1f to s.surface.copy(alpha = 0f))))
            dragHandle(Modifier.fillMaxWidth().height(52.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TIconButton(Symbols.Minimize, stringResource(Res.string.setup_minimize), minimize)
                TIconButton(Symbols.Close, stringResource(Res.string.setup_close), onClose, enabled = closeEnabled)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetupScreen(model: SetupModel, exit: () -> Unit, minimize: () -> Unit, dragHandle: @Composable (Modifier) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val busy = model.step == SetupStep.Installing
    LaunchedEffect(model.location) { model.onLocationChanged(); delay(250); model.recheck() }
    SetupFrame(model.step.ordinal, dragHandle, busy, closeEnabled = !busy, onClose = exit, minimize = minimize) {
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 32.dp, top = 56.dp, bottom = 28.dp)) {
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = model.step,
                transitionSpec = {
                    val dir = if (model.forward) 1 else -1
                    (slideInHorizontally(spatial) { it / 5 * dir } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { -it / 5 * dir } + fadeOut(effects))
                },
                modifier = Modifier.weight(1f).width(520.dp),
                label = "setup",
            ) { step ->
                when (step) {
                    SetupStep.Welcome -> WelcomeStep(model.version)
                    SetupStep.Features -> FeaturesStep()
                    SetupStep.Location -> LocationStep(model)
                    SetupStep.Installing -> InstallingStep(model)
                    SetupStep.Done -> DoneStep(model)
                }
            }
            BottomBar(
                step = model.step,
                onBack = {
                    when (model.step) {
                        SetupStep.Features -> model.go(SetupStep.Welcome)
                        SetupStep.Location -> model.go(SetupStep.Features)
                        SetupStep.Done -> if (model.error != null) model.go(SetupStep.Location)
                        else -> Unit
                    }
                },
                primaryLabel = when (model.step) {
                    SetupStep.Location -> Res.string.setup_install
                    SetupStep.Done -> if (model.error != null) Res.string.setup_retry else Res.string.setup_finish
                    else -> Res.string.setup_next
                },
                primaryEnabled = when (model.step) {
                    SetupStep.Location -> model.problem == null
                    SetupStep.Installing -> false
                    else -> true
                },
                onPrimary = {
                    when (model.step) {
                        SetupStep.Welcome -> model.go(SetupStep.Features)
                        SetupStep.Features -> model.go(SetupStep.Location)
                        SetupStep.Location -> scope.launch { model.install() }
                        SetupStep.Done -> if (model.error != null) scope.launch { model.install() } else {
                            if (model.launchAfter) model.launchInstalled()
                            exit()
                        }
                        SetupStep.Installing -> Unit
                    }
                },
            )
        }
    }
}

/** Точки шагов (активная — вытянутая «пилюля» на пружине) и кнопки «Назад» / «Далее». */
@Composable
private fun BottomBar(step: SetupStep, onBack: () -> Unit, primaryLabel: StringResource, primaryEnabled: Boolean, onPrimary: () -> Unit) {
    val dots = listOf(SetupStep.Welcome, SetupStep.Features, SetupStep.Location, SetupStep.Done)
    val current = if (step == SetupStep.Installing) 2 else dots.indexOf(step)
    Row(Modifier.width(520.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            dots.forEachIndexed { i, _ ->
                val active = i == current
                val w by animateDpAsState(if (active) 28.dp else 10.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
                Box(Modifier.size(w, 10.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Spacer(Modifier.weight(1f))
        val canBack = step == SetupStep.Features || step == SetupStep.Location
        if (canBack) TextButton(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.setup_back)) }
        Spacer(Modifier.width(8.dp))
        if (step != SetupStep.Installing) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(primaryLabel), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                if (step != SetupStep.Done) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Symbols.ArrowForward, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                }
            }
        }
    }
}

/** Элементы шага появляются по очереди, с пружинным отскоком. */
@Composable
private fun Modifier.appear(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { delay(90L * index); a.animateTo(1f, spec) }
    return graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 36f
    }
}

@Composable
private fun WelcomeStep(version: String) {
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        YouLauncherLogo(Modifier.appear(0), size = 84.dp)
        Spacer(Modifier.height(28.dp))
        Text("YouLauncher", style = MaterialTheme.typography.displayLargeEmphasized, modifier = Modifier.appear(1))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(Res.string.setup_tagline), style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.appear(2))
        Spacer(Modifier.height(18.dp))
        Text(stringResource(Res.string.setup_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(3))
        Spacer(Modifier.height(22.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.appear(4)) {
            Text(stringResource(Res.string.setup_version, version), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeaturesStep() {
    val features = listOf(
        Triple(Symbols.Palette, Res.string.setup_feature_md3_title, Res.string.setup_feature_md3_body),
        Triple(Symbols.Wallpaper, Res.string.setup_feature_bg_title, Res.string.setup_feature_bg_body),
        Triple(Symbols.Extension, Res.string.setup_feature_mods_title, Res.string.setup_feature_mods_body),
        Triple(Symbols.DeployedCode, Res.string.setup_feature_versions_title, Res.string.setup_feature_versions_body),
        Triple(Symbols.AccountCircle, Res.string.setup_feature_accounts_title, Res.string.setup_feature_accounts_body),
    )
    val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Puffy)
    val containers = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Text(stringResource(Res.string.setup_features_title), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.appear(0))
        Spacer(Modifier.height(24.dp))
        features.forEachIndexed { i, (icon, title, body) ->
            Row(Modifier.padding(vertical = 7.dp).appear(i + 1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val (bg, fg) = containers[i % containers.size]
                Box(Modifier.size(52.dp).clip(shapes[i].toShape()).background(bg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LocationStep(model: SetupModel) {
    val browseTitle = stringResource(Res.string.setup_browse_title)
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Text(stringResource(Res.string.setup_location_title), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.appear(0))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.setup_location_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(1))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.appear(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            model.drives.take(4).forEach { d ->
                val selected = model.target?.root == d.root
                val ring by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "drive")
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.width(122.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .border((2 * ring).dp, MaterialTheme.colorScheme.primary.copy(alpha = ring), MaterialTheme.shapes.extraLarge)
                        .selectable(selected, role = Role.RadioButton) { model.location = InstallPlan.locationOn(d).toString() },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Symbols.HardDrive, null, Modifier.size(20.dp))
                            Text(d.label, style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                        LinearProgressIndicator(progress = { 1f - d.freeBytes.toFloat() / d.totalBytes }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(Res.string.setup_drive_free, fileSize(d.freeBytes), fileSize(d.totalBytes)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.appear(3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = model.location,
                onValueChange = { model.location = it },
                label = { Text(stringResource(Res.string.setup_folder)) },
                singleLine = true,
                isError = model.problem != null,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                chooseFolder(browseTitle, model.target)?.let { dir ->
                    model.location = (if (dir.fileName?.toString().equals(InstallPlan.APP_NAME, true) || InstallPlan.isOurs(dir)) dir else dir.resolve(InstallPlan.APP_NAME)).toString()
                }
            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.setup_browse)) }
        }
        Spacer(Modifier.height(8.dp))
        val problem = model.problem
        Text(
            when {
                problem != null -> stringResource(problemText(problem))
                model.target?.let { InstallPlan.isOurs(it) } == true -> stringResource(Res.string.setup_update_found)
                else -> stringResource(Res.string.setup_required, fileSize(model.required))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.appear(4),
        )
        Spacer(Modifier.height(6.dp))
        // Где будут игры: без этой галочки гигабайты экземпляров оказывались на диске C при любом выбранном диске.
        val dataHint = when {
            !model.dataHere -> stringResource(Res.string.setup_data_appdata_hint, model.appData.toString())
            model.oldData != null && model.target?.let { InstallPlan.hasData(InstallPlan.localDataDir(it)) } != true ->
                stringResource(Res.string.setup_data_move_hint, model.oldData.toString())
            else -> stringResource(Res.string.setup_data_here_hint, model.target?.let { InstallPlan.localDataDir(it).toString() }.orEmpty())
        }
        CheckRow(stringResource(Res.string.setup_data_here), model.dataHere, model::chooseDataHere, Modifier.appear(5), hint = dataHint)
        CheckRow(stringResource(Res.string.setup_desktop_shortcut), model.desktop, { model.desktop = it }, Modifier.appear(6))
        CheckRow(stringResource(Res.string.setup_start_menu), model.startMenu, { model.startMenu = it }, Modifier.appear(7))
    }
}

private fun problemText(p: LocationProblem): StringResource = when (p) {
    LocationProblem.NotAbsolute -> Res.string.setup_problem_not_absolute
    LocationProblem.NotWritable -> Res.string.setup_problem_not_writable
    LocationProblem.NotEnoughSpace -> Res.string.setup_problem_space
    LocationProblem.InsideSource -> Res.string.setup_problem_inside
    LocationProblem.NotEmpty -> Res.string.setup_problem_not_empty
}

private fun chooseFolder(title: String, start: Path?): Path? {
    val chooser = javax.swing.JFileChooser(start?.parent?.toFile()).apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    Row(
        modifier.clip(MaterialTheme.shapes.large).toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallingStep(model: SetupModel) {
    val shown by animateFloatAsState(model.progress, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "progress")
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        LoadingIndicator(Modifier.size(72.dp).appear(0))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(if (model.moving) Res.string.setup_moving_title else Res.string.setup_installing_title), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.appear(1))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(if (model.moving) Res.string.setup_moving_body else Res.string.setup_installing_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(2))
        Spacer(Modifier.height(28.dp))
        LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth().appear(3))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().appear(4)) {
            Text(model.currentFile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${(shown * 100).toInt()}%", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DoneStep(model: SetupModel) {
    val failed = model.error != null
    val pop = remember { Animatable(0.3f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(failed) { pop.snapTo(0.3f); pop.animateTo(1f, spec) }
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        val (bg, fg) = if (failed) MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Box(
            Modifier.size(96.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(MaterialShapes.Sunny.toShape()).background(bg),
            contentAlignment = Alignment.Center,
        ) { Icon(if (failed) Symbols.ErrorFilled else Symbols.Check, null, tint = fg, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(if (failed) Res.string.setup_failed_title else Res.string.setup_done_title), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.appear(1))
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                model.error == "running" -> stringResource(Res.string.setup_running)
                failed -> model.error.orEmpty()
                else -> stringResource(Res.string.setup_done_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.appear(2),
        )
        if (!failed) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.appear(3)) {
                Text(model.location, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            CheckRow(stringResource(Res.string.setup_launch), model.launchAfter, { model.launchAfter = it }, Modifier.appear(4))
        }
    }
}

/** Удаление в том же оформлении: вопрос, галочка «удалить и мои данные», прогресс и итог. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UninstallScreen(
    appDir: Path,
    exit: () -> Unit,
    minimize: () -> Unit,
    dragHandle: @Composable (Modifier) -> Unit = {},
    startStage: Int = 0,
    // Игры рядом с программой (portable.txt) — данные в её папке data, иначе в %APPDATA%.
    dataDir: Path = if (java.nio.file.Files.exists(appDir.resolve(InstallPlan.PORTABLE_MARKER))) InstallPlan.localDataDir(appDir) else InstallPlan.appDataDir(),
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(startStage) }
    var removeData by remember { mutableStateOf(false) }
    // После удаления папка программы стирается отдельным процессом, когда окно закроется.
    // Данные внутри папки программы остаются, если их не просили удалить.
    val finish = { if (stage == 2) InstallPlan.scheduleRemoval(appDir, keepData = !removeData); exit() }
    SetupFrame(stage * 2, dragHandle, busy = stage == 1, closeEnabled = stage != 1, onClose = finish, minimize = minimize) {
        val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val scaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 32.dp, top = 56.dp, bottom = 28.dp)) {
            AnimatedContent(stage, transitionSpec = { (fadeIn(fadeInSpec) + scaleIn(scaleSpec, 0.94f)) togetherWith fadeOut(fadeOutSpec) }, modifier = Modifier.weight(1f).width(520.dp), label = "uninstall") { st ->
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    when (st) {
                        0 -> {
                            YouLauncherLogo(Modifier.appear(0), size = 72.dp)
                            Spacer(Modifier.height(24.dp))
                            Text(stringResource(Res.string.uninstall_title), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.appear(1))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(Res.string.uninstall_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(2))
                            Spacer(Modifier.height(16.dp))
                            CheckRow(stringResource(Res.string.uninstall_data), removeData, { removeData = it }, Modifier.appear(3))
                            Text(stringResource(Res.string.uninstall_data_note, dataDir.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).appear(4))
                        }
                        1 -> {
                            LoadingIndicator(Modifier.size(72.dp))
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(Res.string.uninstall_removing), style = MaterialTheme.typography.displaySmallEmphasized)
                        }
                        else -> {
                            Box(Modifier.size(96.dp).clip(MaterialShapes.Sunny.toShape()).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Symbols.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(stringResource(Res.string.uninstall_done_title), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.appear(1))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(Res.string.uninstall_done_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(2))
                        }
                    }
                }
            }
            Row(Modifier.width(520.dp), horizontalArrangement = Arrangement.End) {
                when (stage) {
                    0 -> {
                        TextButton(onClick = exit, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                stage = 1
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val links = ShortcutPlace.entries.mapNotNull { WindowsShortcuts.folder(it)?.resolve("${InstallPlan.APP_NAME}.lnk") }
                                        InstallPlan.uninstall(WindowsRegistry, links, if (removeData) dataDir else null)
                                    }
                                    delay(500)
                                    stage = 2
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(Res.string.uninstall_confirm)) }
                    }
                    2 -> Button(onClick = finish, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.setup_finish)) }
                }
            }
        }
    }
}
