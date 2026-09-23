/*
 * Copyright (C) 2025-2026 ASCP OS Project
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

package com.android.systemui.qs.ui.composable

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.res.R

enum class QsBrightnessSliderPosition {
    TOP,
    BOTTOM
}

data class QsBrightnessSettings(
    val showSlider: Int, // 0: Never, 1: Show when expanded, 2: Show always
    val position: QsBrightnessSliderPosition,
)

@Composable
fun rememberQsBrightnessSettings(): QsBrightnessSettings {
    val context = LocalContext.current
    var settings by remember {
        mutableStateOf(
            QsBrightnessSettings(
                showSlider = Settings.Secure.getIntForUser(
                    context.contentResolver,
                    Settings.Secure.QS_SHOW_BRIGHTNESS_SLIDER,
                    if (context.resources.getBoolean(R.bool.def_qs_show_brightness_slider)) 1 else 0,
                    UserHandle.USER_CURRENT
                ),
                position = if (Settings.Secure.getIntForUser(
                    context.contentResolver,
                    Settings.Secure.QS_BRIGHTNESS_SLIDER_POSITION,
                    0,
                    UserHandle.USER_CURRENT
                ) == 1) QsBrightnessSliderPosition.BOTTOM else QsBrightnessSliderPosition.TOP
            )
        )
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val show = Settings.Secure.getIntForUser(
                    context.contentResolver,
                    Settings.Secure.QS_SHOW_BRIGHTNESS_SLIDER,
                    if (context.resources.getBoolean(R.bool.def_qs_show_brightness_slider)) 1 else 0,
                    UserHandle.USER_CURRENT
                )
                val pos = if (Settings.Secure.getIntForUser(
                    context.contentResolver,
                    Settings.Secure.QS_BRIGHTNESS_SLIDER_POSITION,
                    0,
                    UserHandle.USER_CURRENT
                ) == 1) QsBrightnessSliderPosition.BOTTOM else QsBrightnessSliderPosition.TOP
                settings = QsBrightnessSettings(show, pos)
            }
        }
        val cr = context.contentResolver
        cr.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.QS_SHOW_BRIGHTNESS_SLIDER),
            false, observer, UserHandle.USER_ALL
        )
        cr.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.QS_BRIGHTNESS_SLIDER_POSITION),
            false, observer, UserHandle.USER_ALL
        )
        onDispose {
            cr.unregisterContentObserver(observer)
        }
    }

    return settings
}
