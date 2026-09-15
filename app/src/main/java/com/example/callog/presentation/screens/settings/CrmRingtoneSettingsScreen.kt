package com.example.callog.presentation.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.repository.RingtoneRepository
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*

enum class RingtoneTarget(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    HOT("Hot Lead Ringtone", "Ringtone played for high-intent, ready-to-buy leads.", Icons.Default.LocalFireDepartment, Color(0xFFE85C5C)),
    WARM("Warm Lead Ringtone", "Ringtone played for engaged prospects & active discussions.", Icons.Default.Bolt, Color(0xFFF4B544)),
    COLD("Cold Lead Ringtone", "Ringtone played for new prospects & cold outreach calls.", Icons.Default.AcUnit, Color(0xFF4A4FD8)),
    CUSTOMER("Customer / VIP Ringtone", "Ringtone played for active customers & enterprise accounts.", Icons.Default.WorkspacePremium, Color(0xFF45C79A)),
    DEFAULT("Default Phone Ringtone", "Fallback ringtone for uncategorized or system numbers.", Icons.Default.NotificationsActive, Color(0xFF94A3B8))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmRingtoneSettingsScreen(
    ringtoneRepository: RingtoneRepository,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Local mutable states for the active URIs
    var hotUri by remember { mutableStateOf(ringtoneRepository.hotLead()) }
    var warmUri by remember { mutableStateOf(ringtoneRepository.warmLead()) }
    var coldUri by remember { mutableStateOf(ringtoneRepository.coldLead()) }
    var customerUri by remember { mutableStateOf(ringtoneRepository.customer()) }

    var activePickerTarget by remember { mutableStateOf<RingtoneTarget?>(null) }
    var playingTarget by remember { mutableStateOf<RingtoneTarget?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        playingTarget = null
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
        }
    }

    fun playAudio(target: RingtoneTarget, uri: Uri?) {
        stopAudio()
        val targetUri = uri ?: ringtoneRepository.default()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                setDataSource(context, targetUri)
                prepare()
                start()
                setOnCompletionListener {
                    stopAudio()
                }
            }
            mediaPlayer = player
            playingTarget = target
        } catch (e: Exception) {
            stopAudio()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val chosenUri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            when (activePickerTarget) {
                RingtoneTarget.HOT -> {
                    ringtoneRepository.setRingtoneForLeadHot(chosenUri)
                    hotUri = chosenUri
                }
                RingtoneTarget.WARM -> {
                    ringtoneRepository.setRingtoneForLeadWarm(chosenUri)
                    warmUri = chosenUri
                }
                RingtoneTarget.COLD -> {
                    ringtoneRepository.setRingtoneForLeadCold(chosenUri)
                    coldUri = chosenUri
                }
                RingtoneTarget.CUSTOMER -> {
                    ringtoneRepository.setRingtoneForCustomer(chosenUri)
                    customerUri = chosenUri
                }
                else -> {}
            }
        }
        activePickerTarget = null
    }

    fun launchRingtonePicker(target: RingtoneTarget, currentUri: Uri?) {
        activePickerTarget = target
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select ${target.title}")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri ?: ringtoneRepository.default())
        }
        pickerLauncher.launch(intent)
    }

    fun getRingtoneTitle(uri: Uri?): String {
        if (uri == null) return "System Default"
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Custom Audio"
        } catch (e: Exception) {
            "Custom Sound"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CRM Ringtone Policy",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Banner
            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AllSetBlue, AllSetLavender)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Lead Ringtone Automation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Callog plays distinct sounds so you instantly recognize Hot, Warm, and Customer calls before answering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Text(
                text = "Lead Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50,
                modifier = Modifier.padding(top = 8.dp)
            )

            // 1. Hot Leads Card
            RingtoneCategoryCard(
                target = RingtoneTarget.HOT,
                ringtoneTitle = getRingtoneTitle(hotUri),
                isPlaying = playingTarget == RingtoneTarget.HOT,
                onPlayToggle = {
                    if (playingTarget == RingtoneTarget.HOT) stopAudio()
                    else playAudio(RingtoneTarget.HOT, hotUri)
                },
                onChangeRingtone = { launchRingtonePicker(RingtoneTarget.HOT, hotUri) },
                onResetToDefault = {
                    ringtoneRepository.setRingtoneForLeadHot(null)
                    hotUri = null
                },
                isCustomSet = hotUri != null
            )

            // 2. Warm Leads Card
            RingtoneCategoryCard(
                target = RingtoneTarget.WARM,
                ringtoneTitle = getRingtoneTitle(warmUri),
                isPlaying = playingTarget == RingtoneTarget.WARM,
                onPlayToggle = {
                    if (playingTarget == RingtoneTarget.WARM) stopAudio()
                    else playAudio(RingtoneTarget.WARM, warmUri)
                },
                onChangeRingtone = { launchRingtonePicker(RingtoneTarget.WARM, warmUri) },
                onResetToDefault = {
                    ringtoneRepository.setRingtoneForLeadWarm(null)
                    warmUri = null
                },
                isCustomSet = warmUri != null
            )

            // 3. Cold Leads Card
            RingtoneCategoryCard(
                target = RingtoneTarget.COLD,
                ringtoneTitle = getRingtoneTitle(coldUri),
                isPlaying = playingTarget == RingtoneTarget.COLD,
                onPlayToggle = {
                    if (playingTarget == RingtoneTarget.COLD) stopAudio()
                    else playAudio(RingtoneTarget.COLD, coldUri)
                },
                onChangeRingtone = { launchRingtonePicker(RingtoneTarget.COLD, coldUri) },
                onResetToDefault = {
                    ringtoneRepository.setRingtoneForLeadCold(null)
                    coldUri = null
                },
                isCustomSet = coldUri != null
            )

            // 4. Customers Card
            RingtoneCategoryCard(
                target = RingtoneTarget.CUSTOMER,
                ringtoneTitle = getRingtoneTitle(customerUri),
                isPlaying = playingTarget == RingtoneTarget.CUSTOMER,
                onPlayToggle = {
                    if (playingTarget == RingtoneTarget.CUSTOMER) stopAudio()
                    else playAudio(RingtoneTarget.CUSTOMER, customerUri)
                },
                onChangeRingtone = { launchRingtonePicker(RingtoneTarget.CUSTOMER, customerUri) },
                onResetToDefault = {
                    ringtoneRepository.setRingtoneForCustomer(null)
                    customerUri = null
                },
                isCustomSet = customerUri != null
            )

            // 5. System Default Info
            GlassyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Slate300,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "System Fallback Ringtone",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate300
                            )
                            Text(
                                text = getRingtoneTitle(ringtoneRepository.default()),
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (playingTarget == RingtoneTarget.DEFAULT) stopAudio()
                            else playAudio(RingtoneTarget.DEFAULT, ringtoneRepository.default())
                        }
                    ) {
                        Icon(
                            imageVector = if (playingTarget == RingtoneTarget.DEFAULT) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Test Audio",
                            tint = Teal300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RingtoneCategoryCard(
    target: RingtoneTarget,
    ringtoneTitle: String,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onChangeRingtone: () -> Unit,
    onResetToDefault: () -> Unit,
    isCustomSet: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    GlassyCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(target.accentColor.copy(alpha = 0.15f))
                            .border(1.dp, target.accentColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = target.icon,
                            contentDescription = null,
                            tint = target.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        Text(
                            text = target.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                }
            }

            // Current Tone Row & Controls
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate900.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onPlayToggle,
                            modifier = Modifier
                                .size(36.dp)
                                .scale(if (isPlaying) pulseScale else 1f)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                                contentDescription = if (isPlaying) "Stop" else "Play",
                                tint = if (isPlaying) Red500 else target.accentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = ringtoneTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCustomSet) target.accentColor else Slate300
                            )
                            Text(
                                text = if (isCustomSet) "Custom Rule Active" else "Inheriting Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCustomSet) target.accentColor.copy(alpha = 0.8f) else Slate400
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onChangeRingtone,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = target.accentColor.copy(alpha = 0.15f),
                                contentColor = target.accentColor
                            )
                        ) {
                            Text("Change", style = MaterialTheme.typography.labelMedium)
                        }

                        if (isCustomSet) {
                            IconButton(
                                onClick = onResetToDefault,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Reset",
                                    tint = Slate400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
