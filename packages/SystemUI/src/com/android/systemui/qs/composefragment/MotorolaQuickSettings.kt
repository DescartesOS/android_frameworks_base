/*
 * SPDX-FileCopyrightText: 2026 DescartesOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.qs.composefragment

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.systemui.animation.Expandable
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon as TileIcon
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.res.R
import java.util.Locale
import kotlinx.coroutines.delay

internal const val QS_PANEL_STYLE_MOTOROLA = 6
internal val LocalMotorolaQsStyle = compositionLocalOf { false }
private val MotorolaConnectivitySpecs =
    setOf("wifi", "internet", "cell", "mobile_data", "mobiledata", "bt", "bluetooth")

internal data class MotorolaColors(
    val surface: Color,
    val onSurface: Color,
    val active: Color,
    val onActive: Color,
    val iconBubble: Color,
)

@Composable
internal fun motorolaColors(): MotorolaColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return MotorolaColors(
        surface = if (dark) Color(0xFF18191B) else Color.White,
        onSurface = if (dark) Color(0xFFFAFAFA) else Color(0xFF202424),
        active = if (dark) Color(0xFFEBEBEB) else scheme.primaryContainer,
        onActive = Color(0xFF54816D),
        iconBubble = if (dark) Color(0xFF323638) else Color(0xFFEAEEEE),
    )
}

@Composable
internal fun rememberMotorolaQsStyle(): Boolean {
    val context = LocalContext.current
    fun read() = Settings.System.getIntForUser(
        context.contentResolver, Settings.System.QS_PANEL_STYLE, 0, UserHandle.USER_CURRENT
    ) == QS_PANEL_STYLE_MOTOROLA
    var enabled by remember(context) { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = read() }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { enabled = read() }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_PANEL_STYLE),
            false, observer, UserHandle.USER_ALL,
        )
        context.registerReceiver(
            receiver, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_EXPORTED
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return enabled
}

@Composable
internal fun MotorolaQuickSettingsLayout(
    tiles: List<TileViewModel>,
    qsHost: QSHost,
    brightnessViewModel: BrightnessSliderViewModel,
    audioVolumeInteractor: AudioVolumeInteractor,
    data: MediaData?,
    listening: () -> Boolean,
    onEdit: () -> Unit,
    onOpenMedia: (PendingIntent) -> Unit,
    onOutputSwitcher: (MediaData) -> Unit,
    onOpenConnectivity: (String) -> Unit,
    modifier: Modifier = Modifier,
    headerHeight: Dp = 102.dp,
    mediaHost: @Composable (Modifier) -> Unit = {},
) {
    val view = LocalView.current
    val present = tiles.associateBy { it.spec.spec }
    val userId = qsHost.userId
    val temporary = remember(qsHost, present.keys, userId, view) {
        listOf("wifi", "cell", "bt").filter { it !in present }.mapNotNull { spec ->
            qsHost.createTile(spec)?.let { tile ->
                tile.tileSpec = spec
                tile.userSwitch(userId)
                spec to tile
            }
        }.toMap()
    }
    DisposableEffect(temporary) {
        onDispose { temporary.values.forEach(QSTile::destroy) }
    }
    val pinned = remember(tiles, temporary, view) {
        listOf("wifi", "cell", "bt").map { spec ->
            present[spec] ?: temporary[spec]?.let {
                TileViewModel(it, TileSpec.create(spec), Expandable.fromView(view))
            }
        }
    }
    val rest = remember(tiles) { tiles.filterNot { it.spec.spec in MotorolaConnectivitySpecs } }
    TileListener(pinned.filterNotNull() + rest, listening)
    val colors = motorolaColors()

    BoxWithConstraints(modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        val gap = 20.dp
        val columns = 4
        val cell = ((maxWidth - gap * (columns - 1)) / columns)
        val controlsHeight = 180.dp
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MotorolaHeader(onEdit, Modifier.height(headerHeight), listening())
            Row(
                Modifier.fillMaxWidth().height(controlsHeight),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                Column(
                    Modifier.width(cell * 2 + gap).fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp)).background(colors.surface)
                        .padding(horizontal = 17.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val labels = listOf(
                        stringResource(R.string.quick_settings_wifi_label),
                        stringResource(R.string.quick_settings_cellular_detail_title),
                        stringResource(R.string.quick_settings_bluetooth_label),
                    )
                    val actions = listOf(
                        Settings.ACTION_WIFI_SETTINGS,
                        Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                        Settings.ACTION_BLUETOOTH_SETTINGS,
                    )
                    pinned.forEachIndexed { index, tile ->
                        if (tile != null) {
                            MotorolaTile(tile, colors, connectivity = true,
                                modifier = Modifier.fillMaxWidth().weight(1f))
                        } else {
                            Text(
                                labels[index], color = colors.onSurface, fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                                    .clickable { onOpenConnectivity(actions[index]) }
                                    .padding(vertical = 16.dp),
                            )
                        }
                    }
                }
                MotorolaVolumeSlider(
                    audioVolumeInteractor, colors, Modifier.width(cell).fillMaxHeight()
                )
                MotorolaBrightnessSlider(
                    brightnessViewModel, colors, Modifier.width(cell).fillMaxHeight()
                )
            }
            Box(Modifier.fillMaxWidth().height(80.dp)) {
                mediaHost(Modifier.matchParentSize())
                MotorolaMediaCard(
                    data, onOpenMedia, onOutputSwitcher,
                    Modifier.matchParentSize(), listening = listening(),
                )
            }
            rest.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { tile ->
                        MotorolaTile(
                            tile, colors, connectivity = false,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MotorolaHeader(onEdit: () -> Unit, modifier: Modifier = Modifier, listening: Boolean) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(listening) {
        if (listening) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L)
            }
        }
    }
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val timePattern = DateFormat.getBestDateTimePattern(
        locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    ).replace("a", "").trim()
    Row(
        modifier.fillMaxWidth().padding(bottom = 17.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).offset(x = (-3).dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                DateFormat.format(timePattern, now).toString(),
                fontFamily = FontFamily.Default, fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Normal,
                color = motorolaColors().onSurface,
                maxLines = 1, modifier = Modifier.alignByBaseline(),
            )
            Text(
                DateFormat.format("EEE, d MMM", now).toString(),
                fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 18.sp, color = motorolaColors().onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
        }
        Box(
            Modifier.offset(x = 15.dp).size(48.dp).clip(CircleShape).clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.accessibility_quick_settings_edit),
                onClick = onEdit,
            ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painterResource(R.drawable.ic_motorola_edit),
                stringResource(R.string.accessibility_quick_settings_edit),
                tint = motorolaColors().onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MotorolaTile(
    tile: TileViewModel,
    colors: MotorolaColors,
    connectivity: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by tile.state.collectAsStateWithLifecycle(initialValue = tile.currentState)
    val ui = state.toUiState(context.resources)
    val active = state.state == Tile.STATE_ACTIVE
    val available = state.state != Tile.STATE_UNAVAILABLE
    val icon = state.toIconProvider().icon
    val drawable = remember(icon, state.state) { icon?.getDrawable(context) }
    val image = drawable?.let { Icon.Loaded(it, null) }
        ?: Icon.Resource(R.drawable.ic_error_outline, null)
    val foreground = if (active) colors.onActive else colors.onSurface
    val fill = if (active) colors.active else colors.surface
    val onClick = { tile.mainClick(null) }
    val touch = modifier
        .clip(RoundedCornerShape(22.dp))
        .background(if (connectivity) colors.surface else fill)
        .semantics(mergeDescendants = true) {
            contentDescription = ui.accessibilityUiState.contentDescription
            stateDescription = ui.accessibilityUiState.stateDescription
            role = if (connectivity) Role.Button else ui.accessibilityUiState.accessibilityRole
            ui.accessibilityUiState.toggleableState?.takeIf { !connectivity }?.let {
                toggleableState = it
            }
        }
        .combinedClickable(
            enabled = available && ui.handlesMainClick, onClick = onClick,
            onLongClick = if (ui.handlesSettingsClick) { { tile.settingsClick(null) } } else null,
        )
    if (connectivity) {
        Row(touch, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (active) colors.active else colors.iconBubble)
                    .semantics {
                        contentDescription = ui.label
                        role = Role.Switch
                        (state as? QSTile.BooleanState)?.let {
                            toggleableState = if (it.value) ToggleableState.On else ToggleableState.Off
                        }
                    }
                    .clickable(enabled = available) {
                        if (ui.handlesToggleClick) tile.toggleClick() else tile.mainClick(null)
                    },
                contentAlignment = Alignment.Center,
            ) {
                TileIcon(icon = image, modifier = Modifier.size(21.dp), tint = foreground)
            }
            Column(Modifier.weight(1f)) {
                Text(ui.label, color = colors.onSurface.copy(alpha = if (active) 1f else 0.78f), fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val secondary = if (ui.secondaryLabel.isBlank() &&
                    tile.spec.spec == "bt" && state.state == Tile.STATE_INACTIVE) {
                    stringResource(R.string.switch_bar_off)
                } else ui.secondaryLabel
                if (secondary.isNotBlank()) {
                    Text(secondary, color = colors.onSurface.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        Box(touch, contentAlignment = Alignment.Center) {
            TileIcon(
                icon = image, modifier = Modifier.size(28.dp),
                tint = foreground.copy(alpha = if (available) 1f else 0.38f),
            )
        }
    }
}
