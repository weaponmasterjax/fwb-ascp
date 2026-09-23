/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * Copyright (C) 2026 kenway214
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.panels.ui.compose

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.compose.infinitegrid.TileColors
import com.android.systemui.qs.panels.ui.compose.infinitegrid.TileDefaults
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.res.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MaterialControlPanel(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 8.dp,
    tileViewModels: List<TileViewModel>? = null,
    keyguardStateController: KeyguardStateController? = null,
    activityStarter: ActivityStarter? = null,
) {
    val context = LocalContext.current
    val cr = context.contentResolver
    val scope = rememberCoroutineScope()

    fun readEnabled(): Boolean = try {
        Settings.System.getIntForUser(
            cr, Settings.System.QS_WIDGET_PANEL, 0, UserHandle.USER_CURRENT
        ) == 1
    } catch (_: Exception) { false }

    fun readSliderStyle(): Int = try {
        Settings.System.getIntForUser(
            cr, Settings.System.QS_WIDGET_SLIDER_CORNER, 0, UserHandle.USER_CURRENT
        )
    } catch (_: Exception) { 0 }

    fun readShowTiles(): Boolean = try {
        Settings.System.getIntForUser(
            cr, "qs_widget_show_tiles", 1, UserHandle.USER_CURRENT
        ) == 1
    } catch (_: Exception) { true }

    fun readTile1(): String = try {
        Settings.System.getStringForUser(
            cr, "qs_widget_tile_1", UserHandle.USER_CURRENT
        ) ?: "internet"
    } catch (_: Exception) { "internet" }

    fun readTile2(): String = try {
        Settings.System.getStringForUser(
            cr, "qs_widget_tile_2", UserHandle.USER_CURRENT
        ) ?: "bt"
    } catch (_: Exception) { "bt" }

    fun readMediaPlayerSetting(): Int = try {
        Settings.Secure.getIntForUser(
            cr, Settings.Secure.QS_SHOW_MEDIA_PLAYER, 2, UserHandle.USER_CURRENT
        )
    } catch (_: Exception) { 2 }

    var enabled by remember { mutableStateOf(readEnabled()) }
    var sliderStyle by remember { mutableIntStateOf(readSliderStyle()) }
    var showTiles by remember { mutableStateOf(readShowTiles()) }
    var tile1 by remember { mutableStateOf(readTile1()) }
    var tile2 by remember { mutableStateOf(readTile2()) }

    var savedMediaPlayerValue by remember {
        mutableIntStateOf(
            if (readEnabled()) 0 else readMediaPlayerSetting()
        )
    }

    fun syncMediaPlayerSetting(widgetEnabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                if (widgetEnabled) {
                    savedMediaPlayerValue = readMediaPlayerSetting()
                    Settings.Secure.putIntForUser(
                        cr,
                        Settings.Secure.QS_SHOW_MEDIA_PLAYER,
                        0,
                        UserHandle.USER_CURRENT,
                    )
                } else {
                    Settings.Secure.putIntForUser(
                        cr,
                        Settings.Secure.QS_SHOW_MEDIA_PLAYER,
                        savedMediaPlayerValue,
                        UserHandle.USER_CURRENT,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val nowEnabled = readEnabled()
                val wasEnabled = enabled
                enabled = nowEnabled
                sliderStyle = readSliderStyle()
                showTiles = readShowTiles()
                tile1 = readTile1()
                tile2 = readTile2()
                if (nowEnabled != wasEnabled) {
                    syncMediaPlayerSetting(nowEnabled)
                }
            }
        }
        try {
            cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.QS_WIDGET_PANEL),
                false, observer, UserHandle.USER_ALL,
            )
            cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.QS_WIDGET_SLIDER_CORNER),
                false, observer, UserHandle.USER_ALL,
            )
            cr.registerContentObserver(
                Settings.System.getUriFor("qs_widget_show_tiles"),
                false, observer, UserHandle.USER_ALL,
            )
            cr.registerContentObserver(
                Settings.System.getUriFor("qs_widget_tile_1"),
                false, observer, UserHandle.USER_ALL,
            )
            cr.registerContentObserver(
                Settings.System.getUriFor("qs_widget_tile_2"),
                false, observer, UserHandle.USER_ALL,
            )
        } catch (_: Exception) {}

        onDispose { cr.unregisterContentObserver(observer) }
    }

    val fraction by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "MaterialControlPanelFraction",
    )

    if (fraction == 0f) return

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val animatedHeight = (placeable.height * fraction).roundToInt()
                layout(placeable.width, animatedHeight) {
                    placeable.place(0, 0)
                }
            }
            .clipToBounds()
            .graphicsLayer { alpha = fraction },
        contentAlignment = Alignment.TopCenter,
    ) {
        MaterialControlPanelContent(
            verticalPadding = verticalPadding,
            sliderStyle = sliderStyle,
            showTiles = showTiles,
            tile1 = tile1,
            tile2 = tile2,
            tileViewModels = tileViewModels,
            keyguardStateController = keyguardStateController,
            activityStarter = activityStarter,
        )
    }
}

