/*
 * Copyright (C) 2026 The DescartesOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.navigationbar.gestural.onehand;

public final class OneHandGestureMath {
    private OneHandGestureMath() {}
    public static int direction(float inward, float vertical, boolean longSwipe) {
        if (inward <= 0f) return 0;
        if (vertical < -inward * (longSwipe ? 0.577f : 1f)) return 1;
        if (vertical > inward * (longSwipe ? 0.364f : 0.466f)) return 2;
        return 0;
    }
}
