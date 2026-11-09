/*
 * SPDX-FileCopyrightText: 2026 DescartesOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.qs.composefragment

import android.media.AudioManager
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.qs.footer.ui.compose.rememberSystemSettingEnabled
import com.android.systemui.res.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun MotorolaBrightnessSlider(
    viewModel: BrightnessSliderViewModel,
    colors: MotorolaColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val min = viewModel.minBrightness.value
    val max = viewModel.maxBrightness.value
    val span = (max - min).coerceAtLeast(1)
    val value = viewModel.currentBrightness.value
    val fraction = if (value < 0) 0.5f else ((value - min).toFloat() / span).coerceIn(0f, 1f)
    val automatic by rememberSystemSettingEnabled(Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
    fun gamma(f: Float) = GammaBrightness((min + span * f).roundToInt().coerceIn(min, max))

    MotorolaVerticalSlider(
        fraction = fraction,
        colors = colors,
        icon = R.drawable.ic_coloros_brightness,
        tint = Color(0xFFF5A000),
        label = stringResource(R.string.quick_settings_brightness_dialog_title),
        onChange = { f -> scope.launch { viewModel.onDrag(Drag.Dragging(gamma(f))) } },
        onStop = { f -> scope.launch { viewModel.onDrag(Drag.Stopped(gamma(f))) } },
        onLongPress = {
            Settings.System.putIntForUser(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (automatic) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                UserHandle.USER_CURRENT,
            )
        },
        automatic = automatic,
        modifier = modifier,
    )
}

@Composable
internal fun MotorolaVolumeSlider(
    interactor: AudioVolumeInteractor,
    colors: MotorolaColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val stream = remember { AudioStream(AudioManager.STREAM_MUSIC) }
    val model by remember(interactor) { interactor.getAudioStream(stream) }
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val min = model?.minVolume ?: 0
    val max = model?.maxVolume ?: 1
    val span = (max - min).coerceAtLeast(1)
    val fraction = model?.let { (it.volume - min).toFloat() / span } ?: 0f
    fun volume(f: Float) = (min + span * f).roundToInt().coerceIn(min, max)

    MotorolaVerticalSlider(
        fraction = if (model?.isMuted == true) 0f else fraction,
        colors = colors,
        icon = R.drawable.ic_music_note,
        tint = colors.onActive,
        label = stringResource(R.string.stream_music),
        onChange = { f -> scope.launch { interactor.setVolume(stream, volume(f)) } },
        onStop = { f -> scope.launch { interactor.setVolume(stream, volume(f)) } },
        onLongPress = {
            audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        },
        steps = span - 1,
        modifier = modifier,
    )
}

@Composable
private fun MotorolaVerticalSlider(
    fraction: Float,
    colors: MotorolaColors,
    icon: Int,
    tint: Color,
    label: String,
    onChange: (Float) -> Unit,
    onStop: (Float) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    automatic: Boolean = false,
    steps: Int = 0,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var local by remember { mutableFloatStateOf(fraction.coerceIn(0f, 1f)) }
    var dragging by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onChange)
    val stop by rememberUpdatedState(onStop)
    val longPress by rememberUpdatedState(onLongPress)
    LaunchedEffect(fraction, dragging) {
        if (!dragging) {
            delay(150L)
            local = fraction.coerceIn(0f, 1f)
        }
    }
    fun applyProgress(value: Float) {
        local = value.coerceIn(0f, 1f)
        change(local)
        stop(local)
    }

    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(colors.surface)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(local, 0f..1f, steps.coerceAtLeast(0))
                setProgress { applyProgress(it); true }
            }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) {
                    val amount = if (steps > 0) 1f / (steps + 1) else 0.05f
                    applyProgress(local + (if (it.key == Key.DirectionUp) amount else -amount))
                    true
                } else false
            }
            .focusable()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = local
                    var moved = false
                    var held = false
                    val job = scope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        held = true
                        longPress()
                    }
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    dragging = true
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) break
                            if ((pointer.position - down.position).getDistance() >
                                viewConfiguration.touchSlop) {
                                moved = true
                                job.cancel()
                            }
                            if (!held && moved) {
                                local = (start + (down.position.y - pointer.position.y) /
                                    size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                change(local)
                            }
                            pointer.consume()
                        }
                    } finally {
                        job.cancel()
                        if (!held && moved) stop(local)
                        dragging = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            },
    ) {
        if (local > 0f) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .fillMaxHeight(local.coerceIn(0f, 1f)).background(colors.active)
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon), null,
                tint = if (local >= 0.15f) tint else colors.onSurface,
                modifier = Modifier.size(28.dp),
            )
            if (automatic) {
                Text("A", color = if (local >= 0.15f) tint else colors.onSurface,
                    fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(colors.active, RoundedCornerShape(4.dp)))
            }
        }
    }
}
