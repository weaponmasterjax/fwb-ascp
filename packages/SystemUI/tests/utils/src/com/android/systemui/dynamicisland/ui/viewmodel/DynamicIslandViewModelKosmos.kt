/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.viewmodel

import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.dynamicisland.ui.model.PopupChipId
import com.android.systemui.dynamicisland.ui.model.PopupChipModel

private class HiddenIslandChip(chipId: PopupChipId) : IslandChipViewModel, ExclusiveActivatable() {
    override val chip: PopupChipModel = PopupChipModel.Hidden(chipId)
}

val Kosmos.dynamicIslandViewModelFactory: DynamicIslandViewModel.Factory by
    Kosmos.Fixture {
        object : DynamicIslandViewModel.Factory {
            override fun create(): DynamicIslandViewModel {
                return DynamicIslandViewModel(
                    context = applicationContext,
                    mediaControlChip = HiddenIslandChip(PopupChipId.MediaControl),
                    hotspotChip = HiddenIslandChip(PopupChipId.Hotspot),
                    bluetoothChip = HiddenIslandChip(PopupChipId.Bluetooth),
                    screenRecordChip = HiddenIslandChip(PopupChipId.ScreenRecord),
                    liveScoreChip = HiddenIslandChip(PopupChipId.LiveScore),
                    flashlightChip = HiddenIslandChip(PopupChipId.Flashlight),
                    stopwatchChip = HiddenIslandChip(PopupChipId.Stopwatch),
                    alarmChip = HiddenIslandChip(PopupChipId.Alarm),
                    ongoingCallChip = HiddenIslandChip(PopupChipId.OngoingCall),
                    promotedOngoingChip = HiddenIslandChip(PopupChipId.PromotedOngoing),
                )
            }
        }
    }
