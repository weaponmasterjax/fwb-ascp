/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.brightness.ui.compose.BrightnessSliderDimensions
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.composable.QuickSettingsScene
import com.android.systemui.shade.ui.composable.Shade
import com.android.systemui.shade.ui.composable.ShadeHeader

// durationScale is kept in the signature (unused) so existing call sites don't need to change.
// Spring specs are physics-driven, not duration-driven, so there is no direct use for it here.
fun TransitionBuilder.toQuickSettingsSceneTransition(durationScale: Double = 1.0) {
    spec = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        // STIFFNESS_MEDIUM_LOW doesn't exist as a constant; using a custom value between
        // Spring.StiffnessLow (200f) and Spring.StiffnessMedium (1500f) for a balanced
        // springy feel, matching QuickSettingsControllerImpl's expansion spring.
        stiffness = 600f,
    )
    val translationY = ShadeHeader.Dimensions.CollapsedHeightForTransitions

    fractionRange(end = 0.3f) {
        translate(ShadeHeader.Elements.ExpandedContent,
            y = -(ShadeHeader.Dimensions.ExpandedHeight - translationY))
        translate(ShadeHeader.Elements.Clock, y = -translationY)
        translate(ShadeHeader.Elements.ShadeCarrierGroup, y = -translationY)
    }

    fractionRange(end = 0.4f) {
        fade(ShadeHeader.Elements.ExpandedContent)
        fade(ShadeHeader.Elements.CollapsedContentStart)
        fade(ShadeHeader.Elements.CollapsedContentEnd)
        fade(ShadeHeader.Elements.PrivacyChip)
    }

    fractionRange(end = 0.5f) {
        fade(ShadeHeader.Elements.Clock)
        fade(ShadeHeader.Elements.ShadeCarrierGroup)
        fade(Elements.BrightnessSlider)
    }

    fractionRange(end = 0.6f) { fade(Shade.Elements.BackgroundScrim) }

    // New all compose element
    // Ideally, we would want to have the BrightnessSlider start right off screen and translate
    // down,
    // And have the other elements of the content slide anchored to that. But anchoredTranslate does
    // not support that yet.
    translate(
        Elements.QuickSettingsContent,
        y = -ShadeHeader.Dimensions.ExpandedHeight - BrightnessSliderDimensions.Default.thumbHeight,
    )

    fractionRange(start = 0.45f) { fade(Elements.FooterActions) }

    fractionRange(end = 0.3f) {
        fade(Notifications.Elements.HeadsUpNotificationPlaceholder)
    }

    translate(Notifications.Elements.NotificationScrim, Edge.Top, false)
    translate(QuickSettingsScene.Companion.InternalScenes.Edit.rootElementKey, Edge.Top, true)
}
