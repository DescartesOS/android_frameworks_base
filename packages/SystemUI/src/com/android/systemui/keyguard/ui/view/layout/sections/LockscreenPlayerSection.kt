/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.axion.compose.host.AxComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.composable.media.LockscreenExpandedMediaArt
import com.android.systemui.keyguard.ui.composable.media.LockscreenMediaExpansion
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

class LockscreenPlayerSection
@Inject
constructor(@ShadeDisplayAware private val context: Context) : KeyguardSection() {

    private val viewId = R.id.lockscreen_player_art

    override fun addViews(constraintLayout: ConstraintLayout) {
        constraintLayout.addView(AxComposeView(context).apply { id = viewId }, 0)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        constraintLayout.requireViewById<AxComposeView>(viewId).setContent {
            PlatformTheme {
                LockscreenMediaExpansion.ObserveSettings()
                LockscreenExpandedMediaArt(alpha = { 1f })
            }
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
            connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(viewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        constraintLayout.findViewById<AxComposeView>(viewId)?.let { constraintLayout.removeView(it) }
    }
}
