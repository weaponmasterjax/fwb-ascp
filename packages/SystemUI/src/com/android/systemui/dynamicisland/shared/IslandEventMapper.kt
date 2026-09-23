/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import android.media.AudioManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.model.RecordingState
import com.android.systemui.res.R

internal data class EventStyle(
    val accent: Color,
    val icon: ImageVector?,
    val labelRes: Int,
)

internal fun eventStyleFor(event: IslandEvent): EventStyle = when (event) {
    is IslandEvent.AudioRecording -> EventStyle(
        accent = when (event.state) {
            RecordingState.RECORDING -> RedAccent
            RecordingState.PAUSED -> PausedGray
            RecordingState.SAVED -> GreenAccent
        },
        icon = when (event.state) {
            RecordingState.RECORDING -> Icons.Filled.Mic
            RecordingState.PAUSED -> Icons.Filled.Pause
            RecordingState.SAVED -> Icons.Filled.CheckCircle
        },
        labelRes = when (event.state) {
            RecordingState.RECORDING -> R.string.dynamic_island_recording
            RecordingState.PAUSED -> R.string.dynamic_island_paused
            RecordingState.SAVED -> R.string.dynamic_island_saved
        },
    )
    is IslandEvent.Media -> EventStyle(
        accent = PurpleAccent,
        icon = Icons.Filled.MusicNote,
        labelRes = R.string.dynamic_island_music,
    )
    is IslandEvent.Call -> EventStyle(
        accent = GreenAccent,
        icon = null,
        labelRes = R.string.dynamic_island_call,
    )
    is IslandEvent.PromotedOngoing -> EventStyle(
        accent = BlueAccent,
        icon = null,
        labelRes = R.string.dynamic_island_on,
    )
    is IslandEvent.NowPlaying -> EventStyle(
        accent = MintAccent,
        icon = Icons.Filled.MusicNote,
        labelRes = R.string.dynamic_island_now_playing,
    )
    is IslandEvent.Sports -> EventStyle(
        accent = when (event.status) {
            IslandEvent.GameStatus.LIVE, IslandEvent.GameStatus.HALFTIME -> GreenAccent
            IslandEvent.GameStatus.PRE_GAME -> BlueAccent
            IslandEvent.GameStatus.FINAL -> MintAccent
        },
        icon = null,
        labelRes = R.string.dynamic_island_on,
    )
    is IslandEvent.Bluetooth -> EventStyle(
        accent = BlueAccent,
        icon = null,
        labelRes = R.string.dynamic_island_connected,
    )
    is IslandEvent.Hotspot -> EventStyle(
        accent = TealAccent,
        icon = Icons.Filled.Wifi,
        labelRes = R.string.dynamic_island_hotspot,
    )
    is IslandEvent.Charging -> EventStyle(
        accent = GreenAccent,
        icon = Icons.Filled.BatteryChargingFull,
        labelRes = if (event.isWireless) R.string.dynamic_island_wireless_charging
        else R.string.dynamic_island_charging,
    )
    is IslandEvent.Alarm -> EventStyle(
        accent = OrangeAccent,
        icon = Icons.Filled.Alarm,
        labelRes = R.string.dynamic_island_alarm,
    )
    is IslandEvent.Timer -> EventStyle(
        accent = BlueAccent,
        icon = Icons.Filled.Timer,
        labelRes = R.string.dynamic_island_timer,
    )
    is IslandEvent.Stopwatch -> EventStyle(
        accent = MintAccent,
        icon = Icons.Filled.AvTimer,
        labelRes = R.string.dynamic_island_stopwatch,
    )
    is IslandEvent.RingerMode -> EventStyle(
        accent = when (event.mode) {
            AudioManager.RINGER_MODE_SILENT -> RedAccent
            AudioManager.RINGER_MODE_VIBRATE -> OrangeAccent
            else -> BlueAccent
        },
        icon = when (event.mode) {
            AudioManager.RINGER_MODE_SILENT -> Icons.Filled.VolumeOff
            AudioManager.RINGER_MODE_VIBRATE -> Icons.Filled.Vibration
            else -> Icons.Filled.VolumeUp
        },
        labelRes = when (event.mode) {
            AudioManager.RINGER_MODE_SILENT -> R.string.dynamic_island_silent
            AudioManager.RINGER_MODE_VIBRATE -> R.string.dynamic_island_vibrate
            else -> R.string.dynamic_island_ring
        },
    )
    is IslandEvent.Vpn -> EventStyle(
        accent = IndigoAccent,
        icon = Icons.Filled.VpnKey,
        labelRes = if (event.isBranded) R.string.dynamic_island_vpn_active
        else R.string.dynamic_island_vpn_connected,
    )
    is IslandEvent.Clipboard -> EventStyle(
        accent = IndigoAccent,
        icon = Icons.Filled.ContentCopy,
        labelRes = R.string.dynamic_island_copied,
    )
    is IslandEvent.Torch -> EventStyle(
        accent = YellowAccent,
        icon = Icons.Filled.FlashlightOn,
        labelRes = R.string.dynamic_island_flashlight,
    )
    is IslandEvent.Notification -> EventStyle(
        accent = BlueAccent,
        icon = Icons.Filled.Notifications,
        labelRes = R.string.dynamic_island_on,
    )
    is IslandEvent.AppSwitch -> EventStyle(
        accent = BlueAccent,
        icon = null,
        labelRes = R.string.dynamic_island_on,
    )
    is IslandEvent.BiometricUnlock -> EventStyle(
        accent = GreenAccent,
        icon = Icons.Filled.Fingerprint,
        labelRes = R.string.dynamic_island_unlocked,
    )
    is IslandEvent.KeyguardIndication -> EventStyle(
        accent = when (event.indicationType) {
            IslandEvent.KeyguardIndication.IndicationType.BIOMETRIC -> GreenAccent
            IslandEvent.KeyguardIndication.IndicationType.ALIGNMENT -> OrangeAccent
            else -> IndigoAccent
        },
        icon = null,
        labelRes = R.string.dynamic_island_on,
    )
    is IslandEvent.AospChip -> EventStyle(
        accent = BlueAccent,
        icon = Icons.Filled.Notifications,
        labelRes = R.string.dynamic_island_on,
    )
}
