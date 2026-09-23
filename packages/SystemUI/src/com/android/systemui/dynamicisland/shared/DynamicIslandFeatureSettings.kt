/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dynamicisland.shared

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object DynamicIslandFeatureSettings {
    const val SHOW_DYNAMIC_ISLAND = Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND
    const val MEDIA_CONTROLS = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_MEDIA_CONTROLS
    const val SCREEN_RECORDING = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_SCREEN_RECORDING
    const val ALARMS = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_ALARMS
    const val FLASHLIGHT = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_FLASHLIGHT
    const val STOPWATCH = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_STOPWATCH
    const val LIVE_SCORES = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_LIVE_SCORES
    const val LYRICS = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_LYRICS
    const val ONGOING_ACTIVITIES = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_ONGOING_ACTIVITIES
    const val CALLS = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_CALLS
    const val HOTSPOT = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_HOTSPOT
    const val BLUETOOTH = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_BLUETOOTH
    const val WIDTH_SCALE = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_WIDTH_SCALE
    const val HEIGHT_SCALE = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_HEIGHT_SCALE
    const val POPUP_COLOR_MODE = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_POPUP_COLOR_MODE

    const val SCALE_MIN = 0.7f
    const val SCALE_MAX = 1.4f
    private const val SCALE_DEFAULT = 1.0f
    private const val SCALE_PERCENT_DEFAULT = 100

    fun ContentResolver.readDynamicIslandScale(key: String): Float {
        val percent = Settings.System.getIntForUser(
            this,
            key,
            SCALE_PERCENT_DEFAULT,
            UserHandle.USER_CURRENT,
        )
        return (percent / 100f).coerceIn(SCALE_MIN, SCALE_MAX)
    }

    fun observeDynamicIslandScale(
        context: Context,
        key: String,
    ): Flow<Float> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(context.contentResolver.readDynamicIslandScale(key))
                    }
                }

            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(key),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            trySend(context.contentResolver.readDynamicIslandScale(key))
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }

    const val POPUP_COLOR_MODE_DEFAULT = 0
    const val POPUP_COLOR_MODE_BLUR = 1
    const val POPUP_COLOR_MODE_SOLID_BLACK = 2

    fun ContentResolver.readDynamicIslandPopupColorMode(): Int {
        return Settings.System.getIntForUser(
            this,
            POPUP_COLOR_MODE,
            POPUP_COLOR_MODE_BLUR,
            UserHandle.USER_CURRENT,
        )
    }

    fun observeDynamicIslandPopupColorMode(context: Context): Flow<Int> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(context.contentResolver.readDynamicIslandPopupColorMode())
                    }
                }
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(POPUP_COLOR_MODE),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            trySend(context.contentResolver.readDynamicIslandPopupColorMode())
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }

    fun ContentResolver.readDynamicIslandFeatureEnabled(
        key: String,
        defaultValue: Boolean = true,
    ): Boolean {
        return Settings.System.getIntForUser(
            this,
            key,
            if (defaultValue) 1 else 0,
            UserHandle.USER_CURRENT,
        ) != 0
    }

    fun observeDynamicIslandFeatureEnabled(
        context: Context,
        key: String,
        defaultValue: Boolean = true,
    ): Flow<Boolean> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(
                            context.contentResolver.readDynamicIslandFeatureEnabled(
                                key,
                                defaultValue,
                            )
                        )
                    }
                }

            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(key),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            trySend(context.contentResolver.readDynamicIslandFeatureEnabled(key, defaultValue))
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }

    fun observeDynamicIslandEnabled(context: Context): Flow<Boolean> =
        observeDynamicIslandFeatureEnabled(context, SHOW_DYNAMIC_ISLAND, defaultValue = false)
}