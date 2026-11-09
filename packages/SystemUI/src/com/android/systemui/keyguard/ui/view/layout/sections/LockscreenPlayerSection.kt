/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.axion.compose.host.AxComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.classifier.Classifier
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.composable.media.LockscreenExpandedMediaArt
import com.android.systemui.keyguard.ui.composable.media.LockscreenMediaCard
import com.android.systemui.keyguard.ui.composable.media.LockscreenMediaExpansion
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaFalsingSystem
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

private const val ART_SLOT_SHARE = 0.3f

class LockscreenPlayerSection
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val mediaViewModelFactory: MediaViewModel.Factory,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val falsingSystem: MediaFalsingSystem,
) : KeyguardSection() {

    private val artId = R.id.lockscreen_player_art
    private val cardId = R.id.lockscreen_player_card

    override fun addViews(constraintLayout: ConstraintLayout) {
        constraintLayout.addView(AxComposeView(context).apply { id = artId }, 0)
        constraintLayout.addView(AxComposeView(context).apply { id = cardId })
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        constraintLayout.requireViewById<AxComposeView>(artId).setContent {
            PlatformTheme {
                LockscreenMediaExpansion.ObserveSettings()
                LockscreenExpandedMediaArt(alpha = { 1f })
            }
        }
        constraintLayout.requireViewById<AxComposeView>(cardId).setContent {
            PlatformTheme {
                PlayerCard(
                    mediaViewModelFactory,
                    mediaCarouselInteractor,
                    keyguardInteractor,
                    falsingSystem,
                )
            }
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            constrainWidth(artId, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(artId, ConstraintSet.MATCH_CONSTRAINT)
            connect(artId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(artId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(artId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(artId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

            constrainWidth(cardId, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(cardId, ConstraintSet.WRAP_CONTENT)
            connect(cardId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(cardId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(
                cardId,
                ConstraintSet.TOP,
                R.id.smart_space_barrier_bottom,
                ConstraintSet.BOTTOM,
            )

            val bottomMargin =
                context.resources.getDimensionPixelSize(R.dimen.keyguard_status_view_bottom_margin)
            connect(
                R.id.nssl_placeholder,
                ConstraintSet.TOP,
                cardId,
                ConstraintSet.BOTTOM,
                bottomMargin,
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        constraintLayout.findViewById<AxComposeView>(artId)?.let { constraintLayout.removeView(it) }
        constraintLayout.findViewById<AxComposeView>(cardId)?.let { constraintLayout.removeView(it) }
    }
}

@Composable
private fun PlayerCard(
    mediaViewModelFactory: MediaViewModel.Factory,
    mediaCarouselInteractor: MediaCarouselInteractor,
    keyguardInteractor: KeyguardInteractor,
    falsingSystem: MediaFalsingSystem,
) {
    LockscreenMediaExpansion.ObserveSettings()
    val allowMedia by
        mediaCarouselInteractor.allowMediaOnLockscreen.collectAsStateWithLifecycle()
    val hasActiveMedia by mediaCarouselInteractor.hasActiveMedia.collectAsStateWithLifecycle()
    val onLockscreen by
        mediaCarouselInteractor.isOnLockscreen.collectAsStateWithLifecycle(initialValue = false)
    val dozing by keyguardInteractor.isDozing.collectAsStateWithLifecycle()
    val visible =
        LockscreenMediaExpansion.enabled && allowMedia && hasActiveMedia && onLockscreen && !dozing

    DisposableEffect(Unit) {
        LockscreenMediaExpansion.supported = true
        onDispose {
            LockscreenMediaExpansion.supported = false
            LockscreenMediaExpansion.mediaVisible = false
        }
    }
    SideEffect { LockscreenMediaExpansion.mediaVisible = visible }
    if (!visible) return

    val behavior =
        MediaUiBehavior(
            carouselVisibility = MediaCarouselVisibility.WhenAnyCardIsActive,
            isCarouselScrollFalseTouch = {
                falsingSystem.isFalseTouch(Classifier.MEDIA_CAROUSEL_SWIPE)
            },
        )
    val slotHeight =
        (LocalConfiguration.current.screenHeightDp * ART_SLOT_SHARE *
                LockscreenMediaExpansion.fraction)
            .dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(
            Modifier.fillMaxWidth().height(slotHeight).onGloballyPositioned {
                LockscreenMediaExpansion.artSlotBounds =
                    Rect(it.positionInWindow(), it.size.toSize())
            }
        )
        LockscreenMediaCard(
            viewModelFactory = mediaViewModelFactory,
            behavior = behavior,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
