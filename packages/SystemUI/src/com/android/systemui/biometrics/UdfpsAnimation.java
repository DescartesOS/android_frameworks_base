/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import static android.content.Intent.ACTION_USER_SWITCHED;

import static com.android.systemui.doze.util.BurnInHelperKt.getBurnInOffset;
import static com.android.systemui.doze.util.BurnInHelperKt.getBurnInProgressOffset;

import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.policy.KeyguardStateController;

public class UdfpsAnimation extends ImageView {

    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "UdfpsAnimations";

    private boolean mShowing = false;
    private Context mContext;
    private int mAnimationSize;
    private AnimationDrawable recognizingAnim;
    private boolean mShaderMode;
    private WaterRippleShader mWaterShader;
    private final Paint mWaterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator mShaderAnimator;
    private Bitmap mWallpaperBitmap;
    private float mCurrentRadiusDp;
    private float mCurrentRipplesDp = 13f;
    private long mWallpaperGeneration;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final PathInterpolator mEaseOut = new PathInterpolator(0f, 0f, 0.1f, 1f);
    private final PathInterpolator mSmooth = new PathInterpolator(0.33f, 0f, 0.67f, 1f);

    private final WindowManager.LayoutParams mAnimParams = new WindowManager.LayoutParams();
    private WindowManager mWindowManager;

    private boolean mIsKeyguard;

    private final int mMaxBurnInOffsetX;
    private final int mMaxBurnInOffsetY;

    private static final String UDFPS_ANIMATIONS_PACKAGE = "com.crdroid.udfps.animations";

    private final KeyguardStateController mKeyguardStateController;
    private final AuthController mAuthController;
    private final FingerprintSensorPropertiesInternal mProps;

    private boolean mAnimationAdded = false;

