/*
 * Copyright (C) 2026 The DescartesOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.charging;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.res.R;
import com.oplus.vfxsdk.charge.BaseEngine;
import com.oplus.vfxsdk.charge.charging.VFXChargingEngine;
import java.io.IOException;
import java.io.InputStream;

public final class ColorOsChargingView extends FrameLayout {
    private static final String TAG = "ColorOsChargingView";
    private static final String ASSETS = "charging/coloros/";
    private final TextView levelText;
    private final TextView chargingText;
    private final LinearLayout label;
    private final RingView ring;

    public ColorOsChargingView(Context context, int level, long microwatts, Runnable failed,
            Runnable dismissed) {
        super(context);
        setBackgroundColor(0x99000000);
        label = new LinearLayout(context);
        label.setOrientation(LinearLayout.VERTICAL);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        Typeface font = Typeface.createFromAsset(context.getAssets(), ASSETS + "charge.ttf");
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        levelText = text(context, 64, font);
        row.addView(levelText);
        TextView percent = text(context, 22, font);
        percent.setText("%");
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(-2, -2);
        percentParams.gravity = Gravity.BOTTOM;
        percentParams.bottomMargin = dp(11);
        row.addView(percent, percentParams);
        label.addView(row);
        chargingText = text(context, 16, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chargingText.setGravity(Gravity.CENTER);
        chargingText.setPadding(0, dp(5), 0, 0);
        label.addView(chargingText);
        ring = new RingView(context, ChargingPower.isFast(microwatts),
                () -> label.animate().alpha(1f).setDuration(500).start(), failed, dismissed);
        addView(ring, new LayoutParams(-1, -1));
        LayoutParams labelParams = new LayoutParams(-2, -2, Gravity.CENTER);
        labelParams.bottomMargin = dp(24);
        addView(label, labelParams);
        updateBatteryState(level, microwatts);
    }

    public void updateBatteryState(int level, long microwatts) {
        levelText.setText(String.format(getResources().getConfiguration().getLocales().get(0),
                "%d", Math.max(0, Math.min(100, level))));
        boolean fast = ChargingPower.isFast(microwatts);
        chargingText.setText(fast ? R.string.charging_animation_fast : R.string.charging_animation_normal);
        ring.setFastCharging(fast);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static TextView text(Context context, int size, Typeface font) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        text.setTextColor(Color.WHITE);
        text.setTypeface(font);
        text.setIncludeFontPadding(false);
        return text;
    }

    @Override protected void onDetachedFromWindow() {
        label.animate().cancel();
        super.onDetachedFromWindow();
    }

    private static final class RingView extends TextureView implements TextureView.SurfaceTextureListener {
        private final Runnable started;
        private final Runnable failed;
        private final Runnable dismissed;
        private volatile boolean fastCharging;
        private boolean released;
        private Handler renderHandler;

        RingView(Context context, boolean fast, Runnable started, Runnable failed, Runnable dismissed) {
            super(context);
            fastCharging = fast;
            this.started = started;
            this.failed = failed;
            this.dismissed = dismissed;
            setOpaque(false);
            setSurfaceTextureListener(this);
        }

        void setFastCharging(boolean fast) {
            if (fastCharging == fast) return;
            fastCharging = fast;
            if (renderHandler != null && !released) {
                renderHandler.post(() -> { if (engine != null) configure(engine, fastCharging); });
            }
        }

        private VFXChargingEngine engine;
        private Surface surface;
        private HandlerThread worker;

        @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            released = false;
            HandlerThread loader = new HandlerThread("ColorOsChargeLoader");
            loader.start();
            worker = loader;
            renderHandler = new Handler(loader.getLooper());
            Surface renderSurface = new Surface(texture);
            surface = renderSurface;
            renderHandler.post(() -> {
                try {
                    engine = new VFXChargingEngine();
                    engine.init();
                    engine.setAssetManager(getContext().getAssets());
                    engine.setDensity(getResources().getDisplayMetrics().density);
                    engine.setRingStartedCallback(() -> post(() -> {
                        if (!released && isAttachedToWindow()) started.run();
                    }));
                    loadTexture("ring_4_in_1.png", BaseEngine.TextureID.TEXTURE3);
                    loadTexture("glow.png", BaseEngine.TextureID.TEXTURE5);
                    loadTexture("flash.png", BaseEngine.TextureID.TEXTURE9);
                    configure(engine, fastCharging);
                    engine.setSurface(renderSurface);
                } catch (Exception | LinkageError error) {
                    Log.w(TAG, "Native charging unavailable; using the standard ripple", error);
                    post(() -> { if (!released && isAttachedToWindow()) failed.run(); });
                }
            });
        }

        private void loadTexture(String name, BaseEngine.TextureID id) throws IOException {
            try (InputStream stream = getContext().getAssets().open(ASSETS + name)) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap == null) throw new IOException("Cannot decode " + name);
                try {
                    engine.setBitmap(bitmap, id);
                } finally {
                    bitmap.recycle();
                }
            }
        }

        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            released = true;
            final Surface oldSurface = surface;
            final HandlerThread oldWorker = worker;
            renderHandler.post(() -> {
                try {
                    if (engine != null) {
                        engine.release();
                        engine.stop();
                        engine.destroySurface();
                        engine.exit();
                        engine = null;
                    }
                } finally {
                    oldSurface.release();
                    texture.release();
                    oldWorker.quitSafely();
                }
            });
            return false;
        }

        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            if (!released) dismissed.run();
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
    }

    private static void configure(VFXChargingEngine engine, boolean fast) {
        engine.setParticles(fast ? 256 : 196, fast ? 30 : 20, fast ? 0.003f : 0.0006f, fast ? 15f : 6f);
        engine.setParticlesCircle(fast ? 0f : 0.1f, fast ? 0.4f : 0.15f);
        engine.setParticlesMoveType(fast ? 0 : 1);
        engine.setRingAndParticlesCDDisplacement(fast ? 2 : 0, fast ? 12 : 1,
                fast ? 0.08f : 0.03f, fast ? 0.08f : 0.03f);
        engine.setParticlesLuminance(fast ? 1.11f : 1f);
        engine.setParticlesDistortion(fast ? 1.8f : 1.1f);
        engine.setRingGlowTime(6f, fast ? 5f : 3f, 3f, fast ? 0.6f : 0.3f);
        engine.setRingRotateSpeed(fast ? 0.5f : 0.35f, 0.3f);
        engine.setBlurWeight(fast ? 0.01f : 0.02f, 1f);
        engine.setParticlesRing(93f, 135f);
        engine.setRingGlowWeight(1f);
        engine.setRingRadius(190);
        engine.setFlashSize(60);
        engine.setFlashDuration(1.28f);
        engine.setAnimationDuration(20f, 1f, 0.5f, 0f);
        engine.setRingLineWeight(1f);
        engine.setRingRingWeight(1f);
        engine.setRingFadeInCurve(0.35f, 0.75f, 0f, 1f);
        engine.setRingFadeOutCurve(0.36f, 0.36f, 0.59f, 0.59f);
        engine.setFlashEnabled(false);
        engine.setRingStartOffset(0f);
    }
}
