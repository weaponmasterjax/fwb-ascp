/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.platform

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ExecutorContentObserver
import android.hardware.SensorPrivacyManager
import android.hardware.display.ColorDisplayManager
import android.media.projection.StopReason
import android.net.TetheringManager
import android.nfc.NfcAdapter
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.RemoteException
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.service.dreams.IDreamManager
import android.util.Log
import android.view.WindowManager
import com.android.internal.util.ScreenshotHelper
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.screenrecord.ScreenRecordUxController
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.connectivity.MobileDataIndicators
import com.android.systemui.statusbar.connectivity.NetworkController
import com.android.systemui.statusbar.connectivity.SignalCallback
import com.android.systemui.statusbar.connectivity.WifiIndicators
import com.android.systemui.statusbar.phone.ManagedProfileController
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.statusbar.policy.CastController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.DataSaverController
import com.android.systemui.statusbar.policy.FlashlightController
import com.android.systemui.statusbar.policy.HotspotController
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyController
import com.android.systemui.statusbar.policy.LocationController
import com.android.systemui.statusbar.policy.RotationLockController
import com.android.systemui.statusbar.policy.SecurityController
import com.android.systemui.statusbar.policy.ZenModeController
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SecureSettings
import com.android.wifitrackerlib.WifiEntry
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class PlatformController @Inject constructor(
    private val context: Context,
    private val secureSettings: SecureSettings,
    private val globalSettings: GlobalSettings,
    @Main private val mainExecutor: Executor,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val networkController: NetworkController,
    private val accessPointController: AccessPointController,
    private val bluetoothController: BluetoothController,
    private val hotspotController: HotspotController,
    private val flashlightController: FlashlightController,
    private val locationController: LocationController,
    private val rotationLockController: RotationLockController,
    private val batteryController: BatteryController,
    private val zenModeController: ZenModeController,
    private val dataSaverController: DataSaverController,
    private val localBluetoothManager: LocalBluetoothManager?,
    private val sensorPrivacyController: IndividualSensorPrivacyController,
    private val managedProfileController: ManagedProfileController,
    private val securityController: SecurityController,
    private val castController: CastController,
    private val screenRecordUxController: ScreenRecordUxController,
    private val configurationController: ConfigurationController,
    powerManager: PowerManager
) {

    private lateinit var service: PlatformService

    private val wakeLock: PowerManager.WakeLock =
        powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Platform:Caffeine")

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val uiModeManager: UiModeManager = context.getSystemService(UiModeManager::class.java)
    private val colorDisplayManager: ColorDisplayManager =
        context.getSystemService(ColorDisplayManager::class.java)
    private val tetheringManager: TetheringManager? =
        context.getSystemService(TetheringManager::class.java)
    private val dreamManager: IDreamManager? = try {
        IDreamManager.Stub.asInterface(ServiceManager.getService("dreams"))
    } catch (e: Exception) { null }

    private val screenshotHelper = ScreenshotHelper(context)
    private val handler = Handler(Looper.getMainLooper())
    private val labelCache = mutableMapOf<String, String>()
    private var latestAccessPoints: List<WifiEntry> = emptyList()

    val supportedFeatures: Array<String> by lazy {
        buildList {
            addAll(BASE_FEATURES)
            if (nfcAdapter != null) add(PlatformClient.FEATURE_NFC)
            if (sensorPrivacyController.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA))
                add(PlatformClient.FEATURE_CAMERA_PRIVACY)
            if (sensorPrivacyController.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE))
                add(PlatformClient.FEATURE_MIC_PRIVACY)
            add(PlatformClient.FEATURE_WORK_PROFILE)
            if (tetheringManager?.isTetheringSupported == true) add(PlatformClient.FEATURE_USB_TETHER)
            if (dreamManager != null) add(PlatformClient.FEATURE_DREAM)
            if (batteryController.isReverseSupported) add(PlatformClient.FEATURE_POWER_SHARE)
            add(PlatformClient.FEATURE_CAFFEINE)
            add(PlatformClient.FEATURE_VPN)
            add(PlatformClient.FEATURE_CAST)
            add(PlatformClient.FEATURE_SMART_PIXELS)
            add(PlatformClient.FEATURE_SCREEN_RECORD)
            add(PlatformClient.FEATURE_SCREENSHOT)
        }.toTypedArray()
    }

    fun init(service: PlatformService) {
        this.service = service
        registerObservers()
    }

    private fun registerObservers() {
        // Network
        networkController.addCallback(object : SignalCallback {
            override fun setWifiIndicators(indicators: WifiIndicators) {
                val bundle = Bundle().apply {
                    putBoolean("enabled", indicators.enabled)
                    putBoolean("active", indicators.enabled && indicators.description != null)
                    putBoolean("connected", indicators.description != null)
                    putString("ssid", indicators.description ?: "")
                }
                service.broadcastState(PlatformClient.FEATURE_WIFI, bundle)
            }

            override fun setMobileDataIndicators(indicators: MobileDataIndicators) {
                val bundle = Bundle().apply {
                    putBoolean("enabled", indicators.isDefault)
                    putBoolean("active", indicators.isDefault)
                    putString("description", indicators.typeContentDescription?.toString() ?: "")
                }
                service.broadcastState(PlatformClient.FEATURE_MOBILE_DATA, bundle)
            }
        })

        accessPointController.addAccessPointCallback(
            object : AccessPointController.AccessPointCallback {
                override fun onAccessPointsChanged(accessPoints: List<WifiEntry>) {
                    latestAccessPoints = accessPoints
                }
                override fun onSettingsActivityTriggered(settingsIntent: Intent?) {}
                override fun onWifiScan(isScan: Boolean) {}
            }
        )

        // Bluetooth
        bluetoothController.addCallback(object : BluetoothController.Callback {
            override fun onBluetoothStateChange(enabled: Boolean) = broadcastBluetooth()
            override fun onBluetoothDevicesChanged() = broadcastBluetooth()
        })
        broadcastBluetooth()

        // Hotspot
        hotspotController.addCallback { enabled, numDevices ->
            service.broadcastState(PlatformClient.FEATURE_HOTSPOT, Bundle().apply {
                putBoolean("enabled", enabled)
                putBoolean("active", enabled)
                putInt("numDevices", numDevices)
            })
        }

        // Flashlight
        flashlightController.addCallback(object : FlashlightController.FlashlightListener {
            override fun onFlashlightChanged(enabled: Boolean) = broadcastFlashlight(enabled)
            override fun onFlashlightError() = broadcastFlashlight(false)
            override fun onFlashlightAvailabilityChanged(available: Boolean) =
                broadcastFlashlight(flashlightController.isEnabled, available)
            override fun onFlashlightStrengthChanged(level: Int) {}
        })
        broadcastFlashlight(flashlightController.isEnabled)

        // Location
        locationController.addCallback(object : LocationController.LocationChangeCallback {
            override fun onLocationSettingsChanged(locationEnabled: Boolean) {
                service.broadcastBool(PlatformClient.FEATURE_LOCATION, locationEnabled)
            }
        })
        service.broadcastBool(PlatformClient.FEATURE_LOCATION, locationController.isLocationEnabled)

        // Rotation
        rotationLockController.addCallback(object : RotationLockController.RotationLockControllerCallback {
            override fun onRotationLockStateChanged(rotationLocked: Boolean, affordanceVisible: Boolean) {
                service.broadcastState(PlatformClient.FEATURE_ROTATION, Bundle().apply {
                    putBoolean("enabled", !rotationLocked)
                    putBoolean("active", !rotationLocked)
                })
            }
        })
        service.broadcastBool(PlatformClient.FEATURE_ROTATION, !rotationLockController.isRotationLocked)

        // Battery
        batteryController.addCallback(object : BatteryController.BatteryStateChangeCallback {
            override fun onPowerSaveChanged(isPowerSave: Boolean) {
                service.broadcastBool(PlatformClient.FEATURE_BATTERY_SAVER, isPowerSave)
            }
            override fun onReverseChanged(isReverse: Boolean, level: Int, name: String?) {
                service.broadcastState(PlatformClient.FEATURE_POWER_SHARE, Bundle().apply {
                    putBoolean("enabled", isReverse)
                    putBoolean("active", isReverse)
                    putInt("level", level)
                })
            }
            override fun onBatteryLevelChanged(level: Int, pluggedIn: Boolean, charging: Boolean) {
                service.broadcastState(PlatformClient.KEY_BATTERY, Bundle().apply {
                    putInt("level", level)
                    putBoolean("isCharging", charging)
                    putBoolean("isPluggedIn", pluggedIn)
                })
            }
        })

        // Zen / DND
        zenModeController.addCallback(object : ZenModeController.Callback {
            override fun onZenChanged(zen: Int) {
                service.broadcastState(PlatformClient.FEATURE_ZEN, Bundle().apply {
                    putBoolean("enabled", zen != 0)
                    putBoolean("active", zen != 0)
                    putInt("mode", zen)
                })
            }
        })

        // Data Saver
        dataSaverController.addCallback(object : DataSaverController.Listener {
            override fun onDataSaverChanged(isDataSaving: Boolean) {
                service.broadcastBool(PlatformClient.FEATURE_DATA_SAVER, isDataSaving)
            }
        })

        // Security / VPN
        securityController.addCallback(object : SecurityController.SecurityControllerCallback {
            override fun onStateChanged() {
                val isVpn = securityController.isVpnEnabled
                service.broadcastState(PlatformClient.FEATURE_VPN, Bundle().apply {
                    putBoolean("enabled", isVpn)
                    putBoolean("active", isVpn)
                    putString("name", securityController.primaryVpnName ?: "")
                })
            }
        })

        // Cast
        castController.addCallback(object : CastController.Callback {
            override fun onCastDevicesChanged() {
                val active = castController.castDevices.firstOrNull { it.isCasting }
                service.broadcastState(PlatformClient.FEATURE_CAST, Bundle().apply {
                    putBoolean("enabled", active != null)
                    putBoolean("active", active != null)
                    putString("deviceName", active?.name ?: "")
                })
            }
        })

        // Screen Record
        screenRecordUxController.addCallback(object : ScreenRecordUxController.StateChangeCallback {
            override fun onCountdown(millisUntilFinished: Long) = broadcastScreenRecord()
            override fun onCountdownEnd() = broadcastScreenRecord()
            override fun onRecordingStart() = broadcastScreenRecord()
            override fun onRecordingEnd() = broadcastScreenRecord()
        })

        // Settings observers
        observeSecure(Settings.Secure.DOZE_ALWAYS_ON, PlatformClient.FEATURE_AOD)
        observeSecure(Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, PlatformClient.FEATURE_COLOR_INVERSION)
        observeSecure(Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED, PlatformClient.FEATURE_COLOR_CORRECTION)
        observeSecure(SETTING_REDUCE_BRIGHT, PlatformClient.FEATURE_REDUCE_BRIGHTNESS)
        observeSecure(SETTING_NIGHT_DISPLAY, PlatformClient.FEATURE_NIGHT_LIGHT)
        observeSecure(SETTING_ONE_HANDED, PlatformClient.FEATURE_ONE_HANDED_MODE)
        observeSecure(SETTING_SMART_PIXELS, PlatformClient.FEATURE_SMART_PIXELS)
        observeGlobal(Settings.Global.AIRPLANE_MODE_ON, PlatformClient.FEATURE_AIRPLANE_MODE)
        observeGlobal(Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, PlatformClient.FEATURE_HEADS_UP)

        // NFC
        nfcAdapter?.let {
            service.broadcastBool(PlatformClient.FEATURE_NFC, it.isEnabled)
            broadcastDispatcher.registerReceiver(
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        service.broadcastBool(PlatformClient.FEATURE_NFC, it.isEnabled)
                    }
                },
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            )
        }

        // Configuration / Dark Mode
        configurationController.addCallback(object : ConfigurationController.ConfigurationListener {
            override fun onLocaleListChanged() = labelCache.clear()
            override fun onUiModeChanged() {
                val config = context.resources.configuration
                service.broadcastBool(PlatformClient.FEATURE_DARK_MODE, isDarkMode(config))
            }
        })
        service.broadcastBool(PlatformClient.FEATURE_DARK_MODE, isDarkMode(context.resources.configuration))
    }

    private fun broadcastBluetooth() {
        val enabled = bluetoothController.isBluetoothEnabled
        val devices = bluetoothController.connectedDevices.map { dev ->
            Bundle().apply {
                putString("name", dev.name)
                putString("address", dev.address)
                putBoolean("isConnected", dev.isConnected)
            }
        }
        service.broadcastState(PlatformClient.FEATURE_BLUETOOTH, Bundle().apply {
            putBoolean("enabled", enabled)
            putBoolean("active", enabled)
            putParcelableArrayList("devices", ArrayList(devices))
        })
    }

    private fun broadcastFlashlight(enabled: Boolean, available: Boolean = flashlightController.hasFlashlight()) {
        service.broadcastState(PlatformClient.FEATURE_FLASHLIGHT, Bundle().apply {
            putBoolean("enabled", enabled)
            putBoolean("active", enabled)
            putBoolean("available", available)
        })
    }

    private fun broadcastScreenRecord() {
        val recording = screenRecordUxController.isRecording
        val starting = screenRecordUxController.isStarting
        service.broadcastState(PlatformClient.FEATURE_SCREEN_RECORD, Bundle().apply {
            putBoolean("enabled", recording || starting)
            putBoolean("active", recording)
            putBoolean("starting", starting)
        })
    }

    fun toggle(feature: String) {
        when (feature) {
            PlatformClient.FEATURE_WIFI -> {
                val cur = service.getState(feature).getBoolean("enabled", false)
                networkController.setWifiEnabled(!cur)
            }
            PlatformClient.FEATURE_MOBILE_DATA -> {
                val ctrl = networkController.mobileDataController ?: return
                ctrl.isMobileDataEnabled = !ctrl.isMobileDataEnabled
            }
            PlatformClient.FEATURE_BLUETOOTH ->
                bluetoothController.setBluetoothEnabled(!bluetoothController.isBluetoothEnabled)
            PlatformClient.FEATURE_HOTSPOT -> {
                val cur = service.getState(feature).getBoolean("enabled", false)
                hotspotController.setHotspotEnabled(!cur)
            }
            PlatformClient.FEATURE_FLASHLIGHT -> {
                if (flashlightController.hasFlashlight())
                    flashlightController.setFlashlight(!flashlightController.isEnabled)
            }
            PlatformClient.FEATURE_LOCATION ->
                locationController.setLocationEnabled(!locationController.isLocationEnabled)
            PlatformClient.FEATURE_ROTATION ->
                rotationLockController.setRotationLocked(!rotationLockController.isRotationLocked, TAG)
            PlatformClient.FEATURE_BATTERY_SAVER ->
                batteryController.setPowerSaveMode(!batteryController.isPowerSave)
            PlatformClient.FEATURE_ZEN -> {
                val cur = zenModeController.zen
                zenModeController.setZen(if (cur == 0) 1 else 0, null, TAG)
            }
            PlatformClient.FEATURE_DATA_SAVER ->
                dataSaverController.setDataSaverEnabled(!dataSaverController.isDataSaverEnabled)
            PlatformClient.FEATURE_AOD -> toggleSecure(Settings.Secure.DOZE_ALWAYS_ON)
            PlatformClient.FEATURE_AIRPLANE_MODE -> {
                val enabled = !getGlobalBool(Settings.Global.AIRPLANE_MODE_ON)
                setGlobalBool(Settings.Global.AIRPLANE_MODE_ON, enabled)
                context.sendBroadcastAsUser(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled),
                    UserHandle.ALL
                )
            }
            PlatformClient.FEATURE_NFC -> nfcAdapter?.let {
                if (it.isEnabled) it.disable() else it.enable()
            }
            PlatformClient.FEATURE_DARK_MODE ->
                uiModeManager.setNightModeActivated(!isDarkMode(context.resources.configuration))
            PlatformClient.FEATURE_NIGHT_LIGHT ->
                colorDisplayManager.setNightDisplayActivated(!colorDisplayManager.isNightDisplayActivated)
            PlatformClient.FEATURE_COLOR_INVERSION ->
                toggleSecure(Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
            PlatformClient.FEATURE_COLOR_CORRECTION ->
                toggleSecure(Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED)
            PlatformClient.FEATURE_REDUCE_BRIGHTNESS -> toggleSecure(SETTING_REDUCE_BRIGHT)
            PlatformClient.FEATURE_ONE_HANDED_MODE -> toggleSecure(SETTING_ONE_HANDED)
            PlatformClient.FEATURE_HEADS_UP -> toggleGlobal(Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED)
            PlatformClient.FEATURE_AUTO_SYNC ->
                ContentResolver.setMasterSyncAutomatically(!ContentResolver.getMasterSyncAutomatically())
            PlatformClient.FEATURE_CAMERA_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.CAMERA,
                    !sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA)
                )
            PlatformClient.FEATURE_MIC_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.MICROPHONE,
                    !sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
            PlatformClient.FEATURE_WORK_PROFILE ->
                managedProfileController.setWorkModeEnabled(!managedProfileController.isWorkModeEnabled)
            PlatformClient.FEATURE_USB_TETHER -> {
                val cur = service.getState(feature).getBoolean("active", false)
                tetheringManager?.setUsbTethering(!cur)
            }
            PlatformClient.FEATURE_DREAM -> try {
                dreamManager?.let { if (it.isDreaming) it.awaken() else it.dream() }
            } catch (e: RemoteException) {
                Log.w(TAG, "Dream toggle failed", e)
            }
            PlatformClient.FEATURE_POWER_SHARE ->
                batteryController.setReverseState(!batteryController.isReverseOn)
            PlatformClient.FEATURE_CAFFEINE -> {
                if (wakeLock.isHeld) wakeLock.release() else wakeLock.acquire(CAFFEINE_DURATION_MS)
                service.broadcastBool(feature, wakeLock.isHeld)
            }
            PlatformClient.FEATURE_VPN -> {
                if (securityController.isVpnEnabled) securityController.disconnectPrimaryVpn()
            }
            PlatformClient.FEATURE_CAST -> {
                val active = castController.castDevices.firstOrNull { it.isCasting }
                active?.let { castController.stopCasting(it, StopReason.STOP_QS_TILE) }
            }
            PlatformClient.FEATURE_SMART_PIXELS -> toggleSecure(SETTING_SMART_PIXELS)
            PlatformClient.FEATURE_SCREEN_RECORD -> {
                if (screenRecordUxController.isStarting) {
                    screenRecordUxController.cancelCountdown()
                } else if (screenRecordUxController.isRecording) {
                    screenRecordUxController.stopRecording(StopReason.STOP_QS_TILE)
                } else {
                    screenRecordUxController.createScreenRecordDialog(null).show()
                }
            }
            PlatformClient.FEATURE_SCREENSHOT -> {
                handler.postDelayed({
                    screenshotHelper.takeScreenshot(
                        WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                        WindowManager.ScreenshotSource.SCREENSHOT_OTHER,
                        handler,
                        null
                    )
                }, SCREENSHOT_DELAY_MS)
            }
            else -> Log.w(TAG, "Unknown toggle: $feature")
        }
    }

    fun setEnabled(feature: String, enabled: Boolean) {
        when (feature) {
            PlatformClient.FEATURE_WIFI -> networkController.setWifiEnabled(enabled)
            PlatformClient.FEATURE_MOBILE_DATA ->
                networkController.mobileDataController?.let { it.isMobileDataEnabled = enabled }
            PlatformClient.FEATURE_BLUETOOTH -> bluetoothController.setBluetoothEnabled(enabled)
            PlatformClient.FEATURE_HOTSPOT -> hotspotController.setHotspotEnabled(enabled)
            PlatformClient.FEATURE_FLASHLIGHT -> {
                if (flashlightController.hasFlashlight()) flashlightController.setFlashlight(enabled)
            }
            PlatformClient.FEATURE_LOCATION -> locationController.setLocationEnabled(enabled)
            PlatformClient.FEATURE_ROTATION -> rotationLockController.setRotationLocked(!enabled, TAG)
            PlatformClient.FEATURE_BATTERY_SAVER -> batteryController.setPowerSaveMode(enabled)
            PlatformClient.FEATURE_ZEN -> zenModeController.setZen(if (enabled) 1 else 0, null, TAG)
            PlatformClient.FEATURE_DATA_SAVER -> dataSaverController.setDataSaverEnabled(enabled)
            PlatformClient.FEATURE_AOD -> setSecureBool(Settings.Secure.DOZE_ALWAYS_ON, enabled)
            PlatformClient.FEATURE_AIRPLANE_MODE -> {
                setGlobalBool(Settings.Global.AIRPLANE_MODE_ON, enabled)
                context.sendBroadcastAsUser(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled),
                    UserHandle.ALL
                )
            }
            PlatformClient.FEATURE_NFC -> nfcAdapter?.let { if (enabled) it.enable() else it.disable() }
            PlatformClient.FEATURE_DARK_MODE -> uiModeManager.setNightModeActivated(enabled)
            PlatformClient.FEATURE_NIGHT_LIGHT -> colorDisplayManager.setNightDisplayActivated(enabled)
            PlatformClient.FEATURE_COLOR_INVERSION ->
                setSecureBool(Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, enabled)
            PlatformClient.FEATURE_COLOR_CORRECTION ->
                setSecureBool(Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED, enabled)
            PlatformClient.FEATURE_REDUCE_BRIGHTNESS -> setSecureBool(SETTING_REDUCE_BRIGHT, enabled)
            PlatformClient.FEATURE_ONE_HANDED_MODE -> setSecureBool(SETTING_ONE_HANDED, enabled)
            PlatformClient.FEATURE_HEADS_UP ->
                setGlobalBool(Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, enabled)
            PlatformClient.FEATURE_AUTO_SYNC -> ContentResolver.setMasterSyncAutomatically(enabled)
            PlatformClient.FEATURE_CAMERA_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.CAMERA,
                    enabled
                )
            PlatformClient.FEATURE_MIC_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.MICROPHONE,
                    enabled
                )
            PlatformClient.FEATURE_WORK_PROFILE -> managedProfileController.setWorkModeEnabled(enabled)
            PlatformClient.FEATURE_USB_TETHER -> tetheringManager?.setUsbTethering(enabled)
            PlatformClient.FEATURE_DREAM -> try {
                dreamManager?.let { if (enabled) it.dream() else it.awaken() }
            } catch (e: RemoteException) {
                Log.w(TAG, "Dream setEnabled failed", e)
            }
            PlatformClient.FEATURE_POWER_SHARE -> batteryController.setReverseState(enabled)
            PlatformClient.FEATURE_CAFFEINE -> {
                if (enabled && !wakeLock.isHeld) {
                    wakeLock.acquire(CAFFEINE_DURATION_MS)
                } else if (!enabled && wakeLock.isHeld) {
                    wakeLock.release()
                }
                service.broadcastBool(feature, wakeLock.isHeld)
            }
            PlatformClient.FEATURE_VPN -> {
                if (!enabled && securityController.isVpnEnabled) securityController.disconnectPrimaryVpn()
            }
            PlatformClient.FEATURE_CAST -> {
                if (!enabled) {
                    castController.castDevices.firstOrNull { it.isCasting }
                        ?.let { castController.stopCasting(it, StopReason.STOP_QS_TILE) }
                }
            }
            PlatformClient.FEATURE_SMART_PIXELS -> setSecureBool(SETTING_SMART_PIXELS, enabled)
            PlatformClient.FEATURE_SCREEN_RECORD -> {
                if (!enabled && screenRecordUxController.isRecording) {
                    screenRecordUxController.stopRecording(StopReason.STOP_QS_TILE)
                }
            }
            PlatformClient.FEATURE_SCREENSHOT -> if (enabled) toggle(feature)
            else -> Log.w(TAG, "Unknown setEnabled: $feature")
        }
    }

    fun setValue(feature: String, value: Int) {
        when (feature) {
            PlatformClient.FEATURE_ZEN -> {
                if (zenModeController.zen != value) zenModeController.setZen(value, null, TAG)
            }
            else -> Log.w(TAG, "Unknown setValue: $feature")
        }
    }

    fun performAction(feature: String, param: String) {
        when (feature) {
            PlatformClient.ACTION_WIFI_CONNECT ->
                latestAccessPoints.find { it.key == param || it.title == param }
                    ?.let { accessPointController.connect(it) }
            PlatformClient.ACTION_BT_CONNECT ->
                getAllBluetoothDevices().find { it.address == param }?.let {
                    if (it.isConnected) it.disconnect() else it.connect(true)
                }
            else -> Log.w(TAG, "Unknown performAction: $feature")
        }
    }

    private fun getAllBluetoothDevices(): Collection<CachedBluetoothDevice> =
        localBluetoothManager?.cachedDeviceManager?.cachedDevicesCopy
            ?: bluetoothController.connectedDevices

    fun getLabel(feature: String): String? {
        labelCache[feature]?.let { return it }
        val label = when {
            FEATURE_LABEL_RES.containsKey(feature) -> context.getString(FEATURE_LABEL_RES[feature]!!)
            feature == PlatformClient.FEATURE_SMART_PIXELS -> "Smart Pixels"
            else -> return null
        }
        labelCache[feature] = label
        return label
    }

    fun getSecondaryLabel(feature: String, state: Bundle): String? = when (feature) {
        PlatformClient.FEATURE_WIFI -> {
            val ssid = state.getString("ssid")
            if (state.getBoolean("connected") && !ssid.isNullOrEmpty()) ssid else null
        }
        PlatformClient.FEATURE_BLUETOOTH -> {
            @Suppress("DEPRECATION")
            val devices = state.getParcelableArrayList<Bundle>("devices")
            devices?.firstOrNull { it.getBoolean("isConnected") }?.getString("name")
        }
        PlatformClient.FEATURE_HOTSPOT -> {
            val num = state.getInt("numDevices", 0)
            if (num > 0) "$num ${if (num == 1) "device" else "devices"}" else null
        }
        PlatformClient.FEATURE_MOBILE_DATA -> state.getString("description")?.takeIf { it.isNotEmpty() }
        PlatformClient.FEATURE_ZEN -> {
            val mode = state.getInt("mode", 0)
            if (mode != 0) context.getString(R.string.zen_mode_on) else null
        }
        PlatformClient.FEATURE_VPN -> state.getString("name")?.takeIf { it.isNotEmpty() }
        PlatformClient.FEATURE_CAST -> state.getString("deviceName")?.takeIf { it.isNotEmpty() }
        PlatformClient.FEATURE_SCREEN_RECORD -> {
            when {
                state.getBoolean("active") -> context.getString(R.string.quick_settings_screen_record_stop)
                state.getBoolean("starting") -> context.getString(R.string.quick_settings_screen_record_start)
                else -> null
            }
        }
        else -> null
    }

    private fun getSecureBool(key: String): Boolean =
        secureSettings.getIntForUser(key, 0, UserHandle.USER_CURRENT) == 1

    private fun setSecureBool(key: String, value: Boolean) {
        val token = Binder.clearCallingIdentity()
        try {
            secureSettings.putIntForUser(key, if (value) 1 else 0, UserHandle.USER_CURRENT)
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun toggleSecure(key: String) = setSecureBool(key, !getSecureBool(key))

    private fun getGlobalBool(key: String): Boolean = globalSettings.getInt(key, 0) == 1

    private fun setGlobalBool(key: String, value: Boolean) {
        val token = Binder.clearCallingIdentity()
        try {
            globalSettings.putInt(key, if (value) 1 else 0)
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun toggleGlobal(key: String) = setGlobalBool(key, !getGlobalBool(key))

    private fun observeSecure(key: String, feature: String) {
        secureSettings.registerContentObserverSync(
            key, object : ExecutorContentObserver(mainExecutor) {
                override fun onChange(selfChange: Boolean) {
                    service.broadcastBool(feature, getSecureBool(key))
                }
            }
        )
        service.broadcastBool(feature, getSecureBool(key))
    }

    private fun observeGlobal(key: String, feature: String) {
        globalSettings.registerContentObserverSync(
            key, object : ExecutorContentObserver(mainExecutor) {
                override fun onChange(selfChange: Boolean) {
                    service.broadcastBool(feature, getGlobalBool(key))
                }
            }
        )
        service.broadcastBool(feature, getGlobalBool(key))
    }

    companion object {
        private const val TAG = "PlatformController"
        private const val CAFFEINE_DURATION_MS = 5L * 60 * 1000
        private const val SCREENSHOT_DELAY_MS = 500L
        const val SETTING_NIGHT_DISPLAY = "night_display_activated"
        const val SETTING_REDUCE_BRIGHT = "reduce_bright_colors_activated"
        const val SETTING_ONE_HANDED = "one_handed_mode_enabled"
        const val SETTING_SMART_PIXELS = "smart_pixel_filter_enabled"

        fun isDarkMode(config: Configuration): Boolean =
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        private val BASE_FEATURES = arrayOf(
            PlatformClient.FEATURE_WIFI,
            PlatformClient.FEATURE_MOBILE_DATA,
            PlatformClient.FEATURE_BLUETOOTH,
            PlatformClient.FEATURE_HOTSPOT,
            PlatformClient.FEATURE_FLASHLIGHT,
            PlatformClient.FEATURE_LOCATION,
            PlatformClient.FEATURE_ROTATION,
            PlatformClient.FEATURE_BATTERY_SAVER,
            PlatformClient.FEATURE_ZEN,
            PlatformClient.FEATURE_AOD,
            PlatformClient.FEATURE_DATA_SAVER,
            PlatformClient.FEATURE_AIRPLANE_MODE,
            PlatformClient.FEATURE_DARK_MODE,
            PlatformClient.FEATURE_NIGHT_LIGHT,
            PlatformClient.FEATURE_COLOR_INVERSION,
            PlatformClient.FEATURE_COLOR_CORRECTION,
            PlatformClient.FEATURE_REDUCE_BRIGHTNESS,
            PlatformClient.FEATURE_ONE_HANDED_MODE,
            PlatformClient.FEATURE_HEADS_UP,
            PlatformClient.FEATURE_AUTO_SYNC
        )

        private val FEATURE_LABEL_RES = mapOf(
            PlatformClient.FEATURE_WIFI to R.string.quick_settings_wifi_label,
            PlatformClient.FEATURE_MOBILE_DATA to R.string.quick_settings_internet_label,
            PlatformClient.FEATURE_BLUETOOTH to R.string.quick_settings_bluetooth_label,
            PlatformClient.FEATURE_HOTSPOT to R.string.quick_settings_hotspot_label,
            PlatformClient.FEATURE_FLASHLIGHT to R.string.quick_settings_flashlight_label,
            PlatformClient.FEATURE_LOCATION to R.string.quick_settings_location_label,
            PlatformClient.FEATURE_ROTATION to R.string.quick_settings_rotation_unlocked_label,
            PlatformClient.FEATURE_BATTERY_SAVER to R.string.battery_detail_switch_title,
            PlatformClient.FEATURE_ZEN to R.string.quick_settings_dnd_label,
            PlatformClient.FEATURE_AOD to R.string.quick_settings_aod_label,
            PlatformClient.FEATURE_DATA_SAVER to R.string.data_saver,
            PlatformClient.FEATURE_AIRPLANE_MODE to R.string.airplane_mode,
            PlatformClient.FEATURE_NFC to R.string.quick_settings_nfc_label,
            PlatformClient.FEATURE_DARK_MODE to R.string.quick_settings_ui_mode_night_label,
            PlatformClient.FEATURE_NIGHT_LIGHT to R.string.quick_settings_night_display_label,
            PlatformClient.FEATURE_COLOR_INVERSION to R.string.quick_settings_inversion_label,
            PlatformClient.FEATURE_COLOR_CORRECTION to R.string.quick_settings_color_correction_label,
            PlatformClient.FEATURE_REDUCE_BRIGHTNESS to com.android.internal.R.string.reduce_bright_colors_feature_name,
            PlatformClient.FEATURE_ONE_HANDED_MODE to R.string.quick_settings_onehanded_label,
            PlatformClient.FEATURE_HEADS_UP to R.string.quick_settings_heads_up_label,
            PlatformClient.FEATURE_AUTO_SYNC to R.string.quick_settings_sync_label,
            PlatformClient.FEATURE_CAMERA_PRIVACY to R.string.quick_settings_camera_label,
            PlatformClient.FEATURE_MIC_PRIVACY to R.string.quick_settings_mic_label,
            PlatformClient.FEATURE_WORK_PROFILE to R.string.quick_settings_work_mode_label,
            PlatformClient.FEATURE_USB_TETHER to R.string.quick_settings_usb_tether_label,
            PlatformClient.FEATURE_DREAM to R.string.quick_settings_screensaver_label,
            PlatformClient.FEATURE_CAFFEINE to R.string.quick_settings_caffeine_label,
            PlatformClient.FEATURE_VPN to R.string.quick_settings_vpn_label,
            PlatformClient.FEATURE_CAST to R.string.quick_settings_cast_title,
            PlatformClient.FEATURE_SCREEN_RECORD to R.string.quick_settings_screen_record_label,
            PlatformClient.FEATURE_SCREENSHOT to R.string.quick_settings_screenshot_label
        )
    }
}