@Composable
private fun MaterialControlPanelContent(
    verticalPadding: Dp,
    sliderStyle: Int,
    showTiles: Boolean,
    tile1: String,
    tile2: String,
    tileViewModels: List<TileViewModel>?,
    keyguardStateController: KeyguardStateController?,
    activityStarter: ActivityStarter?,
) {
    val spacing = 24.dp
    val totalWeight = 1.75f + 0.69f + 0.69f
    val musicWeight = 1.75f
    val gapCount = 2

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
    ) {
        if (showTiles) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTile(
                    tileSpec = tile1,
                    tileViewModels = tileViewModels,
                    activityStarter = activityStarter,
                    modifier = Modifier.weight(1f),
                )
                WidgetTile(
                    tileSpec = tile2,
                    tileViewModels = tileViewModels,
                    activityStarter = activityStarter,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val availableWidth = maxWidth - spacing * gapCount
            val musicWidth = availableWidth * musicWeight / totalWeight
            val rowHeight = (musicWidth * 0.80f).coerceAtLeast(112.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(musicWeight)
                        .fillMaxHeight(),
                ) {
                    MaterialMusicPlayer(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        keyguardStateController = keyguardStateController,
                        activityStarter = activityStarter,
                    )
                }

                MaterialVerticalBrightnessSlider(
                    modifier = Modifier
                        .weight(0.69f)
                        .fillMaxHeight()
                        .widthIn(max = 64.dp),
                    sliderStyle = sliderStyle,
                )

                MaterialVerticalVolumeSlider(
                    modifier = Modifier
                        .weight(0.69f)
                        .fillMaxHeight()
                        .widthIn(max = 64.dp),
                    sliderStyle = sliderStyle,
                )
            }
        }
    }
}

