package com.resdownload.android.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ExpressiveSnackbarMaxWidth = 320.dp
private val ExpressiveSnackbarFabCenterOffset = 48.dp

@Composable
fun ExpressiveSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Scaffold places the 64.dp collapsed dock 16.dp above the bottom bar.
    Box(
        modifier = modifier
            .height(ExpressiveSnackbarFabCenterOffset * 2)
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        SnackbarHost(hostState = hostState) { data ->
            ExpressiveSnackbar(data)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = ExpressiveSnackbarMaxWidth)
            .defaultMinSize(minHeight = 52.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("expressiveSnackbar"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            data.visuals.actionLabel?.let { actionLabel ->
                TextButton(
                    onClick = data::performAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(actionLabel)
                }
            }
            if (data.visuals.withDismissAction) {
                IconButton(
                    onClick = data::dismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭通知",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
