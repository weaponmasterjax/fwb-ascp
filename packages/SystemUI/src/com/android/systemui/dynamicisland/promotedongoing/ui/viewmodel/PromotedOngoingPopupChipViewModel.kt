/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.promotedongoing.ui.viewmodel

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class PromotedOngoingPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val dynamicIslandInteractor: DynamicIslandInteractor,
) : IslandChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("PromotedOngoingPopupChipViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.PromotedOngoing),
            source =
                dynamicIslandInteractor.settings.isDynamicIslandOngoingActive.flatMapLatest { enabled
                    ->
                    if (!enabled) {
                        flowOf(PopupChipModel.Hidden(PopupChipId.PromotedOngoing))
                    } else {
                        dynamicIslandInteractor.uiState.map { uiState ->
                            val event =
                                uiState.events.firstOrNull { it is IslandEvent.PromotedOngoing }
                                    as? IslandEvent.PromotedOngoing
                            if (event == null) {
                                PopupChipModel.Hidden(PopupChipId.PromotedOngoing)
                            } else {
                                toPopupChipModel(event)
                            }
                        }
                    }
                },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(event: IslandEvent.PromotedOngoing): PopupChipModel {
        val iconModel =
            event.appIcon?.let { drawable ->
                Icon.Loaded(drawable, ContentDescription.Loaded(event.appName))
            }
                ?: Icon.Resource(
                    resId = com.android.systemui.res.R.drawable.ic_info,
                    contentDescription = ContentDescription.Loaded(event.appName),
                )
        val chipText =
            if (event.progress >= 0f) {
                "${(event.progress * 100).toInt()}%"
            } else if (event.shortText.isNotEmpty()) {
                event.shortText
            } else {
                ""
            }
        return PopupChipModel.Shown(
            chipId = PopupChipId.PromotedOngoing,
            icons = listOf(ChipIcon(icon = iconModel)),
            chipText = chipText,
            colors = ColorsModel.DynamicIslandDownload,
            contentDescription = event.appName,
            popupContent = PopupContentModel.PromotedOngoing(event),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): PromotedOngoingPopupChipViewModel
    }
}
