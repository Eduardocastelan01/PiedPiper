package com.lostf1sh.pixelplayeross.presentation.gdrive.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GDriveDashboardScreen(
    viewModel: GDriveDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
    val lastSyncSongCount by viewModel.lastSyncSongCount.collectAsStateWithLifecycle()
    val currentFolderName by viewModel.currentFolderName.collectAsStateWithLifecycle()

    val apiKeyInput by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val oauthTokenInput by viewModel.oauthTokenInput.collectAsStateWithLifecycle()
    val folderIdInput by viewModel.folderIdInput.collectAsStateWithLifecycle()
    val folderNameInput by viewModel.folderNameInput.collectAsStateWithLifecycle()

    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val testSuccessMessage by viewModel.testSuccessMessage.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Google Drive",
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isConfigured) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Disconnect",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header / Status Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google_drive),
                            contentDescription = "Google Drive",
                            modifier = Modifier.size(36.dp),
                            tint = Color.Unspecified
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (isConfigured) "Google Drive Connected" else "Connect Google Drive",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = if (isConfigured) {
                            val countStr = "$lastSyncSongCount song(s) indexed"
                            val dateStr = if (lastSyncTimestamp > 0) {
                                " · Last sync: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(lastSyncTimestamp))}"
                            } else ""
                            countStr + dateStr
                        } else {
                            "Stream your personal music library directly from Google Drive"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (isConfigured) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.syncLibrary() },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSyncing) "Syncing..." else "Sync Now")
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearSongs() },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            // Sync Progress Card
            AnimatedVisibility(visible = isSyncing, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            val progress = syncProgress
                            Text(
                                text = if (progress != null && progress.second > 0) {
                                    "Indexing track ${progress.first} of ${progress.second}..."
                                } else {
                                    "Scanning Google Drive audio files..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = RoundedSans,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        syncProgress?.let { (scanned, total) ->
                            if (total > 0) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { scanned.toFloat() / total },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Status message card
            AnimatedVisibility(visible = statusMessage != null, enter = fadeIn(), exit = fadeOut()) {
                statusMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium, fontFamily = RoundedSans)
                        }
                    }
                }
            }

            // Error message card
            AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                errorMessage?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text(err, style = MaterialTheme.typography.bodyMedium, fontFamily = RoundedSans, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Configuration Form
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Credenciales de Conexión",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Para tu Google Drive privado, Google requiere un Token OAuth (ya29...). Para una API Key de Google Cloud (AIza...), debes habilitar 'Google Drive API' en Google Cloud Console y compartir la carpeta con enlace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { viewModel.onApiKeyChanged(it) },
                        label = { Text("Google Cloud API Key") },
                        placeholder = { Text("AIzaSy...") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Text(
                        text = "Used for folders shared via public link or view-with-link permissions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = oauthTokenInput,
                        onValueChange = { viewModel.onOauthTokenChanged(it) },
                        label = { Text("OAuth Bearer Token (Optional)") },
                        placeholder = { Text("ya29.a0...") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Text(
                        text = "Provide if you wish to stream private files directly from your personal Drive.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = folderIdInput,
                        onValueChange = { viewModel.onFolderIdChanged(it) },
                        label = { Text("Music Folder ID (Optional)") },
                        placeholder = { Text("e.g. 1A2b3C4d5E6f...") },
                        leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Text(
                        text = "The folder ID from the Google Drive URL. Leave blank to scan your whole Drive.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { viewModel.onFolderNameChanged(it) },
                        label = { Text("Folder Display Name") },
                        placeholder = { Text("Drive Music") },
                        leadingIcon = { Icon(Icons.Rounded.Label, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.saveConfig() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }

                        Button(
                            onClick = { viewModel.testConnection() },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Test Connection")
                            }
                        }
                    }

                    testSuccessMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(msg, style = MaterialTheme.typography.bodySmall, fontFamily = RoundedSans)
                            }
                        }
                    }
                }
            }

            // Quick Guide Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "How Google Drive Streaming Works",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. Create a free API Key on Google Cloud Console with Google Drive API enabled.\n" +
                        "2. Share your music folder (Anyone with the link can view).\n" +
                        "3. Paste the Folder ID from the Drive URL (after /folders/...) and your API Key.\n" +
                        "4. Pied Piper streams music on-the-fly via its internal proxy, with zero local storage used until you download tracks for offline listening.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f
                    )
                }
            }
        }
    }
}
