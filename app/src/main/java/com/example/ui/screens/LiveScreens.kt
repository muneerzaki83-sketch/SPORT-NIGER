package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Broadcast
import com.example.data.model.ChatComment
import com.example.data.model.UiState
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HeroBroadcastCard
import com.example.ui.components.HlsVideoPlayer
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.ElevatedNavySurface
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SoftSteelText
import com.example.ui.theme.VictoryEmerald
import com.example.ui.viewmodel.SportNigerViewModel

@Composable
fun LiveHubScreen(
    viewModel: SportNigerViewModel,
    onOpenBroadcast: (String) -> Unit
) {
    val strings = LocalStrings.current
    val broadcastsState by viewModel.broadcasts.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = strings.liveChannels,
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhiteText
            )
        }

        when (val state = broadcastsState) {
            UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrightBlueAccent)
                }
            }
            is UiState.Error -> item {
                EmptyStateView(state.message)
            }
            is UiState.Success -> {
                val liveNow = state.data.filter { it.status == "LIVE" }
                val scheduled = state.data.filter { it.status != "LIVE" }
                if (state.data.isEmpty()) {
                    item { EmptyStateView(strings.noStreamsEmpty) }
                } else {
                    if (liveNow.isNotEmpty()) {
                        items(liveNow, key = { it.id }) { bc ->
                            HeroBroadcastCard(
                                broadcast = bc,
                                onWatchClick = { onOpenBroadcast(bc.id) }
                            )
                        }
                    }
                    if (scheduled.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            SectionHeader(title = strings.upcomingStreams)
                        }
                        items(scheduled, key = { it.id }) { bc ->
                            HeroBroadcastCard(
                                broadcast = bc,
                                onWatchClick = { onOpenBroadcast(bc.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LivePlayerScreen(
    broadcastId: String,
    viewModel: SportNigerViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val broadcastsState by viewModel.broadcasts.collectAsStateWithLifecycle()
    val staffRole by viewModel.staffRole.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val secret by viewModel.selectedBroadcastSecret.collectAsStateWithLifecycle()

    val broadcast: Broadcast? = (broadcastsState as? UiState.Success)?.data?.firstOrNull { it.id == broadcastId }
    val commentsFlow = remember(broadcastId) { viewModel.observeCommentsForBroadcast(broadcastId) }
    val commentsState by commentsFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)

    var isFullscreen by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }

    LaunchedEffect(broadcastId, staffRole) {
        if (staffRole.canManageBroadcasts()) {
            viewModel.loadBroadcastSecret(broadcastId)
        } else {
            viewModel.clearBroadcastSecret()
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    if (broadcast == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavyBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.noStreamsEmpty, color = SoftSteelText)
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                    Text(strings.back)
                }
            }
        }
        return
    }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
            HlsVideoPlayer(
                playbackUrl = broadcast.playbackUrl,
                streamType = broadcast.streamType,
                youtubeVideoId = broadcast.youtubeVideoId,
                isFullscreen = true,
                onToggleFullscreen = { isFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("player_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = PureWhiteText)
                }
                Text(
                    text = broadcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhiteText,
                    maxLines = 1
                )
            }
            LivePulseBadge(
                label = if (broadcast.status == "LIVE") strings.statusLive else broadcast.status,
                isLive = broadcast.status == "LIVE"
            )
        }

        // Video Player
        if (broadcast.status == "OFFLINE" || broadcast.status == "ENDED") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(ElevatedNavySurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${broadcast.status} • ${broadcast.scheduledLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftSteelText
                )
            }
        } else {
            HlsVideoPlayer(
                playbackUrl = broadcast.playbackUrl,
                streamType = broadcast.streamType,
                youtubeVideoId = broadcast.youtubeVideoId,
                isFullscreen = false,
                onToggleFullscreen = { isFullscreen = true }
            )
        }

        // Match Info, Staff Broadcast Controls & Live Chat
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${broadcast.sportCategory} • ${broadcast.streamType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrightBlueAccent
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = SoftSteelText, modifier = Modifier.size(16.dp))
                                Text("${broadcast.viewerCount} ${strings.viewersWatching}", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                            }
                        }
                        Text(broadcast.title, style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
                        Text(broadcast.description, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                        Text(
                            text = "${strings.ingestProtocolLabel}: ${broadcast.ingestProtocol}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSteelText
                        )
                    }
                }
            }

            // Staff Stream Control & Masked RTMP Secret Card (Streaming Manager / Admin only)
            if (staffRole.canManageBroadcasts() || staffRole.canModerateChat()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepBlueContainer.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, BrightBlueAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Staff Broadcast Controls (${staffRole.displayName})", style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (staffRole.canManageBroadcasts()) {
                                    Button(
                                        onClick = { viewModel.updateBroadcastStatus(broadcast.id, "LIVE") },
                                        colors = ButtonDefaults.buttonColors(containerColor = VictoryEmerald),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(strings.startBroadcast, color = DarkNavyBackground)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.updateBroadcastStatus(broadcast.id, "ENDED") },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(strings.stopBroadcast, color = LiveCrimson)
                                    }
                                }
                                if (staffRole.canModerateChat()) {
                                    OutlinedButton(
                                        onClick = { viewModel.toggleBroadcastChat(broadcast.id, !broadcast.chatEnabled) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(strings.toggleChat, color = PureWhiteText)
                                    }
                                }
                            }
                            if (secret != null && staffRole.canManageBroadcasts()) {
                                HorizontalDivider(color = BorderSlate)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = BrightBlueAccent, modifier = Modifier.size(16.dp))
                                    Text("RTMP: ${secret!!.rtmpServerUrl}", style = MaterialTheme.typography.bodyMedium, color = PureWhiteText)
                                }
                                Text("Key: ${secret!!.streamKeyMasked} • ${secret!!.obsProfileNote}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText)
                            }
                        }
                    }
                }
            }

            // Live Chat Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = BrightBlueAccent)
                    Text(strings.liveChatTitle, style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
                }
            }

            if (!broadcast.chatEnabled) {
                item { EmptyStateView(strings.chatDisabled) }
            } else if (userProfile?.chatBlocked == true) {
                item { EmptyStateView(strings.chatBlockedNotice) }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text(strings.chatPlaceholder, color = MutedSteelText) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardNavySurface,
                                unfocusedContainerColor = CardNavySurface,
                                focusedBorderColor = BrightBlueAccent,
                                unfocusedBorderColor = BorderSlate,
                                focusedTextColor = PureWhiteText,
                                unfocusedTextColor = PureWhiteText
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_chat_input")
                        )
                        Button(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.sendChatComment(broadcast.id, chatInput)
                                    chatInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                        }
                    }
                }
            }

            when (val cs = commentsState) {
                UiState.Loading -> item {
                    CircularProgressIndicator(color = BrightBlueAccent, modifier = Modifier.size(24.dp))
                }
                is UiState.Error -> item {
                    Text(cs.message, color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
                }
                is UiState.Success -> {
                    items(cs.data, key = { it.id }) { comment ->
                        ChatCommentItem(
                            comment = comment,
                            canModerate = staffRole.canModerateChat(),
                            onReport = { viewModel.reportComment(broadcast.id, comment) },
                            onDelete = { viewModel.moderateDeleteComment(broadcast.id, comment.id) },
                            onBlockUser = { viewModel.blockUserFromChat(comment.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatCommentItem(
    comment: ChatComment,
    canModerate: Boolean,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    onBlockUser: () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardNavySurface,
        border = BorderStroke(1.dp, if (comment.reported) LiveCrimson.copy(alpha = 0.6f) else BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(comment.userName, style = MaterialTheme.typography.labelLarge, color = BrightBlueAccent)
                Text(comment.message, style = MaterialTheme.typography.bodyMedium, color = PureWhiteText)
            }
            Row {
                IconButton(onClick = onReport, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Flag, contentDescription = strings.reportComment, tint = SoftSteelText, modifier = Modifier.size(18.dp))
                }
                if (canModerate) {
                    IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Delete, contentDescription = strings.deleteComment, tint = LiveCrimson, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onBlockUser, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Block, contentDescription = strings.blockUser, tint = LiveCrimson, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
