/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.sliders.ui.compose

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class VolumeGradient(
    val startColor: Color,
    val endColor: Color,
)

@Composable
fun rememberVolumeGradientEnabled(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readEnabled(): Boolean {
        return try {
            Settings.System.getIntForUser(
                contentResolver, "volume_slider_gradient", 0,
                UserHandle.USER_CURRENT
            ) != 0
        } catch (_: Throwable) {
            false
        }
    }

    var enabled by remember { mutableStateOf(readEnabled()) }

    DisposableEffect(contentResolver) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    enabled = readEnabled()
                }
            }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor("volume_slider_gradient"),
            false, observer, UserHandle.USER_ALL
        )

        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return enabled
}

@Composable
fun rememberGradientColorMode(): Int {
    val contentResolver = LocalContext.current.contentResolver

    fun readMode(): Int = try {
        Settings.System.getIntForUser(
            contentResolver, "custom_gradient_color_mode", 0,
            UserHandle.USER_CURRENT
        )
    } catch (_: Throwable) {
        0
    }

    var mode by remember { mutableIntStateOf(readMode()) }

    DisposableEffect(contentResolver) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                mode = readMode()
            }
        }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor("custom_gradient_color_mode"),
            false, observer, UserHandle.USER_ALL
        )

        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return mode
}

@Composable
fun rememberGradientCustomColors(): VolumeGradient {
    val contentResolver = LocalContext.current.contentResolver

    fun readStart(): Int = try {
        Settings.System.getIntForUser(
            contentResolver, "custom_gradient_start_color", 0,
            UserHandle.USER_CURRENT
        )
    } catch (_: Throwable) {
        0
    }

    fun readEnd(): Int = try {
        Settings.System.getIntForUser(
            contentResolver, "custom_gradient_end_color", 0,
            UserHandle.USER_CURRENT
        )
    } catch (_: Throwable) {
        0
    }

    var startInt by remember { mutableIntStateOf(readStart()) }
    var endInt by remember { mutableIntStateOf(readEnd()) }

    DisposableEffect(contentResolver) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                startInt = readStart()
                endInt = readEnd()
            }
        }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor("custom_gradient_start_color"),
            false, observer, UserHandle.USER_ALL
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor("custom_gradient_end_color"),
            false, observer, UserHandle.USER_ALL
        )

        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return VolumeGradient(
        startColor = Color(startInt).takeIf { it != Color.Transparent }
            ?: MaterialTheme.colorScheme.primary,
        endColor = Color(endInt).takeIf { it != Color.Transparent }
            ?: MaterialTheme.colorScheme.secondary,
    )
}
