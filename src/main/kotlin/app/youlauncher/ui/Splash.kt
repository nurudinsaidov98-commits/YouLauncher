package app.youlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.youlauncher.resources.Res
import app.youlauncher.resources.app_disclaimer
import app.youlauncher.resources.app_name
import app.youlauncher.ui.brand.YouLauncherLogo
import org.jetbrains.compose.resources.stringResource

/** Загрузочный экран: холмы логотипа поднимаются на медленной пространственной пружине. */
@Composable
fun SplashScreen() {
    val rise = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { rise.animateTo(1f, spec) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                YouLauncherLogo(size = 112.dp, progress = rise.value)
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displaySmallEmphasized)
                LoadingIndicator(Modifier.size(48.dp))
                Text(stringResource(Res.string.app_disclaimer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
