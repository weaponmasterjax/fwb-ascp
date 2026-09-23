/*
 * SPDX-FileCopyrightText: 2026 ASCP OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.smartpixel

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject

@SysUISingleton
class SmartPixelOverlay @Inject constructor(
    @Application private val context: Context,
    private val windowManager: WindowManager,
    private val secureSettings: SecureSettings,
    @Main private val mainHandler: Handler,
) : CoreStartable {

    private var filterView: SmartPixelView? = null
    private var isEnabled = false
    private var percent = DEFAULT_PERCENT
    private var isUdfpsPaused = false

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            updateSettings()
        }
    }

    override fun start() {
        instance = this
        secureSettings.registerContentObserverForUserSync(
            KEY_ENABLED, false, settingsObserver, UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_PERCENT, false, settingsObserver, UserHandle.USER_ALL,
        )
        updateSettings()
    }

    private fun updateSettings() {
        isEnabled = secureSettings.getIntForUser(KEY_ENABLED, 0, UserHandle.USER_CURRENT) == 1
        percent = secureSettings.getIntForUser(KEY_PERCENT, DEFAULT_PERCENT, UserHandle.USER_CURRENT)
            .coerceIn(MIN_PERCENT, MAX_PERCENT)
        applyOverlayState()
    }

    private fun setUdfpsPausedInternal(paused: Boolean) {
        if (isUdfpsPaused == paused) return
        isUdfpsPaused = paused
        applyOverlayState()
    }

    private fun applyOverlayState() {
        val shouldShow = isEnabled && !isUdfpsPaused
        if (shouldShow) {
            if (filterView == null) {
                val view = SmartPixelView(context)
                val params = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                    LayoutParams.FLAG_NOT_FOCUSABLE or
                        LayoutParams.FLAG_NOT_TOUCHABLE or
                        LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    title = "SmartPixelFilter"
                    layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    privateFlags = privateFlags or LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
                }
                windowManager.addView(view, params)
                filterView = view
            }
            filterView?.updatePattern(percent)
        } else {
            filterView?.let {
                windowManager.removeViewImmediate(it)
                filterView = null
            }
        }
    }

    private class SmartPixelView(context: Context) : View(context) {
        private val patternPaint = Paint()
        private var patternBitmap: Bitmap? = null
        private var currentPercent = -1

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        fun updatePattern(percent: Int) {
            if (currentPercent == percent && patternBitmap != null) return
            currentPercent = percent

            val threshold = (TOTAL_PIXELS * percent / 100f).toInt().coerceIn(1, TOTAL_PIXELS - 1)
            val bmp = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
            for (y in 0 until TILE_SIZE) {
                for (x in 0 until TILE_SIZE) {
                    if (BAYER_MATRIX[y * TILE_SIZE + x] < threshold) {
                        bmp.setPixel(x, y, Color.BLACK)
                    }
                }
            }

            patternBitmap?.recycle()
            patternBitmap = bmp
            patternPaint.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (patternBitmap != null) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), patternPaint)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            patternBitmap?.recycle()
            patternBitmap = null
        }
    }

    companion object {
        const val KEY_ENABLED = Settings.Secure.SMART_PIXEL_FILTER_ENABLED
        const val KEY_PERCENT = Settings.Secure.SMART_PIXEL_FILTER_PERCENT

        const val DEFAULT_PERCENT = 25
        const val MIN_PERCENT = 10
        const val MAX_PERCENT = 75

        private const val TILE_SIZE = 8
        private const val TOTAL_PIXELS = TILE_SIZE * TILE_SIZE

        private val BAYER_MATRIX = intArrayOf(
             0, 32,  8, 40,  2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44,  4, 36, 14, 46,  6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
             3, 35, 11, 43,  1, 33,  9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47,  7, 39, 13, 45,  5, 37,
            63, 31, 55, 23, 61, 29, 53, 21,
        )

        @Volatile
        private var instance: SmartPixelOverlay? = null

        @JvmStatic
        fun setUdfpsPaused(paused: Boolean) {
            instance?.let { overlay ->
                overlay.mainHandler.post {
                    overlay.setUdfpsPausedInternal(paused)
                }
            }
        }
    }
}
