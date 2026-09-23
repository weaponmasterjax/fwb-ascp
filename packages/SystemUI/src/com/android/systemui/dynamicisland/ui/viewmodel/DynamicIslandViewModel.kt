/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.dynamicisland.alarm.ui.viewmodel.AlarmPopupChipViewModel
import com.android.systemui.dynamicisland.bluetooth.ui.viewmodel.BluetoothPopupChipViewModel
import com.android.systemui.dynamicisland.flashlight.ui.viewmodel.FlashlightPopupChipViewModel
import com.android.systemui.dynamicisland.hotspot.ui.viewmodel.HotspotPopupChipViewModel
import com.android.systemui.dynamicisland.livescore.ui.viewmodel.LiveScorePopupChipViewModel
import com.android.systemui.dynamicisland.media.ui.viewmodel.MediaControlChipViewModel
import com.android.systemui.dynamicisland.ongoingcall.ui.viewmodel.OngoingCallsPopupChipViewModel
import com.android.systemui.dynamicisland.promotedongoing.ui.viewmodel.PromotedOngoingPopupChipViewModel
import com.android.systemui.dynamicisland.screenrecord.ui.viewmodel.ScreenRecordPopupChipViewModel
import com.android.systemui.dynamicisland.stopwatch.ui.viewmodel.StopwatchPopupChipViewModel
import com.android.systemui.dynamicisland.ui.model.PopupChipId
import com.android.systemui.dynamicisland.ui.model.PopupChipModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Collects phone dynamic-island pages and exposes the currently visible [PopupChipModel.Shown]
 * chips. Scene container hosts this as a centered overlay on the home status bar.
 */
class DynamicIslandViewModel
internal constructor(
    private val context: Context,
    private val mediaControlChip: IslandChipViewModel,
    private val hotspotChip: IslandChipViewModel,
    private val bluetoothChip: IslandChipViewModel,
    private val screenRecordChip: IslandChipViewModel,
    private val liveScoreChip: IslandChipViewModel,
    private val flashlightChip: IslandChipViewModel,
    private val stopwatchChip: IslandChipViewModel,
    private val alarmChip: IslandChipViewModel,
    private val ongoingCallChip: IslandChipViewModel,
    private val promotedOngoingChip: IslandChipViewModel,
) : ExclusiveActivatable() {

    @AssistedInject
    constructor(
        @Application context: Context,
        mediaControlChipFactory: MediaControlChipViewModel.Factory,
        hotspotChipFactory: HotspotPopupChipViewModel.Factory,
        bluetoothChipFactory: BluetoothPopupChipViewModel.Factory,
        screenRecordChipFactory: ScreenRecordPopupChipViewModel.Factory,
        liveScoreChipFactory: LiveScorePopupChipViewModel.Factory,
        flashlightChipFactory: FlashlightPopupChipViewModel.Factory,
        stopwatchChipFactory: StopwatchPopupChipViewModel.Factory,
        alarmChipFactory: AlarmPopupChipViewModel.Factory,
        ongoingCallChipFactory: OngoingCallsPopupChipViewModel.Factory,
        promotedOngoingChipFactory: PromotedOngoingPopupChipViewModel.Factory,
    ) : this(
        context = context,
        mediaControlChip = mediaControlChipFactory.create(),
        hotspotChip = hotspotChipFactory.create(),
        bluetoothChip = bluetoothChipFactory.create(),
        screenRecordChip = screenRecordChipFactory.create(),
        liveScoreChip = liveScoreChipFactory.create(),
        flashlightChip = flashlightChipFactory.create(),
        stopwatchChip = stopwatchChipFactory.create(),
        alarmChip = alarmChipFactory.create(),
        ongoingCallChip = ongoingCallChipFactory.create(),
        promotedOngoingChip = promotedOngoingChipFactory.create(),
    )

    private var isDynamicIslandEnabled by mutableStateOf(readDynamicIslandEnabled())
    private val dynamicIslandObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isDynamicIslandEnabled = readDynamicIslandEnabled()
                if (!isDynamicIslandEnabled) {
                    currentShownPopupChipId = null
                }
            }
        }

    /** The ID of the current chip that is showing its popup, or `null` if no chip is shown. */
    private var currentShownPopupChipId by mutableStateOf<PopupChipId?>(null)

    private val incomingPopupChipBundle: PopupChipBundle by derivedStateOf {
        PopupChipBundle(
            media = mediaControlChip.chip,
            hotspot = hotspotChip.chip,
            bluetooth = bluetoothChip.chip,
            screenRecord = screenRecordChip.chip,
            liveScore = liveScoreChip.chip,
            flashlight = flashlightChip.chip,
            stopwatch = stopwatchChip.chip,
            alarm = alarmChip.chip,
            ongoingCall = ongoingCallChip.chip,
            promotedOngoing = promotedOngoingChip.chip,
        )
    }

    val shownPopupChips: List<PopupChipModel.Shown> by derivedStateOf {
        if (!isDynamicIslandEnabled) {
            return@derivedStateOf emptyList()
        }

        val bundle = incomingPopupChipBundle
        listOfNotNull(
                bundle.media,
                bundle.hotspot,
                bundle.bluetooth,
                bundle.screenRecord,
                bundle.liveScore,
                bundle.stopwatch,
                bundle.alarm,
                bundle.flashlight,
                bundle.ongoingCall,
                bundle.promotedOngoing,
            )
            .filterIsInstance<PopupChipModel.Shown>()
            .map { chip ->
                chip.copy(
                    isPopupShown = chip.chipId == currentShownPopupChipId,
                    showPopup = { currentShownPopupChipId = chip.chipId },
                    hidePopup = {
                        if (currentShownPopupChipId == chip.chipId) {
                            currentShownPopupChipId = null
                        }
                    },
                )
            }
    }

    override suspend fun onActivated() {
        coroutineScope {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND),
                false,
                dynamicIslandObserver,
                UserHandle.USER_ALL,
            )
            dynamicIslandObserver.onChange(false)
            launch { mediaControlChip.activate() }
            launch { hotspotChip.activate() }
            launch { bluetoothChip.activate() }
            launch { screenRecordChip.activate() }
            launch { liveScoreChip.activate() }
            launch { flashlightChip.activate() }
            launch { stopwatchChip.activate() }
            launch { alarmChip.activate() }
            launch { ongoingCallChip.activate() }
            launch { promotedOngoingChip.activate() }
            try {
                awaitCancellation()
            } finally {
                context.contentResolver.unregisterContentObserver(dynamicIslandObserver)
            }
        }
    }

    private data class PopupChipBundle(
        val media: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.MediaControl),
        val hotspot: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Hotspot),
        val bluetooth: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Bluetooth),
        val screenRecord: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.ScreenRecord),
        val liveScore: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.LiveScore),
        val flashlight: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Flashlight),
        val stopwatch: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Stopwatch),
        val alarm: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Alarm),
        val ongoingCall: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.OngoingCall),
        val promotedOngoing: PopupChipModel =
            PopupChipModel.Hidden(chipId = PopupChipId.PromotedOngoing),
    )

    private fun readDynamicIslandEnabled(): Boolean {
        return Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND,
            0,
            UserHandle.USER_CURRENT,
        ) != 0
    }

    @AssistedFactory
    interface Factory {
        fun create(): DynamicIslandViewModel
    }
}
