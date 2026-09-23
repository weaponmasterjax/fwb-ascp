/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui

import com.android.systemui.dynamicisland.domain.DynamicIslandInteractor
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DynamicIslandStatusBarExpansion
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    private val interactor: DynamicIslandInteractor,
) {
    private val _intent = MutableStateFlow(false)

    val isExpanded: StateFlow<Boolean> =
        combine(_intent, interactor.isOnKeyguard) { intent, onKg -> intent && !onKg }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Lazily, false)

    init {
        interactor.isOnKeyguard
            .onEach { if (it) collapse() }
            .launchIn(applicationScope)

        interactor.uiState
            .map { state ->
                state.events.isEmpty() || state.events.all { it is IslandEvent.AospChip }
            }
            .distinctUntilChanged()
            .onEach { shouldCollapse ->
                if (shouldCollapse) collapse()
            }
            .launchIn(applicationScope)

        combine(
            interactor.qsExpansion.map { it > 0f }.distinctUntilChanged(),
            interactor.legacyShadeExpansion.map { it > 0f }.distinctUntilChanged(),
            interactor.isPanelExpanded,
        ) { qs, shade, panel -> qs || shade || panel }
            .distinctUntilChanged()
            .onEach { if (it) collapse() }
            .launchIn(applicationScope)
    }

    fun expand() {
        val state = interactor.uiState.value
        if (state.events.isEmpty() || state.events.all { it is IslandEvent.AospChip }) return
        _intent.value = true
    }

    fun collapse() {
        _intent.value = false
    }

    fun toggle() {
        if (_intent.value) collapse() else expand()
    }
}
