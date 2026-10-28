/* Adapted from Samsung Electronics SystemUI S741BXXS2AZI5. */
package com.android.systemui.media.oneui

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import com.android.systemui.common.ui.compose.Icon as PlayerIcon
import com.android.systemui.common.ui.compose.OneUiFontFamily
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OneUiPlayerAction(
    val description: String,
    val onClick: (() -> Unit)?,
    val icon: Icon? = null,
)

/** Shared expanded player for the status bar and the One UI QS media tile. */
@Composable
fun OneUiDynamicPlayer(
    title: String,
    artist: String,
    artwork: Icon?,
    playing: Boolean,
    previous: OneUiPlayerAction?,
    playPause: OneUiPlayerAction?,
    next: OneUiPlayerAction?,
    progress: Float,
    progressText: String,
    durationText: String,
    onSeek: ((Float) -> Unit)?,
    onSeekFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenApp: (() -> Unit)? = null,
    output: @Composable () -> Unit = {},
    pageIndicator: @Composable () -> Unit = {},
    progressContent: (@Composable () -> Unit)? = null,
    deviceName: String? = null,
    appIcon: Icon? = null,
    leadingAction: OneUiPlayerAction? = null,
    trailingAction: OneUiPlayerAction? = null,
) {
    val drawable = (artwork as? Icon.Loaded)?.drawable
    val colors by produceState(OneUiMediaColors.Default, drawable) {
        value = withContext(Dispatchers.Default) {
            runCatching { OneUiMediaColors.fromDrawable(drawable) }
                .getOrDefault(OneUiMediaColors.Default)
        }
    }
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.fillMaxWidth().heightIn(min = 164.dp).clip(shape).background(Color(colors.primary))) {
        if (artwork != null) {
            PlayerArtwork(artwork, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.7f)))))
        } else {
            AndroidView(
                factory = { OneUiMediaShaderView(it) },
                update = { it.colors = colors; it.playing = playing },
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.oneui_media_this_phone),
                    modifier = Modifier.weight(1f), color = Color.White,
                    fontFamily = OneUiFontFamily, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                output()
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(26.dp)
                    .clickable(enabled = onOpenApp != null) { onOpenApp?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                appIcon?.let { PlayerIcon(it, modifier = Modifier.size(14.dp), tint = Color.White) }
                Text(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · "),
                    modifier = Modifier.weight(1f).basicMarquee(), color = Color.White,
                    fontFamily = OneUiFontFamily, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            progressContent?.invoke()
                ?: PlayerSeekBar(progress, progressText, durationText, onSeek, onSeekFinished)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val buttonSize = minOf(48.dp, maxWidth / 5)
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtraAction(leadingAction, buttonSize)
                    AnimatedAction(previous, R.raw.oneui_media_previous, buttonSize)
                    PlayPauseAction(playPause, playing, buttonSize)
                    AnimatedAction(next, R.raw.oneui_media_next, buttonSize)
                    ExtraAction(trailingAction, buttonSize)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { pageIndicator() }
        }
    }
}

@Composable
private fun PlayerArtwork(artwork: Icon?, modifier: Modifier) {
    when (artwork) {
        is Icon.Loaded -> Image(rememberDrawablePainter(artwork.drawable), null,
            modifier, contentScale = ContentScale.Crop)
        is Icon.Resource -> Image(painterResource(artwork.resId), null,
            modifier, contentScale = ContentScale.Crop)
        null -> Box(modifier.background(Color.White.copy(alpha = 0.12f)))
    }
}

@Composable
private fun PlayPauseAction(action: OneUiPlayerAction?, playing: Boolean, size: Dp) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.oneui_media_play_pause))
    val progress = remember { Animatable(if (playing) 0.5f else 0f) }
    var previousPlaying by remember { mutableStateOf(playing) }
    LaunchedEffect(playing, composition) {
        if (playing != previousPlaying) {
            progress.snapTo(if (playing) 0f else 0.5f)
            progress.animateTo(if (playing) 0.5f else 1f, tween(300))
            previousPlaying = playing
        }
    }
    ActionContainer(action, filled = true, size = size) {
        LottieAnimation(composition, progress = { progress.value }, modifier = Modifier.size(size * 0.75f))
    }
}

@Composable
private fun AnimatedAction(action: OneUiPlayerAction?, @RawRes animation: Int, size: Dp) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    val progress = remember { Animatable(0f) }
    var clicks by remember { mutableStateOf(0) }
    LaunchedEffect(clicks) {
        if (clicks != 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(300))
        }
    }
    ActionContainer(action, filled = true, size = size, onClick = { clicks++ }) {
        LottieAnimation(composition, progress = { progress.value }, modifier = Modifier.size(size * (2f / 3f)))
    }
}

@Composable
private fun ActionContainer(
    action: OneUiPlayerAction?,
    onClick: () -> Unit = {},
    filled: Boolean = false,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
            action?.onClick?.invoke()
        },
        enabled = action?.onClick != null,
        modifier = Modifier.size(size)
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .semantics { contentDescription = action?.description.orEmpty() },
        content = content,
    )
}

@Composable
private fun ExtraAction(action: OneUiPlayerAction?, size: Dp) {
    if (action?.icon == null) {
        Spacer(Modifier.size(size))
    } else {
        ActionContainer(action, size = size) {
            PlayerIcon(action.icon, Modifier.size(size * 0.5f), tint = Color.White)
        }
    }
}

@Composable
private fun PlayerSeekBar(
    progress: Float,
    progressText: String,
    durationText: String,
    onSeek: ((Float) -> Unit)?,
    onSeekFinished: (() -> Unit)?,
) {
    Column {
        AndroidView(
            factory = { OneUiPlayerSeekBar(it) },
            update = {
                it.onSeek = onSeek
                it.onSeekFinished = onSeekFinished
                it.isEnabled = onSeek != null
                it.contentDescription = "$progressText / $durationText"
                it.setFraction(progress.coerceIn(0f, 1f))
            },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(progressText, color = Color.White.copy(alpha = 0.75f),
                fontFamily = OneUiFontFamily, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(durationText, color = Color.White.copy(alpha = 0.75f),
                fontFamily = OneUiFontFamily, style = MaterialTheme.typography.labelSmall)
        }
    }
}
