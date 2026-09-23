/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui

import com.android.systemui.dynamicisland.domain.DynamicIslandInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DynamicIslandKeyguardExpansion
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    private val interactor: DynamicIslandInteractor,
) {
    private val _intent = MutableStateFlow(false)

    private val hasChip =
        interactor.uiState
            .map { it.shouldShow && it.topEvent != null }
            .distinctUntilChanged()

    val canShow: StateFlow<Boolean> = run {
        val contextAndDoze =
            combine(
                interactor.isOnKeyguard,
                interactor.isDozing,
                interactor.dozeAmount.map { it > 0f }.distinctUntilChanged(),
                interactor.qsExpansion.map { it > 0f }.distinctUntilChanged(),
                interactor.isPanelExpanded,
            ) { onKg, dozing, dozeAmt, qs, panelExp ->
                onKg && !dozing && !dozeAmt && !qs && !panelExp
            }
        val shadeAndBouncer =
            combine(
                interactor.isBouncerShowing,
                interactor.legacyShadeExpansion.map { it >= 0.95f }.distinctUntilChanged(),
                hasChip,
            ) { bouncer, shadeFull, chip ->
                !bouncer && shadeFull && chip
            }
        combine(contextAndDoze, shadeAndBouncer) { a, b -> a && b }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, false)
    }

    val isExpanded: StateFlow<Boolean> =
        combine(_intent, canShow) { intent, show -> intent && show }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Lazily, false)

    private val _collapseSettled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseSettled: SharedFlow<Unit> = _collapseSettled.asSharedFlow()

    fun notifyCollapseSettled() {
        _collapseSettled.tryEmit(Unit)
    }

    init {
        interactor.isOnKeyguard
            .onEach { if (!it) collapse() }
            .launchIn(applicationScope)
    }

    fun expand() {
        if (interactor.uiState.value.topEvent == null) return
        _intent.value = true
    }

    fun collapse() {
        _intent.value = false
    }

    fun toggle() {
        if (_intent.value) collapse() else expand()
    }
}
