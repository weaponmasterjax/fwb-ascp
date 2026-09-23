/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.hotspot.ui.viewmodel

import android.content.Context
import android.net.TrafficStats
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.HOTSPOT
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.dynamicisland.ui.model.ChipIcon
import com.android.systemui.dynamicisland.ui.model.ColorsModel
import com.android.systemui.dynamicisland.ui.model.PopupChipId
import com.android.systemui.dynamicisland.ui.model.PopupChipModel
import com.android.systemui.dynamicisland.ui.model.PopupContentModel
import com.android.systemui.dynamicisland.ui.viewmodel.IslandChipViewModel
import com.android.systemui.statusbar.policy.HotspotController
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** ViewModel backing the Hotspot page inside the dynamic island. */
class HotspotPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val hotspotController: HotspotController,
) : IslandChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("HotspotPopupChipViewModel.hydrator")

    private var hotspotStartTotalBytes: Long = 0L

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.Hotspot),
            source =
                callbackFlow {
                        val callback =
                            object : HotspotController.Callback {
                                override fun onHotspotChanged(enabled: Boolean, numDevices: Int) {
                                    trySend(readHotspotState(enabled, numDevices))
                                }
                            }

                        hotspotController.addCallback(callback)
                        trySend(
                            readHotspotState(
                                hotspotController.isHotspotEnabled,
                                hotspotController.getNumConnectedDevices(),
                            )
                        )
                        awaitClose { hotspotController.removeCallback(callback) }
                    }
                    .map(::toPopupChipModel)
                    .combine(
                        observeDynamicIslandFeatureEnabled(context, HOTSPOT)
                    ) { model, enabled ->
                        if (enabled) model else PopupChipModel.Hidden(PopupChipId.Hotspot)
                    },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(state: HotspotState): PopupChipModel {
        if (!state.isEnabled) {
            return PopupChipModel.Hidden(PopupChipId.Hotspot)
        }

        val event =
            IslandEvent.Hotspot(
                numDevices = state.numDevices,
                sharedBytes = state.sharedBytes,
            )

        val contentDescription =
            ContentDescription.Resource(R.string.dynamic_island_hotspot)

        val chipText = "${state.numDevices}"

        return PopupChipModel.Shown(
            chipId = PopupChipId.Hotspot,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_hotspot,
                                contentDescription = contentDescription,
                            ),
                    )
                ),
            chipText = chipText,
            colors = ColorsModel.DynamicIslandHotspot,
            contentDescription = contentDescription.loadContentDescription(context),
            popupContent = PopupContentModel.Hotspot(event),
        )
    }

    private fun readHotspotState(enabled: Boolean, numDevices: Int): HotspotState {
        if (!enabled) {
            hotspotStartTotalBytes = 0L
            return HotspotState(isEnabled = false, numDevices = 0, sharedBytes = 0L)
        }
        val currentTotal = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        if (hotspotStartTotalBytes == 0L && currentTotal > 0L) {
            hotspotStartTotalBytes = currentTotal
        }
        val delta = if (hotspotStartTotalBytes > 0L) maxOf(0L, currentTotal - hotspotStartTotalBytes) else 0L
        return HotspotState(isEnabled = true, numDevices = numDevices, sharedBytes = delta)
    }

    @AssistedFactory
    interface Factory {
        fun create(): HotspotPopupChipViewModel
    }
}

private data class HotspotState(
    val isEnabled: Boolean,
    val numDevices: Int,
    val sharedBytes: Long,
)
