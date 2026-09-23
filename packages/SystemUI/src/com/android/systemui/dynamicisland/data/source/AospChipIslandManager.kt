/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.data.source

import com.android.systemui.dynamicisland.domain.DynamicIslandChipsRefiner
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SysUISingleton
class AospChipIslandManager @Inject constructor(
    private val refiner: DynamicIslandChipsRefiner,
) {
    val aospChipEvents: Flow<List<IslandEvent.AospChip>> =
        refiner.chipsFlow.map { model ->
            model.active
                .filter { isAbsorbed(it) }
                .map { IslandEvent.AospChip(active = it) }
        }

    private fun isAbsorbed(chip: OngoingActivityChipModel.Active): Boolean {
        val key = chip.key
        return key.startsWith("callChip-") ||
            key == "ShareToApp" ||
            key == "ScreenRecord" ||
            key == "CastToOtherDevice"
    }
}
