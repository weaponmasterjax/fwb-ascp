/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dynamicisland.media.domain.interactor.MediaControlChipInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Starts dynamic island media observation on phones, not only large-screen devices. */
@SysUISingleton
class DynamicIslandStartable
@Inject
constructor(
    @Background val bgScope: CoroutineScope,
    private val mediaControlChipInteractor: MediaControlChipInteractor,
) : CoreStartable {

    override fun start() {
        bgScope.launch { mediaControlChipInteractor.initialize() }
    }
}
