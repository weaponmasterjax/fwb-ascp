/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.viewmodel

import com.android.systemui.lifecycle.Activatable
import com.android.systemui.dynamicisland.ui.model.PopupChipModel

/** View model for a single dynamic island page. */
interface IslandChipViewModel : Activatable {
    val chip: PopupChipModel
}
