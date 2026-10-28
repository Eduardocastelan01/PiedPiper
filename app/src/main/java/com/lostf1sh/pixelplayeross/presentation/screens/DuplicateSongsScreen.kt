package com.lostf1sh.pixelplayeross.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.library.DuplicateFinder
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.viewmodel.DuplicateSongsViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DuplicateSongsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: DuplicateSongsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val duplicatesQueueName = stringResource(R.string.duplicates_title)
    val context = LocalContext.current

    var showCleanAllDialog by remember { mutableStateOf(false) }
    var groupToClean by remember { mutableStateOf<DuplicateFinder.DuplicateGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.duplicates_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.auth_cd_back))
                    }
                },
                actions = {
                    if (uiState.groups.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showCleanAllDialog = true },
                            modifier = Modifier.padding(end = 8.dp),
                            enabled = !uiState.isCleaning
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Conservar mejor calidad",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading || uiState.isCleaning -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ContainedLoadingIndicator()
                        if (uiState.isCleaning) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Eliminando duplicados de menor calidad...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            uiState.groups.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.duplicates_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HighQuality,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(
                                            R.string.duplicates_summary,
                                            uiState.groups.size,
                                            uiState.totalDuplicates
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${uiState.totalRedundantSongs} pistas duplicadas pueden eliminarse conservando la pista de mayor fidelidad.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    items(uiState.groups, key = { it.title + "|" + it.artist + "|" + it.songs.first().id }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            onSongClick = { song ->
                                playerViewModel.playSongs(group.songs, song, queueName = duplicatesQueueName)
                            },
                            onCleanGroup = { groupToClean = group }
                        )
                    }
                }
            }
        }
    }

    if (showCleanAllDialog) {
        val redundantCount = uiState.totalRedundantSongs
        AlertDialog(
            onDismissRequest = { showCleanAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Conservar pistas de mayor calidad",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Se conservará la canción de mejor fidelidad (formato sin pérdida, mayor bitrate y frecuencia de muestreo) de cada grupo.\n\nSe eliminarán $redundantCount archivos duplicados del almacenamiento de forma permanente.\n\n¿Deseas continuar?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanAllDialog = false
                        viewModel.removeAllDuplicates { cleaned ->
                            Toast.makeText(
                                context,
                                "Se conservaron las mejores pistas y se eliminaron $cleaned duplicados",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar $redundantCount duplicados")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCleanAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    groupToClean?.let { group ->
        val toRemoveCount = group.duplicatesToRemove.size
        AlertDialog(
            onDismissRequest = { groupToClean = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Conservar versión de mayor fidelidad", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Se conservará: \"${group.bestSong.title}\" (${formatAudioQuality(group.bestSong)}).\n\nSe eliminarán $toRemoveCount pista(s) duplicada(s) de menor calidad.\n\n¿Confirmar eliminación?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupToClean = null
                        viewModel.removeDuplicatesInGroup(group) {
                            Toast.makeText(context, "Duplicados eliminados", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar duplicados")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { groupToClean = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateFinder.DuplicateGroup,
    onSongClick: (Song) -> Unit,
    onCleanGroup: () -> Unit
) {
    val bestSong = remember(group) { group.bestSong }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.duplicates_group_title_format, group.title, group.artist),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.duplicates_group_count, group.songs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (group.songs.size > 1) {
                    FilledTonalButton(
                        onClick = onCleanGroup,
                        shape = CircleShape,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Limpiar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            group.songs.forEach { song ->
                val isBest = song.id == bestSong.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isBest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                        )
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBest) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Mayor calidad",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.album.ifBlank { song.title },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isBest) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "Mayor calidad",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${formatAudioQuality(song)} • ${song.path}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun formatAudioQuality(song: Song): String {
    val ext = song.path.substringAfterLast('.', "").uppercase(Locale.ROOT)
    val bitrateStr = if ((song.bitrate ?: 0) > 0) "${song.bitrate} kbps" else null
    val sampleRateStr = if ((song.sampleRate ?: 0) > 0) "${(song.sampleRate!! / 1000f).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }} kHz" else null

    val details = listOfNotNull(ext.ifBlank { null }, bitrateStr, sampleRateStr)
    return if (details.isNotEmpty()) details.joinToString(" • ") else "Audio"
}
