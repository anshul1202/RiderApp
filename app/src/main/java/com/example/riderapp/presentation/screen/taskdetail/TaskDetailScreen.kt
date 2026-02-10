package com.example.riderapp.presentation.screen.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.riderapp.domain.model.*
import com.example.riderapp.presentation.components.formatRelativeTime
import com.example.riderapp.presentation.components.getStatusColor
import com.example.riderapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFailNotesDialog by remember { mutableStateOf<ActionType?>(null) }

    // Show success message
    LaunchedEffect(uiState.actionSuccess) {
        uiState.actionSuccess?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Show error message
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.task?.id ?: "Task Detail"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.task == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        FilledTonalButton(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
            uiState.task != null -> {
                val task = uiState.task!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Task Info Card
                    item {
                        TaskInfoCard(task = task)
                    }

                    // Action History Section
                    item {
                        Text(
                            text = "Action History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (uiState.actions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = "No actions performed yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = uiState.actions,
                            key = { _, action -> action.id }
                        ) { index, action ->
                            ActionTimelineItem(
                                action = action,
                                isLast = index == uiState.actions.lastIndex
                            )
                        }
                    }

                    // Available Actions Section
                    item {
                        Text(
                            text = "Available Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (uiState.availableActions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = "All actions completed for this task",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        item {
                            ActionButtonsGrid(
                                availableActions = uiState.availableActions,
                                actionInProgress = uiState.actionInProgress,
                                onActionClick = { actionType ->
                                    if (actionType == ActionType.FAIL_PICKUP || actionType == ActionType.FAIL_DELIVERY) {
                                        showFailNotesDialog = actionType
                                    } else {
                                        viewModel.performAction(actionType)
                                    }
                                }
                            )
                        }
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Fail notes dialog
    showFailNotesDialog?.let { actionType ->
        FailNotesDialog(
            actionType = actionType,
            onDismiss = { showFailNotesDialog = null },
            onConfirm = { notes ->
                viewModel.performAction(actionType, notes)
                showFailNotesDialog = null
            }
        )
    }
}

@Composable
private fun TaskInfoCard(task: Task) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Type and Status badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                val typeContainerColor = when (task.type) {
                    TaskType.PICKUP -> PickupBadgeContainer
                    TaskType.DROP -> DropBadgeContainer
                }
                val typeContentColor = when (task.type) {
                    TaskType.PICKUP -> PickupBadge
                    TaskType.DROP -> DropBadge
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = typeContainerColor
                ) {
                    Text(
                        text = task.type.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = typeContentColor
                    )
                }

                // Status badge
                val statusColor = getStatusColor(task.status)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = task.status.displayName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            HorizontalDivider()

            // Customer info
            DetailRow(label = "Customer", value = task.customerName)
            DetailRow(label = "Phone", value = task.customerPhone)
            DetailRow(label = "Address", value = task.address)

            if (task.description.isNotBlank()) {
                DetailRow(label = "Description", value = task.description)
            }

            DetailRow(
                label = "Created",
                value = formatTimestamp(task.createdAt)
            )

            // Sync status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Sync:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val syncIcon = when (task.syncStatus) {
                    SyncStatus.SYNCED -> Icons.Default.CloudDone
                    SyncStatus.PENDING -> Icons.Default.SyncProblem
                    SyncStatus.FAILED -> Icons.Default.CloudOff
                }
                val syncColor = when (task.syncStatus) {
                    SyncStatus.SYNCED -> StatusGreen
                    SyncStatus.PENDING -> StatusAmber
                    SyncStatus.FAILED -> StatusRed
                }
                Icon(
                    imageVector = syncIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = syncColor
                )
                Text(
                    text = task.syncStatus.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = syncColor
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionTimelineItem(
    action: TaskAction,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        if (action.isSynced) MaterialTheme.colorScheme.primary
                        else StatusAmber
                    )
            )
            // Vertical line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        // Action content
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.actionType.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatTimestamp(action.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!action.notes.isNullOrBlank()) {
                        Text(
                            text = action.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Sync indicator
                Icon(
                    imageVector = if (action.isSynced) Icons.Default.CloudDone else Icons.Default.SyncProblem,
                    contentDescription = if (action.isSynced) "Synced" else "Pending sync",
                    modifier = Modifier.size(16.dp),
                    tint = if (action.isSynced) StatusGreen else StatusAmber
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsGrid(
    availableActions: List<ActionType>,
    actionInProgress: Boolean,
    onActionClick: (ActionType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availableActions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowActions.forEach { actionType ->
                    val buttonColor = getActionColor(actionType)
                    FilledTonalButton(
                        onClick = { onActionClick(actionType) },
                        enabled = !actionInProgress,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = buttonColor.copy(alpha = 0.15f),
                            contentColor = buttonColor
                        )
                    ) {
                        if (actionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = actionType.displayName,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                // Fill remaining space if odd number of actions
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FailNotesDialog(
    actionType: ActionType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = actionType.displayName,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Please provide a reason for this failure:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(notes.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusRed
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getActionColor(actionType: ActionType): Color {
    return when (actionType) {
        ActionType.REACH -> StatusBlue
        ActionType.PICK_UP -> StatusGreen
        ActionType.DELIVER -> StatusGreen
        ActionType.FAIL_PICKUP -> StatusRed
        ActionType.FAIL_DELIVERY -> StatusRed
        ActionType.RETURN -> StatusAmber
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
