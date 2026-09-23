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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon as SysUISharedIcon
import com.android.systemui.dynamicisland.shared.IslandActions
import com.android.systemui.dynamicisland.ui.model.PopupChipId
import com.android.systemui.dynamicisland.ui.model.PopupChipModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.boundsInWindow
import com.android.systemui.dynamicisland.ui.model.PopupContentModel
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Phone-only centered dynamic island that pages through active popup chips. */
@Composable
fun StatusBarDynamicIslandContainer(
    chips: List<PopupChipModel.Shown>,
    onMediaControlPopupVisibilityChanged: (Boolean) -> Unit,
    islandActions: IslandActions,
    modifier: Modifier = Modifier,
    onIslandBoundsChanged: ((android.graphics.Rect) -> Unit)? = null,
) {
    val cutoutSpec = rememberDynamicIslandCutoutSpec()
    var selectedChipId by remember { mutableStateOf<PopupChipId?>(null) }
    var popupAnchorChip by remember { mutableStateOf<PopupChipModel.Shown?>(null) }
    var popupVisible by remember { mutableStateOf(false) }
    var knownChipIds by remember { mutableStateOf<List<PopupChipId>>(emptyList()) }
    var chipBoundsInScreen by remember { mutableStateOf<Rect?>(null) }
    var lastDirection by remember { mutableIntStateOf(1) }
    val islandView = LocalView.current

    DisposableEffect(Unit) {
        onDispose {
            onIslandBoundsChanged?.invoke(android.graphics.Rect())
        }
    }

    LaunchedEffect(chips) {
        val currentChipIds = chips.map { it.chipId }
        val newestChipId =
            if (knownChipIds.isEmpty()) {
                null
            } else {
                currentChipIds.lastOrNull { it !in knownChipIds }
            }
        selectedChipId =
            when {
                newestChipId != null -> newestChipId
                chips.any { it.chipId == selectedChipId } -> selectedChipId
                else -> chips.firstOrNull()?.chipId
            }
        knownChipIds = currentChipIds

        val incomingCallChip = chips.firstOrNull {
            it.chipId == PopupChipId.OngoingCall &&
                (it.popupContent as? PopupContentModel.OngoingCall)?.event?.callType == "Phone:incoming"
        }
        if (incomingCallChip != null && !popupVisible) {
            incomingCallChip.showPopup()
        }
    }

    val selectedIndex = chips.indexOfFirst { it.chipId == selectedChipId }.coerceAtLeast(0)
    val selectedChip = chips.getOrNull(selectedIndex)
    val shownChip = chips.firstOrNull { it.isPopupShown }

    LaunchedEffect(shownChip) {
        if (shownChip != null) {
            selectedChipId = shownChip.chipId
            popupAnchorChip = shownChip
            popupVisible = true
        } else if (popupAnchorChip != null) {
            popupVisible = false
            delay(240)
            popupAnchorChip = null
        }
    }

    LaunchedEffect(chips) {
        onMediaControlPopupVisibilityChanged(
            chips.any { it.chipId == PopupChipId.MediaControl && it.isPopupShown }
        )
    }

    if (selectedChip == null) {
        onIslandBoundsChanged?.invoke(android.graphics.Rect())
        return
    }

    fun selectRelative(direction: Int) {
        if (chips.size <= 1) return
        lastDirection = if (direction >= 0) 1 else -1
        val newIndex = (selectedIndex + direction).mod(chips.size)
        val newChip = chips[newIndex]
        selectedChipId = newChip.chipId
        if (popupVisible) {
            newChip.showPopup()
        }
    }

    val secondaryIndex = if (chips.size >= 2) (selectedIndex + 1).mod(chips.size) else null
    val secondaryChip = secondaryIndex?.let { chips.getOrNull(it) }

    Box(
        modifier =
            modifier
                .padding(horizontal = 8.dp)
                .offset(x = cutoutSpec.horizontalOffset)
                .onGloballyPositioned { coordinates ->
                    val b = coordinates.boundsInWindow()
                    onIslandBoundsChanged?.invoke(
                        android.graphics.Rect(
                            b.left.toInt(),
                            b.top.toInt(),
                            b.right.toInt(),
                            b.bottom.toInt(),
                        )
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Layout(
            content = {
                // Measurable 0: Secondary Companion Bubble (if present)
                if (secondaryChip != null && !popupVisible) {
                    val colors = secondaryChip.colors
                    val bubbleBg = colors.chipBackground(
                        isPopupShown = false,
                        colorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
                    )
                    val bubbleContent = colors.chipContent(
                        isPopupShown = false,
                        colorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(bubbleBg)
                                .clickable {
                                    selectedChipId = secondaryChip.chipId
                                    secondaryChip.showPopup()
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        val secondaryContent = secondaryChip.popupContent
                        when {
                            secondaryContent is PopupContentModel.Bluetooth -> {
                                BluetoothPillIcon(size = 20.dp)
                            }
                            secondaryContent is PopupContentModel.Hotspot -> {
                                HotspotPillIcon(event = secondaryContent.event)
                            }
                            secondaryContent is PopupContentModel.OngoingCall -> {
                                CallPillIcon(
                                    event = secondaryContent.event,
                                    size = 20.dp,
                                    animated =
                                        secondaryContent.event.callStartTimeMs <= 0L ||
                                            secondaryContent.event.callType == "Phone:incoming",
                                )
                            }
                            secondaryChip.icons.isNotEmpty() -> {
                                val chipIcon = secondaryChip.icons.first()
                                val isArtwork =
                                    secondaryContent is PopupContentModel.Media &&
                                        chipIcon.icon is SysUISharedIcon.Loaded
                                if (isArtwork && chipIcon.icon is SysUISharedIcon.Loaded) {
                                    Image(
                                        painter = rememberDrawablePainter(chipIcon.icon.drawable),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    com.android.systemui.common.ui.compose.Icon(
                                        icon = chipIcon.icon,
                                        tint = bubbleContent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Measurable 1 (or 0 if no secondary): Primary Notch Pill
                AnimatedContent(
                    targetState = selectedChip.chipId,
                    transitionSpec = {
                        if (targetState == initialState) {
                            fadeIn(animationSpec = tween(150)) togetherWith
                                fadeOut(animationSpec = tween(150))
                        } else {
                            val slideDirection =
                                if (
                                    chips.indexOfFirst { it.chipId == targetState } >
                                        chips.indexOfFirst { it.chipId == initialState }
                                ) {
                                    1
                                } else {
                                    -1
                                }
                            (slideInHorizontally(
                                animationSpec = tween(220),
                                initialOffsetX = { fullWidth -> slideDirection * fullWidth / 2 },
                            ) + fadeIn(animationSpec = tween(180))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(200),
                                    targetOffsetX = { fullWidth -> -slideDirection * fullWidth / 3 },
                                ) + fadeOut(animationSpec = tween(140)))
                        }
                    },
                    label = "dynamic_island_chip",
                ) { chipId ->
                    val chip = chips.firstOrNull { it.chipId == chipId } ?: return@AnimatedContent
                    var horizontalDragPx by remember(chipId, chips.size) { mutableFloatStateOf(0f) }
                    val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

                    StatusBarDynamicIslandChip(
                        viewModel = chip,
                        pageCount = chips.size,
                        cutoutSpec = cutoutSpec,
                        modifier =
                            Modifier.onGloballyPositioned { coordinates ->
                                    chipBoundsInScreen = coordinates.boundsInScreen(islandView)
                                }
                                .pointerInput(chips.size, chip.chipId) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            when {
                                                horizontalDragPx <= -thresholdPx -> selectRelative(1)
                                                horizontalDragPx >= thresholdPx -> selectRelative(-1)
                                            }
                                            horizontalDragPx = 0f
                                        },
                                        onDragCancel = { horizontalDragPx = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            horizontalDragPx += dragAmount
                                            if (chips.size > 1 && abs(horizontalDragPx) > 8f) {
                                                change.consume()
                                            }
                                        },
                                    )
                                },
                        onTap = {
                            if (chip.isPopupShown) chip.hidePopup() else chip.showPopup()
                        },
                    )
                }
            }
        ) { measurables, constraints ->
            val hasSecondary = secondaryChip != null && !popupVisible && measurables.size == 2
            val secondaryMeasurable = if (hasSecondary) measurables[0] else null
            val primaryMeasurable = if (hasSecondary) measurables[1] else measurables[0]

            val primaryPlaceable = primaryMeasurable.measure(constraints)
            val secondaryPlaceable = secondaryMeasurable?.measure(constraints)

            val spacingPx = 6.dp.roundToPx()
            val totalHeight = maxOf(primaryPlaceable.height, secondaryPlaceable?.height ?: 0)
            val secondaryWidthTotal = if (secondaryPlaceable != null) secondaryPlaceable.width + spacingPx else 0
            val totalWidth = primaryPlaceable.width + (secondaryWidthTotal * 2)

            layout(totalWidth, totalHeight) {
                // Primary is placed at secondaryWidthTotal, making it dead-center of totalWidth
                val primaryX = secondaryWidthTotal
                val primaryY = (totalHeight - primaryPlaceable.height) / 2
                primaryPlaceable.placeRelative(primaryX, primaryY)

                // Secondary companion bubble is placed to the left (x = 0)
                if (secondaryPlaceable != null) {
                    val secondaryX = 0
                    val secondaryY = (totalHeight - secondaryPlaceable.height) / 2
                    secondaryPlaceable.placeRelative(secondaryX, secondaryY)
                }
            }
        }

        popupAnchorChip?.let { anchoredChip ->
            StatusBarPopup(
                viewModel = anchoredChip,
                isVisible = popupVisible,
                islandActions = islandActions,
                chipBoundsInScreen = chipBoundsInScreen,
                onSwipeNext = { selectRelative(1) },
                onSwipePrev = { selectRelative(-1) },
                hasMultipleChips = chips.size > 1,
                pageDirection = lastDirection,
            )
        }
    }
}
