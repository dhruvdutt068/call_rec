package com.example.callog.presentation.screens.favorites

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@Composable
fun FavoritesScreen(
    viewModel: CallViewModel,
    onCallClick: (Long) -> Unit,
    onContactClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favoriteLogs by viewModel.favoriteLogs.collectAsState()

    // Group unique favorite contacts by phone number
    val uniqueFavorites = remember(favoriteLogs) {
        favoriteLogs.distinctBy { it.number }
    }

    fun makeCall(phone: String) {
        if (phone.isBlank()) return
        viewModel.initiateCall(context, phone.trim())
    }

    fun openWhatsApp(phone: String) {
        try {
            val cleanNumber = phone.replace("+", "").replace(" ", "").replace("-", "")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("FavoritesScreen", "Failed to open WhatsApp", e)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Starred & VIP Favorites",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "${uniqueFavorites.size} high-priority contacts for 1-tap calling",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
        }

        if (uniqueFavorites.isEmpty()) {
            EmptyStateView(
                title = "No Favorites Added",
                description = "Star contacts from Call Details, Contact profiles, or Post-Call summaries to access them quickly here.",
                icon = Icons.Outlined.Star,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(uniqueFavorites, key = { it.id }) { log ->
                    FavoriteContactCard(
                        log = log,
                        onCall = { makeCall(log.number) },
                        onWhatsApp = { openWhatsApp(log.number) },
                        onToggleFavorite = { viewModel.toggleFavorite(log.id) },
                        onItemClick = {
                            if (log.personId != null && onContactClick != null) {
                                onContactClick(log.personId)
                            } else {
                                onCallClick(log.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteContactCard(
    log: CallLogEntry,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onToggleFavorite: () -> Unit,
    onItemClick: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ContactAvatar(
                name = log.displayName,
                initials = log.initials,
                photoUri = log.contactPhotoUri,
                modifier = Modifier.size(48.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = log.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (log.tags.isNotEmpty()) {
                    Text(
                        text = log.tags.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = AllSetLavender,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = log.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }

            // Quick Actions: WhatsApp & Call & Unfavorite
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onWhatsApp,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Green500.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "WhatsApp",
                        tint = Green500,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onCall,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Teal500.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = Teal300,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Remove Favorite",
                        tint = Amber500,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}