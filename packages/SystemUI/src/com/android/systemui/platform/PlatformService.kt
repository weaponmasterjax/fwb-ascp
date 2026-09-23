/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.platform

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Service component declared in AndroidManifest for IPC binding from GameSpace.
 */
class PlatformBinderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = PlatformService.sBinder
}

@SysUISingleton
class PlatformService @Inject constructor(
    private val context: Context,
    @Main private val mainDispatcher: CoroutineDispatcher,
    @Application private val scope: CoroutineScope,
    private val controller: PlatformController
) : CoreStartable {

    private val callbacks = RemoteCallbackList<IPlatformCallback>()
    private val stateCache = ConcurrentHashMap<String, Bundle>()

    private val binderStub = object : IPlatformService.Stub() {
        override fun toggle(feature: String?) {
            enforceCaller()
            if (feature.isNullOrEmpty()) return
            scope.launch(mainDispatcher) { controller.toggle(feature) }
        }

        override fun setEnabled(feature: String?, enabled: Boolean) {
            enforceCaller()
            if (feature.isNullOrEmpty()) return
            scope.launch(mainDispatcher) { controller.setEnabled(feature, enabled) }
        }

        override fun setValue(feature: String?, value: Int) {
            enforceCaller()
            if (feature.isNullOrEmpty()) return
            scope.launch(mainDispatcher) { controller.setValue(feature, value) }
        }

        override fun performAction(feature: String?, param: String?) {
            enforceCaller()
            if (feature.isNullOrEmpty() || param.isNullOrEmpty()) return
            scope.launch(mainDispatcher) { controller.performAction(feature, param) }
        }

        override fun getState(feature: String?): Bundle =
            if (feature != null) stateCache[feature] ?: Bundle.EMPTY else Bundle.EMPTY

        override fun getAllStates(): Bundle = Bundle().apply {
            stateCache.forEach { (key, bundle) -> putBundle(key, bundle) }
        }

        override fun getSupportedFeatures(): Array<String> = controller.supportedFeatures

        override fun registerCallback(callback: IPlatformCallback?) {
            if (callback == null) return
            callbacks.register(callback)
            stateCache.forEach { (key, bundle) ->
                try {
                    callback.onStateChanged(key, bundle)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Failed initial dispatch for $key", e)
                }
            }
        }

        override fun unregisterCallback(callback: IPlatformCallback?) {
            if (callback != null) callbacks.unregister(callback)
        }
    }

    override fun start() {
        sBinder = binderStub
        controller.init(this)
        Log.i(TAG, "PlatformService started")
    }

    fun getState(key: String): Bundle = stateCache[key] ?: Bundle.EMPTY

    fun broadcastState(key: String, state: Bundle) {
        val normalized = Bundle(state)
        enrichBundle(key, normalized)
        val old = stateCache[key]
        if (old != null && bundlesEqual(old, normalized)) return
        stateCache[key] = normalized

        synchronized(callbacks) {
            val count = callbacks.beginBroadcast()
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onStateChanged(key, normalized)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Callback failed for key=$key", e)
                }
            }
            callbacks.finishBroadcast()
        }
    }

    fun broadcastBool(key: String, active: Boolean) {
        broadcastState(key, Bundle().apply {
            putBoolean("enabled", active)
            putBoolean("active", active)
        })
    }

    private fun enrichBundle(key: String, state: Bundle) {
        if (state === Bundle.EMPTY) return
        if (!state.containsKey("tileState")) {
            state.putInt("tileState", when {
                !state.getBoolean("available", true) -> PlatformClient.TILE_STATE_UNAVAILABLE
                state.getBoolean("active", false) -> PlatformClient.TILE_STATE_ACTIVE
                else -> PlatformClient.TILE_STATE_INACTIVE
            })
        }
        val label = controller.getLabel(key)
        if (label != null && !state.containsKey("label")) {
            state.putString("label", label)
        }
        val secondary = controller.getSecondaryLabel(key, state)
        if (secondary != null && !state.containsKey("secondaryLabel")) {
            state.putString("secondaryLabel", secondary)
        }
    }

    private fun bundlesEqual(a: Bundle, b: Bundle): Boolean {
        if (a.size() != b.size()) return false
        for (k in a.keySet()) {
            if (!b.containsKey(k)) return false
            val va = a.get(k)
            val vb = b.get(k)
            val eq = when {
                va is Bundle && vb is Bundle -> bundlesEqual(va, vb)
                va is IntArray && vb is IntArray -> va.contentEquals(vb)
                va is LongArray && vb is LongArray -> va.contentEquals(vb)
                va is BooleanArray && vb is BooleanArray -> va.contentEquals(vb)
                va is Array<*> && vb is Array<*> -> va.contentDeepEquals(vb)
                else -> va == vb
            }
            if (!eq) return false
        }
        return true
    }

    private fun enforceCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.SYSTEM_UID || uid == Process.myUid()) return
        val token = Binder.clearCallingIdentity()
        try {
            val pkgs = context.packageManager.getPackagesForUid(uid)
            val allowed = pkgs?.any {
                it == "com.android.systemui" || it == "io.chaldeaprjkt.gamespace"
            } == true
            if (!allowed) {
                throw SecurityException("PlatformService: caller uid=$uid not permitted")
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    companion object {
        private const val TAG = "PlatformService"
        @Volatile
        internal var sBinder: IBinder? = null
    }
}
