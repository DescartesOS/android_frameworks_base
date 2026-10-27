/* Adapted from Samsung Electronics SystemUI S741BXXS2AZI5 (IUX AGSL renderer). */
package com.android.systemui.media.oneui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.android.systemui.res.R
import org.json.JSONArray
import org.json.JSONObject

/** Runs the original media_album_lighting shader with Samsung's uniform defaults. */
class OneUiMediaShaderView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: RuntimeShader? = null
    private var elapsedMs = 0L
    private var lastFrameMs = 0L
    private var framePending = false
    private var visibleToUser = false
    private val frame = Runnable {
        framePending = false
        if (shouldAnimate()) {
            val now = SystemClock.uptimeMillis()
            if (lastFrameMs != 0L) elapsedMs += now - lastFrameMs
            lastFrameMs = now
            invalidate()
            scheduleFrame()
        }
    }

    var playing = false
        set(value) {
            if (field == value) return
            field = value
            updateAnimation()
        }

    var colors = OneUiMediaColors.Default
        set(value) {
            if (field == value) return
            field = value
            applyColors()
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        try {
            val json = resources.openRawResource(R.raw.oneui_media_lighting).bufferedReader()
                .use { JSONObject(it.readText()) }
            val layers = json.getJSONArray("shaders")
            var effect: RenderEffect? = null
            for (index in 0 until layers.length()) {
                val layer = layers.getJSONObject(index)
                if (!layer.optBoolean("enabled", true)) continue
                if (layer.optBoolean("isBlur")) {
                    val blur = RenderEffect.createBlurEffect(
                        layer.getDouble("radiusX").toFloat() * 500f,
                        layer.getDouble("radiusY").toFloat() * 500f,
                        Shader.TileMode.CLAMP,
                    )
                    effect = effect?.let { RenderEffect.createChainEffect(blur, it) } ?: blur
                } else {
                    val runtimeShader = RuntimeShader(layer.getString("agslShaderCode"))
                    val uniforms = layer.getJSONArray("uniforms")
                    for (uniformIndex in 0 until uniforms.length()) {
                        val uniform = uniforms.getJSONObject(uniformIndex)
                        val value = uniform.get("value")
                        val floats = if (value is JSONArray) {
                            FloatArray(value.length()) { value.getDouble(it).toFloat() }
                        } else {
                            floatArrayOf((value as Number).toFloat())
                        }
                        // AGSL may eliminate uniforms that the shader does not use.
                        runCatching { runtimeShader.setFloatUniform(uniform.getString("name"), floats) }
                    }
                    shader = runtimeShader
                    paint.shader = runtimeShader
                }
            }
            setRenderEffect(effect)
            applyColors()
        } catch (error: IllegalArgumentException) {
            Log.w("OneUiMediaShader", "Media shader unavailable", error)
            shader = null
        }
    }

    private fun applyColors() {
        fun set(name: String, color: Int) {
            shader?.setFloatUniform(name, Color.red(color) / 255f,
                Color.green(color) / 255f, Color.blue(color) / 255f)
        }
        set("primaryColor", colors.primary)
        set("secondaryColor", colors.secondary)
        set("accentColor", colors.accent)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentShader = shader ?: return
        if (width == 0 || height == 0 || !canvas.isHardwareAccelerated) return
        currentShader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
        currentShader.setFloatUniform("iTime", elapsedMs / 1000f)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleToUser = isVisible
        updateAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        framePending = false
        lastFrameMs = 0L
        super.onDetachedFromWindow()
    }

    private fun shouldAnimate() = playing && visibleToUser && isAttachedToWindow
        && shader != null && ValueAnimator.areAnimatorsEnabled()

    private fun scheduleFrame() {
        if (framePending || !shouldAnimate()) return
        framePending = true
        postDelayed(frame, 33L)
    }

    private fun updateAnimation() {
        removeCallbacks(frame)
        framePending = false
        lastFrameMs = 0L
        scheduleFrame()
    }
}
