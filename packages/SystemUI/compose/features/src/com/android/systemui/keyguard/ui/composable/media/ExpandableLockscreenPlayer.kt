/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.penguin.LockscreenPlayer
import javax.inject.Inject

class ExpandableLockscreenPlayer @Inject constructor() : LockscreenPlayer {

    override val isEnabled: Boolean
        get() = LockscreenMediaExpansion.enabled

    override val expansion: Float
        get() = LockscreenMediaExpansion.fraction

    @Composable
    override fun ObserveSettings() {
        LockscreenMediaExpansion.ObserveSettings()
    }

    override fun setMediaVisible(visible: Boolean) {
        LockscreenMediaExpansion.mediaVisible = visible
    }

    @Composable
    override fun Card(
        viewModelFactory: MediaViewModel.Factory,
        behavior: MediaUiBehavior,
        modifier: Modifier,
    ) {
        LockscreenMediaCard(
            viewModelFactory = viewModelFactory,
            behavior = behavior,
            modifier = modifier,
        )
    }

    @Composable
    override fun ExpandedArt(alpha: () -> Float, covered: () -> Float, unlocking: () -> Float) {
        LockscreenExpandedMediaArt(alpha = alpha)
    }

    @Composable
    override fun ColumnScope.SmallClockColumn(
        notificationsActive: Boolean,
        clock: @Composable () -> Unit,
        media: @Composable ColumnScope.() -> Unit,
        notifications: @Composable (Modifier) -> Unit,
    ) {
        DisposableEffect(Unit) {
            LockscreenMediaExpansion.supported = true
            onDispose { LockscreenMediaExpansion.supported = false }
        }
        SideEffect { LockscreenMediaExpansion.notificationsShowing = notificationsActive }
        val expansion = LockscreenMediaExpansion.fraction
        val notifications by
            animateFloatAsState(
                if (notificationsActive) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "ExpandedMediaNotifications",
            )
        val artWeight = expansion * (1f - 0.22f * notifications)
        val collapseOnTap =
            Modifier.pointerInput(Unit) { detectTapGestures { LockscreenMediaExpansion.collapse() } }
        Box(Modifier.collapsible { 1f - LockscreenMediaExpansion.fraction.within(0f, 0.6f) }) {
            clock()
        }
        if (expansion > 0f) {
            LockscreenExpandedClock(
                Modifier.collapsible { LockscreenMediaExpansion.fraction.within(0.3f, 1f) }
                    .then(collapseOnTap)
                    .padding(top = 8.dp, bottom = 16.dp)
            )
        }
        if (artWeight > 0.001f) {
            Spacer(
                Modifier.weight(artWeight)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        LockscreenMediaExpansion.artSlotBounds =
                            Rect(it.positionInWindow(), it.size.toSize())
                    }
                    .then(collapseOnTap)
            )
        }
        media()
        notifications(Modifier.weight(maxOf(1f - artWeight, 0.001f)))
    }
}
