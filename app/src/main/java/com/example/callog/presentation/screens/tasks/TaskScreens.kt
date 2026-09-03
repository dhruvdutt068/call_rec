package com.example.callog.presentation.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CRM Tasks", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onCreateTaskClick) {
                        Icon(Icons.Default.Add, contentDescription = "Create Task", tint = Teal300)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTaskClick,
                containerColor = Teal300,
                contentColor = Slate900
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Pending", "Completed").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Teal300.copy(alpha = 0.2f),
                            selectedLabelColor = Teal300
                        )
                    )
                }
            }

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = "No Tasks",
                        description = "You are all caught up! Create a new task to stay organized.",
                        icon = Icons.Default.CheckCircle
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
    val priorityColor = when (task.priority) {
        "High" -> Red500
        "Medium" -> Amber500
        else -> Teal300
    }

    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleComplete) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle Complete",
                    tint = if (task.isCompleted) Teal300 else Slate400
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.isCompleted) Slate400 else Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(priorityColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.priority,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Due: ${task.dueDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = Slate400
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
                title = { Text("New Follow-up Task", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Teal300)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Linked Contact", style = MaterialTheme.typography.labelSmall, color = Slate400)
                            Text(initialContact.name, style = MaterialTheme.typography.titleSmall, color = Slate50)
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
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Add notes and details...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Text("Priority", style = MaterialTheme.typography.titleSmall, color = Slate300)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("High", "Medium", "Low").forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p) }
                    )
                }
            }

            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                label = { Text("Due Date & Time") },
                modifier = Modifier.fillMaxWidth(),
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
                    .height(50.dp),
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Slate900)
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
                title = { Text("Task Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("Task not found", color = Slate400)
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
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Slate50)
                        Text(task.description, style = MaterialTheme.typography.bodyMedium, color = Slate300)

                        HorizontalDivider(color = Slate700)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Priority", color = Slate400)
                            Text(task.priority, fontWeight = FontWeight.Bold, color = if (task.priority == "High") Red500 else Amber500)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Due Date", color = Slate400)
                            Text(task.dueDate, color = Slate50)
                        }

                        if (task.linkedContactName != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Linked Contact", color = Slate400)
                                Text(task.linkedContactName, fontWeight = FontWeight.SemiBold, color = Teal300)
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
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (task.isCompleted) Amber500 else Teal300,
                        contentColor = Slate900
                    )
                ) {
                    Text(if (task.isCompleted) "Mark as Incomplete" else "Mark as Completed", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
