package com.oplus.vfxsdk.charge.charging;

import android.util.Log;
import com.oplus.vfxsdk.charge.BaseEngine;
import com.oplus.vfxsdk.charge.IEngine;

public class VFXChargingEngine extends BaseEngine {
    static final String TAG = "VFXChargingEngine";
    static final String VERSION = "1.0.9";
    private Runnable ringStartedCallback;

    public static native long nativeInit(IEngine iEngine);

    public static native void nativeSetAnimationDuration(long j, float f, float f2, float f3, float f4);

    public static native void nativeSetBlurWeight(long j, float f, float f2);

    public static native void nativeSetCenter(long j, float f, float f2);

    public static native void nativeSetDensityScale(long j, float f);

    public static native void nativeSetFlashDuration(long j, float f);

    public static native void nativeSetFlashEnabled(long j, boolean z);

    public static native void nativeSetFlashSize(long j, int i);

    public static native void nativeSetFrameInterval(long j, int i);

    public static native void nativeSetParticles(long j, int i, int i2, float f, float f2);

    public static native void nativeSetParticlesCircle(long j, float f, float f2);

    public static native void nativeSetParticlesDistortion(long j, float f);

    public static native void nativeSetParticlesLuminance(long j, float f);

    public static native void nativeSetParticlesMoveType(long j, int i);

    public static native void nativeSetParticlesRing(long j, float f, float f2);

    public static native void nativeSetRingAndParticlesCDDisplacement(long j, int i, int i2, float f, float f2);

    public static native void nativeSetRingFadeInCurve(long j, float f, float f2, float f3, float f4);

    public static native void nativeSetRingFadeOutCurve(long j, float f, float f2, float f3, float f4);

    public static native void nativeSetRingGlowTime(long j, float f, float f2, float f3, float f4);

    public static native void nativeSetRingGlowWeight(long j, float f);

    public static native void nativeSetRingLineWeight(long j, float f);

    public static native void nativeSetRingRadius(long j, int i);

    public static native void nativeSetRingRingWeight(long j, float f);

    public static native void nativeSetRingRotateSpeed(long j, float f, float f2);

    public static native void nativeSetRingStartOffset(long j, float f);

    public static native void nativeStart(long j);

    public static native void nativeStop(long j);

    private void onFlashAnimationFinishedCallBack() {}
    private void onRingAnimationStartedCallBack() {
        if (ringStartedCallback != null) ringStartedCallback.run();
    }
    public void setRingStartedCallback(Runnable callback) { ringStartedCallback = callback; }
    public void init() {
        if (this.mHandle != 0) {
            Log.e("ChargingEffect_VFXChargingEngine", "A render thread exist.");
        } else {
            this.mHandle = nativeInit(this);
        }
    }

    public void release() {
        this.ringStartedCallback = null;
    }

    public void setAnimationDuration(float f, float f2, float f3, float f4) {
        nativeSetAnimationDuration(this.mHandle, f, f2, f3, f4);
    }

    public void setBlurWeight(float f, float f2) {
        nativeSetBlurWeight(this.mHandle, f, f2);
    }

    public void setCenter(float f, float f2) {
        nativeSetCenter(this.mHandle, f, f2);
    }


    public void setDensityScale(float f) {
        nativeSetDensityScale(this.mHandle, f);
    }

    public void setFlashDuration(float f) {
        nativeSetFlashDuration(this.mHandle, f);
    }

    public void setFlashEnabled(boolean z) {
        nativeSetFlashEnabled(this.mHandle, z);
    }

    public void setFlashSize(int i) {
        nativeSetFlashSize(this.mHandle, i);
    }

    public void setFrameInterval(int i) {
        nativeSetFrameInterval(this.mHandle, i);
    }

    public void setParticles(int i, int i2, float f, float f2) {
        nativeSetParticles(this.mHandle, i, i2, f, f2);
    }

    public void setParticlesCircle(float f, float f2) {
        nativeSetParticlesCircle(this.mHandle, f, f2);
    }

    public void setParticlesDistortion(float f) {
        nativeSetParticlesDistortion(this.mHandle, f);
    }

    public void setParticlesLuminance(float f) {
        nativeSetParticlesLuminance(this.mHandle, f);
    }

    public void setParticlesMoveType(int i) {
        nativeSetParticlesMoveType(this.mHandle, i);
    }

    public void setParticlesRing(float f, float f2) {
        nativeSetParticlesRing(this.mHandle, f, f2);
    }

    public void setRingAndParticlesCDDisplacement(int i, int i2, float f, float f2) {
        nativeSetRingAndParticlesCDDisplacement(this.mHandle, i, i2, f, f2);
    }

    public void setRingFadeInCurve(float f, float f2, float f3, float f4) {
        nativeSetRingFadeInCurve(this.mHandle, f, f2, f3, f4);
    }

    public void setRingFadeOutCurve(float f, float f2, float f3, float f4) {
        nativeSetRingFadeOutCurve(this.mHandle, f, f2, f3, f4);
    }

    public void setRingGlowTime(float f, float f2, float f3, float f4) {
        nativeSetRingGlowTime(this.mHandle, f, f2, f3, f4);
    }

    public void setRingGlowWeight(float f) {
        nativeSetRingGlowWeight(this.mHandle, f);
    }

    public void setRingLineWeight(float f) {
        nativeSetRingLineWeight(this.mHandle, f);
    }

    public void setRingRadius(int i) {
        nativeSetRingRadius(this.mHandle, i);
    }

    public void setRingRingWeight(float f) {
        nativeSetRingRingWeight(this.mHandle, f);
    }

    public void setRingRotateSpeed(float f, float f2) {
        nativeSetRingRotateSpeed(this.mHandle, f, f2);
    }

    public void setRingStartOffset(float f) {
        nativeSetRingStartOffset(this.mHandle, f);
    }

    public void start() {
        nativeStart(this.mHandle);
    }

    public void stop() {
        nativeStop(this.mHandle);
    }
}