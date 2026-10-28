/* Adapted for the Samsung media player layout. */
package com.android.systemui.media.oneui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.SeekBar
import kotlin.math.roundToInt

internal class OneUiPlayerSeekBar(context: Context) : SeekBar(context) {
    var onSeek: ((Float) -> Unit)? = null
    var onSeekFinished: (() -> Unit)? = null
    private var tracking = false

    init {
        val density = resources.displayMetrics.density
        fun track(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 2f * density
            setSize(1, (4f * density).roundToInt())
        }
        val layers = LayerDrawable(arrayOf(track(0x66ffffff),
            ClipDrawable(track(0xff667493.toInt()), Gravity.START, ClipDrawable.HORIZONTAL)))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        progressDrawable = layers
        thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xff3e4964.toInt())
            setStroke((2f * density).roundToInt(), Color.WHITE)
            setSize((18f * density).roundToInt(), (18f * density).roundToInt())
        }
        thumbOffset = (9f * density).roundToInt()
        setPadding(thumbOffset, 0, thumbOffset, 0)
        background = null
        splitTrack = false
        max = 10000
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    onSeek?.invoke(value.toFloat() / max)
                    if (!tracking) onSeekFinished?.invoke()
                }
            }
            override fun onStartTrackingTouch(view: SeekBar) { tracking = true }
            override fun onStopTrackingTouch(view: SeekBar) {
                onSeekFinished?.invoke()
                tracking = false
            }
        })
    }

    fun setFraction(value: Float) {
        if (!tracking) progress = (value * max).roundToInt()
    }
}
