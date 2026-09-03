package com.example.callog.presentation.navigation3.allset

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.screens.contacts.ContactDetailsScreen
import com.example.callog.presentation.screens.contacts.ContactsScreen
import com.example.callog.presentation.viewmodel.CallViewModel

/**
 * Adaptive UI Layout for Contacts.
 * Supports:
 * - Single-pane mode (phones): navigates standard stack push/pop.
 * - Dual-pane mode (tablets/foldables): displays Contact List and Contact Details side-by-side.
 */
@Composable
fun AdaptiveContactsLayout(
    viewModel: CallViewModel,
    isExpandedScreen: Boolean,
    selectedContactId: String?,
    onContactClick: (contactId: String) -> Unit,
    onScheduleMeetingClick: (contactId: String) -> Unit,
    onCreateTaskClick: (contactId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isExpandedScreen) {
        // Dual-pane layout: List on left, Details on right
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                ContactsScreen(
                    viewModel = viewModel,
                    onContactClick = onContactClick
                )
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 1.dp)

            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                if (selectedContactId != null) {
                    ContactDetailsScreen(
                        contactId = selectedContactId,
                        viewModel = viewModel,
                        onBackClick = {},
                        onScheduleMeetingClick = onScheduleMeetingClick,
                        onCreateTaskClick = onCreateTaskClick
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Select a contact to view details", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    } else {
        // Single-pane mode: renders list
        ContactsScreen(
            viewModel = viewModel,
            onContactClick = onContactClick,
            modifier = modifier
        )
    }
}
