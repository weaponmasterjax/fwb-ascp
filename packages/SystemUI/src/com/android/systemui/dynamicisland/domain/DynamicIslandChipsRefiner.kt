/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.domain

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsRefiner
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SysUISingleton
class DynamicIslandChipsRefiner @Inject constructor(
    private val settings: DynamicIslandSettings,
) : OngoingActivityChipsRefiner {

    private val _chipsFlow = MutableStateFlow(MultipleOngoingActivityChipsModel())
    val chipsFlow: StateFlow<MultipleOngoingActivityChipsModel> = _chipsFlow.asStateFlow()

    override fun transform(input: MultipleOngoingActivityChipsModel): MultipleOngoingActivityChipsModel {
        _chipsFlow.value = input
        val dynamicChipActive = settings.isEnabled.value
        val dynamicIslandCallsActive = settings.isDynamicIslandCallsActive.value
        val dynamicIslandOngoingActive = settings.isDynamicIslandOngoingActive.value

        if (!dynamicChipActive && !dynamicIslandCallsActive && !dynamicIslandOngoingActive) {
            return input
        }

        return input.copy(
            active =
                input.active.map { chip ->
                    val isCallChip = chip.key.startsWith("callChip-")
                    val shouldHideCall =
                        (dynamicChipActive && "call" !in settings.disabledEventTypes.value) ||
                            dynamicIslandCallsActive
                    val shouldHide =
                        if (isCallChip) {
                            shouldHideCall
                        } else {
                            dynamicChipActive || dynamicIslandOngoingActive
                        }
                    if (shouldHide) chip.copy(isHidden = true) else chip
                },
        )
    }
}
