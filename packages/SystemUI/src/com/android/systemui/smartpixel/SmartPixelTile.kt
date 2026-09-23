/*
 * SPDX-FileCopyrightText: 2026 ASCP OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.smartpixel

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.quicksettings.Tile
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.settings.SecureSettings
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Provider

class SmartPixelTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val secureSettings: SecureSettings,
    private val keyguardStateController: KeyguardStateController,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val dialogDelegateProvider: Provider<SmartPixelDialogDelegate>,
    @Main private val mainExecutor: Executor,
) : QSTileImpl<BooleanState>(
    host, uiEventLogger, backgroundLooper, mainHandler, falsingManager,
    metricsLogger, statusBarStateController, activityStarter, qsLogger,
) {
    companion object {
        const val TILE_SPEC = "smart_pixels"
        private const val INTERACTION_JANK_TAG = "smart_pixels"
    }

    override fun newTileState(): BooleanState {
        val state = BooleanState()
        state.handlesLongClick = true
        return state
    }

    override fun handleClick(expandable: Expandable?) {
        val newState = !mState.value
        secureSettings.putIntForUser(
            SmartPixelOverlay.KEY_ENABLED,
            if (newState) 1 else 0,
            UserHandle.USER_CURRENT,
        )
        refreshState(newState)
    }

    override fun handleLongClick(expandable: Expandable?) {
        val animateFromExpandable = expandable != null && !keyguardStateController.isShowing

        val runnable = Runnable {
            val dialog: SystemUIDialog = dialogDelegateProvider.get().createDialog()
            if (animateFromExpandable) {
                val controller = expandable?.dialogTransitionController(
                    DialogCuj(
                        InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                        INTERACTION_JANK_TAG,
                    )
                )
                controller?.let { dialogTransitionAnimator.show(dialog, it) } ?: dialog.show()
            } else {
                dialog.show()
            }
        }

        mainExecutor.execute {
            mActivityStarter.executeRunnableDismissingKeyguard(
                runnable,
                null,
                true,
                true,
                false,
            )
        }
    }

    override fun getLongClickIntent(): Intent? = null

    override fun getTileLabel(): CharSequence =
        mContext.getString(R.string.quick_settings_smart_pixels_label)

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val enabled = if (arg is Boolean) {
            arg
        } else {
            secureSettings.getIntForUser(
                SmartPixelOverlay.KEY_ENABLED, 0, UserHandle.USER_CURRENT,
            ) == 1
        }
        state.value = enabled
        state.label = mContext.getString(R.string.quick_settings_smart_pixels_label)
        state.secondaryLabel = mContext.getString(
            if (enabled) R.string.quick_settings_smart_pixels_on
            else R.string.quick_settings_smart_pixels_off,
        )
        state.contentDescription = state.label
        state.expandedAccessibilityClassName = MaterialSwitch::class.java.name
        state.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        state.icon = ResourceIcon.get(
            if (enabled) R.drawable.qs_smart_pixels_icon_on
            else R.drawable.qs_smart_pixels_icon_off
        )
    }

    override fun isAvailable(): Boolean = true

    override fun getMetricsCategory(): Int = MetricsEvent.QS_PANEL
}

class SmartPixelDialogDelegate @Inject constructor(
    private val sysuiDialogFactory: SystemUIDialogFactory,
    private val secureSettings: SecureSettings,
    private val shadeDialogContextInteractor: ShadeDialogContextInteractor,
) {

    fun createDialog(): SystemUIDialog =
        sysuiDialogFactory.create(context = shadeDialogContextInteractor.context) {
            SmartPixelDialogContent(it)
        }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SmartPixelDialogContent(dialog: SystemUIDialog) {
        val isCurrentlyInDarkTheme = isSystemInDarkTheme()
        val cachedDarkTheme = remember { isCurrentlyInDarkTheme }
        PlatformTheme(isDarkTheme = cachedDarkTheme) {
            AlertDialogContent(
                title = { Text(stringResource(R.string.quick_settings_smart_pixels_label)) },
                content = { SmartPixelControls() },
                positiveButton = {
                    PlatformButton(onClick = { dialog.dismiss() }) {
                        Text(stringResource(R.string.quick_settings_done))
                    }
                },
                contentBottomPadding = 8.dp,
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SmartPixelControls() {
        var isEnabled by remember {
            mutableStateOf(
                secureSettings.getIntForUser(
                    SmartPixelOverlay.KEY_ENABLED, 0, UserHandle.USER_CURRENT
                ) == 1
            )
        }
        var currentPercent by remember {
            mutableIntStateOf(
                secureSettings.getIntForUser(
                    SmartPixelOverlay.KEY_PERCENT,
                    SmartPixelOverlay.DEFAULT_PERCENT,
                    UserHandle.USER_CURRENT
                ).coerceIn(SmartPixelOverlay.MIN_PERCENT, SmartPixelOverlay.MAX_PERCENT)
            )
        }
        var sliderValue by remember(currentPercent) { mutableFloatStateOf(currentPercent.toFloat()) }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (isEnabled) R.string.quick_settings_smart_pixels_on
                        else R.string.quick_settings_smart_pixels_off,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        secureSettings.putIntForUser(
                            SmartPixelOverlay.KEY_ENABLED,
                            if (checked) 1 else 0,
                            UserHandle.USER_CURRENT
                        )
                    },
                    thumbContent = {
                        Icon(
                            imageVector = if (isEnabled) Icons.Filled.Check else Icons.Filled.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.smart_pixels_percent_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.smart_pixels_percent_format,
                            sliderValue.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val intVal = sliderValue.toInt()
                        currentPercent = intVal
                        secureSettings.putIntForUser(
                            SmartPixelOverlay.KEY_PERCENT,
                            intVal.coerceIn(SmartPixelOverlay.MIN_PERCENT, SmartPixelOverlay.MAX_PERCENT),
                            UserHandle.USER_CURRENT
                        )
                    },
                    valueRange = SmartPixelOverlay.MIN_PERCENT.toFloat()..SmartPixelOverlay.MAX_PERCENT.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    enabled = isEnabled,
                )
            }
        }
    }
}