@Composable
private fun WidgetTile(
    tileSpec: String,
    tileViewModels: List<TileViewModel>?,
    activityStarter: ActivityStarter?,
    modifier: Modifier = Modifier,
) {
    val tileViewModel = remember(tileViewModels, tileSpec) {
        tileViewModels?.find {
            it.spec.spec == tileSpec ||
            (tileSpec == "internet" && (it.spec.spec == "wifi" || it.spec.spec == "cell")) ||
            (tileSpec == "wifi" && (it.spec.spec == "internet" || it.spec.spec == "cell")) ||
            (tileSpec == "cell" && (it.spec.spec == "internet" || it.spec.spec == "wifi"))
        }
    }

    if (tileViewModel != null) {
        DisposableEffect(tileViewModel) {
            val token = Any()
            tileViewModel.startListening(token)
            onDispose {
                tileViewModel.stopListening(token)
            }
        }

        val resources = LocalContext.current.resources
        val uiState by produceState(
            initialValue = tileViewModel.currentState.toUiState(resources),
            key1 = tileViewModel,
            key2 = resources,
        ) {
            tileViewModel.state.collect { value = it.toUiState(resources) }
        }
        val iconProvider by produceState(
            initialValue = tileViewModel.currentState.toIconProvider(),
            key1 = tileViewModel,
        ) {
            tileViewModel.state.collect { value = it.toIconProvider() }
        }

        val colors = TileDefaults.getColorForState(uiState = uiState, iconOnly = false)

        val context = LocalContext.current
        val onClick: () -> Unit = {
            val enabled: Boolean = Settings.Secure.getInt(context.contentResolver,
                Settings.Secure.QSTILE_REQUIRES_UNLOCKING, 1) == 1
            val keyguardController = try { com.android.systemui.Dependency.get(KeyguardStateController::class.java) } catch (_: Exception) { null }
            val starter = activityStarter ?: try { com.android.systemui.Dependency.get(ActivityStarter::class.java) } catch (_: Exception) { null }
            val isLocked = keyguardController != null && keyguardController.isMethodSecure && (keyguardController.isShowing || !keyguardController.isUnlocked)
            if (enabled && isLocked && starter != null) {
                starter.postQSRunnableDismissingKeyguard {
                    if (tileSpec == "internet" || tileSpec == "wifi" || tileSpec == "cell") {
                        context.sendBroadcast(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    } else {
                        tileViewModel.mainClick(null)
                    }
                }
            } else {
                if (tileSpec == "internet" || tileSpec == "wifi" || tileSpec == "cell") {
                    context.sendBroadcast(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                } else {
                    tileViewModel.mainClick(null)
                }
            }
        }

        WidgetTileLayout(
            label = uiState.label.toString(),
            secondaryLabel = uiState.secondaryLabel?.toString(),
            iconProvider = { iconProvider },
            colors = colors,
            onClick = onClick,
            onLongClick = { tileViewModel.settingsClick(null) },
            modifier = modifier,
        )
    } else {
        WidgetTileFallback(
            tileSpec = tileSpec,
            activityStarter = activityStarter,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetTileLayout(
    label: String,
    secondaryLabel: String?,
    iconProvider: () -> IconProvider,
    colors: TileColors,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val animatedBgColor by animateColorAsState(colors.background, label = "WidgetTileBg")
    val animatedLabelColor by animateColorAsState(colors.label, label = "WidgetTileLabel")
    val animatedSecondaryLabelColor by animateColorAsState(colors.secondaryLabel, label = "WidgetTileSecondary")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(animatedBgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            SmallTileContent(
                iconProvider = {
                    val icon = iconProvider().icon
                    if (icon is com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon) {
                        Icon.Resource(icon.resId, null)
                    } else if (icon != null) {
                        Icon.Loaded(icon.getDrawable(this), null)
                    } else {
                        Icon.Resource(R.drawable.ic_error_outline, null)
                    }
                },
                color = colors.icon,
                size = { 22.dp },
            )
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                color = animatedLabelColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!secondaryLabel.isNullOrEmpty()) {
                Text(
                    text = secondaryLabel,
                    color = animatedSecondaryLabelColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class FallbackTileState(
    val label: String,
    val secondaryLabel: String,
    val isActive: Boolean,
    val iconResId: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetTileFallback(
    tileSpec: String,
    activityStarter: ActivityStarter?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorScheme = LocalAndroidColorScheme.current

    var state by remember(tileSpec) {
        mutableStateOf(getFallbackState(tileSpec, context))
    }

    DisposableEffect(tileSpec, context) {
        val cr = context.contentResolver

        fun updateState() {
            state = getFallbackState(tileSpec, context)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                updateState()
            }
        }

        val filter = IntentFilter().apply {
            when (tileSpec) {
                "internet", "wifi", "cell" -> {
                    addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
                    addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                }
                "bt" -> {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                }
                "airplane" -> {
                    addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                }
                "dnd" -> {
                    addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                }
                "saver" -> {
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                }
            }
        }

        if (filter.countActions() > 0) {
            context.registerReceiver(receiver, filter)
        }

        var observer: ContentObserver? = null
        val uri = when (tileSpec) {
            "rotation" -> Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
            "airplane" -> Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON)
            "dnd" -> Settings.Global.getUriFor(Settings.Global.ZEN_MODE)
            else -> null
        }
        if (uri != null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    updateState()
                }
            }
            cr.registerContentObserver(uri, false, observer, UserHandle.USER_ALL)
        }

        var torchCallback: CameraManager.TorchCallback? = null
        var cameraManager: CameraManager? = null
        if (tileSpec == "flashlight") {
            try {
                cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                torchCallback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        state = state.copy(
                            secondaryLabel = if (enabled) "On" else "Off",
                            isActive = enabled,
                            iconResId = if (enabled) R.drawable.qs_flashlight_icon_off else R.drawable.qs_flashlight_icon_off,
                        )
                    }
                }
                cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {}
        }

        updateState()

        onDispose {
            if (filter.countActions() > 0) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
            if (observer != null) {
                try { cr.unregisterContentObserver(observer) } catch (_: Exception) {}
            }
            if (torchCallback != null && cameraManager != null) {
                try { cameraManager.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
            }
        }
    }

    val activeBg = MaterialTheme.colorScheme.primaryContainer
    val inactiveBg = colorScheme.surfaceEffect1
    val animatedBgColor by animateColorAsState(
        if (state.isActive) activeBg else inactiveBg,
        label = "WidgetFallbackBg",
    )

    val activeLabel = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveLabel = MaterialTheme.colorScheme.onSurface
    val animatedLabelColor by animateColorAsState(
        if (state.isActive) activeLabel else inactiveLabel,
        label = "WidgetFallbackLabel",
    )

    val activeSecLabel = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    val inactiveSecLabel = MaterialTheme.colorScheme.onSurfaceVariant
    val animatedSecondaryLabelColor by animateColorAsState(
        if (state.isActive) activeSecLabel else inactiveSecLabel,
        label = "WidgetFallbackSecLabel",
    )

    val activeIconBg = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val inactiveIconBg = colorScheme.surfaceEffect2
    val animatedIconBgColor by animateColorAsState(
        if (state.isActive) activeIconBg else inactiveIconBg,
        label = "WidgetFallbackIconBg",
    )

    val onClick: () -> Unit = {
        val enabled: Boolean = Settings.Secure.getInt(context.contentResolver,
            Settings.Secure.QSTILE_REQUIRES_UNLOCKING, 1) == 1
        val keyguardController = try { com.android.systemui.Dependency.get(KeyguardStateController::class.java) } catch (_: Exception) { null }
        val starter = activityStarter ?: try { com.android.systemui.Dependency.get(ActivityStarter::class.java) } catch (_: Exception) { null }
        val isLocked = keyguardController != null && keyguardController.isMethodSecure && (keyguardController.isShowing || !keyguardController.isUnlocked)
        if (enabled && isLocked && starter != null) {
            starter.postQSRunnableDismissingKeyguard {
                performFallbackClick(tileSpec, context, state, starter)
            }
        } else {
            performFallbackClick(tileSpec, context, state, starter)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(animatedBgColor)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(animatedIconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            SmallTileContent(
                iconProvider = { Icon.Resource(state.iconResId, null) },
                color = animatedLabelColor,
                size = { 22.dp },
            )
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = state.label,
                color = animatedLabelColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.secondaryLabel.isNotEmpty()) {
                Text(
                    text = state.secondaryLabel,
                    color = animatedSecondaryLabelColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun getFallbackState(tileSpec: String, context: Context): FallbackTileState {
    val cr = context.contentResolver
    return when (tileSpec) {
        "internet", "wifi", "cell" -> {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            var active = false
            var sec = "Disconnected"
            var icon = R.drawable.ic_qs_no_internet_available

            val activeNetwork = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    active = true
                    val wifiInfo = (capabilities.transportInfo as? WifiInfo) ?: wm?.connectionInfo
                    val rawSsid = wifiInfo?.ssid
                    val ssid = rawSsid?.replace("\"", "")
                    if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                        sec = ssid
                    } else {
                        sec = "Connected"
                    }
                    icon = R.drawable.ic_qs_no_internet_available
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    active = true
                    sec = "Mobile data"
                    icon = R.drawable.ic_qs_no_internet_available
                }
            } else if (wm?.isWifiEnabled == true) {
                sec = "On"
                icon = R.drawable.ic_qs_no_internet_available
            }
            val title = if (tileSpec == "wifi") "Wi-Fi" else if (tileSpec == "cell") "Mobile data" else "Internet"
            FallbackTileState(title, sec, active, icon)
        }
        "bt" -> {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            var active = false
            var sec = "Off"
            var icon = R.drawable.qs_bluetooth_icon_off
            if (adapter != null && adapter.isEnabled) {
                active = true
                icon = R.drawable.qs_bluetooth_icon_on
                val connectedDeviceName = try {
                    adapter.bondedDevices?.firstOrNull { device ->
                        try {
                            val method = device.javaClass.getMethod("isConnected")
                            method.invoke(device) as? Boolean == true
                        } catch (_: Exception) { false }
                    }?.let { dev ->
                        try {
                            val aliasMethod = dev.javaClass.getMethod("getAlias")
                            aliasMethod.invoke(dev) as? String ?: dev.name
                        } catch (_: Exception) { dev.name }
                    }
                } catch (_: Exception) { null }

                sec = connectedDeviceName ?: "On"
            }
            FallbackTileState("Bluetooth", sec, active, icon)
        }
        "flashlight" -> {
            FallbackTileState("Flashlight", "Off", false, R.drawable.qs_flashlight_icon_off)
        }
        "airplane" -> {
            val isOn = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            FallbackTileState(
                "Airplane mode",
                if (isOn) "On" else "Off",
                isOn,
                R.drawable.qs_airplane_icon_off
            )
        }
        "dnd" -> {
            val isOn = Settings.Global.getInt(cr, Settings.Global.ZEN_MODE, 0) != 0
            FallbackTileState(
                "Do Not Disturb",
                if (isOn) "On" else "Off",
                isOn,
                R.drawable.qs_dnd_icon_off
            )
        }
        "rotation" -> {
            val isOn = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
            FallbackTileState(
                "Auto-rotate",
                if (isOn) "Auto-rotate" else "Portrait",
                isOn,
                R.drawable.qs_auto_rotate_icon_off
            )
        }
        "saver" -> {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isOn = pm?.isPowerSaveMode == true
            FallbackTileState(
                "Data Saver",
                if (isOn) "On" else "Off",
                isOn,
                R.drawable.qs_data_saver_icon_off
            )
        }
        else -> FallbackTileState("Tile", "Quick toggle", false, R.drawable.ic_settings_24dp)
    }
}

private fun performFallbackClick(
    tileSpec: String,
    context: Context,
    state: FallbackTileState,
    activityStarter: ActivityStarter?,
) {
    val cr = context.contentResolver
    when (tileSpec) {
        "internet", "wifi", "cell" -> {
            context.sendBroadcast(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        }
        "bt" -> {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (activityStarter != null) {
                activityStarter.startActivity(intent, true)
            } else {
                context.startActivity(intent)
            }
        }
        "flashlight" -> {
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId != null) {
                    cm.setTorchMode(cameraId, !state.isActive)
                }
            } catch (_: Exception) {}
        }
        "airplane" -> {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (activityStarter != null) {
                activityStarter.startActivity(intent, true)
            } else {
                context.startActivity(intent)
            }
        }
        "dnd" -> {
            val newValue = if (state.isActive) 0 else 1
            Settings.Global.putInt(cr, Settings.Global.ZEN_MODE, newValue)
        }
        "rotation" -> {
            val newValue = if (state.isActive) 0 else 1
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, newValue)
        }
        "saver" -> {
            val intent = Intent(Settings.ACTION_DATA_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (activityStarter != null) {
                activityStarter.startActivity(intent, true)
            } else {
                context.startActivity(intent)
            }
        }
    }
}


