/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.compose

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.shared.*
import com.android.systemui.res.R

private fun resolveRemoteViews(ctx: Context, notification: Notification): RemoteViews? {
    notification.bigContentView?.let {
        return it
    }
    notification.contentView?.let {
        return it
    }
    try {
        val builder = Notification.Builder.recoverBuilder(ctx, notification)
        builder.createBigContentView()?.let {
            return it
        }
        builder.createContentView()?.let {
            return it
        }
    } catch (_: Exception) {}
    return null
}

private fun applyOrReapplyRemoteViews(frame: FrameLayout, notification: Notification): Boolean {
    val rv =
        try {
            resolveRemoteViews(frame.context, notification)
        } catch (_: Exception) {
            return false
        } ?: return false
    val existing = if (frame.childCount > 0) frame.getChildAt(0) else null
    if (existing != null) {
        try {
            rv.reapply(frame.context, existing)
            prepareForIsland(existing)
            return true
        } catch (_: Exception) {
            frame.removeAllViews()
        }
    }
    return try {
        val inflated = rv.apply(frame.context, frame)
        prepareForIsland(inflated)
        frame.addView(
            inflated,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun SbnContentView(sbn: StatusBarNotification, fallback: @Composable () -> Unit) {
    var failed by remember(sbn.key) { mutableStateOf(false) }
    if (failed) {
        fallback()
        return
    }
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { frame ->
            val notification = sbn.notification
            if (notification == null || !applyOrReapplyRemoteViews(frame, notification)) {
                failed = true
            }
        },
        modifier = Modifier.fillMaxWidth().clip(ShapeIconMedium),
    )
}

private val COLLAPSE_CHIP_IDS = arrayOf("expand_button_touch_container", "expand_button")

private fun prepareForIsland(root: View) {
    val res = root.resources

    for (name in COLLAPSE_CHIP_IDS) {
        val id = res.getIdentifier(name, "id", "android")
        if (id != 0) root.findViewById<View>(id)?.visibility = View.GONE
    }
    hideCollapseButtons(root)
}

private val COLLAPSE_LABELS = setOf("collapse", "expand", "minimize")

private fun hideCollapseButtons(view: View) {
    if (view is Button) {
        val text = view.text?.toString()?.lowercase() ?: ""
        if (COLLAPSE_LABELS.any { text.contains(it) }) {
            view.visibility = View.GONE
        }
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            hideCollapseButtons(view.getChildAt(i))
        }
    }
}

@Composable
internal fun PromotedOngoingExpanded(
    event: IslandEvent.PromotedOngoing,
    interactor: IslandActions,
) {
    val context = LocalContext.current
    val percentText = when {
        event.progress in 0f..1f -> "${(event.progress * 100).toInt()}%"
        event.shortText.contains("%") -> event.shortText
        else -> null
    }

    PopupSurface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        modifier =
            Modifier.fillMaxWidth().clickable {
                try {
                    event.sbn.notification?.contentIntent?.sendWithBal(context)
                } catch (_: Exception) {}
                interactor.collapseIsland()
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: App icon, App name, and Percentage Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val appIcon = event.appIcon
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.toScaledBitmap(28.dp),
                        contentDescription = event.appName,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF007AFF).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.appName.ifEmpty { "Downloading" },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (percentText != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF007AFF).copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007AFF).copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = percentText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF4DA6FF),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Title & Progress Text Info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val displayTitle = event.title.ifEmpty { event.shortText.ifEmpty { event.appName } }
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (event.text.isNotEmpty()) {
                    Text(
                        text = event.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Progress Bar
            if (event.progress >= 0f || event.isIndeterminate) {
                val progressValue = if (event.isIndeterminate) 0f else event.progress.coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(if (event.isIndeterminate) 0.3f else progressValue)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF007AFF), Color(0xFF4DA6FF))
                                    )
                                ),
                    )
                }
            }

            // Action Buttons (e.g. Pause, Cancel, Resume)
            val usableActions =
                event.actions.filter { action ->
                    val label = action.label.toString().lowercase()
                    label != "collapse" && label != "expand" && label != "minimize"
                }

            if (usableActions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    usableActions.take(3).forEach { notifAction ->
                        val label = notifAction.label.toString()
                        val isCancel = label.lowercase().contains("cancel") || label.lowercase().contains("stop")

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCancel) MaterialTheme.colorScheme.error.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCancel) MaterialTheme.colorScheme.error.copy(alpha = 0.32f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                ),
                            modifier =
                                Modifier.weight(1f)
                                    .height(38.dp)
                                    .clickable {
                                        try {
                                            notifAction.action.actionIntent?.sendWithBal(context)
                                        } catch (_: Exception) {}
                                        interactor.collapseIsland()
                                    },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isCancel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RowScope.CompactPromotedOngoingRow(event: IslandEvent.PromotedOngoing) {
    event.appIcon?.let { icon ->
        Image(
            bitmap = icon.toScaledBitmap(SizeCompactIcon),
            contentDescription = null,
            modifier = Modifier.size(SizeCompactIcon).clip(ShapeCompact),
            contentScale = ContentScale.Crop,
        )
    }
        ?: Box(
            modifier =
                Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(BlueAccent.copy(alpha = AlphaIconBg)),
            contentAlignment = Alignment.Center,
        ) {
            PulsingDot(color = BlueAccent, size = SpaceMd)
        }
    Spacer(Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(
            event.title.ifEmpty { event.appName },
            color = OnCardText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subLabel = event.shortText.ifEmpty {
            if ((event.progress >= 0f || event.isIndeterminate) && event.text.isNotEmpty()) event.text
            else ""
        }
        if (subLabel.isNotEmpty()) {
            Text(subLabel, color = BlueAccent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

