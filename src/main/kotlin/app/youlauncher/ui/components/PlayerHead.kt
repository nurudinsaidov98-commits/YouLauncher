package app.youlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.youlauncher.ui.background.SceneGenerator
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * Аватар игрока: пиксельная «голова» 8×8, нарисованная кодом. Узор и палитра выводятся из UUID,
 * поэтому у каждого аккаунта свой стабильный аватар. Если у аккаунта есть скин (Ely.by),
 * рисуется лицо из текстуры скина (слой головы 8×8 и верхний слой).
 */
@Composable
fun PlayerHead(uuid: String, size: Dp = 40.dp, modifier: Modifier = Modifier, skin: ImageBitmap? = null) {
    val dark = app.youlauncher.ui.theme.LocalIsDark.current
    val pattern = remember(uuid) { HeadPattern.from(uuid) }
    val scheme = remember(uuid, dark) { SceneSchemes.get(SceneGenerator.seedFor(uuid), dark) }
    val palette = remember(scheme) {
        listOf(scheme.primary, scheme.primaryContainer, scheme.tertiary, scheme.secondaryContainer, scheme.onPrimaryContainer, scheme.tertiaryContainer)
    }
    Canvas(modifier.size(size).clip(MaterialTheme.shapes.small)) {
        if (skin != null && skin.width >= 64) drawSkinFace(skin) else drawPattern(pattern, palette)
    }
}

/** Аватар аккаунта: лицо из скина Ely.by, пока скин грузится или если его нет, узор из UUID. */
@Composable
fun AccountHead(account: app.youlauncher.core.model.Account, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val skin = account.skinUrl?.let { app.youlauncher.ui.images.rememberRemoteImage(it) }
    PlayerHead(account.uuid, size, modifier, skin)
}

/** Узор 8×8: левая половина случайна, правая зеркальна (как у классических идентиконов). */
class HeadPattern(val cells: IntArray) {
    companion object {
        fun from(uuid: String): HeadPattern {
            val rnd = java.util.Random(SceneGenerator.hash("head:$uuid"))
            val cells = IntArray(64)
            for (y in 0 until 8) for (x in 0 until 4) {
                val v = when {
                    y == 4 && (x == 1 || x == 2) -> 4 // глаза
                    y >= 6 -> if (rnd.nextFloat() < 0.6f) 2 else 3
                    y <= 1 -> if (rnd.nextFloat() < 0.75f) 0 else 5
                    else -> if (rnd.nextFloat() < 0.8f) 1 else 3
                }
                cells[y * 8 + x] = v
                cells[y * 8 + (7 - x)] = v
            }
            return HeadPattern(cells)
        }
    }
}

private fun DrawScope.drawPattern(pattern: HeadPattern, palette: List<Color>) {
    val cell = size.width / 8f
    for (i in 0 until 64) {
        val x = i % 8
        val y = i / 8
        drawRect(palette[pattern.cells[i]], topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
    }
}

private fun DrawScope.drawSkinFace(skin: ImageBitmap) {
    val scale = skin.width / 64
    val dst = IntSize(size.width.toInt(), size.height.toInt())
    drawImage(skin, IntOffset(8 * scale, 8 * scale), IntSize(8 * scale, 8 * scale), dstSize = dst, filterQuality = FilterQuality.None)
    drawImage(skin, IntOffset(40 * scale, 8 * scale), IntSize(8 * scale, 8 * scale), dstSize = dst, filterQuality = FilterQuality.None)
}

/** Скин активного аккаунта Ely.by (null у офлайн-аккаунта, без скина или без аккаунта). */
@Composable
fun rememberActiveSkinUrl(): String? {
    val accounts = app.youlauncher.ui.LocalAppGraph.current.accounts
    val list by accounts.accounts.collectAsState()
    val activeId by accounts.activeId.collectAsState()
    return list.firstOrNull { it.id == activeId }?.takeIf { it.type != app.youlauncher.core.model.AccountType.Offline }?.skinUrl
}

/**
 * Аватар профиля в круге: голова скина Ely.by, своя картинка или узор (см. [app.youlauncher.core.profile.AvatarSource]).
 * Пока скин грузится, виден узор, поэтому аватар не мигает пустотой.
 */
@Composable
fun ProfileAvatar(profile: app.youlauncher.core.profile.Profile, size: Dp, modifier: Modifier = Modifier, pictureMaxSize: Int = 256) {
    val source = app.youlauncher.core.profile.AvatarSource.choose(profile, rememberActiveSkinUrl())
    val fallbackSeed = profile.avatarSeed.ifBlank { profile.id }
    androidx.compose.foundation.layout.Box(modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        when (source) {
            is app.youlauncher.core.profile.AvatarSource.Skin ->
                PlayerHead(fallbackSeed, size, skin = app.youlauncher.ui.images.rememberRemoteImage(source.url))
            is app.youlauncher.core.profile.AvatarSource.Picture -> {
                val image = app.youlauncher.ui.images.rememberRemoteImage(source.path, pictureMaxSize)
                if (image != null) androidx.compose.foundation.Image(image, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(size))
                else PlayerHead(fallbackSeed, size)
            }
            is app.youlauncher.core.profile.AvatarSource.Pattern -> PlayerHead(source.seed, size)
        }
    }
}
