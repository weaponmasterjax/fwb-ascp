/*
 * SPDX-FileCopyrightText: ASCP OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.usb

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.TetheringManager
import android.os.Handler
import android.os.HandlerExecutor
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.compose.theme.PlatformTheme
import com.android.systemui.CoreStartable
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.createBottomSheet
import com.android.systemui.statusbar.policy.KeyguardStateController
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class UsbModePickerStartable @Inject constructor(
    private val context: Context,
    private val sysuiDialogFactory: SystemUIDialogFactory,
    private val shadeDialogContextInteractor: ShadeDialogContextInteractor,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val userManager: UserManager,
    private val keyguardStateController: KeyguardStateController,
    private val activityStarter: ActivityStarter,
    @Main private val mainHandler: Handler,
) : CoreStartable {

    private var currentDialog: ComponentSystemUIDialog? = null
    private var isUsbConnected = false
    private var shownForCurrentSession = false
    private var pendingShow = false

    private val usbManager: UsbManager =
        context.getSystemService(UsbManager::class.java)!!
    private val tetheringManager: TetheringManager =
        context.getSystemService(TetheringManager::class.java)!!
    private val midiSupported = context.packageManager
        .hasSystemFeature(PackageManager.FEATURE_MIDI)
    private val tetheringSupported = tetheringManager.isTetheringSupported
    private val isAdminUser = userManager.isAdminUser
    private val fileTransferRestricted = userManager.hasBaseUserRestriction(
        UserManager.DISALLOW_USB_FILE_TRANSFER, UserHandle.of(UserHandle.myUserId()),
    )
    private val tetheringRestricted = userManager.hasBaseUserRestriction(
        UserManager.DISALLOW_CONFIG_TETHERING, UserHandle.of(UserHandle.myUserId()),
    )

    private val showDialogRunnable = Runnable {
        if (isUsbConnected && isEnabled()) {
            showDialog()
        }
    }

    private val keyguardCallback = object : KeyguardStateController.Callback {
        override fun onUnlockedChanged() {
            if (keyguardStateController.isUnlocked && pendingShow && isUsbConnected) {
                pendingShow = false
                if (isEnabled()) {
                    mainHandler.removeCallbacks(showDialogRunnable)
                    mainHandler.postDelayed(showDialogRunnable, UNLOCK_SHOW_DELAY_MS)
                }
            }
        }
    }

    override fun start() {
        keyguardStateController.addCallback(keyguardCallback)

        broadcastDispatcher.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)
                    if (connected) {
                        isUsbConnected = true
                        if (!shownForCurrentSession) {
                            shownForCurrentSession = true
                            if (!isEnabled()) return

                            if (keyguardStateController.isUnlocked) {
                                mainHandler.removeCallbacks(showDialogRunnable)
                                mainHandler.postDelayed(showDialogRunnable, CONNECT_DEBOUNCE_MS)
                            } else {
                                pendingShow = true
                            }
                        }
                    } else {
                        isUsbConnected = false
                        shownForCurrentSession = false
                        pendingShow = false
                        mainHandler.removeCallbacks(showDialogRunnable)
                        dismissDialog()
                    }
                }
            },
            IntentFilter(UsbManager.ACTION_USB_STATE),
        )
    }

    private fun isEnabled(): Boolean {
        return Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.USB_MODE_PICKER_DIALOG,
            1,
            UserHandle.USER_CURRENT,
        ) != 0
    }

    fun showDialog() {
        showDialog(usbManager.currentFunctions)
    }

    private fun showDialog(functions: Long) {
        dismissDialog()

        val modes = buildModeList(functions)
        if (modes.isEmpty()) return

        val statusText = resolveStatusText(modes)

        val dialog = sysuiDialogFactory.createBottomSheet(
            context = shadeDialogContextInteractor.context,
            content = { bottomSheetDialog ->
                UsbModePickerBottomSheet(bottomSheetDialog, modes, statusText, functions)
            },
        )

        dialog.setOnDismissListener {
            if (currentDialog == dialog) {
                currentDialog = null
            }
        }

        currentDialog = dialog
        dialog.show()
    }

    @Composable
    private fun UsbModePickerBottomSheet(
        dialog: SystemUIDialog,
        initialModes: List<UsbModeItem>,
        statusText: String,
        currentFunctions: Long,
    ) {
        val isCurrentlyInDarkTheme = isSystemInDarkTheme()
        val cachedDarkTheme = remember { isCurrentlyInDarkTheme }

        PlatformTheme(isDarkTheme = cachedDarkTheme) {
            UsbModePickerContent(
                modes = initialModes,
                statusText = statusText,
                onModeSelected = { function ->
                    onModeSelected(function, currentFunctions)
                    dialog.dismiss()
                },
                onSettingsClick = {
                    dialog.dismiss()
                    openUsbSettings()
                },
                onDismiss = { dialog.dismiss() },
            )
        }
    }

    private fun resolveStatusText(modes: List<UsbModeItem>): String {
        val selected = modes.firstOrNull { it.selected }
        return if (selected != null && selected.function != UsbManager.FUNCTION_NONE) {
            context.getString(selected.labelResId)
        } else {
            context.getString(R.string.usb_charging_status)
        }
    }

    private fun openUsbSettings() {
        val intent = Intent.makeRestartActivityTask(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$UsbDetailsActivity",
            ),
        )
        activityStarter.startActivity(intent, true)
    }

    private fun onModeSelected(function: Long, previousFunction: Long) {
        if (function == resolveDisplayFunction(previousFunction)) return

        if (isAuthRequired(function)) {
            if (keyguardStateController.isMethodSecure && !keyguardStateController.isUnlocked) {
                activityStarter.postQSRunnableDismissingKeyguard {
                    applyUsbFunction(function, previousFunction)
                }
                return
            }
        }
        applyUsbFunction(function, previousFunction)
    }

    private fun applyUsbFunction(function: Long, previousFunction: Long) {
        if (function == UsbManager.FUNCTION_RNDIS || function == UsbManager.FUNCTION_NCM) {
            tetheringManager.startTethering(
                TetheringManager.TETHERING_USB,
                HandlerExecutor(mainHandler),
                object : TetheringManager.StartTetheringCallback {
                    override fun onTetheringFailed(error: Int) {
                        usbManager.currentFunctions = previousFunction
                    }
                },
            )
        } else {
            usbManager.currentFunctions = function
        }
    }

    private fun buildModeList(currentFunctions: Long): List<UsbModeItem> {
        val displayFunction = resolveDisplayFunction(currentFunctions)
        return USB_MODES.filter { isFunctionSupported(it.function) }
            .map { it.copy(selected = it.function == displayFunction) }
    }

    private fun resolveDisplayFunction(functions: Long): Long = when {
        (functions and UsbManager.FUNCTION_ACCESSORY) != 0L -> UsbManager.FUNCTION_MTP
        functions == UsbManager.FUNCTION_NCM -> UsbManager.FUNCTION_RNDIS
        else -> functions
    }

    private fun isFunctionSupported(function: Long): Boolean {
        if (!midiSupported && (function and UsbManager.FUNCTION_MIDI) != 0L) return false
        if (!tetheringSupported && (function and UsbManager.FUNCTION_RNDIS) != 0L) return false
        if (isDisallowedBySystem(function)) return false
        if (!isAdminUser && (function and UsbManager.FUNCTION_RNDIS) != 0L) return false
        return true
    }

    private fun isDisallowedBySystem(function: Long): Boolean {
        if (fileTransferRestricted && ((function and UsbManager.FUNCTION_MTP) != 0L
                    || (function and UsbManager.FUNCTION_PTP) != 0L)) return true
        if (tetheringRestricted && (function and UsbManager.FUNCTION_RNDIS) != 0L) return true
        if (!UsbManager.isUvcSupportEnabled()
            && (function and UsbManager.FUNCTION_UVC) != 0L) return true
        return false
    }

    private fun isAuthRequired(function: Long): Boolean =
        function != UsbManager.FUNCTION_UVC && function != UsbManager.FUNCTION_MIDI

    private fun dismissDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("UsbModePickerStartable:")
        pw.println("  dialogShowing=${currentDialog != null}")
        pw.println("  isUsbConnected=$isUsbConnected")
        pw.println("  pendingShow=$pendingShow")
        pw.println("  shownForCurrentSession=$shownForCurrentSession")
        pw.println("  isEnabled=${isEnabled()}")
        pw.println("  currentFunctions=${usbManager.currentFunctions}")
    }

    companion object {
        private const val CONNECT_DEBOUNCE_MS = 200L
        private const val UNLOCK_SHOW_DELAY_MS = 500L

        private val USB_MODES = listOf(
            UsbModeItem(UsbManager.FUNCTION_MTP, R.string.usb_mode_file_transfer, Icons.Outlined.SwapVert, false),
            UsbModeItem(UsbManager.FUNCTION_RNDIS, R.string.usb_mode_tethering, Icons.Outlined.Usb, false),
            UsbModeItem(UsbManager.FUNCTION_MIDI, R.string.usb_mode_midi, Icons.Outlined.MusicNote, false),
            UsbModeItem(UsbManager.FUNCTION_PTP, R.string.usb_mode_photo_transfer, Icons.Outlined.CameraAlt, false),
            UsbModeItem(UsbManager.FUNCTION_UVC, R.string.usb_mode_webcam, Icons.Outlined.Videocam, false),
            UsbModeItem(UsbManager.FUNCTION_NONE, R.string.usb_mode_charging_only, Icons.Outlined.Bolt, false),
        )
    }
}
