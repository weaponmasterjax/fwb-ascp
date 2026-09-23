/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.bluetooth.ui.viewmodel

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.BLUETOOTH
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.dynamicisland.data.source.ConnectivityIslandManager
import com.android.systemui.dynamicisland.ui.model.ChipIcon
import com.android.systemui.dynamicisland.ui.model.ColorsModel
import com.android.systemui.dynamicisland.ui.model.PopupChipId
import com.android.systemui.dynamicisland.ui.model.PopupChipModel
import com.android.systemui.dynamicisland.ui.model.PopupContentModel
import com.android.systemui.dynamicisland.ui.viewmodel.IslandChipViewModel
import com.android.systemui.statusbar.policy.BluetoothController
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** ViewModel backing the Bluetooth page inside the dynamic island. */
class BluetoothPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val bluetoothController: BluetoothController,
    private val connectivityIslandManager: ConnectivityIslandManager,
) : IslandChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("BluetoothPopupChipViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.Bluetooth),
            source =
                callbackFlow {
                        connectivityIslandManager.startBluetooth()
                        val callback =
                            object : BluetoothController.Callback {
                                override fun onBluetoothStateChange(enabled: Boolean) {
                                    trySend(readBluetoothState())
                                }

                                override fun onBluetoothDevicesChanged() {
                                    trySend(readBluetoothState())
                                }
                            }

                        bluetoothController.addCallback(callback)
                        trySend(readBluetoothState())
                        awaitClose { bluetoothController.removeCallback(callback) }
                    }
                    .combine(connectivityIslandManager.bluetoothEvent) { state, btEvent ->
                        if (btEvent != null && btEvent.address == state.address) {
                            state.copy(
                                deviceImage = btEvent.deviceImage ?: state.deviceImage,
                                leftBatteryLevel = btEvent.leftBatteryLevel,
                                rightBatteryLevel = btEvent.rightBatteryLevel,
                                caseBatteryLevel = btEvent.caseBatteryLevel,
                            )
                        } else {
                            state
                        }
                    }
                    .map(::toPopupChipModel)
                    .combine(
                        observeDynamicIslandFeatureEnabled(context, BLUETOOTH)
                    ) { model, enabled ->
                        if (enabled) model else PopupChipModel.Hidden(PopupChipId.Bluetooth)
                    },
        )

    override suspend fun onActivated(): Nothing {
        connectivityIslandManager.startBluetooth()
        hydrator.activate()
    }

    private fun toPopupChipModel(state: BluetoothState): PopupChipModel {
        if (!state.hasConnectedDevice) {
            return PopupChipModel.Hidden(PopupChipId.Bluetooth)
        }

        val event =
            IslandEvent.Bluetooth(
                deviceName = state.deviceName,
                batteryLevel = state.batteryLevel,
                address = state.address,
                deviceIcon = state.deviceIcon,
                deviceTypeLabel = state.deviceTypeLabel,
                iconRes = state.iconRes,
                deviceImage = state.deviceImage,
                leftBatteryLevel = state.leftBatteryLevel,
                rightBatteryLevel = state.rightBatteryLevel,
                caseBatteryLevel = state.caseBatteryLevel,
            )

        val chipIcon =
            Icon.Resource(
                resId = R.drawable.ic_bluetooth_connected,
                contentDescription = ContentDescription.Resource(R.string.dynamic_island_connected),
            )

        return PopupChipModel.Shown(
            chipId = PopupChipId.Bluetooth,
            icons = listOf(ChipIcon(icon = chipIcon)),
            chipText = null,
            colors = ColorsModel.DynamicIslandBluetooth,
            contentDescription = state.deviceName,
            popupContent = PopupContentModel.Bluetooth(event),
        )
    }

    private fun readBluetoothState(): BluetoothState {
        connectivityIslandManager.startBluetooth()
        val devices = bluetoothController.connectedDevices
        val primaryDevice = devices.firstOrNull()
        if (primaryDevice == null) {
            return BluetoothState(hasConnectedDevice = false)
        }
        val iconPair =
            try {
                primaryDevice.getDrawableWithDescription()
            } catch (_: Exception) {
                null
            }
        val directDrawable = if (iconPair?.first is android.graphics.drawable.BitmapDrawable) {
            iconPair.first
        } else null
        val btClass = primaryDevice.btClass
        val iconRes = when {
            btClass?.doesClassMatch(android.bluetooth.BluetoothClass.PROFILE_HEADSET) == true ||
            btClass?.doesClassMatch(android.bluetooth.BluetoothClass.PROFILE_A2DP) == true -> R.drawable.ic_headset
            btClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> R.drawable.ic_watch
            btClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> R.drawable.ic_headset
            else -> R.drawable.ic_headset
        }
        return BluetoothState(
            hasConnectedDevice = true,
            deviceName = primaryDevice.getName() ?: "Bluetooth",
            batteryLevel = primaryDevice.getBatteryLevel(),
            address = primaryDevice.getAddress() ?: "",
            deviceIcon = iconPair?.first,
            deviceTypeLabel = iconPair?.second ?: "",
            iconRes = iconRes,
            deviceImage = directDrawable,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): BluetoothPopupChipViewModel
    }
}

private data class BluetoothState(
    val hasConnectedDevice: Boolean,
    val deviceName: String = "",
    val batteryLevel: Int = -1,
    val address: String = "",
    val deviceIcon: Drawable? = null,
    val deviceTypeLabel: String = "",
    val iconRes: Int = com.android.systemui.res.R.drawable.ic_headset,
    val deviceImage: Drawable? = null,
    val leftBatteryLevel: Int? = null,
    val rightBatteryLevel: Int? = null,
    val caseBatteryLevel: Int? = null,
)
