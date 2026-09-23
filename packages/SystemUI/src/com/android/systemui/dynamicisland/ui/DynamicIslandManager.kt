/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

@SysUISingleton
class DynamicIslandManager
@Inject
constructor(
    private val viewModel: DynamicIslandChipViewModel,
    private val expandedPanel: DynamicIslandExpandedPanel,
) : CoreStartable {

    override fun start() {
        viewModel.interactor.init()
        expandedPanel.init()
    }
}

