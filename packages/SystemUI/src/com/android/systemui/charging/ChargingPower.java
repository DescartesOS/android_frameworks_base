/*
 * Copyright (C) 2026 The DescartesOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.charging;

public final class ChargingPower {
    private static final long FAST_CHARGING_MICROWATTS = 15_000_000L;

    private ChargingPower() {}

    public static long fromMicroampsAndMicrovolts(int microamps, int microvolts) {
        if (microamps <= 0 || microvolts <= 0) return -1L;
        return (long) microamps * microvolts / 1_000_000L;
    }

    public static boolean isFast(long microwatts) {
        return microwatts > FAST_CHARGING_MICROWATTS;
    }
}