    private final KeyguardStateController.Callback keyguardStateCallback = new KeyguardStateController.Callback() {
        @Override
        public void onKeyguardFadingAwayChanged() {
            removeAnimation();
        }
        @Override
        public void onKeyguardGoingAwayChanged() {
            removeAnimation();
        }
    };

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                updateAnimationStyle();
            } else if (Intent.ACTION_WALLPAPER_CHANGED.equals(action) && mShaderMode) {
                loadWallpaperForShader();
            }
        }
    };

    public UdfpsAnimation(Context context, WindowManager windowManager,
           FingerprintSensorPropertiesInternal props, AuthController authController) {
        super(context);
        mContext = context;
        mAuthController = authController;
        mProps = props;

        mWindowManager = windowManager;

        mKeyguardStateController = Dependency.get(KeyguardStateController.class);

        float scaleFactor = getDisplayFactor();

        mMaxBurnInOffsetX = (int) (context.getResources()
            .getDimensionPixelSize(R.dimen.udfps_burn_in_offset_x) * scaleFactor);
        mMaxBurnInOffsetY = (int) (context.getResources()
            .getDimensionPixelSize(R.dimen.udfps_burn_in_offset_y) * scaleFactor);

        mShaderMode = isShaderMode();
        mAnimationSize = mContext.getResources().getDimensionPixelSize(
                mShaderMode ? R.dimen.udfps_shader_view_size : R.dimen.udfps_animation_size);

        mAnimParams.height = mAnimationSize;
        mAnimParams.width = mAnimationSize;

        mAnimParams.format = PixelFormat.TRANSLUCENT;
        mAnimParams.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY; // it must be behind Udfps icon
        mAnimParams.flags =  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mAnimParams.gravity = Gravity.TOP | Gravity.CENTER;

        updatePosition();

        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (mShaderMode) initializeWaterShader();
    }

    private void addAnimation() {
        if (!mAnimationAdded) {
            IntentFilter filter = new IntentFilter(ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
            mContext.registerReceiverAsUser(mIntentReceiver, UserHandle.ALL, filter, null, null);

            updateAnimationStyle();

            mKeyguardStateController.addCallback(keyguardStateCallback);
            mAnimationAdded = true;
        }
    }

    private void updateAnimationStyle() {
        int value = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.UDFPS_ANIM_STYLE, 0, UserHandle.USER_CURRENT);
        mContext.getMainExecutor().execute(() -> {
            updateAnimationStyle(value);
        });
    }

    private void updateAnimationStyle(int styleIdx) {
        stopShaderAnimation();
        setBackground(null);
        if (recognizingAnim != null) {
            recognizingAnim.stop();
            clearAnimation();
            recognizingAnim = null;
        }
        mShaderMode = isShaderMode();
        Drawable bgDrawable = mShaderMode ? null : getBgDrawable(styleIdx);
        if (mShaderMode) {
            initializeWaterShader();
        } else if (styleIdx != 0 && bgDrawable != null) {
            setBackground(bgDrawable);
            recognizingAnim = bgDrawable instanceof AnimationDrawable ? (AnimationDrawable) bgDrawable : null;
        }
        invalidate();
    }

    private Resources getShaderResources() {
        try {
            return mContext.getPackageManager().getResourcesForApplication(UDFPS_ANIMATIONS_PACKAGE);
        } catch (PackageManager.NameNotFoundException e) {
            return mContext.getResources();
        }
    }

    private boolean isShaderMode() {
        Resources resources = getShaderResources();
        int id = resources.getIdentifier("config_udfps_animation_type", "string",
                UDFPS_ANIMATIONS_PACKAGE);
        String type = id != 0 ? resources.getString(id)
                : mContext.getString(R.string.config_udfps_animation_type);
        return "water_ripple".equals(type) || "shader".equals(type);
    }

    private int getShaderInteger(String name, int fallback) {
        Resources overlay = getShaderResources();
        int id = overlay.getIdentifier(name, "integer", UDFPS_ANIMATIONS_PACKAGE);
        if (id != 0) {
            try {
                return overlay.getInteger(id);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        int localId = mContext.getResources().getIdentifier(
                name, "integer", mContext.getPackageName());
        if (localId == 0) return fallback;
        try {
            return mContext.getResources().getInteger(localId);
        } catch (Resources.NotFoundException e) {
            return fallback;
        }
    }

    private void initializeWaterShader() {
        Resources resources = getShaderResources();
        int sourceId = resources.getIdentifier("config_udfps_custom_agsl_shader", "string",
                UDFPS_ANIMATIONS_PACKAGE);
        String source = sourceId != 0 ? resources.getString(sourceId)
                : mContext.getString(R.string.config_udfps_custom_agsl_shader);
        try {
            mWaterShader = new WaterRippleShader(source);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Custom UDFPS shader failed; using built-in shader", e);
            mWaterShader = new WaterRippleShader(null);
        }
        float density = mContext.getResources().getDisplayMetrics().density;
        mWaterShader.setDensity(density);
        mWaterShader.setResolution(mAnimationSize, mAnimationSize);
        mWaterShader.setOrigin(mAnimationSize * 0.5f, mAnimationSize * 0.5f);
        Point displaySize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(displaySize);
        mWaterShader.setScreenSpace((displaySize.x - mAnimationSize) * 0.5f,
                mAnimParams.y, displaySize.x, displaySize.y);
        mWaterPaint.setShader(mWaterShader);
        loadWallpaperForShader();
    }

    private void loadWallpaperForShader() {
        final long generation = ++mWallpaperGeneration;
        new Thread(() -> {
            Bitmap raw = null;
            Bitmap scaled = null;
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                try {
                    raw = wallpaperManager.getBitmap(false, WallpaperManager.FLAG_LOCK);
                } catch (RuntimeException e) {
                    raw = wallpaperManager.getBitmap();
                }
                if (raw != null && !raw.isRecycled()) {
                    Point size = new Point();
                    mWindowManager.getDefaultDisplay().getRealSize(size);
                    scaled = Bitmap.createScaledBitmap(raw,
                            Math.max(1, size.x / 2), Math.max(1, size.y / 2), true);
                }
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Unable to load wallpaper for UDFPS shader", e);
            } finally {
                if (raw != null && raw != scaled && !raw.isRecycled()) raw.recycle();
            }
            final Bitmap result = scaled;
            mMainHandler.post(() -> {
                if (generation != mWallpaperGeneration || !mShaderMode) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                if (mWallpaperBitmap != null && mWallpaperBitmap != result &&
                        !mWallpaperBitmap.isRecycled()) mWallpaperBitmap.recycle();
                mWallpaperBitmap = result;
                if (mWaterShader != null) {
                    mWaterShader.setBackgroundBitmap(result,
                            mContext.getResources().getDisplayMetrics().widthPixels,
                            mContext.getResources().getDisplayMetrics().heightPixels);
                }
                invalidate();
            });
        }, "UdfpsWallpaperShader").start();
    }

    private void startShaderAnimation() {
        if (mWaterShader == null || (mShaderAnimator != null && mShaderAnimator.isRunning())) return;
        final float activeDuration = Math.max(0.1f,
                getShaderInteger("config_udfps_shader_duration_ms", 450) / 1000f);
        final long cycleDuration = Math.max(200,
                getShaderInteger("config_udfps_shader_cycle_ms", 800));
        final float maxRadius = getShaderInteger("config_udfps_shader_max_radius_dp", 106);
        final float peakAmplitude = getShaderInteger("config_udfps_shader_peak_amplitude", 40);
        final float startRipples = getShaderInteger("config_udfps_shader_start_ripples_dp", 13);
        final float endRipples = getShaderInteger("config_udfps_shader_end_ripples_dp", 30);
        final float chromaticOffset = getShaderInteger(
                "config_udfps_shader_chromatic_offset_dp", 2);
        mShaderAnimator = ValueAnimator.ofFloat(0f, cycleDuration / 1000f);
        mShaderAnimator.setDuration(cycleDuration);
        mShaderAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mShaderAnimator.setInterpolator(new LinearInterpolator());
        mShaderAnimator.addUpdateListener(animator -> {
            float t = (float) animator.getAnimatedValue();
            if (t <= activeDuration) {
                float p = Math.max(0f, Math.min(1f, t / activeDuration));
                float peakSec = activeDuration / 3f;
                float amplitude;
                float ripples;
                if (t <= peakSec) {
                    float phase = Math.max(0f, Math.min(1f, t / peakSec));
                    amplitude = peakAmplitude * mEaseOut.getInterpolation(phase);
                    ripples = startRipples;
                } else {
                    float phase = Math.max(0f, Math.min(1f,
                            (t - peakSec) / (activeDuration - peakSec)));
                    float smooth = mSmooth.getInterpolation(phase);
                    amplitude = peakAmplitude * (1f - smooth);
                    ripples = startRipples + (endRipples - startRipples) * smooth;
                }
                float fade = 1f - Math.max(0f, Math.min(1f, (p - 0.82f) / 0.18f));
                mCurrentRadiusDp = maxRadius * mEaseOut.getInterpolation(p);
                mCurrentRipplesDp = ripples;
                mWaterShader.updateWave(t, mCurrentRadiusDp, amplitude, ripples,
                        0f, chromaticOffset, 1f, fade);
            } else {
                mCurrentRadiusDp = 0f;
                mWaterShader.updateWave(t, 0f, 0f, startRipples,
                        0f, chromaticOffset, 1f, 0f);
            }
            invalidate();
        });
        mShaderAnimator.start();
    }

    private void stopShaderAnimation() {
        if (mShaderAnimator != null) {
            mShaderAnimator.cancel();
            mShaderAnimator = null;
        }
        mCurrentRadiusDp = 0f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mWaterShader != null && w > 0 && h > 0) {
            mWaterShader.setResolution(w, h);
            mWaterShader.setOrigin(w * 0.5f, h * 0.5f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mShaderMode && mWaterShader != null && mCurrentRadiusDp > 0.1f) {
            float density = mContext.getResources().getDisplayMetrics().density;
            float radius = (mCurrentRadiusDp + mCurrentRipplesDp * 3.2f) * density;
            canvas.drawCircle(getWidth() * 0.5f, getHeight() * 0.5f, radius, mWaterPaint);
        }
    }

    private Drawable getBgDrawable(int styleIdx) {
        Resources mApkResources = null;
        try {
            mApkResources = mContext.getPackageManager().getResourcesForApplication(UDFPS_ANIMATIONS_PACKAGE);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Failed to load package resources", e);
            return null;
        }
        String[] mStyleNames = null;
        if (mApkResources != null) {
            int res = mApkResources.getIdentifier("udfps_animation_styles",
                    "array", UDFPS_ANIMATIONS_PACKAGE);
            if (res == 0) return null;
            mStyleNames = mApkResources.getStringArray(res);
        }
        if (mStyleNames == null || styleIdx >= mStyleNames.length) {
            return null;
        }
        String drawableName = mStyleNames[styleIdx];
        if (DEBUG) Log.i(LOG_TAG, "Updating animation style to:" + drawableName);
        try {
            int resId = mApkResources.getIdentifier(drawableName, "drawable", UDFPS_ANIMATIONS_PACKAGE);
            if (DEBUG) Log.i(LOG_TAG, "Got resource id: "+ resId +" from package");
            if (resId == 0) return null;
            return mApkResources.getDrawable(resId);
        } catch (Resources.NotFoundException e) {
            Log.w(LOG_TAG, "Drawable resource not found: " + drawableName, e);
            return null;
        }
    }

    public boolean isAnimationEnabled() {
        return mShaderMode || recognizingAnim != null;
    }

    private float getDisplayFactor() {
        return DisplayUtils.getScaleFactor(mContext);
    }

    public void updatePosition() {
        Point displaySize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(displaySize);
        boolean isFullResolution = displaySize.y > 3000; 
        Point udfpsLocation = mAuthController.getUdfpsLocation();
        float scaleFactor = getDisplayFactor();
        float udfpsRadius = isFullResolution ? mAuthController.getUdfpsRadius() : mProps.getLocation().sensorRadius;
        float udfpsLocationY = isFullResolution && udfpsLocation != null ? udfpsLocation.y : mProps.getLocation().sensorLocationY;
        int animationOffset = (int) (mContext.getResources().getDimensionPixelSize(R.dimen.udfps_animation_offset) * scaleFactor);
        mAnimParams.y = (int) (udfpsLocationY * scaleFactor) - (int) (udfpsRadius * scaleFactor)
                        - (mAnimationSize / 2) + animationOffset;
        if (mWaterShader != null) {
            mWaterShader.setScreenSpace((displaySize.x - mAnimationSize) * 0.5f,
                    mAnimParams.y, displaySize.x, displaySize.y);
        }
        if (DEBUG) {
            Log.d(LOG_TAG, "updatePosition: displaySize=" + displaySize + 
                           ", isFullResolution=" + isFullResolution + 
                           ", udfpsLocation=" + udfpsLocation + 
                           ", udfpsRadius=" + udfpsRadius + 
                           ", scaleFactor=" + scaleFactor + 
                           ", udfpsLocationY=" + udfpsLocationY + 
                           ", animationOffset=" + animationOffset + 
                           ", mAnimParams.y=" + mAnimParams.y);
        }
    }

    public void show() {
        if (!mIsKeyguard || (!mShaderMode && recognizingAnim == null)) {
            return;
        }

        if (mShowing && !isAttachedToWindow()) {
            mShowing = false;
        }

        if (mShowing && isAttachedToWindow()) {
            return;
        }

        try {
            if (!isAttachedToWindow()) {
                mWindowManager.addView(this, mAnimParams);
            } else {
                mWindowManager.updateViewLayout(this, mAnimParams);
            }

            mShowing = true;
            if (mShaderMode) {
                startShaderAnimation();
            } else {
                recognizingAnim.stop();
                recognizingAnim.selectDrawable(0);
                recognizingAnim.start();
            }
        } catch (RuntimeException e) {
            mShowing = false;
            Log.e(LOG_TAG, "Error adding animation window", e);
        }
    }

    public void hide() {
        try {
            stopShaderAnimation();
            if (recognizingAnim != null) {
                recognizingAnim.stop();
                recognizingAnim.selectDrawable(0);
                clearAnimation();
            }

            if (isAttachedToWindow()) {
                mWindowManager.removeViewImmediate(this);
            }
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Error removing animation window", e);
        }

        mShowing = false;
    }

    public void removeAnimation() {
        hide();
        mWallpaperGeneration++;
        if (mWallpaperBitmap != null && !mWallpaperBitmap.isRecycled()) {
            mWallpaperBitmap.recycle();
            mWallpaperBitmap = null;
        }
        if (mWaterShader != null) {
            mWaterShader.clearBackground();
            mWaterShader = null;
            mWaterPaint.setShader(null);
        }
        if (mAnimationAdded) {
            mContext.unregisterReceiver(mIntentReceiver);
            mKeyguardStateController.removeCallback(keyguardStateCallback);
            mAnimationAdded = false;
        }
    }

    public void setIsKeyguard(boolean isKeyguard) {
        mIsKeyguard = isKeyguard;
        if (mIsKeyguard) {
            addAnimation();
        } else {
            removeAnimation();
        }
    }
}
