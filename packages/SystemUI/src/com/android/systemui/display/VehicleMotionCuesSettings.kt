/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.systemui.display

import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.settings.SystemSettings
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SysUISingleton
class VehicleMotionCuesSettings @Inject constructor(
    private val systemSettings: SystemSettings,
    @Main private val mainExecutor: Executor,
) {
    companion object {
        const val KEY_ENABLED = Settings.System.VEHICLE_MOTION_CUES
        const val KEY_DOT_SIZE = Settings.System.VEHICLE_MOTION_CUES_DOT_SIZE
        const val KEY_SENSITIVITY = Settings.System.VEHICLE_MOTION_CUES_SENSITIVITY
        const val KEY_DOT_COUNT = Settings.System.VEHICLE_MOTION_CUES_DOT_COUNT

        const val DEFAULT_DOT_SIZE = 10f
        const val DEFAULT_SENSITIVITY = 15f
        const val DEFAULT_DOT_COUNT = 14
    }

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _dotSize = MutableStateFlow(DEFAULT_DOT_SIZE)
    val _sensitivity = MutableStateFlow(DEFAULT_SENSITIVITY)
    private val _dotCount = MutableStateFlow(DEFAULT_DOT_COUNT)

    val dotSize: StateFlow<Float> = _dotSize.asStateFlow()
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()
    val dotCount: StateFlow<Int> = _dotCount.asStateFlow()

    private val settingsObserver =
        object : ContentObserver(mainExecutor, 0) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        refresh()
        systemSettings.registerContentObserverForUserSync(
            KEY_ENABLED, false, settingsObserver, UserHandle.USER_ALL,
        )
        systemSettings.registerContentObserverForUserSync(
            KEY_DOT_SIZE, false, settingsObserver, UserHandle.USER_ALL,
        )
        systemSettings.registerContentObserverForUserSync(
            KEY_SENSITIVITY, false, settingsObserver, UserHandle.USER_ALL,
        )
        systemSettings.registerContentObserverForUserSync(
            KEY_DOT_COUNT, false, settingsObserver, UserHandle.USER_ALL,
        )
    }

    fun setEnabled(enabled: Boolean) {
        systemSettings.putIntForUser(KEY_ENABLED, if (enabled) 1 else 0, UserHandle.USER_CURRENT)
    }

    fun setDotSize(value: Float) {
        systemSettings.putFloatForUser(KEY_DOT_SIZE, value, UserHandle.USER_CURRENT)
    }

    fun setSensitivity(value: Float) {
        systemSettings.putFloatForUser(KEY_SENSITIVITY, value, UserHandle.USER_CURRENT)
    }

    fun setDotCount(value: Int) {
        systemSettings.putIntForUser(KEY_DOT_COUNT, value, UserHandle.USER_CURRENT)
    }

    private fun refresh() {
        _isEnabled.value =
            systemSettings.getIntForUser(KEY_ENABLED, 0, UserHandle.USER_CURRENT) == 1
        _dotSize.value =
            systemSettings.getFloatForUser(KEY_DOT_SIZE, DEFAULT_DOT_SIZE, UserHandle.USER_CURRENT)
        _sensitivity.value =
            systemSettings.getFloatForUser(KEY_SENSITIVITY, DEFAULT_SENSITIVITY, UserHandle.USER_CURRENT)
        _dotCount.value =
            systemSettings.getIntForUser(KEY_DOT_COUNT, DEFAULT_DOT_COUNT, UserHandle.USER_CURRENT)
    }
}
