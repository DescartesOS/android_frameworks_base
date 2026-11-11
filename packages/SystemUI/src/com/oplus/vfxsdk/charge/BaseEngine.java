package com.oplus.vfxsdk.charge;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.view.Surface;

public abstract class BaseEngine implements IEngine {
    static final String TAG = "BaseEngine";
    protected long mHandle = 0;

        public enum TextureID {
                BACKGROUND(0),
                FOREGROUND(1),
        TEXTURE3(2),
                TEXTURE4(3),
        TEXTURE5(4),
                TEXTURE6(5),
                TEXTURE7(6),
                TEXTURE8(7),
        TEXTURE9(8),
                TEXTURE10(9),
                TEXTURE11(10),
                TEXTURE12(11);

        public final int id;

        TextureID(int i) {
            this.id = i;
        }
    }

    static {
        System.loadLibrary("nativeChargingRing");
    }

    public static native void nativeDestroySurface(long j);

    public static native void nativeExit(long j);

    public static native void nativeSetAssetManager(long j, AssetManager assetManager);

    public static native void nativeSetBitmap(long j, Bitmap bitmap, int i);

    public static native void nativeSetDensity(long j, double d);

    public static native void nativeSetSurface(long j, Surface surface);

    public void destroySurface() {
        nativeDestroySurface(this.mHandle);
    }

    public void exit() {
        nativeExit(this.mHandle);
        this.mHandle = 0L;
    }

    
    public void setAssetManager(AssetManager assetManager) {
        nativeSetAssetManager(this.mHandle, assetManager);
    }

    public void setBitmap(Bitmap bitmap, TextureID textureID) {

        nativeSetBitmap(this.mHandle, bitmap, textureID.id);
    }

    
    public void setDensity(float f) {
        nativeSetDensity(this.mHandle, f);
    }

    
    public void setSurface(Surface surface) {

        nativeSetSurface(this.mHandle, surface);
    }
}
