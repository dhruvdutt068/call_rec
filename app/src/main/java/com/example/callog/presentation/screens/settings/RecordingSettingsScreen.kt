package com.example.callog.presentation.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.callog.core.telecom.OemCapabilityDetector
import com.example.callog.data.provider.RecordingScanner
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OEM Recording Onboarding and Capability Management Screen.
 *
 * Implements Phase 5:
 * - Hardware OEM capability detection (Samsung, Xiaomi, OnePlus, Oppo, Realme, Vivo, Pixel)
 * - Verified native settings intent dispatching
 * - Step-by-step setup guides
 * - Storage permission validation
 * - Test recording scan workflow
 * - Privacy & legal compliance disclosures
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(
    oemDetector: OemCapabilityDetector,
    recordingScanner: RecordingScanner,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val capabilityInfo = remember { oemDetector.detectCapabilities() }

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(context, "Storage permission is required to detect call recordings.", Toast.LENGTH_SHORT).show()
        }
    }

    var isScanning by remember { mutableStateOf(false) }
    var scanResultCount by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call Recording Onboarding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate200
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900
                )
            )
        },
        containerColor = Slate950,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── 1. Device Info & Capability Status Card ───────────────────────────
            GlassyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${capabilityInfo.manufacturer} ${capabilityInfo.model}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate50
                            )
                            Text(
                                text = "Platform: ${capabilityInfo.oemType.displayName} (Android ${Build.VERSION.RELEASE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }

                        val (badgeText, badgeColor) = if (capabilityInfo.isNativeRecordingSupported) {
                            "Supported" to Green500
                        } else {
                            "Manual" to Amber500
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    HorizontalDivider(color = Slate800)

                    Text(
                        text = capabilityInfo.autoRecordingDetectionNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate300,
                        lineHeight = 18.sp
                    )
                }
            }

            // ── 2. Settings Shortcut (if verified intent available) ────────────────
            if (capabilityInfo.settingsIntent != null) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(capabilityInfo.settingsIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open settings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal500,
                        contentColor = Slate950
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Native Recording Settings",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── 3. Step-by-Step Setup Instructions ────────────────────────────────
            GlassyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = null,
                            tint = Teal400,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Setup Instructions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }

                    capabilityInfo.setupInstructions.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Teal500.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal300
                                )
                            }
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate200,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── 4. Permission Status Card ─────────────────────────────────────────
            GlassyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Media / Audio Access",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate100
                        )
                        Text(
                            text = if (hasPermission) "Permission granted. Callog can observe audio recordings." else "Permission needed to discover saved recordings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPermission) Green400 else Amber400
                        )
                    }

                    if (!hasPermission) {
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(requiredPermission) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400)
                        ) {
                            Text("Grant")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = Green400
                        )
                    }
                }
            }

            // ── 5. Test Recording Discovery Workflow ──────────────────────────────
            GlassyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Verify Recording Discovery",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    Text(
                        text = "Scan existing device storage directories and MediaStore to test the discovery bridge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )

                    Button(
                        onClick = {
                            isScanning = true
                            coroutineScope.launch {
                                val discovered = withContext(Dispatchers.IO) {
                                    recordingScanner.scanRecordings()
                                }
                                scanResultCount = discovered.size
                                isScanning = false
                            }
                        },
                        enabled = !isScanning && hasPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Slate100)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Slate100,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning device...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Discovery Scan")
                        }
                    }

                    if (scanResultCount != null) {
                        Text(
                            text = "Found ${scanResultCount} call recording(s) on device storage.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (scanResultCount!! > 0) Green400 else Amber400
                        )
                    }
                }
            }

            // ── 6. Privacy & Legal Compliance Disclosure ──────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Privacy Note: Callog only accesses audio files generated by your device's built-in dialer or manually chosen folders. Calls are matched locally using call timestamps and phone numbers. Local files are verified with SHA-256 before synchronization.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
