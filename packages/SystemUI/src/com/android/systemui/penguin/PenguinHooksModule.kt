/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin

import com.android.systemui.keyguard.ui.composable.media.ExpandableLockscreenPlayer
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module

@Module
interface PenguinHooksModule {
    @BindsOptionalOf fun depthSubject(): DepthSubject
    @BindsOptionalOf fun lockscreenPlayerOptional(): LockscreenPlayer

    @Binds fun lockscreenPlayer(player: ExpandableLockscreenPlayer): LockscreenPlayer
}
