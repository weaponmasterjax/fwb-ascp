/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.dynamicisland.shared.IslandActions
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.shared.*
import com.android.systemui.flashlight.ui.composable.VerticalFlashlightSlider
import com.android.systemui.res.R

@Composable
internal fun RowScope.TorchPill(event: IslandEvent.Torch) {
    val style = eventStyleFor(event)
    style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(15.dp)) }
    Spacer(Modifier.width(SpaceSm))
    Text(stringResource(style.labelRes), color = OnCardText, style = PillPrimary)
    if (event.supportsLevel) {
        Spacer(Modifier.width(SpaceSm))
        val pct = (event.level.toFloat() / event.maxLevel * 100).toInt()
        Text("$pct%", color = style.accent, style = PillAccent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TorchExpanded(
    event: IslandEvent.Torch,
    interactor: IslandActions,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    var isDragging by remember { mutableStateOf(false) }
    var localLevel by remember { mutableIntStateOf(event.level) }
    LaunchedEffect(event.level) { if (!isDragging) localLevel = event.level }
    val displayLevel = if (isDragging) localLevel else event.level

    val style = eventStyleFor(event)
    ExpandedCardLayout(
        accentColor = style.accent,
        icon = { style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(28.dp)) } },
        title = {
            Text(stringResource(style.labelRes), color = OnCardText, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(style.accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (event.supportsLevel) "${(displayLevel.toFloat() / event.maxLevel * 100).toInt()}%" else stringResource(R.string.dynamic_island_torch_active),
                    color = style.accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
        },
        trailing = {
            androidx.compose.material3.Button(
                onClick = { interactor.dismissEvent(event) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = style.accent,
                    contentColor = androidx.compose.ui.graphics.Color.Black,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.dynamic_island_turn_off),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        },
        actions = if (event.supportsLevel) {
            {
                Box(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    VerticalFlashlightSlider(
                        valueRange = 0..event.maxLevel,
                        onValueChange = {
                            isDragging = true
                            localLevel = it
                            interactor.setTorchLevelTemporary(it)
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            interactor.setTorchLevel(it)
                        },
                        isEnabled = true,
                        levelValue = displayLevel,
                        hapticsViewModelFactory = hapticsViewModelFactory,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = style.accent,
                                activeTrackColor = style.accent,
                            ),
                    )
                }
            }
        } else null,
    )
}

@Composable
internal fun RowScope.CompactTorchRow(event: IslandEvent.Torch) {
    val style = eventStyleFor(event)
    style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(SpaceXxl)) }
    Spacer(Modifier.width(SpaceSm))
    Text(
        stringResource(style.labelRes),
        color = SubtleGray,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier.weight(1f),
    )
    if (event.supportsLevel) {
        val pct = (event.level.toFloat() / event.maxLevel * 100).toInt()
        Text("$pct%", color = style.accent, style = PillAccent)
    } else {
        Text(stringResource(R.string.dynamic_island_on), color = style.accent, style = PillAccent)
    }
}

