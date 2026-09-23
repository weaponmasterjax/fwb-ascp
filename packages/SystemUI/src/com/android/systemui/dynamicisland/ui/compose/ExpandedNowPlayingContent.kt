/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.dynamicisland.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.dynamicisland.model.IslandEvent
import com.android.systemui.dynamicisland.shared.ActionChip
import com.android.systemui.dynamicisland.shared.ExpandedCardLayout
import com.android.systemui.dynamicisland.shared.IslandActions
import com.android.systemui.dynamicisland.shared.MintAccent
import com.android.systemui.dynamicisland.shared.OnActionText
import com.android.systemui.dynamicisland.shared.OnCardSecondary
import com.android.systemui.dynamicisland.shared.OnCardText
import com.android.systemui.dynamicisland.shared.SpaceLg
import com.android.systemui.dynamicisland.shared.sendWithBal
import com.android.systemui.res.R

@Composable
internal fun NowPlayingExpanded(event: IslandEvent.NowPlaying, interactor: IslandActions) {
    val context = LocalContext.current
    ExpandedCardLayout(
        accentColor = MintAccent,
        icon = {
            Icon(Icons.Filled.MusicNote, null, tint = MintAccent, modifier = Modifier.size(30.dp))
        },
        title = {
            Text(
                event.songTitle,
                color = OnCardText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.artist.isNotEmpty()) {
                Text(
                    event.artist,
                    color = OnCardSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing = {
            Text(
                stringResource(R.string.dynamic_island_now_playing),
                color = MintAccent,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        actions = if (event.actions.isNotEmpty()) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpaceLg),
                ) {
                    event.actions.forEach { notifAction ->
                        ActionChip(
                            label = notifAction.label.toString(),
                            color = OnActionText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                try {
                                    notifAction.action.actionIntent?.sendWithBal(context)
                                } catch (_: Exception) {}
                                interactor.collapseIsland()
                            },
                        )
                    }
                }
            }
        } else null,
    )
}
