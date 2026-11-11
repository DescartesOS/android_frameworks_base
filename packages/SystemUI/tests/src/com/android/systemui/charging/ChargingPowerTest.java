/*
 * Copyright (C) 2026 The DescartesOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.charging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChargingPowerTest {
    @Test public void thresholdIsStrictlyAbove15Watts() {
        assertFalse(ChargingPower.isFast(ChargingPower.fromMicroampsAndMicrovolts(2_000_000, 5_000_000)));
        assertFalse(ChargingPower.isFast(ChargingPower.fromMicroampsAndMicrovolts(3_000_000, 5_000_000)));
        assertTrue(ChargingPower.isFast(ChargingPower.fromMicroampsAndMicrovolts(3_000_001, 5_000_000)));
        assertTrue(ChargingPower.isFast(ChargingPower.fromMicroampsAndMicrovolts(2_000_000, 9_000_000)));
    }

    @Test public void unknownReadingsAreNeverFast() {
        for (int[] readings : new int[][]{{-1, -1}, {-1, 9_000_000}, {3_000_000, -1},
                {0, 9_000_000}, {3_000_000, 0}}) {
            long power = ChargingPower.fromMicroampsAndMicrovolts(readings[0], readings[1]);
            assertEquals(-1L, power);
            assertFalse(ChargingPower.isFast(power));
        }
    }

    @Test public void conversionDoesNotOverflowAtHighPower() {
        assertEquals(120_000_000L, ChargingPower.fromMicroampsAndMicrovolts(6_000_000, 20_000_000));
        assertTrue(ChargingPower.isFast(ChargingPower.fromMicroampsAndMicrovolts(Integer.MAX_VALUE,
                Integer.MAX_VALUE)));
    }
}
