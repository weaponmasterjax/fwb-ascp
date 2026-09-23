/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.content.Context
import android.os.SystemProperties
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.MaterialTheme
import com.android.compose.theme.LocalAndroidColorScheme

class CustomColorScheme(private val context: Context) {
    val qsTileColor: Color
        @Composable
        @ReadOnlyComposable
        get() {
            val resolver = context.contentResolver
            val blurEnabled = Settings.Global.getInt(
                resolver,
                Settings.Global.DISABLE_WINDOW_BLURS,
                if (blurEnabledByDefault) 0 else 1
            ) != 1

            val useAlternateColor = Settings.System.getInt(
                resolver,
                Settings.System.QS_TILE_ALTERNATE_COLOR,
                0
            ) == 1

            return if (blurEnabled) {
                if (useAlternateColor)
                    MaterialTheme.colorScheme.surfaceContainer
                else
                    LocalAndroidColorScheme.current.surfaceEffect1
            } else {
                LocalAndroidColorScheme.current.surfaceEffect1
            }
        }

    companion object {
        private val blurEnabledByDefault: Boolean by lazy {
            SystemProperties.getBoolean("ro.custom.blur.enable", false)
        }

        val current: CustomColorScheme
            @Composable
            @ReadOnlyComposable
            get() = CustomColorScheme(LocalContext.current)
    }
}

