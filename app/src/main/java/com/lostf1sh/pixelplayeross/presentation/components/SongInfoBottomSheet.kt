@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.offline.CloudOfflineRepository
import com.lostf1sh.pixelplayeross.data.offline.OfflineDownloadStatus
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.AutoSizingTextToFill
import com.lostf1sh.pixelplayeross.utils.formatDuration
import com.lostf1sh.pixelplayeross.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lostf1sh.pixelplayeross.data.media.CoverArtUpdate
import com.lostf1sh.pixelplayeross.data.media.CustomMetadataChanges
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SongInfoBottomSheetViewModel.ToneTarget
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.TransformOrigin
import com.lostf1sh.pixelplayeross.presentation.screens.TabAnimation
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import com.lostf1sh.pixelplayeross.utils.AudioMetaUtils
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SongInfoBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddNextToQueue: () -> Unit,
    onAddToPlayList: () -> Unit,
    onDeleteFromDevice: (activity: Activity, song: Song, onResult: (Boolean) -> Unit) -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToArtistById: (Long) -> Unit = { onNavigateToArtist() },
    onNavigateToGenre: () -> Unit,
    onEditSong: (
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
        coverArtUpdate: CoverArtUpdate?,
        customMetadataChanges: CustomMetadataChanges
    ) -> Unit,
    removeFromListTrigger: () -> Unit,
    songInfoViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showEditSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showTonePickerDialog by remember { mutableStateOf(false) }
    var toneConfirmationTarget by remember { mutableStateOf<ToneTarget?>(null) }
    var pendingTonePermissionSong by remember { mutableStateOf<Song?>(null) }
    var pendingTonePermissionTarget by remember { mutableStateOf<ToneTarget?>(null) }
    val audioMeta by songInfoViewModel.audioMeta.collectAsStateWithLifecycle()
    val resolvedArtists by songInfoViewModel.resolvedArtists.collectAsStateWithLifecycle()
    val offlineDownload by songInfoViewModel.offlineDownload.collectAsStateWithLifecycle()
    val musicBrainzState by songInfoViewModel.musicBrainzState.collectAsStateWithLifecycle()
    val isCloudSong = remember(song.contentUriString) { CloudOfflineRepository.isCloudSong(song) }
    val ringtonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pendingSong = pendingTonePermissionSong
        val pendingTarget = pendingTonePermissionTarget
        pendingTonePermissionSong = null
        pendingTonePermissionTarget = null
        if (pendingSong == null || pendingTarget == null) {
            return@rememberLauncherForActivityResult
        }
        if (songInfoViewModel.hasSystemWritePermission()) {
            songInfoViewModel.setSongAsTone(pendingSong, pendingTarget) { result ->
                val message = when (result) {
                    is SongInfoBottomSheetViewModel.ToneActionResult.Success -> result.message
                    is SongInfoBottomSheetViewModel.ToneActionResult.Error -> result.message
                    is SongInfoBottomSheetViewModel.ToneActionResult.NeedsSystemWritePermission -> result.message
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.song_info_ringtone_permission_missing),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestToneSystemWritePermission(songToSet: Song, target: ToneTarget, message: String) {
        pendingTonePermissionSong = songToSet
        pendingTonePermissionTarget = target
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        try {
            ringtonePermissionLauncher.launch(songInfoViewModel.createSystemWriteSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            try {
                ringtonePermissionLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                pendingTonePermissionSong = null
                pendingTonePermissionTarget = null
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.song_info_ringtone_failed,
                        e.localizedMessage ?: ""
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun handleToneResult(
        songToSet: Song,
        target: ToneTarget,
        result: SongInfoBottomSheetViewModel.ToneActionResult
    ) {
        when (result) {
            is SongInfoBottomSheetViewModel.ToneActionResult.Success -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is SongInfoBottomSheetViewModel.ToneActionResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is SongInfoBottomSheetViewModel.ToneActionResult.NeedsSystemWritePermission -> {
                requestToneSystemWritePermission(songToSet, target, result.message)
            }
        }
    }

    fun setCurrentSongAsTone(target: ToneTarget) {
        songInfoViewModel.setSongAsTone(song, target) { result ->
            handleToneResult(song, target, result)
        }
    }

    val evenCornerRadiusElems = 26.dp

    val listItemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 20.dp,
            smoothnessAsPercentTL = 60, cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
        )
    }
    val albumArtShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }
    val playButtonShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }

    val sheetState = rememberModalSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (isFavorite) evenCornerRadiusElems else 60.dp,
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )
    val favoriteButtonShape = remember(favoriteButtonCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
            smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
        )
    }
    val infoSegmentContainerShape = remember {
        RoundedCornerShape(20.dp)
    }
    val infoSegmentItemShape = remember {
        RoundedCornerShape(8.dp)
    }

    val audioMetaLabel = remember(audioMeta) {
        val meta = audioMeta ?: return@remember null
        val formatLabel = AudioMetaUtils.mimeTypeToFormat(meta.mimeType)
            .takeIf { it != "-" }
            ?.uppercase(java.util.Locale.getDefault())
        val parts = buildList {
            meta.sampleRate?.takeIf { it > 0 }
                ?.let { add(String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)) }
            meta.bitrate?.takeIf { it > 0 }
                ?.let { add("${it / 1000} kbps") }
            formatLabel?.let { add(it) }
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    val songLocationInfo = remember(song.path, song.contentUriString) {
        songInfoViewModel.getSongLocationInfo(song)
    }

    LaunchedEffect(song.id) {
        songInfoViewModel.bindSong(song)
        songInfoViewModel.loadAudioMeta(song)
        songInfoViewModel.loadArtistsForSong(song)
    }

    LaunchedEffect(musicBrainzState) {
        if (musicBrainzState is SongInfoBottomSheetViewModel.MusicBrainzUiState.Applied) {
            Toast.makeText(
                context,
                context.getString(R.string.musicbrainz_applied),
                Toast.LENGTH_LONG
            ).show()
            songInfoViewModel.dismissMusicBrainz()
        }
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    // Keep the pager from growing past the screen on small devices: the sheet header and
    // grab handle take ~180dp; never shrink below 280dp so content stays usable.
    val maxPagerHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            180.dp
        ).coerceAtLeast(280.dp)

    // Defer the height animation past the first two frames so the sheet doesn't visibly
    // animate its content height while it is still opening.
    var heightAnimationEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        heightAnimationEnabled = true
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!showEditSheet) {
                onDismiss()
            }
        },
        sheetState = sheetState,
    ) {
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = stringResource(R.string.widget_album_art),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(song.artist)
                                        if (song.album.isNotBlank() && song.album != song.title) {
                                            append(" • ")
                                            append(song.album)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!audioMetaLabel.isNullOrBlank()) {
                                    Text(
                                        text = audioMetaLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            val isEditable = remember(song) { songInfoViewModel.isSongEditable(song) }
                            if (isEditable) {
                                FilledTonalIconButton(
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    onClick = { showEditSheet = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.cd_edit_song_metadata),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val sizeAnimationSpec = if (heightAnimationEnabled) {
                        tween<androidx.compose.ui.unit.IntSize>(durationMillis = 280)
                    } else {
                        androidx.compose.animation.core.snap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxPagerHeight)
                            .animateContentSize(
                                animationSpec = sizeAnimationSpec,
                                alignment = Alignment.TopCenter
                            )
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            when (page) {
                                0 -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        // Quick Action Bar (Play, Like, Queue, Share)
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                SongQuickActionButton(
                                                    icon = Icons.Rounded.PlayArrow,
                                                    label = stringResource(R.string.play_playback),
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    onClick = onPlaySong,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                SongQuickActionButton(
                                                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                    label = if (isFavorite) stringResource(R.string.cd_remove_from_favorites) else stringResource(R.string.cd_add_to_favorites),
                                                    containerColor = favoriteButtonContainerColor,
                                                    contentColor = favoriteButtonContentColor,
                                                    onClick = onToggleFavorite,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                SongQuickActionButton(
                                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                                    label = stringResource(R.string.action_add_to_queue),
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    onClick = onAddToQueue,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                SongQuickActionButton(
                                                    icon = Icons.Rounded.Share,
                                                    label = stringResource(R.string.cd_share_song_file),
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    onClick = {
                                                        try {
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "audio/*"
                                                                putExtra(Intent.EXTRA_STREAM, song.contentUriString.toUri())
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(
                                                                Intent.createChooser(
                                                                    shareIntent,
                                                                    context.getString(R.string.song_info_share_chooser_title)
                                                                )
                                                            )
                                                        } catch (e: Exception) {
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(R.string.error_share_song_format, e.localizedMessage ?: ""),
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // Structured Options List (Apple Music / Spotify style grouped card)
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                SongMenuRowItem(
                                                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                                                    title = stringResource(R.string.action_queue_next),
                                                    onClick = onAddNextToQueue
                                                )
                                                SongMenuRowItem(
                                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                    title = stringResource(R.string.shortcut_playlist_short),
                                                    onClick = onAddToPlayList
                                                )
                                                if (!isCloudSong) {
                                                    SongMenuRowPainterItem(
                                                        painter = painterResource(R.drawable.rounded_notifications_active_24),
                                                        title = stringResource(R.string.song_info_choose_tone),
                                                        onClick = { showTonePickerDialog = true }
                                                    )
                                                }
                                                SongMenuRowItem(
                                                    icon = Icons.Rounded.TravelExplore,
                                                    title = stringResource(R.string.musicbrainz_lookup),
                                                    onClick = { songInfoViewModel.searchMusicBrainz(song) }
                                                )
                                                if (isCloudSong) {
                                                    val cloudLabel = when (offlineDownload?.status) {
                                                        OfflineDownloadStatus.QUEUED -> stringResource(R.string.cloud_download_queued)
                                                        OfflineDownloadStatus.DOWNLOADING -> offlineDownload?.progress
                                                            ?.let { stringResource(R.string.cloud_download_progress, (it * 100).toInt()) }
                                                            ?: stringResource(R.string.cloud_downloading)
                                                        OfflineDownloadStatus.COMPLETE -> stringResource(R.string.cloud_remove_download)
                                                        OfflineDownloadStatus.FAILED -> stringResource(R.string.cloud_download_retry)
                                                        null -> stringResource(R.string.cloud_download_action)
                                                    }
                                                    SongMenuRowItem(
                                                        icon = when (offlineDownload?.status) {
                                                            OfflineDownloadStatus.COMPLETE -> Icons.Rounded.CloudDone
                                                            else -> Icons.Rounded.CloudDownload
                                                        },
                                                        title = cloudLabel,
                                                        onClick = {
                                                            when (offlineDownload?.status) {
                                                                OfflineDownloadStatus.FAILED ->
                                                                    songInfoViewModel.retryOfflineDownload(song)
                                                                else -> songInfoViewModel.toggleOfflineDownload(song)
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    SongMenuRowItem(
                                                        icon = Icons.Default.DeleteForever,
                                                        title = stringResource(R.string.delete_action),
                                                        iconTint = MaterialTheme.colorScheme.error,
                                                        textColor = MaterialTheme.colorScheme.error,
                                                        onClick = {
                                                            (context as? Activity)?.let { activity ->
                                                                onDeleteFromDevice(activity, song) { result ->
                                                                    if (result) {
                                                                        removeFromListTrigger()
                                                                        onDismiss()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        item {
                                            Spacer(Modifier.height(84.dp))
                                        }
                                    }
                                }
                                1 -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(infoSegmentContainerShape),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_info_label_duration),
                                                    supporting = formatDuration(song.duration),
                                                    icon = Icons.Rounded.Schedule,
                                                    iconDescription = stringResource(R.string.cd_duration_icon),
                                                    shape = infoSegmentItemShape,
                                                )

                                                if (!song.genre.isNullOrEmpty()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_field_genre),
                                                        supporting = song.genre,
                                                        icon = Icons.Rounded.MusicNote,
                                                        iconDescription = stringResource(R.string.cd_genre_icon),
                                                        shape = infoSegmentItemShape,
                                                        onClick = onNavigateToGenre,
                                                    )
                                                }

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_field_album),
                                                    supporting = song.album,
                                                    icon = Icons.Rounded.Album,
                                                    iconDescription = stringResource(R.string.cd_album_icon),
                                                    shape = infoSegmentItemShape,
                                                    onClick = onNavigateToAlbum,
                                                )

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_field_artist),
                                                    supporting = song.displayArtist,
                                                    icon = Icons.Rounded.Person,
                                                    iconDescription = stringResource(R.string.cd_artist_icon),
                                                    shape = infoSegmentItemShape,
                                                    onClick = {
                                                        if (song.artists.size > 1) {
                                                            showArtistPicker = true
                                                        } else {
                                                            onNavigateToArtist()
                                                        }
                                                    },
                                                )

                                                if (!audioMetaLabel.isNullOrEmpty()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_info_label_song_metadata),
                                                        supporting = audioMetaLabel,
                                                        icon = Icons.Rounded.Info,
                                                        iconDescription = stringResource(R.string.cd_audio_format_icon),
                                                        shape = infoSegmentItemShape,
                                                    )
                                                }

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(songLocationInfo.labelRes),
                                                    supporting = songLocationInfo.value,
                                                    icon = if (songLocationInfo.isCloud) Icons.Rounded.Cloud else Icons.Rounded.AudioFile,
                                                    iconDescription = stringResource(
                                                        if (songLocationInfo.isCloud) {
                                                            R.string.cd_provider_icon
                                                        } else {
                                                            R.string.cd_file_icon
                                                        }
                                                    ),
                                                    shape = infoSegmentItemShape,
                                                )
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.height(80.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(5.dp),
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {}
                ) {
                    TabAnimation(
                        index = 0,
                        title = stringResource(R.string.song_info_tab_options),
                        selectedIndex = pagerState.currentPage,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_options),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.song_info_tab_options_badge),
                                fontFamily = RoundedSans,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    TabAnimation(
                        index = 1,
                        title = stringResource(R.string.song_info_tab_details),
                        selectedIndex = pagerState.currentPage,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.cd_details_tab),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.song_info_tab_info_badge),
                                fontFamily = RoundedSans,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    EditSongSheet(
        visible = showEditSheet,
        song = song,
        onDismiss = { showEditSheet = false },
        onSave = { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArt, customMetadataChanges ->
            onEditSong(
                title,
                artist,
                album,
                albumArtist,
                composer,
                genre,
                lyrics,
                trackNumber,
                discNumber,
                replayGainTrackGainDb,
                replayGainAlbumGainDb,
                coverArt,
                customMetadataChanges
            )
            showEditSheet = false
        },
    )

    when (val state = musicBrainzState) {
        SongInfoBottomSheetViewModel.MusicBrainzUiState.Idle,
        SongInfoBottomSheetViewModel.MusicBrainzUiState.Applied -> Unit

        SongInfoBottomSheetViewModel.MusicBrainzUiState.Loading -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(stringResource(R.string.musicbrainz_lookup)) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                        Text(stringResource(R.string.musicbrainz_searching))
                    }
                }
            )
        }

        is SongInfoBottomSheetViewModel.MusicBrainzUiState.Error -> {
            AlertDialog(
                onDismissRequest = songInfoViewModel::dismissMusicBrainz,
                confirmButton = {
                    TextButton(onClick = { songInfoViewModel.searchMusicBrainz(song) }) {
                        Text(stringResource(R.string.cloud_download_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = songInfoViewModel::dismissMusicBrainz) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.musicbrainz_lookup_failed)) },
                text = { Text(state.message) }
            )
        }

        is SongInfoBottomSheetViewModel.MusicBrainzUiState.Results -> {
            AlertDialog(
                onDismissRequest = songInfoViewModel::dismissMusicBrainz,
                confirmButton = {
                    TextButton(onClick = songInfoViewModel::dismissMusicBrainz) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.musicbrainz_choose_match)) },
                text = {
                    if (state.matches.isEmpty()) {
                        Text(stringResource(R.string.musicbrainz_no_results))
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.matches, key = { it.recordingId + (it.releaseId ?: "") }) { match ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            songInfoViewModel.applyMusicBrainzMatch(song, match)
                                        },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(match.title, fontWeight = FontWeight.Bold)
                                        Text(
                                            listOfNotNull(
                                                match.artist.takeIf { it.isNotBlank() },
                                                match.album.takeIf { it.isNotBlank() },
                                                match.year.takeIf { it > 0 }?.toString()
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            stringResource(R.string.musicbrainz_match_score, match.score),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    val artistPickerSheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && resolvedArtists.isNotEmpty()) {
        com.lostf1sh.pixelplayeross.presentation.components.player.PlayerArtistPickerBottomSheet(
            song = song,
            artists = resolvedArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                showArtistPicker = false
                onNavigateToArtistById(artist.id)
            }
        )
    }

    if (showTonePickerDialog) {
        ToneTargetPickerDialog(
            onDismiss = { showTonePickerDialog = false },
            onTargetSelected = { target ->
                showTonePickerDialog = false
                toneConfirmationTarget = target
            }
        )
    }

    toneConfirmationTarget?.let { target ->
        ToneConfirmationDialog(
            song = song,
            target = target,
            onDismiss = { toneConfirmationTarget = null },
            onConfirm = {
                toneConfirmationTarget = null
                setCurrentSongAsTone(target)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneTargetPickerDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (ToneTarget) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneDialogIcon(target = null)
                    Text(
                        text = stringResource(R.string.song_info_tone_picker_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.song_info_tone_picker_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ToneTarget.values().forEach { target ->
                        ToneTargetOption(
                            target = target,
                            onClick = { onTargetSelected(target) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneTargetOption(
    target: ToneTarget,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        leadingContent = {
            ToneDialogIcon(
                target = target,
                modifier = Modifier.size(42.dp),
                iconModifier = Modifier.size(22.dp),
            )
        },
        content = {
            Text(
                text = stringResource(target.titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(stringResource(target.subtitleResId))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneConfirmationDialog(
    song: Song,
    target: ToneTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneDialogIcon(target = target)
                    Text(
                        text = stringResource(R.string.song_info_tone_confirm_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.song_info_tone_confirm_body,
                        song.title,
                        stringResource(target.confirmLabelResId),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(onClick = onConfirm) {
                        Text(stringResource(R.string.song_info_tone_confirm_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneDialogIcon(
    target: ToneTarget?,
    modifier: Modifier = Modifier.size(56.dp),
    iconModifier: Modifier = Modifier.size(28.dp),
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (target) {
            ToneTarget.Ringtone -> Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ToneTarget.Notification -> Icon(
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ToneTarget.Alarm -> Icon(
                painter = painterResource(R.drawable.rounded_alarm_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            null -> Icon(
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RingtoneActionButton(
    modifier: Modifier,
    showText: Boolean,
    compactText: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    if (showText) {
        FilledTonalButton(
            modifier = modifier,
            colors = colors,
            contentPadding = PaddingValues(horizontal = if (compactText) 12.dp else 18.dp),
            shape = CircleShape,
            onClick = onClick,
        ) {
            Icon(
                modifier = Modifier.size(if (compactText) 20.dp else 24.dp),
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = stringResource(R.string.cd_choose_song_tone),
            )
            Spacer(Modifier.width(if (compactText) 6.dp else 8.dp))
            Text(
                text = stringResource(
                    if (compactText) R.string.song_info_set_as_short else R.string.song_info_choose_tone
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        FilledTonalIconButton(
            modifier = modifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = CircleShape,
            onClick = onClick,
        ) {
            Icon(
                modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = stringResource(R.string.cd_choose_song_tone),
            )
        }
    }
}

private val ToneTarget.titleResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_title
        ToneTarget.Notification -> R.string.song_info_tone_notification_title
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_title
    }

private val ToneTarget.subtitleResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_subtitle
        ToneTarget.Notification -> R.string.song_info_tone_notification_subtitle
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_subtitle
    }

private val ToneTarget.confirmLabelResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_label
        ToneTarget.Notification -> R.string.song_info_tone_notification_label
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_label
    }

@Composable
private fun SongInfoSegmentedListItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    iconDescription: String,
    shape: Shape,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .let { baseModifier ->
            if (onClick != null) {
                baseModifier.clickable(onClick = onClick)
            } else {
                baseModifier
            }
        }

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            content = { Text(headline) },
            supportingContent = { Text(supporting) },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                )
            }
        )
    }
}


@Composable
private fun SongQuickActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SongMenuRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SongMenuRowPainterItem(
    painter: Painter,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
