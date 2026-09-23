/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ongoingcall.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.dynamicisland.domain.DynamicIslandInteractor
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.dynamicisland.ui.model.ChipIcon
import com.android.systemui.dynamicisland.ui.model.ColorsModel
import com.android.systemui.dynamicisland.ui.model.PopupChipId
import com.android.systemui.dynamicisland.ui.model.PopupChipModel
import com.android.systemui.dynamicisland.ui.model.PopupContentModel
import com.android.systemui.dynamicisland.ui.viewmodel.IslandChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class OngoingCallsPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val dynamicIslandInteractor: DynamicIslandInteractor,
) : IslandChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("OngoingCallsPopupChipViewModel.hydrator")

    private val timerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.OngoingCall),
            source =
                dynamicIslandInteractor.settings.isDynamicIslandCallsActive.flatMapLatest { enabled ->
                    if (!enabled) {
                        flowOf(PopupChipModel.Hidden(PopupChipId.OngoingCall))
                    } else {
                        dynamicIslandInteractor.uiState
                            .map { uiState ->
                                uiState.events.firstOrNull { it is IslandEvent.Call }
                                    as? IslandEvent.Call
                            }
                            .distinctUntilChanged()
                            .flatMapLatest { event ->
                                if (event == null) {
                                    flowOf(PopupChipModel.Hidden(PopupChipId.OngoingCall))
                                } else {
                                    timerFlow.map { currentTime ->
                                        toPopupChipModel(event, currentTime)
                                    }
                                }
                            }
                    }
                },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(event: IslandEvent.Call, currentTime: Long): PopupChipModel {
        val iconModel =
            Icon.Resource(
                resId = com.android.systemui.res.R.drawable.ic_call,
                contentDescription = ContentDescription.Loaded(event.callerName ?: "Call"),
            )
        val isConnected = event.callStartTimeMs > 0L && event.callType == "Phone:active"
        val chipText =
            if (isConnected) {
                val elapsedSecs = ((currentTime - event.callStartTimeMs) / 1000).coerceAtLeast(0)
                String.format("%02d:%02d", elapsedSecs / 60, elapsedSecs % 60)
            } else if (event.callType == "Phone:outgoing") {
                "Outgoing"
            } else {
                "Incoming"
            }
        return PopupChipModel.Shown(
            chipId = PopupChipId.OngoingCall,
            icons = listOf(ChipIcon(icon = iconModel)),
            chipText = chipText,
            colors = ColorsModel.DynamicIslandCall,
            contentDescription = event.callerName,
            popupContent = PopupContentModel.OngoingCall(event),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): OngoingCallsPopupChipViewModel
    }
}
