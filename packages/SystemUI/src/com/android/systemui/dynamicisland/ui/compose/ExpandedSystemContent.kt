/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.compose

import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.dynamicisland.shared.toScaledBitmap
import com.android.systemui.dynamicisland.shared.IslandActions
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.shared.*
import com.android.systemui.res.R

@Composable
internal fun ChargingExpanded(event: IslandEvent.Charging) {
    ExpandedCardLayout(
        accentColor = GreenAccent,
        icon = {
            Icon(Icons.Filled.BatteryChargingFull, null, tint = GreenAccent, modifier = Modifier.size(30.dp))
        },
        title = {
            Text(
                if (event.isWireless) stringResource(R.string.dynamic_island_wireless_charging)
                else stringResource(R.string.dynamic_island_charging),
                color = OnCardText,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            ) {
                StatusChip("${event.level}%", GreenAccent)
                if (event.isPowerSave) {
                    StatusChip(stringResource(R.string.dynamic_island_battery_saver), OrangeAccent)
                }
            }
        },
        trailing = if (!event.timeRemaining.isNullOrEmpty()) {
            {
                Text(
                    "${event.timeRemaining} ${stringResource(R.string.dynamic_island_until_full)}",
                    color = SubtleGray,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else null,
    )
}

@Composable
fun BluetoothPopup(
    event: IslandEvent.Bluetooth,
    interactor: IslandActions,
    modifier: Modifier = Modifier,
) {
    PopupSurface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        modifier = modifier.widthIn(min = 300.dp, max = 380.dp),
    ) {
        BluetoothExpanded(event = event, interactor = interactor)
    }
}

@Composable
internal fun BluetoothExpanded(event: IslandEvent.Bluetooth, interactor: IslandActions) {
    val accent = Color(0xFF4DA6FF)
    val hasBatteries = event.leftBatteryLevel != null || event.rightBatteryLevel != null || event.caseBatteryLevel != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Event Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (event.deviceImage != null) {
                Image(
                    painter = rememberDrawablePainter(event.deviceImage),
                    contentDescription = event.deviceName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bluetooth_connected),
                        contentDescription = "Bluetooth",
                        tint = accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = event.deviceTypeLabel.ifEmpty { stringResource(R.string.dynamic_island_connected) },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    text = event.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.batteryLevel >= 0 && !hasBatteries) {
                    Text(
                        text = "${event.batteryLevel}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = accent,
                    )
                }
            }
        }

        // Untethered Batteries (Left, Right, Case)
        if (hasBatteries) {
            val batteries = listOfNotNull(
                event.leftBatteryLevel?.takeIf { it in 0..100 }?.let {
                    stringResource(R.string.dynamic_island_bluetooth_left_earbud) to it
                },
                event.rightBatteryLevel?.takeIf { it in 0..100 }?.let {
                    stringResource(R.string.dynamic_island_bluetooth_right_earbud) to it
                },
                event.caseBatteryLevel?.takeIf { it in 0..100 }?.let {
                    stringResource(R.string.dynamic_island_bluetooth_case) to it
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                batteries.forEach { (label, level) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$level%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                }
            }
        }

        // Action Chip (Disconnect)
        ActionChip(
            label = stringResource(R.string.dynamic_island_disconnect),
            painter = painterResource(id = R.drawable.ic_bluetooth_connected),
            color = OnDestructiveText,
            bg = DestructiveBg,
            modifier = Modifier.fillMaxWidth(),
            onClick = { interactor.disconnectBluetooth(event.address) },
        )
    }
}

@Composable
internal fun HotspotBadge(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 38.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF0F2D4A))
                .border(
                    1.dp,
                    Color(0xFF1E5D96).copy(alpha = 0.8f),
                    androidx.compose.foundation.shape.CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_hotspot),
            contentDescription = "Hotspot",
            tint = Color(0xFF4DA6FF),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun HotspotPopup(
    event: IslandEvent.Hotspot,
    interactor: IslandActions,
    modifier: Modifier = Modifier,
) {
    PopupSurface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        modifier = modifier.widthIn(min = 300.dp, max = 380.dp),
    ) {
        HotspotExpanded(event = event, interactor = interactor)
    }
}

