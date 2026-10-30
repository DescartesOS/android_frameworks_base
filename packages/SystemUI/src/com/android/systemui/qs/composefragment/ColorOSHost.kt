/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.os.UserHandle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.compose.animation.scene.ContentScope
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.dialog.MediaOutputDialogManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.panels.ui.compose.infinitegrid.InfiniteGridLayout
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalColorOSQsStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSColorOSMediaArtFill
import com.android.systemui.qs.shared.style.LocalGlassBlurAlpha
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import javax.inject.Inject

class ColorOSHostDeps
@Inject
constructor(
    val infiniteGridLayout: InfiniteGridLayout,
    val audioVolumeInteractor: AudioVolumeInteractor,
    val mediaDataManager: MediaDataManager,
    val activityStarter: ActivityStarter,
    val mediaOutputDialogManager: MediaOutputDialogManager,
)

val LocalColorOSHostDeps = staticCompositionLocalOf<ColorOSHostDeps?> { null }

private val ColorOSHostExtraMargin = 22.dp

@Composable
fun ContentScope.ColorOSHostContent(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
) {
    val deps = LocalColorOSHostDeps.current ?: return
    val sessions = remember { ColorOSMediaSessions() }
    DisposableEffect(deps.mediaDataManager, sessions) {
        deps.mediaDataManager.addListener(sessions)
        onDispose { deps.mediaDataManager.removeListener(sessions) }
    }
    var listening by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        listening = true
        onStopOrDispose { listening = false }
    }
    val artFill = rememberQSColorOSMediaArtFill()

    CompositionLocalProvider(LocalColorOSQsStyle provides true) {
        Box(
            modifier =
                modifier
                    .element(Elements.QuickSettingsContent)
                    .padding(
                        horizontal =
                            dimensionResource(id = R.dimen.qs_horizontal_margin) +
                                ColorOSHostExtraMargin
                    )
                    .sysuiResTag("quick_settings_panel")
        ) {
            GridAnchor()
            ColorOSQuickSettingsLayout(
                tiles = viewModel.tileGridViewModel.tileViewModels,
                gridLayout = viewModel.tileGridViewModel.gridLayout,
                infiniteGridLayout = deps.infiniteGridLayout,
                brightnessViewModel = viewModel.brightnessSliderViewModel,
                audioVolumeInteractor = deps.audioVolumeInteractor,
                showMedia = viewModel.showMedia && sessions.current != null,
                media = { mediaModifier ->
                    sessions.current?.let { data ->
                        ColorOSMediaCard(
                            data = data,
                            artFill = artFill,
                            onOpen = { intent ->
                                deps.activityStarter.postStartActivityDismissingKeyguard(intent)
                            },
                            onOutputSwitcher = { mediaData ->
                                val deviceIntent = mediaData.device?.intent
                                if (deviceIntent != null) {
                                    if (deviceIntent.isActivity) {
                                        deps.activityStarter.postStartActivityDismissingKeyguard(
                                            deviceIntent
                                        )
                                    }
                                } else {
                                    deps.mediaOutputDialogManager.createAndShow(
                                        mediaData.packageName,
                                        /* aboveStatusBar= */ true,
                                        /* view= */ null,
                                        UserHandle.getUserHandleForUid(mediaData.appUid),
                                        mediaData.token,
                                        /* useSystemColors= */ false,
                                    )
                                }
                            },
                            modifier = mediaModifier,
                        )
                    }
                },
                listening = { listening },
                modifier = Modifier,
                availableHeight = Dp.Unspecified,
                glassAlpha = LocalGlassBlurAlpha.current,
            )
        }
    }
}
