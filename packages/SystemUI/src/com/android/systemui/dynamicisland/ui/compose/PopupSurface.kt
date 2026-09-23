/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dynamicisland.ui.compose

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import com.android.internal.R as InternalR
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_BLUR
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_SOLID_BLACK
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.observeDynamicIslandPopupColorMode
import com.android.systemui.dynamicisland.shared.DynamicIslandFeatureSettings.readDynamicIslandPopupColorMode

private class PopupBlurHost(
    context: Context,
    private val cornerRadiusPx: Float,
    private val blurRadiusPx: Int = (34 * context.resources.displayMetrics.density).toInt(),
) : View(context) {

    private var blurDrawable: BackgroundBlurDrawable? = null
    private val overlayColor: Int = run {
        val isDark =
            (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val baseColor =
            if (isDark) {
                try {
                    context.getColor(android.R.color.system_neutral1_900)
                } catch (_: Exception) {
                    context.getColor(InternalR.color.customColorSurfaceEffect0)
                }
            } else {
                try {
                    context.getColor(android.R.color.system_neutral1_50)
                } catch (_: Exception) {
                    context.getColor(InternalR.color.customColorSurfaceEffect0)
                }
            }
        ColorUtils.setAlphaComponent(baseColor, (0.40f * 255).toInt())
    }

    private val bgDrawable: GradientDrawable =
        GradientDrawable().also { d ->
            d.setColor(0x00000000)
            d.cornerRadius = cornerRadiusPx
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? ViewGroup)?.layoutTransition = null
        if (blurDrawable == null) {
            blurDrawable =
                viewRootImpl?.createBackgroundBlurDrawable()?.apply {
                    setBlurRadius(blurRadiusPx)
                    setCornerRadius(cornerRadiusPx)
                    setCallback(this@PopupBlurHost)
                    setVisible(true, false)
                }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        blurDrawable?.apply {
            setVisible(false, false)
            setBlurRadius(0)
            callback = null
        }
        blurDrawable = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        blurDrawable?.setVisible(isVisible, false)
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        who === blurDrawable || super.verifyDrawable(who)

    override fun draw(canvas: Canvas) {
        if (width > 0 && height > 0) {
            bgDrawable.setBounds(0, 0, width, height)
            val blur = blurDrawable
            if (blur != null) {
                blur.setBounds(0, 0, width, height)
                blur.setBlurRadius(blurRadiusPx)
                blur.setCornerRadius(cornerRadiusPx)
                blur.draw(canvas)
                bgDrawable.setColor(overlayColor)
                bgDrawable.draw(canvas)
                bgDrawable.setColor(0x00000000)
            } else {
                // Fallback if blur is unavailable
                bgDrawable.setColor(overlayColor and 0x00FFFFFF or (0xCC shl 24))
                bgDrawable.draw(canvas)
                bgDrawable.setColor(0x00000000)
            }
        }
    }
}

@Composable
fun rememberPopupColorMode(): Int {
    val context = LocalContext.current
    val initial = remember(context) { context.contentResolver.readDynamicIslandPopupColorMode() }
    val flow = remember(context) { observeDynamicIslandPopupColorMode(context) }
    val mode by flow.collectAsState(initial = initial)
    return mode
}

@Composable
fun PopupSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 12.dp,
    colorMode: Int = rememberPopupColorMode(),
    content: @Composable () -> Unit,
) {
    when (colorMode) {
        POPUP_COLOR_MODE_BLUR -> {
            val density = LocalDensity.current
            Box(
                modifier =
                    modifier
                        .clip(shape)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                AndroidView(
                    factory = { ctx ->
                        val radiusPx = with(density) { 32.dp.toPx() }
                        PopupBlurHost(ctx, radiusPx).also {
                            it.layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                        }
                    },
                    modifier = Modifier.matchParentSize(),
                )
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    content()
                }
            }
        }
        POPUP_COLOR_MODE_SOLID_BLACK -> {
            Surface(
                color = Color.Black,
                contentColor = Color.White,
                shape = shape,
                shadowElevation = shadowElevation,
                modifier = modifier,
            ) {
                content()
            }
        }
        else -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = shape,
                shadowElevation = shadowElevation,
                modifier = modifier,
            ) {
                content()
            }
        }
    }
}
