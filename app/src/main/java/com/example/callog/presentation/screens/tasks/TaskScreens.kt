package com.example.callog.presentation.screens.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.ExpressiveEmptyState
import com.example.callog.presentation.components.ExpressiveSegmentedButtonGroup
import com.example.callog.presentation.components.ExpressiveSegmentedButtonItem
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.components.PriorityPill
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

data class CrmTaskItem(
    val id: String,
    val title: String,
    val description: String,
    val priority: String, // "High", "Medium", "Low"
    val isCompleted: Boolean,
    val dueDate: String,
    val linkedContactName: String? = null,
    val linkedContactId: String? = null
)

// Sample in-memory state store for task demonstration (decoupled from nav)
object CrmTaskStore {
    val sampleTasks = mutableStateListOf(
        CrmTaskItem(
            id = "task-1",
            title = "Follow up with client regarding proposal",
            description = "Discuss discount tier and finalize annual billing terms.",
            priority = "High",
            isCompleted = false,
            dueDate = "Tomorrow, 2:00 PM",
            linkedContactName = "John Doe",
            linkedContactId = "1"
        ),
        CrmTaskItem(
            id = "task-2",
            title = "Send recording transcript",
            description = "Email audio transcript from discovery call.",
            priority = "Medium",
            isCompleted = false,
            dueDate = "Friday, 5:00 PM",
            linkedContactName = "Sarah Connor",
            linkedContactId = "2"
        ),
        CrmTaskItem(
            id = "task-3",
            title = "Review Q3 sales call logs",
            description = "Audit missed calls and update CRM tags.",
            priority = "Low",
            isCompleted = true,
            dueDate = "Yesterday",
            linkedContactName = null,
            linkedContactId = null
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onCreateTaskClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks = CrmTaskStore.sampleTasks
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            "Pending" -> tasks.filter { !it.isCompleted }
            "Completed" -> tasks.filter { it.isCompleted }
            else -> tasks
        }
    }

    val pendingCount = remember(tasks) { tasks.count { !it.isCompleted } }
    val completedCount = remember(tasks) { tasks.count { it.isCompleted } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CRM Tasks",
                        style = CallogTypography.sectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onCreateTaskClick,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Task",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTaskClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CallogShapes.interactive
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Expressive Segmented Button Group
            val filterItems = listOf(
                ExpressiveSegmentedButtonItem(key = "All", label = "All", count = tasks.size),
                ExpressiveSegmentedButtonItem(key = "Pending", label = "Pending", count = pendingCount),
                ExpressiveSegmentedButtonItem(key = "Completed", label = "Done", count = completedCount)
            )
            ExpressiveSegmentedButtonGroup(
                items = filterItems,
                selectedKey = selectedFilter,
                onItemSelected = { selectedFilter = it },
                modifier = Modifier.padding(top = 8.dp)
            )

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveEmptyState(
                        title = if (selectedFilter == "Completed") "No Completed Tasks" else "No Tasks",
                        description = if (selectedFilter == "Completed") "Completed tasks will be recorded here." else "You are all caught up! Create a new task to stay organized.",
                        icon = Icons.Outlined.CheckCircle,
                        actionLabel = if (selectedFilter != "Completed") "Create Task" else null,
                        onActionClick = onCreateTaskClick
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleComplete = {
                                val idx = CrmTaskStore.sampleTasks.indexOfFirst { it.id == task.id }
                                if (idx != -1) {
                                    CrmTaskStore.sampleTasks[idx] = task.copy(isCompleted = !task.isCompleted)
                                }
                            },
                            onClick = { onTaskClick(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: CrmTaskItem,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checkInteraction = remember { MutableInteractionSource() }
    val checkPressed by checkInteraction.collectIsPressedAsState()
    val checkScale by animateFloatAsState(
        targetValue = if (checkPressed) 0.85f else 1f,
        animationSpec = CallogMotion.bouncySpring(),
        label = "checkScale"
    )

    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleComplete,
                interactionSource = checkInteraction,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .scale(checkScale)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Mark incomplete" else "Mark complete",
                    tint = if (task.isCompleted) CallogSemanticColors.SyncSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = CallogTypography.entityName,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityPill(priority = task.priority)
                    Text(
                        text = "Due: ${task.dueDate}",
                        style = CallogTypography.denseData,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    initialContactId: String?,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onTaskCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.contacts.collectAsState()
    val initialContact = remember(contacts, initialContactId) {
        contacts.find { it.contactId == initialContactId }
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("High") }
    var dueDate by remember { mutableStateOf("Tomorrow, 10:00 AM") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New Follow-up Task",
                        style = CallogTypography.sectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (initialContact != null) {
                GlassyCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CallogShapes.avatar,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Linked Contact",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = initialContact.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title") },
                placeholder = { Text("e.g., Call back about contract terms") },
                modifier = Modifier.fillMaxWidth(),
                shape = CallogShapes.card,
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Add notes and details...") },
                modifier = Modifier.fillMaxWidth(),
                shape = CallogShapes.card,
                minLines = 3
            )

            Text(
                text = "Priority Level",
                style = CallogTypography.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            val priorityOptions = listOf("High", "Medium", "Low")
            val priorityItems = priorityOptions.map { ExpressiveSegmentedButtonItem(key = it, label = it) }
            ExpressiveSegmentedButtonGroup(
                items = priorityItems,
                selectedKey = priority,
                onItemSelected = { priority = it }
            )

            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                label = { Text("Due Date & Time") },
                modifier = Modifier.fillMaxWidth(),
                shape = CallogShapes.card,
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        CrmTaskStore.sampleTasks.add(
                            0,
                            CrmTaskItem(
                                id = "task-${System.currentTimeMillis()}",
                                title = title.trim(),
                                description = description.trim(),
                                priority = priority,
                                isCompleted = false,
                                dueDate = dueDate,
                                linkedContactName = initialContact?.name,
                                linkedContactId = initialContact?.contactId
                            )
                        )
                        onTaskCreated()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = title.isNotBlank(),
                shape = CallogShapes.interactive
            ) {
                Text("Save Task", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsScreen(
    taskId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val task = CrmTaskStore.sampleTasks.find { it.id == taskId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Task Details",
                        style = CallogTypography.sectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (task == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    title = "Task Not Found",
                    description = "This task may have been removed or does not exist.",
                    icon = Icons.Outlined.CheckCircle,
                    actionLabel = "Go Back",
                    onActionClick = onBackClick
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = task.title,
                            style = CallogTypography.heroTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Priority",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PriorityPill(priority = task.priority)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Due Date",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = task.dueDate,
                                style = CallogTypography.denseData,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (task.linkedContactName != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Linked Contact",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = task.linkedContactName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val idx = CrmTaskStore.sampleTasks.indexOfFirst { it.id == task.id }
                        if (idx != -1) {
                            CrmTaskStore.sampleTasks[idx] = task.copy(isCompleted = !task.isCompleted)
                        }
                        onBackClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = CallogShapes.interactive,
                    colors = if (task.isCompleted) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(
                        text = if (task.isCompleted) "Mark as Incomplete" else "Mark as Completed",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

