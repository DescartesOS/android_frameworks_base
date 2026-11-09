/*
 * SPDX-FileCopyrightText: 2026 DescartesOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.qs.composefragment

import android.app.PendingIntent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.res.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Composable
internal fun MotorolaMediaCard(
    data: MediaData?,
    onOpen: (PendingIntent) -> Unit,
    onOutput: (MediaData) -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = true,
) {
    val context = LocalContext.current
    val colors = motorolaColors()
    val art by produceState<ImageBitmap?>(null, data?.artwork) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                data?.artwork?.loadDrawable(context)?.toBitmap()
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val appIcon by produceState<ImageBitmap?>(null, data?.appIcon) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                data?.appIcon?.loadDrawable(context)?.toBitmap(width = 48, height = 48)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val controller = remember(data?.token) {
        data?.token?.let { runCatching { MediaController(context, it) }.getOrNull() }
    }
    var playback by remember(controller) { mutableStateOf(controller?.playbackState) }
    var duration by remember(controller) {
        mutableLongStateOf(controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
    }
    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { playback = state }
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            }
        }
        controller?.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { controller?.unregisterCallback(callback) }
    }
    val playing = playback?.state?.let { it == PlaybackState.STATE_PLAYING }
        ?: (data?.isPlaying == true)
    var now by remember(controller) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(controller, playing, listening) {
        if (playing && listening) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(500L)
            }
        }
    }
    val position = playback?.let {
        val delta = if (playing && it.lastPositionUpdateTime > 0L)
            (now - it.lastPositionUpdateTime).coerceAtLeast(0L) * it.playbackSpeed else 0f
        (it.position + delta.toLong()).coerceIn(0L, duration.coerceAtLeast(0L))
    } ?: 0L
    val fraction = if (duration > 0L) position.toFloat() / duration else 0f
    val hasArt = art != null
    val foreground = if (hasArt) Color.White else colors.onSurface
    val buttons = data?.semanticActions
    val transport = controller?.transportControls
    val previous = buttons?.prevOrCustom
    val next = buttons?.nextOrCustom
    val playPause = buttons?.playOrPause

    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(colors.surface)
            .clickable(enabled = data?.clickIntent != null) {
                data?.clickIntent?.let(onOpen)
            },
    ) {
        art?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f),
                        Color.Black.copy(alpha = 0.55f)))
                )
            )
        }
        Column(
            Modifier.fillMaxSize().padding(start = 18.dp, end = 12.dp, top = 7.dp, bottom = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        appIcon?.let { Image(it, null, Modifier.size(12.dp)) }
                        Text(
                            data?.song?.toString() ?: stringResource(R.string.qs_coloros_no_media),
                            color = foreground, fontFamily = FontFamily.Default, fontSize = 9.sp, lineHeight = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    data?.artist?.toString()?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = foreground.copy(alpha = 0.85f), fontFamily = FontFamily.Default, fontSize = 9.sp, lineHeight = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (data != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFEBEBEB))
                            .clickable(enabled = data.device?.enabled != false) { onOutput(data) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_motorola_phone), null,
                            Modifier.size(9.dp), tint = Color(0xFF202424))
                        Text(
                            data.device?.name?.toString()
                                ?: stringResource(R.string.qs_motorola_this_phone),
                            color = Color(0xFF202424), fontFamily = FontFamily.Default, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 64.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MotorolaMediaProgress(
                    fraction, playing, now, foreground,
                    enabled = duration > 0L && playback?.actions?.and(PlaybackState.ACTION_SEEK_TO) == PlaybackState.ACTION_SEEK_TO,
                    onSeek = { transport?.seekTo((it * duration).toLong()) },
                    modifier = Modifier.weight(1f).height(40.dp),
                )
                val custom = buttons?.custom0 ?: buttons?.custom1
                if (custom != null) {
                    MotorolaCustomMediaButton(custom, foreground)
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                MotorolaMediaButton(
                    R.drawable.ic_coloros_media_previous,
                    stringResource(R.string.controls_media_button_prev),
                    foreground,
                    previous?.action != null ||
                        playback?.actions?.and(PlaybackState.ACTION_SKIP_TO_PREVIOUS) == PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                ) { previous?.action?.run() ?: transport?.skipToPrevious() }
                MotorolaMediaButton(
                    if (playing) R.drawable.ic_coloros_media_pause else R.drawable.ic_coloros_media_play,
                    stringResource(if (playing) R.string.controls_media_button_pause
                        else R.string.controls_media_button_play),
                    foreground, playPause?.action != null || transport != null,
                ) {
                    val action = playPause?.action
                    if (action != null) action.run()
                    else if (playing) transport?.pause() else transport?.play()
                }
                MotorolaMediaButton(
                    R.drawable.ic_coloros_media_next,
                    stringResource(R.string.controls_media_button_next),
                    foreground,
                    next?.action != null ||
                        playback?.actions?.and(PlaybackState.ACTION_SKIP_TO_NEXT) == PlaybackState.ACTION_SKIP_TO_NEXT,
                ) { next?.action?.run() ?: transport?.skipToNext() }
                MotorolaMediaButton(
                    R.drawable.ic_coloros_media_output,
                    stringResource(R.string.qs_motorola_output),
                    foreground, data != null && data.device?.enabled != false,
                ) { data?.let(onOutput) }
            }
        }
    }
}

@Composable
private fun MotorolaMediaButton(
    icon: Int,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), label,
            tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MotorolaCustomMediaButton(action: MediaAction, tint: Color) {
    val bitmap = remember(action.icon) { action.icon?.toBitmap()?.asImageBitmap() }
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(18.dp))
            .clickable(enabled = action.action != null) { action.action?.run() }
            .semantics { contentDescription = action.contentDescription?.toString().orEmpty() },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it, null, Modifier.size(18.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
        }
    }
}

@Composable
private fun MotorolaMediaProgress(
    fraction: Float,
    playing: Boolean,
    phase: Long,
    color: Color,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.qs_motorola_media_progress)
    Canvas(
        modifier.semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            if (enabled) setProgress { onSeek(it.coerceIn(0f, 1f)); true }
        }.pointerInput(enabled, onSeek) {
            if (enabled) detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
        },
    ) {
        val end = size.width * fraction.coerceIn(0f, 1f)
        val center = size.height / 2f
        drawLine(color.copy(alpha = 0.35f), Offset.Zero.copy(y = center),
            Offset(size.width, center), strokeWidth = 1.dp.toPx())
        val path = Path()
        val amplitude = if (playing) 1.dp.toPx() else 0f
        val wavelength = 10.dp.toPx()
        val shift = (phase % 1_000L) / 1_000f * 6.28f
        path.moveTo(0f, center)
        var x = 0f
        while (x <= end) {
            path.lineTo(x, center + sin(x / wavelength * 6.28f - shift) * amplitude)
            x += 1.dp.toPx()
        }
        drawPath(path, color, style = Stroke(1.dp.toPx()))
        drawCircle(color, 1.5.dp.toPx(), Offset(end, center))
    }
}
