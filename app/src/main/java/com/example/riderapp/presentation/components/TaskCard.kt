package com.example.riderapp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.riderapp.domain.model.*
import com.example.riderapp.ui.theme.*

fun getStatusColor(status: TaskStatus): Color {
    return when (status) {
        TaskStatus.ASSIGNED -> StatusGrey
        TaskStatus.REACHED -> StatusBlue
        TaskStatus.PICKED_UP -> StatusGreen
        TaskStatus.DELIVERED -> StatusGreen
        TaskStatus.FAILED_PICKUP -> StatusRed
        TaskStatus.FAILED_DELIVERY -> StatusRed
        TaskStatus.RETURNED -> StatusAmber
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Task ID + Type badge + Sync indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = task.id,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TaskTypeBadge(type = task.type)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (task.syncStatus == SyncStatus.PENDING || task.syncStatus == SyncStatus.FAILED) {
                        SyncIndicator(syncStatus = task.syncStatus)
                    }
                    Text(
                        text = formatRelativeTime(task.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Customer name
            Text(
                text = task.customerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            Text(
                text = task.address,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status badge
            StatusBadge(status = task.status)
        }
    }
}

@Composable
private fun TaskTypeBadge(type: TaskType) {
    val containerColor = when (type) {
        TaskType.PICKUP -> PickupBadgeContainer
        TaskType.DROP -> DropBadgeContainer
    }
    val contentColor = when (type) {
        TaskType.PICKUP -> PickupBadge
        TaskType.DROP -> DropBadge
    }
    val label = when (type) {
        TaskType.PICKUP -> "PICKUP"
        TaskType.DROP -> "DROP"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun StatusBadge(status: TaskStatus) {
    val color = getStatusColor(status)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = status.displayName,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun SyncIndicator(syncStatus: SyncStatus) {
    val icon = when (syncStatus) {
        SyncStatus.PENDING -> Icons.Default.SyncProblem
        SyncStatus.FAILED -> Icons.Default.CloudOff
        else -> return
    }
    val tint = when (syncStatus) {
        SyncStatus.PENDING -> StatusAmber
        SyncStatus.FAILED -> StatusRed
        else -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = "Sync ${syncStatus.name.lowercase()}",
        modifier = Modifier.size(16.dp),
        tint = tint
    )
}
