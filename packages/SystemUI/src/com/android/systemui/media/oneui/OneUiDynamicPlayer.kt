/* Adapted from Samsung Electronics SystemUI S741BXXS2AZI5. */
package com.android.systemui.media.oneui

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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

data class OneUiPlayerAction(val description: String, val onClick: (() -> Unit)?)

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
) {
    val drawable = (artwork as? Icon.Loaded)?.drawable
    val colors by produceState(OneUiMediaColors.Default, drawable) {
        value = withContext(Dispatchers.Default) {
            runCatching { OneUiMediaColors.fromDrawable(drawable) }
                .getOrDefault(OneUiMediaColors.Default)
        }
    }
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(Color(colors.primary))) {
        AndroidView(
            factory = { OneUiMediaShaderView(it) },
            update = { it.colors = colors; it.playing = playing },
            modifier = Modifier.matchParentSize(),
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f)
                        .clickable(enabled = onOpenApp != null) { onOpenApp?.invoke() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlayerArtwork(artwork, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(title, Modifier.basicMarquee(), color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(artist, color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                output()
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedAction(previous, R.raw.oneui_media_previous)
                PlayPauseAction(playPause, playing)
                AnimatedAction(next, R.raw.oneui_media_next)
            }
            progressContent?.invoke()
                ?: PlayerSeekBar(progress, progressText, durationText, onSeek, onSeekFinished)
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
private fun PlayPauseAction(action: OneUiPlayerAction?, playing: Boolean) {
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
    ActionContainer(action) {
        LottieAnimation(composition, progress = { progress.value }, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun AnimatedAction(action: OneUiPlayerAction?, @RawRes animation: Int) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    val progress = remember { Animatable(0f) }
    var clicks by remember { mutableStateOf(0) }
    LaunchedEffect(clicks) {
        if (clicks != 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(300))
        }
    }
    ActionContainer(action, onClick = { clicks++ }) {
        LottieAnimation(composition, progress = { progress.value }, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun ActionContainer(
    action: OneUiPlayerAction?,
    onClick: () -> Unit = {},
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
        modifier = Modifier.size(60.dp, 48.dp)
            .semantics { contentDescription = action?.description.orEmpty() },
        content = content,
    )
}

@Composable
private fun PlayerSeekBar(
    progress: Float,
    progressText: String,
    durationText: String,
    onSeek: ((Float) -> Unit)?,
    onSeekFinished: (() -> Unit)?,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var pendingProgress by remember { mutableStateOf(0f) }
    Column {
        Slider(
            value = if (scrubbing) pendingProgress else progress.coerceIn(0f, 1f),
            onValueChange = {
                scrubbing = true
                pendingProgress = it
                onSeek?.invoke(it)
            },
            onValueChangeFinished = { onSeekFinished?.invoke(); scrubbing = false },
            enabled = onSeek != null,
            colors = SliderDefaults.colors(thumbColor = Color.White,
                activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(progressText, color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(durationText, color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
