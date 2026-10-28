/*
 * Copyright (C) 2026 The DescartesOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.navigationbar.gestural.onehand

import android.app.KeyguardManager
import android.content.*
import android.database.ContentObserver
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.input.InputManagerGlobal
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityManager
import com.android.internal.util.ScreenshotHelper
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputMonitorCompat
import com.android.systemui.statusbar.policy.FlashlightController
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.CommandQueue
import javax.inject.Inject
import kotlin.math.abs

@SysUISingleton
class OneHandGestureController @Inject constructor(
    @Application private val context: Context,
    private val commandQueue: CommandQueue,
    private val flashlightController: FlashlightController,
    private val keyguardStateController: KeyguardStateController,
) : CoreStartable {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private var monitor: InputMonitorCompat? = null
    private var receiver: InputChannelCompat.InputEventReceiver? = null
    private var enabled = false
    private var leftEnabled = true
    private var rightEnabled = true
    private var landscape = false
    private var width = 20
    private var height = 40
    private var position = 55
    private var distance = 48
    private var holdMillis = 300
    private var opacity = 35
    private var haptic = true
    private var bounds = Rect()
    private var active = false
    private var claimed = false
    private var fired = false
    private var left = false
    private var startX = 0f
    private var startY = 0f
    private var lastInward = 0f
    private var lastVertical = 0f
    private var direction = 0
    private var arrow: Arrow? = null
    private val handles = mutableListOf<View>()
    private val actions = IntArray(12)
    private val longPress = Runnable {
        if (active && claimed && !fired) {
            direction = OneHandGestureMath.direction(lastInward, lastVertical, true)
            val action = action(true)
            if (action != 0) { fired = true; runAction(action); finish() }
        }
    }
    private fun read(key: String, default: Int) = Settings.Secure.getIntForUser(
        context.contentResolver, "onehand_$key", default, UserHandle.USER_CURRENT)
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri == null || uri.lastPathSegment?.startsWith("onehand_") == true) reload()
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) { if (id == Display.DEFAULT_DISPLAY) reload() }
    }

    override fun start() {
        instance = this
        keyguardStateController.addCallback(object : KeyguardStateController.Callback {
            override fun onKeyguardShowingChanged() {
                if (keyguardStateController.isShowing) finish()
                updateHandles()
            }
        })
        accessibility.addTouchExplorationStateChangeListener { enabled ->
            if (enabled) finish()
            updateHandles()
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.CONTENT_URI, true, observer, UserHandle.USER_ALL)
        context.getSystemService(DisplayManager::class.java)
            .registerDisplayListener(displayListener, handler)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_SWITCHED) reload()
                else {
                    finish()
                    handler.postDelayed({ updateHandles() }, 250)
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_SWITCHED)
        }, Context.RECEIVER_NOT_EXPORTED)
        reload()
    }

    private fun reload() {
        finish()
        handles.forEach { removeOverlay(it) }
        handles.clear()
        enabled = read("enabled", 0) != 0
        leftEnabled = read("left_enabled", 1) != 0
        rightEnabled = read("right_enabled", 1) != 0
        landscape = read("landscape", 0) != 0
        width = read("width", 20).coerceIn(8, 40)
        height = read("height", 40).coerceIn(10, 80)
        position = read("position", 55).coerceIn(10, 90)
        distance = read("distance", 48).coerceIn(24, 120)
        holdMillis = read("hold", 300).coerceIn(200, 1000)
        opacity = read("opacity", 35).coerceIn(0, 100)
        haptic = read("haptic", 1) != 0
        for (side in 0..1) for (long in 0..1) for (dir in 0..2) {
            val name = if (side == 0) "left" else "right"
            val kind = if (long == 0) "short" else "long"
            val gesture = arrayOf("straight", "up", "down")[dir]
            actions[side * 6 + long * 3 + dir] =
                read("${name}_${kind}_${gesture}", if (long == 0) 1 else 0)
        }
        bounds = windowManager.maximumWindowMetrics.bounds
        if (enabled && monitor == null) {
            val input = InputMonitorCompat("one-hand-gestures", Display.DEFAULT_DISPLAY)
            monitor = input
            receiver = input.getInputReceiver(Looper.getMainLooper(), Choreographer.getInstance()) {
                if (it is MotionEvent) onMotion(it)
            }
        } else if (!enabled) {
            receiver?.dispose(); receiver = null
            monitor?.dispose(); monitor = null
        }
        if (enabled && (landscape || bounds.height() >= bounds.width())) {
            if (leftEnabled) addHandle(true)
            if (rightEnabled) addHandle(false)
        }
        updateHandles()
    }

    private fun updateHandles() {
        val visible = power.isInteractive && !keyguardStateController.isShowing &&
            !keyguard.isKeyguardLocked && !accessibility.isTouchExplorationEnabled
        handles.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
    }

    private fun dp(value: Int) = value * context.resources.displayMetrics.density
    private fun region(): Pair<Float, Float> {
        val h = bounds.height() * height / 100f
        val top = (bounds.height() * position / 100f - h / 2)
            .coerceIn(dp(48), (bounds.height() - dp(64) - h).coerceAtLeast(dp(48)))
        return top to top + h
    }
    private fun eligible(event: MotionEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ||
            event.displayId != Display.DEFAULT_DISPLAY || event.pointerCount != 1 ||
            event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER || !power.isInteractive ||
            keyguard.isKeyguardLocked || accessibility.isTouchExplorationEnabled ||
            (!landscape && bounds.width() > bounds.height())) return false
        val x = event.x
        val side = if (x <= dp(width)) 0 else if (x >= bounds.width() - dp(width)) 1 else return false
        if ((side == 0 && !leftEnabled) || (side == 1 && !rightEnabled) ||
            (side * 6 until side * 6 + 6).none { actions[it] != 0 }) return false
        val (top, bottom) = region()
        return event.y in top..bottom
    }

    private fun onMotion(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                finish()
                if (!eligible(event)) return
                active = true
                startX = event.x; startY = event.y
                left = event.x < bounds.width() / 2
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> finish()
            MotionEvent.ACTION_MOVE -> if (active) {
                val dx = if (left) event.x - startX else startX - event.x
                val dy = event.y - startY
                if (!claimed && (dx < -dp(12) || abs(dy) > maxOf(dp(32), dx * 3.732f))) {
                    finish(); return
                }
                if (claimed && dx < dp(distance) / 2) { finish(); return }
                lastInward = dx; lastVertical = dy
                direction = OneHandGestureMath.direction(dx, dy, false)
                if (!claimed && dx >= dp(distance)) {
                    claimed = true
                    monitor?.pilferPointers()
                    showArrow()
                    handler.postDelayed(longPress, holdMillis.toLong())
                }
                arrow?.let {
                    it.direction = direction
                    it.invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (active && claimed && !fired) {
                    direction = OneHandGestureMath.direction(lastInward, lastVertical, false)
                    runAction(action(false))
                }
                finish()
            }
        }
    }
    private fun action(long: Boolean) =
        actions[(if (left) 0 else 6) + (if (long) 3 else 0) + direction]

    private fun finish() {
        active = false; claimed = false; fired = false
        handler.removeCallbacks(longPress)
        arrow?.let { removeOverlay(it) }
        arrow = null
    }

    private fun removeOverlay(view: View) {
        try { windowManager.removeViewImmediate(view) }
        catch (e: IllegalArgumentException) { Log.d("OneHandGestures", "Overlay already removed") }
    }

    private fun layout(w: Int, h: Int, x: Int, y: Int) =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                this.x = x; this.y = y
                setTitle("OneHandGestures")
            }

    private fun addHandle(isLeft: Boolean) {
        if (opacity == 0) return
        val (top, bottom) = region()
        val view = View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(opacity * 255 / 100, 255, 255, 255))
                cornerRadius = dp(2)
            }
        }
        windowManager.addView(view, layout(dp(3).toInt(), (bottom - top).toInt(),
            if (isLeft) 0 else bounds.width() - dp(3).toInt(), top.toInt()))
        handles.add(view)
    }
    private fun showArrow() {
        val size = dp(72).toInt()
        arrow = Arrow(context).also {
            it.direction = direction
            it.mirror = !left
            windowManager.addView(it, layout(size, size,
                if (left) dp(8).toInt() else bounds.width() - size - dp(8).toInt(),
                (startY - size / 2).toInt().coerceIn(0, bounds.height() - size)))
        }
    }

    private fun runAction(action: Int) {
        if (action == 0) return
        try {
            if (haptic) context.getSystemService(Vibrator::class.java)
                .vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            val command = commandQueue
            val audio = context.getSystemService(AudioManager::class.java)
            when (action) {
                1 -> key(KeyEvent.KEYCODE_BACK)
                2 -> key(KeyEvent.KEYCODE_HOME)
                3 -> command.toggleRecentApps()
                4 -> command.animateExpandNotificationsPanel()
                5 -> command.animateExpandSettingsPanel(null)
                6 -> power.goToSleep(SystemClock.uptimeMillis())
                7 -> ScreenshotHelper(context).takeScreenshot(
                    android.view.WindowManager.ScreenshotSource.SCREENSHOT_OTHER, handler, null)
                8 -> flashlightController.setFlashlight(!flashlightController.isEnabled)
                9 -> key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                10 -> key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                11 -> key(KeyEvent.KEYCODE_MEDIA_NEXT)
                12 -> command.showGlobalActionsMenu()
                13 -> context.startActivityAsUser(Intent(Intent.ACTION_VOICE_ASSIST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), UserHandle.CURRENT)
                14 -> audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE,
                    AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
                15 -> audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER,
                    AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)

            }
        } catch (e: Exception) { Log.w("OneHandGestures", "Action failed", e) }
    }
    private fun key(code: Int) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            InputManagerGlobal.getInstance().injectInputEvent(KeyEvent(now, now, action,
                code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_NAVIGATION_BAR),
                android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        }
    }
    private class Arrow(context: Context) : View(context) {
        var direction = 0
        var mirror = false
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            paint.color = 0x80E9E9EC.toInt()
            canvas.drawCircle(cx, cy, width * 0.38f, paint)
            canvas.save()
            if (mirror) canvas.scale(-1f, 1f, cx, cy)
            canvas.rotate(when (direction) { 1 -> -45f; 2 -> 45f; else -> 0f }, cx, cy)
            paint.color = 0xB3575757.toInt(); paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * 0.045f; paint.strokeCap = Paint.Cap.ROUND
            val path = Path().apply {
                moveTo(cx - width * 0.1f, cy - height * 0.16f)
                lineTo(cx + width * 0.06f, cy)
                lineTo(cx - width * 0.1f, cy + height * 0.16f)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            canvas.restore()
        }
    }
    companion object {
        private var instance: OneHandGestureController? = null
        @JvmStatic fun shouldHandleDown(event: MotionEvent): Boolean =
            instance?.eligible(event) == true
    }
}
