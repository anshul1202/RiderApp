package com.example.riderapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.riderapp.ui.theme.StatusAmber
import com.example.riderapp.ui.theme.StatusGreen
import com.example.riderapp.ui.theme.StatusRed
import kotlinx.coroutines.delay

@Composable
fun SyncStatusBar(
    unsyncedCount: Int,
    isOnline: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showSyncedMessage = remember { mutableStateOf(false) }

    // Auto-dismiss "All synced" bar after 3 seconds
    LaunchedEffect(unsyncedCount, isOnline) {
        if (isOnline && unsyncedCount == 0) {
            showSyncedMessage.value = true
            delay(3000L)
            showSyncedMessage.value = false
        } else {
            showSyncedMessage.value = false
        }
    }

    val showBar = !isOnline || unsyncedCount > 0 || showSyncedMessage.value

    AnimatedVisibility(
        visible = showBar,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        when {
            !isOnline -> {
                SyncBarContent(
                    backgroundColor = StatusRed,
                    icon = Icons.Default.CloudOff,
                    text = "You're offline — changes saved locally",
                    onClick = null
                )
            }
            unsyncedCount > 0 -> {
                SyncBarContent(
                    backgroundColor = StatusAmber,
                    icon = Icons.Default.Sync,
                    text = "$unsyncedCount action${if (unsyncedCount != 1) "s" else ""} pending sync",
                    onClick = onSyncClick
                )
            }
            showSyncedMessage.value -> {
                SyncBarContent(
                    backgroundColor = StatusGreen,
                    icon = Icons.Default.CloudDone,
                    text = "All changes synced",
                    onClick = null
                )
            }
        }
    }
}

@Composable
private fun SyncBarContent(
    backgroundColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: (() -> Unit)?
) {
    Surface(
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
