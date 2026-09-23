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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.shade.ui.composable.Shade
import com.android.systemui.shade.ui.composable.ShadeHeader

// durationScale is kept in the signature (unused) so existing call sites don't need to change.
// Spring specs are physics-driven, not duration-driven, so there is no direct use for it here.
fun TransitionBuilder.toSplitShadeTransition(durationScale: Double = 1.0) {
    spec = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        // STIFFNESS_MEDIUM_LOW doesn't exist as a constant; using a custom value between
        // Spring.StiffnessLow (200f) and Spring.StiffnessMedium (1500f) for a balanced
        // springy feel, matching QuickSettingsControllerImpl's expansion spring.
        stiffness = 600f,
    )
    distance = UserActionDistance { fromContent, _, _ ->
        val fromContentSize = checkNotNull(fromContent.targetSize())
        fromContentSize.height.toFloat() * 2 / 3f
    }

    fractionRange(end = .4f) { fade(Shade.Elements.BackgroundScrim) }

    fractionRange(end = .35f) {
        fade(ShadeHeader.Elements.Clock)
        fade(ShadeHeader.Elements.CollapsedContentStart)
        fade(ShadeHeader.Elements.CollapsedContentEnd)
        fade(ShadeHeader.Elements.PrivacyChip)
    }

    fractionRange(end = .5f) { fade(Media.Elements.MediaCarousel) }

    fractionRange(start = .25f) {
        fade(QuickSettings.Elements.SplitShadeQuickSettings)
        fade(QuickSettings.Elements.FooterActions)
        fade(Notifications.Elements.NotificationScrim)
    }

    fractionRange(start = .35f) { fade(Notifications.Elements.StackPlaceholder) }
}