@Composable
internal fun HotspotExpanded(event: IslandEvent.Hotspot, interactor: IslandActions) {
    val titleText =
        when (event.numDevices) {
            0 -> stringResource(R.string.dynamic_island_hotspot_active)
            1 -> "1 device connected"
            else -> "${event.numDevices} devices connected"
        }

    val sharedText =
        if (event.sharedBytes > 0L) {
            val mb = event.sharedBytes.toFloat() / (1024f * 1024f)
            "Shared: " + String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            "Shared: 0.0 MB"
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HotspotBadge(size = 42.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = titleText,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sharedText,
                color = Color(0xFF8E8E93),
                fontSize = 12.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        androidx.compose.material3.Button(
            onClick = { interactor.turnOffHotspot() },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors =
                androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2E),
                    contentColor = Color.White,
                ),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = "Turn off",
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun RingerModeExpanded(event: IslandEvent.RingerMode, interactor: IslandActions) {
    val style = eventStyleFor(event)
    ExpandedCardLayout(
        accentColor = style.accent,
        icon = { style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(22.dp)) } },
        title = {
            Text(stringResource(R.string.dynamic_island_sound_mode), color = OnCardText, style = MaterialTheme.typography.titleMedium)
            StatusChip(stringResource(style.labelRes), style.accent)
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceLg),
            ) {
                RingerCard(
                    isSelected = event.mode == AudioManager.RINGER_MODE_NORMAL,
                    icon = Icons.Filled.VolumeUp,
                    label = stringResource(R.string.dynamic_island_ring),
                    accent = BlueAccent,
                    onClick = { interactor.setRingerMode(AudioManager.RINGER_MODE_NORMAL) },
                    modifier = Modifier.weight(1f),
                )
                RingerCard(
                    isSelected = event.mode == AudioManager.RINGER_MODE_VIBRATE,
                    icon = Icons.Filled.Vibration,
                    label = stringResource(R.string.dynamic_island_vibrate),
                    accent = OrangeAccent,
                    onClick = { interactor.setRingerMode(AudioManager.RINGER_MODE_VIBRATE) },
                    modifier = Modifier.weight(1f),
                )
                RingerCard(
                    isSelected = event.mode == AudioManager.RINGER_MODE_SILENT,
                    icon = Icons.Filled.VolumeOff,
                    label = stringResource(R.string.dynamic_island_silent),
                    accent = RedAccent,
                    onClick = { interactor.setRingerMode(AudioManager.RINGER_MODE_SILENT) },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RingerCard(
    isSelected: Boolean,
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by
        animateColorAsState(
            targetValue = if (isSelected) accent.copy(alpha = AlphaIconBg) else OnCardText.copy(alpha = AlphaFaint),
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "ringer_bg",
        )
    val tint by
        animateColorAsState(
            targetValue = if (isSelected) accent else SubtleGray,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "ringer_tint",
        )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ShapeLg,
        color = bg,
    ) {
        Column(
            modifier = Modifier.padding(vertical = SpaceSection),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun VpnExpanded(event: IslandEvent.Vpn) {
    val style = eventStyleFor(event)
    ExpandedCardLayout(
        accentColor = style.accent,
        icon = { style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(28.dp)) } },
        title = {
            Text(
                stringResource(style.labelRes),
                color = OnCardText,
                style = MaterialTheme.typography.titleMedium,
            )
            StatusChip(
                if (event.isValidated) stringResource(R.string.dynamic_island_secured)
                else stringResource(R.string.dynamic_island_connecting),
                if (event.isValidated) GreenAccent else OrangeAccent,
            )
        },
        trailing = {
            PulsingDot(color = if (event.isValidated) GreenAccent else OrangeAccent, size = SpaceMd)
        },
    )
}

@Composable
internal fun RowScope.CompactBluetoothRow(event: IslandEvent.Bluetooth) {
    val AmberAccent = Color(0xFFE5B842)
    Box(
        modifier =
            Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(AmberAccent.copy(alpha = AlphaIconBg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(id = event.iconRes), null, tint = AmberAccent, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(
            event.deviceName,
            color = OnCardText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            event.deviceTypeLabel.ifEmpty { stringResource(R.string.dynamic_island_connected) },
            color = SubtleGray,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (event.batteryLevel >= 0) {
        Spacer(Modifier.width(SpaceMd))
        Text(
            "${event.batteryLevel}%",
            color = if (event.batteryLevel > 20) GreenAccent else RedAccent,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun RowScope.CompactHotspotRow(event: IslandEvent.Hotspot) {
    Box(
        modifier =
            Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(OrangeAccent.copy(alpha = AlphaIconBg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Wifi, null, tint = OrangeAccent, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(stringResource(R.string.dynamic_island_hotspot), color = OnCardText, style = MaterialTheme.typography.bodySmall)
        Text(
            when (event.numDevices) {
                0 -> stringResource(R.string.dynamic_island_no_devices)
                1 -> stringResource(R.string.dynamic_island_one_device)
                else -> stringResource(R.string.dynamic_island_hotspot_devices, event.numDevices)
            },
            color = SubtleGray,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    PulsingDot(color = OrangeAccent, size = 7.dp)
}

@Composable
internal fun RowScope.CompactChargingRow(event: IslandEvent.Charging) {
    Box(
        modifier =
            Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(GreenAccent.copy(alpha = AlphaIconBg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.BatteryChargingFull,
            null,
            tint = GreenAccent,
            modifier = Modifier.size(18.dp),
        )
    }
    Spacer(Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(
            if (event.isWireless) stringResource(R.string.dynamic_island_wireless_charging)
            else stringResource(R.string.dynamic_island_charging),
            color = OnCardText,
            style = MaterialTheme.typography.bodySmall,
        )
        val saverLabel = if (event.isPowerSave) stringResource(R.string.dynamic_island_saver) else null
        Text(
            buildString {
                append("${event.level}%")
                saverLabel?.let { append(" · $it") }
            },
            color = if (event.isPowerSave) OrangeAccent else GreenAccent,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun RowScope.CompactRingerRow(event: IslandEvent.RingerMode) {
    val style = eventStyleFor(event)
    Box(
        modifier = Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(style.accent.copy(alpha = AlphaStatusChip)),
        contentAlignment = Alignment.Center,
    ) {
        style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(18.dp)) }
    }
    Spacer(Modifier.width(SpaceLg))
    Text(event.label, color = OnCardText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
}

@Composable
internal fun RowScope.CompactVpnRow() {
    Box(
        modifier =
            Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(IndigoAccent.copy(alpha = AlphaIconBg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.VpnKey, null, tint = IndigoAccent, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(SpaceLg))
    Text(stringResource(R.string.dynamic_island_vpn_active), color = OnCardText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    PulsingDot(color = GreenAccent, size = 7.dp)
}
