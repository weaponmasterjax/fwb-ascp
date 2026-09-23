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

package com.android.systemui.dynamicisland.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun rememberElapsedDurationText(
    baseElapsedRealtimeMs: Long,
    isRunning: Boolean = true,
): String {
    var now by remember(baseElapsedRealtimeMs) {
        mutableLongStateOf(android.os.SystemClock.elapsedRealtime())
    }

    if (isRunning && baseElapsedRealtimeMs > 0L) {
        LaunchedEffect(baseElapsedRealtimeMs) {
            while (true) {
                now = android.os.SystemClock.elapsedRealtime()
                delay(100)
            }
        }
    }

    val elapsedSeconds = ((now - baseElapsedRealtimeMs).coerceAtLeast(0L) / 1000L)
    return formatElapsedDuration(elapsedSeconds)
}

private fun formatElapsedDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
