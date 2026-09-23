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

import android.view.DisplayCutout
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon as SysUISharedIcon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.observeDynamicIslandScale
import com.android.systemui.dynamicisland.ui.model.PopupChipModel
import com.android.systemui.dynamicisland.ui.model.PopupContentModel
import com.android.systemui.dynamicisland.screenrecord.shared.model.ScreenRecordPopupModel
import com.android.systemui.dynamicisland.shared.PillMono
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Single centered status bar capsule styled like a compact dynamic island. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusBarDynamicIslandChip(
    viewModel: PopupChipModel.Shown,
    pageCount: Int,
    cutoutSpec: DynamicIslandCutoutSpec,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val hapticOnTap: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onTap()
    }
    val mediaOpenApp: (() -> Unit)? =
        (viewModel.popupContent as? PopupContentModel.Media)
            ?.takeIf { it.model.isPlaying }
            ?.model
            ?.openApp
    val hapticOnLongPress: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        mediaOpenApp?.invoke()
    }

    val isMediaChip = viewModel.popupContent is PopupContentModel.Media
    val chipShape = RoundedCornerShape(50)
    val colors = viewModel.colors
    val (widthScale, heightScale) = rememberDynamicIslandSizeScale()
    val chipBackgroundColor =
        colors.chipBackground(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val chipContentColor =
        colors.chipContent(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val chipOutline =
        colors.chipOutline(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    if (viewModel.popupContent.isUtilityStatusContent() && viewModel.icons.isNotEmpty()) {
        UtilityStatusIslandChip(
            viewModel = viewModel,
            onTap = hapticOnTap,
            cutoutSpec = cutoutSpec,
            widthScale = widthScale,
            heightScale = heightScale,
            chipBackgroundColor = chipBackgroundColor,
            chipContentColor = chipContentColor,
            chipOutline = chipOutline,
            modifier = modifier,
        )
        return
    }

    val compactWidth = compactIslandWidthFor(viewModel.popupContent)?.times(widthScale)
    val hasInlineTimer = viewModel.popupContent is PopupContentModel.Stopwatch
    val trailingDecorationWidth =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.Media ->
                if (popupContent.model.isPlaying) {
                    14.dp
                } else if (pageCount > 1) {
                    11.dp
                } else {
                    0.dp
                }
            is PopupContentModel.ScreenRecord -> 11.dp
            else -> {
                if (pageCount > 1) 11.dp else 0.dp
            }
        }
    val leadingDecorationWidth =
        when {
            viewModel.icons.isEmpty() -> 0.dp
            else -> 18.dp + (8.dp * (viewModel.icons.size - 1))
        }
    val maxTextWidth =
        ((CompactIslandMaxWidth * widthScale) - 24.dp - leadingDecorationWidth - trailingDecorationWidth)
            .coerceAtLeast(56.dp)

    val collapseState = rememberDynamicIslandCollapseState(viewModel.isPopupShown)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
            label = "chipPressScale",
        )

    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = 32.dp * heightScale)
                .widthIn(
                    min = compactWidth ?: 0.dp,
                    max = compactWidth ?: (CompactIslandMaxWidth * widthScale),
                )
                .graphicsLayer { scaleX = collapseState.scale * pressScale }
                .clip(chipShape)
                .background(chipBackgroundColor)
                .border(width = 1.dp, color = chipOutline, shape = chipShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = hapticOnTap,
                    onLongClick = mediaOpenApp?.let { { hapticOnLongPress() } },
                )
                .padding(horizontal = 12.dp * widthScale, vertical = 7.dp * heightScale)
                .graphicsLayer { alpha = collapseState.contentAlpha },
        horizontalArrangement =
            if (isMediaChip) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        viewModel.icons.forEachIndexed { index, chipIcon ->
            val isArtworkLike =
                index == 0 &&
                    (viewModel.popupContent is PopupContentModel.Media ||
                        viewModel.popupContent is PopupContentModel.LiveScore)
            if (isArtworkLike && chipIcon.icon is SysUISharedIcon.Loaded) {
                Image(
                    painter = rememberDrawablePainter(chipIcon.icon.drawable),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val accent = if (index == 0) islandChipAccentFor(viewModel.popupContent) else null
                if (accent != null) {
                    CollapsedGlyphBadge(
                        icon = chipIcon.icon,
                        accent = accent,
                        content = viewModel.popupContent,
                        badgeSize = 20.dp * widthScale,
                        iconSize = 13.dp * widthScale,
                    )
                } else {
                    Icon(
                        icon = chipIcon.icon,
                        modifier = Modifier.size(16.dp).clip(CircleShape),
                        tint = if (isArtworkLike) Color.Unspecified else chipContentColor,
                    )
                }
            }
        }

        viewModel.chipText
            ?.takeIf {
                !isMediaChip &&
                    viewModel.popupContent !is PopupContentModel.ScreenRecord &&
                    !hasInlineTimer &&
                    it.isNotBlank()
            }
            ?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = maxTextWidth),
                )
            }

        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.Media ->
                if (popupContent.model.isPlaying) {
                    AudioReactiveBars(
                        isPlaying = true,
                        color = IslandAccents.Music,
                    )
                } else if (pageCount > 1) {
                    SwipeHint(color = chipContentColor.copy(alpha = 0.72f))
                }
            is PopupContentModel.ScreenRecord ->
                when (val model = popupContent.model) {
                    is ScreenRecordPopupModel.Starting ->
                        StatusContent(
                            text = viewModel.chipText.orEmpty(),
                            color = chipContentColor,
                            showSwipeHint = pageCount > 1,
                        )
                    is ScreenRecordPopupModel.Recording ->
                        StatusContent(
                            text = viewModel.chipText.orEmpty(),
                            color = chipContentColor,
                            showSwipeHint = pageCount > 1,
                        )
                }
            is PopupContentModel.Stopwatch ->
                StatusContent(
                    text =
                        if (popupContent.model.isRunning) {
                            rememberElapsedDurationText(
                                popupContent.model.baseElapsedRealtimeMs,
                                isRunning = true,
                            )
                        } else {
                            popupContent.model.elapsedTimeText
                                ?: rememberElapsedDurationText(
                                    popupContent.model.baseElapsedRealtimeMs,
                                    isRunning = false,
                                )
                        },
                    color = chipContentColor,
                    showSwipeHint = pageCount > 1,
                )
            else -> {
                if (pageCount > 1) {
                    SwipeHint(color = chipContentColor.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun UtilityStatusIslandChip(
    viewModel: PopupChipModel.Shown,
    onTap: () -> Unit,
    cutoutSpec: DynamicIslandCutoutSpec,
    widthScale: Float = 1f,
    heightScale: Float = 1f,
    chipBackgroundColor: Color,
    chipContentColor: Color,
    chipOutline: Color,
    modifier: Modifier = Modifier,
) {
    val isHotspot = viewModel.popupContent is PopupContentModel.Hotspot
    val isBluetooth = viewModel.popupContent is PopupContentModel.Bluetooth
    val isCall = viewModel.popupContent is PopupContentModel.OngoingCall
    val isPromotedOngoing = viewModel.popupContent is PopupContentModel.PromotedOngoing
    val isConnectedCall =
        isCall &&
            (viewModel.popupContent as PopupContentModel.OngoingCall).event.callStartTimeMs > 0L &&
            (viewModel.popupContent as PopupContentModel.OngoingCall).event.callType == "Phone:active"

    val wingWidth =
        (when (viewModel.popupContent) {
            is PopupContentModel.Hotspot -> 32.dp
            is PopupContentModel.Bluetooth -> 32.dp
            is PopupContentModel.PromotedOngoing -> 48.dp
            is PopupContentModel.OngoingCall -> if (isConnectedCall) 56.dp else 68.dp
            is PopupContentModel.Flashlight -> 48.dp
            is PopupContentModel.Alarm -> 58.dp
            is PopupContentModel.Stopwatch -> 58.dp
            is PopupContentModel.ScreenRecord -> 54.dp
            else -> 56.dp
        }) * widthScale

    val connectedIslandWidth = (wingWidth * 2) + cutoutSpec.embeddedGapWidth

    val utilityText =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.ScreenRecord ->
                when (val model = popupContent.model) {
                    is ScreenRecordPopupModel.Starting -> "${model.secondsUntilStarted}s"
                    is ScreenRecordPopupModel.Recording ->
                        rememberElapsedDurationText(model.startElapsedRealtimeMs)
                }
            is PopupContentModel.Stopwatch ->
                if (popupContent.model.isRunning) {
                    rememberElapsedDurationText(
                        popupContent.model.baseElapsedRealtimeMs,
                        isRunning = true,
                    )
                } else {
                    popupContent.model.elapsedTimeText
                        ?: rememberElapsedDurationText(
                            popupContent.model.baseElapsedRealtimeMs,
                            isRunning = false,
                        )
                }
            is PopupContentModel.Alarm -> viewModel.chipText.orEmpty()
            is PopupContentModel.Flashlight -> viewModel.chipText.orEmpty()
            is PopupContentModel.Hotspot -> viewModel.chipText.orEmpty()
            is PopupContentModel.Bluetooth -> ""
            is PopupContentModel.PromotedOngoing -> viewModel.chipText.orEmpty()
            is PopupContentModel.OngoingCall -> {
                val isConnected =
                    popupContent.event.callStartTimeMs > 0L &&
                        popupContent.event.callType == "Phone:active"
                if (isConnected) {
                    viewModel.chipText.orEmpty()
                } else if (popupContent.event.callType == "Phone:outgoing") {
                    "Outgoing"
                } else {
                    "Incoming"
                }
            }
            else -> ""
        }

    val collapseState = rememberDynamicIslandCollapseState(viewModel.isPopupShown)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
            label = "utilityChipPressScale",
        )

    Row(
        modifier =
            modifier
                .graphicsLayer { scaleX = collapseState.scale * pressScale }
                .defaultMinSize(minHeight = 32.dp * heightScale)
                .width(connectedIslandWidth)
                .clip(RoundedCornerShape(50))
                .background(chipBackgroundColor)
                .border(width = 1.dp, color = chipOutline, shape = RoundedCornerShape(50))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap,
                )
                .graphicsLayer { alpha = collapseState.contentAlpha },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Wing: Contains icon(s)
        Box(
            modifier = Modifier.width(wingWidth),
            contentAlignment = if (isHotspot || isBluetooth) Alignment.Center else Alignment.CenterStart,
        ) {
            val popupContent = viewModel.popupContent
            when (popupContent) {
                is PopupContentModel.Hotspot -> HotspotPillIcon(event = popupContent.event)
                is PopupContentModel.Bluetooth -> {
                    if (viewModel.icons.isNotEmpty()) {
                        Icon(
                            icon = viewModel.icons.first().icon,
                            modifier = Modifier.size(16.dp * widthScale),
                            tint = Color.White,
                        )
                    }
                }
                is PopupContentModel.OngoingCall ->
                    Box(modifier = Modifier.padding(start = 8.dp * widthScale)) {
                        CallPillIcon(
                            event = popupContent.event,
                            size = 22.dp,
                            animated =
                                popupContent.event.callStartTimeMs <= 0L ||
                                    popupContent.event.callType == "Phone:incoming",
                        )
                    }
                is PopupContentModel.PromotedOngoing ->
                    Box(modifier = Modifier.padding(start = 8.dp * widthScale)) {
                        PromotedOngoingPillIcon(event = popupContent.event, tint = Color(0xFF4DA6FF))
                    }
                else -> {
                    if (viewModel.icons.isNotEmpty()) {
                        val accent = islandChipAccentFor(popupContent)
                        Box(modifier = Modifier.padding(start = 10.dp * widthScale)) {
                            if (accent != null) {
                                CollapsedGlyphBadge(
                                    icon = viewModel.icons.first().icon,
                                    accent = accent,
                                    content = popupContent,
                                    badgeSize = 20.dp * widthScale,
                                    iconSize = 13.dp * widthScale,
                                )
                            } else {
                                Icon(
                                    icon = viewModel.icons.first().icon,
                                    modifier = Modifier.size(16.dp),
                                    tint = chipContentColor,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Camera Cutout Gap
        Spacer(modifier = Modifier.width(cutoutSpec.embeddedGapWidth))

        // Right Wing: Contains text / status / device icon
        Box(
            modifier = Modifier.width(wingWidth),
            contentAlignment = if (isHotspot || isBluetooth) Alignment.Center else Alignment.CenterEnd,
        ) {
            val popupContent = viewModel.popupContent
            if (isHotspot || isBluetooth) {
                if (popupContent is PopupContentModel.Bluetooth) {
                    BluetoothDeviceRightIcon(event = popupContent.event, size = 18.dp)
                } else if (popupContent is PopupContentModel.Hotspot) {
                    Text(
                        text = utilityText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4DA6FF),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (isCall) {
                Text(
                    text = utilityText,
                    style =
                        if (isConnectedCall) PillMono
                        else MaterialTheme.typography.labelSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                    color = Color(0xFF10B981),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 10.dp * widthScale),
                )
            } else if (isPromotedOngoing) {
                Text(
                    text = utilityText,
                    style = PillMono,
                    color = Color(0xFF4DA6FF),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 10.dp * widthScale),
                )
            } else if (popupContent is PopupContentModel.Stopwatch) {
                Text(
                    text = utilityText,
                    style = PillMono,
                    color = chipContentColor,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 10.dp * widthScale),
                )
            } else {
                Text(
                    text = utilityText,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 10.dp * widthScale),
                )
            }
        }
    }
}

@Composable
private fun StatusContent(
    text: String,
    color: Color,
    showSwipeHint: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
        if (showSwipeHint) {
            SwipeHint(color = color.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun SwipeHint(color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) {
            Box(
                modifier = Modifier.size(width = 3.dp, height = 3.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

private val CompactIslandMaxWidth = 192.dp
private val CompactMediaIslandWidth = 108.dp
private val CompactTimerIslandWidth = 116.dp
private val CompactRecordingIslandWidth = 88.dp
private val CompactAlarmIslandWidth = 92.dp
private val CompactUtilityIslandWidth = 74.dp
private val CompactUtilityConnectedIslandChromeWidth = 42.dp
private val CompactUtilityConnectedIslandMinWidth = 96.dp
private val CompactUtilityConnectedIslandMaxWidth = 188.dp
private val DynamicIslandEmbeddedGapFallbackWidth = 38.dp
private val DynamicIslandEmbeddedGapMinWidth = 34.dp
private val DynamicIslandEmbeddedGapMaxWidth = 88.dp
private val DynamicIslandEmbeddedGapSidePadding = 10.dp

data class DynamicIslandCutoutSpec(
    val embeddedGapWidth: Dp,
    val horizontalOffset: Dp,
)

@Composable
fun rememberDynamicIslandCutoutSpec(): DynamicIslandCutoutSpec {
    val density = LocalDensity.current
    val view = LocalView.current
    val displayCutout = view.rootWindowInsets?.displayCutout ?: view.display?.cutout
    val topCutout = displayCutout?.topBoundingRectOrNull()
    val rootWidthPx =
        when {
            view.rootView.width > 0 -> view.rootView.width
            view.width > 0 -> view.width
            else -> view.resources.configuration.windowConfiguration.maxBounds.width()
        }

    return with(density) {
        if (topCutout == null || rootWidthPx <= 0) {
            DynamicIslandCutoutSpec(
                embeddedGapWidth = DynamicIslandEmbeddedGapFallbackWidth,
                horizontalOffset = 0.dp,
            )
        } else {
            val embeddedGapWidthDp =
                (topCutout.width().toDp() + (DynamicIslandEmbeddedGapSidePadding * 2))
                    .coerceIn(
                        DynamicIslandEmbeddedGapMinWidth,
                        DynamicIslandEmbeddedGapMaxWidth,
                    )
            val horizontalOffsetDp = (topCutout.exactCenterX() - (rootWidthPx / 2f)).toDp()
            DynamicIslandCutoutSpec(
                embeddedGapWidth = embeddedGapWidthDp,
                horizontalOffset = horizontalOffsetDp,
            )
        }
    }
}

private fun DisplayCutout.topBoundingRectOrNull() =
    getBoundingRectTop().takeUnless { it.isEmpty }

private fun PopupContentModel.isUtilityStatusContent(): Boolean {
    return this is PopupContentModel.ScreenRecord ||
        this is PopupContentModel.Stopwatch ||
        this is PopupContentModel.Alarm ||
        this is PopupContentModel.Flashlight ||
        this is PopupContentModel.Hotspot ||
        this is PopupContentModel.Bluetooth ||
        this is PopupContentModel.OngoingCall ||
        this is PopupContentModel.PromotedOngoing
}

private fun islandChipAccentFor(content: PopupContentModel): Color? =
    when (content) {
        is PopupContentModel.Flashlight -> IslandAccents.Flashlight
        is PopupContentModel.Alarm -> IslandAccents.Alarm
        is PopupContentModel.Stopwatch -> IslandAccents.Stopwatch
        is PopupContentModel.ScreenRecord -> IslandAccents.Recording
        else -> null
    }

@Composable
private fun CollapsedGlyphBadge(
    icon: SysUISharedIcon,
    accent: Color,
    content: PopupContentModel,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 20.dp,
    iconSize: Dp = 13.dp,
) {
    val transition = rememberInfiniteTransition(label = "collapsed_glyph")
    val breathe by
        transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        )
    val recScale by
        transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.12f,
            animationSpec =
                infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rec_scale",
        )
    val recAlpha by
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rec_alpha",
        )
    val spin by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )

    Box(
        modifier =
            modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(IslandAccents.badgeFill(accent)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = icon,
            modifier =
                Modifier.size(iconSize).graphicsLayer {
                    when (content) {
                        is PopupContentModel.ScreenRecord -> {
                            scaleX = recScale
                            scaleY = recScale
                            alpha = recAlpha
                        }
                        is PopupContentModel.Stopwatch -> rotationZ = spin
                        is PopupContentModel.Alarm -> {
                            scaleX = breathe
                            scaleY = breathe
                        }
                        else -> Unit
                    }
                },
            tint = accent,
        )
    }
}

private fun compactIslandWidthFor(content: PopupContentModel): Dp? {
    return when (content) {
        is PopupContentModel.Media -> CompactMediaIslandWidth
        is PopupContentModel.ScreenRecord ->
            when (content.model) {
                is ScreenRecordPopupModel.Starting -> CompactTimerIslandWidth
                is ScreenRecordPopupModel.Recording -> CompactRecordingIslandWidth
            }
        is PopupContentModel.Stopwatch -> CompactTimerIslandWidth
        is PopupContentModel.Alarm -> CompactAlarmIslandWidth
        is PopupContentModel.Flashlight -> CompactUtilityIslandWidth
        is PopupContentModel.OngoingCall -> CompactTimerIslandWidth
        is PopupContentModel.PromotedOngoing -> CompactTimerIslandWidth
        else -> null
    }
}

@Composable
private fun rememberDynamicIslandSizeScale(): Pair<Float, Float> {
    val context = LocalContext.current
    val widthScale by
        remember { observeDynamicIslandScale(context, DynamicIslandFeatureSettings.WIDTH_SCALE) }
            .collectAsState(initial = 1f)
    val heightScale by
        remember { observeDynamicIslandScale(context, DynamicIslandFeatureSettings.HEIGHT_SCALE) }
            .collectAsState(initial = 1f)
    return widthScale to heightScale
}

private data class DynamicIslandCollapseState(val scale: Float, val contentAlpha: Float)

@Composable
private fun rememberDynamicIslandCollapseState(isOpen: Boolean): DynamicIslandCollapseState {
    val scaleX = remember { Animatable(1f, visibilityThreshold = 0.0005f) }
    val currentIsOpen by rememberUpdatedState(isOpen)

    LaunchedEffect(Unit) {
        snapshotFlow { currentIsOpen }
            .drop(1)
            .collectLatest { open ->
                if (open) {
                    scaleX.animateTo(
                        targetValue = 0f,
                        animationSpec =
                            spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } else {
                    scaleX.animateTo(
                        targetValue = 1f,
                        animationSpec =
                            keyframes {
                                durationMillis = 380
                                0f at 0
                                1.08f at 240 using FastOutSlowInEasing
                                0.96f at 320
                                1f at 380
                            },
                    )
                }
            }
    }
    val fadeThreshold = 0.7f
    val alpha = (scaleX.value / fadeThreshold).coerceIn(0f, 1f)
    return DynamicIslandCollapseState(scale = scaleX.value, contentAlpha = alpha)
}
