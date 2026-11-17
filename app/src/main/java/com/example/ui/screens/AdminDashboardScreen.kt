package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StaffRole
import com.example.data.model.UiState
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.PitchNavySurface
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SoftSteelText
import com.example.ui.theme.VictoryEmerald
import com.example.ui.viewmodel.SportNigerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: SportNigerViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val staffRole by viewModel.staffRole.collectAsStateWithLifecycle()
    val broadcastsState by viewModel.broadcasts.collectAsStateWithLifecycle()
    val matchesState by viewModel.matches.collectAsStateWithLifecycle()
    val wrestlersState by viewModel.wrestlers.collectAsStateWithLifecycle()
    val tournamentsState by viewModel.tournaments.collectAsStateWithLifecycle()
    val newsState by viewModel.newsArticles.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categories.collectAsStateWithLifecycle()
    val staffDirState by viewModel.staffDirectory.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogs.collectAsStateWithLifecycle()

    if (!staffRole.isStaff) {
        Column(modifier = Modifier.fillMaxSize().background(DarkNavyBackground).padding(20.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = PureWhiteText)
            }
            EmptyStateView("Staff authorization required.")
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.adminTabOverview,
        strings.adminTabBroadcasts,
        strings.adminTabScores,
        strings.adminTabKokowa,
        strings.adminTabTournamentsNews,
        strings.adminTabStaffAudit
    )

    Column(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = PureWhiteText)
                }
                Column {
                    Text(strings.adminDashboardTitle, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                    Text("${strings.roleLabel}: ${staffRole.displayName}", style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent)
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = PitchNavySurface,
            contentColor = BrightBlueAccent,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, color = if (selectedTab == idx) BrightBlueAccent else SoftSteelText) },
                    modifier = Modifier.testTag("admin_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    val broadcasts = (broadcastsState as? UiState.Success)?.data.orEmpty()
                    val matches = (matchesState as? UiState.Success)?.data.orEmpty()
                    val wrestlers = (wrestlersState as? UiState.Success)?.data.orEmpty()
                    val tournaments = (tournamentsState as? UiState.Success)?.data.orEmpty()
                    val news = (newsState as? UiState.Success)?.data.orEmpty()
                    val categories = (categoriesState as? UiState.Success)?.data.orEmpty()

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile("Broadcasts", "${broadcasts.size}", Modifier.weight(1f))
                            MetricTile("Live Viewers", "${broadcasts.sumOf { it.viewerCount }}", Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile("Matches", "${matches.size}", Modifier.weight(1f))
                            MetricTile("Wrestlers", "${wrestlers.size}", Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile("Tournaments", "${tournaments.size}", Modifier.weight(1f))
                            MetricTile("Categories / News", "${categories.size} / ${news.size}", Modifier.weight(1f))
                        }
                    }
                    item {
                        Button(
                            onClick = viewModel::seedOfficialCatalog,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("admin_seed_catalog_btn")
                        ) {
                            Text(strings.seedCatalogButton)
                        }
                    }
                }
                1 -> {
                    if (!staffRole.canManageBroadcasts()) {
                        item { EmptyStateView("Requires Streaming Manager or Admin role.") }
                    } else {
                        item {
                            CreateBroadcastForm(onCreate = viewModel::createBroadcast)
                        }
                        val broadcasts = (broadcastsState as? UiState.Success)?.data.orEmpty()
                        items(broadcasts, key = { it.id }) { bc ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                                border = BorderStroke(1.dp, BorderSlate),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(bc.title, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                                    Text("${bc.status} • ${bc.streamType} • ${bc.ingestProtocol}", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.updateBroadcastStatus(bc.id, "LIVE") },
                                            colors = ButtonDefaults.buttonColors(containerColor = VictoryEmerald),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) { Text(strings.startBroadcast, color = DarkNavyBackground) }
                                        OutlinedButton(
                                            onClick = { viewModel.updateBroadcastStatus(bc.id, "ENDED") },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) { Text(strings.stopBroadcast, color = LiveCrimson) }
                                        OutlinedButton(
                                            onClick = { viewModel.toggleBroadcastChat(bc.id, !bc.chatEnabled) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) { Text(if (bc.chatEnabled) "Chat ON" else "Chat OFF", color = PureWhiteText) }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (!staffRole.canManageScores()) {
                        item { EmptyStateView("Requires Score Manager, Sports Manager, or Admin role.") }
                    } else {
                        val matches = (matchesState as? UiState.Success)?.data.orEmpty()
                        items(matches, key = { it.id }) { m ->
                            var hs by remember(m.id) { mutableStateOf(m.homeScore.toString()) }
                            var ascore by remember(m.id) { mutableStateOf(m.awayScore.toString()) }
                            var min by remember(m.id) { mutableStateOf(m.matchMinute) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                                border = BorderStroke(1.dp, BorderSlate),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${m.homeTeamName} vs ${m.awayTeamName} (${m.status})", style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(hs, { hs = it }, label = { Text("Home") }, singleLine = true, modifier = Modifier.weight(1f))
                                        OutlinedTextField(ascore, { ascore = it }, label = { Text("Away") }, singleLine = true, modifier = Modifier.weight(1f))
                                        OutlinedTextField(min, { min = it }, label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.updateMatchScore(m.id, hs.toLongOrNull() ?: 0L, ascore.toLongOrNull() ?: 0L, "LIVE", min)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) { Text(strings.updateScore) }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateMatchScore(m.id, hs.toLongOrNull() ?: 0L, ascore.toLongOrNull() ?: 0L, "COMPLETED", "FT")
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) { Text("Mark FT", color = VictoryEmerald) }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    if (!staffRole.canManageSports()) {
                        item { EmptyStateView("Requires Sports Manager or Admin role.") }
                    } else {
                        item { AddWrestlerForm(onAdd = viewModel::addWrestler) }
                        item { AddKokowaEventForm(onAdd = viewModel::createKokowaEvent) }
                        item { AddSportCategoryForm(onAdd = viewModel::createSportCategory) }
                    }
                }
                4 -> {
                    if (!staffRole.canManageSports() && !staffRole.canManageNews()) {
                        item { EmptyStateView("Requires Sports Manager, News Editor, or Admin role.") }
                    } else {
                        if (staffRole.canManageSports()) {
                            item { AddTournamentForm(onAdd = viewModel::createTournament) }
                        }
                        if (staffRole.canManageNews()) {
                            item { PublishNewsForm(onPublish = viewModel::publishNews) }
                        }
                    }
                }
                5 -> {
                    if (!staffRole.canManageStaff()) {
                        item { EmptyStateView("Super Admin privileges required for Staff RBAC & Audit Logs.") }
                    } else {
                        item { AssignStaffForm(onAssign = viewModel::assignStaffMember) }
                        val staffList = (staffDirState as? UiState.Success)?.data.orEmpty()
                        items(staffList, key = { it.id }) { member ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                                border = BorderStroke(1.dp, BorderSlate),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                                        Text("${member.email} • ${member.role} (${member.status})", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val next = if (member.status == "ACTIVE") "SUSPENDED" else "ACTIVE"
                                            viewModel.setStaffStatus(member.userId, next)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(
                                            if (member.status == "ACTIVE") strings.suspendAction else strings.activateAction,
                                            color = if (member.status == "ACTIVE") LiveCrimson else VictoryEmerald
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Text(strings.auditLogTitle, style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
                        }
                        val logs = (auditLogsState as? UiState.Success)?.data.orEmpty()
                        items(logs, key = { it.id }) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                                border = BorderStroke(1.dp, BorderSlate),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${log.action} • ${log.actorRole}", style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent)
                                    Text(log.summary, style = MaterialTheme.typography.bodyMedium, color = PureWhiteText)
                                    Text(log.actorEmail, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = BrightBlueAccent)
        }
    }
}

@Composable
private fun CreateBroadcastForm(
    onCreate: (String, String, String, String, String, String?, String, String, String, String, String, Boolean, Boolean) -> Unit
) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sportCategory by remember { mutableStateOf("FOOTBALL") }
    var streamType by remember { mutableStateOf("HLS") }
    var playbackUrl by remember { mutableStateOf("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8") }
    var youtubeId by remember { mutableStateOf("") }
    var ingestProtocol by remember { mutableStateOf("OBS_RTMP") }
    var ingestEndpoint by remember { mutableStateOf("rtmp://ingest.sportniger.ne/live") }
    var maskedKey by remember { mutableStateOf("sn_live_••••_obs") }
    var chatEnabled by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, PrimaryBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.createBroadcast, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = BrightBlueAccent)
                Text(strings.streamSecretsNote, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
            }
            OutlinedTextField(title, { title = it }, label = { Text("Broadcast Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sportCategory, { sportCategory = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(streamType, { streamType = it }, label = { Text("HLS / YOUTUBE_LIVE") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(playbackUrl, { playbackUrl = it }, label = { Text("Public Playback URL (.m3u8 / YouTube)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(youtubeId, { youtubeId = it }, label = { Text("YouTube Video ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ingestProtocol, { ingestProtocol = it }, label = { Text("OBS_RTMP / MOBILE_LIVE") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(maskedKey, { maskedKey = it }, label = { Text("Masked Key Label") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = chatEnabled, onCheckedChange = { chatEnabled = it })
                Text("Enable Live Chat", color = PureWhiteText)
            }
            Button(
                onClick = {
                    if (title.isNotBlank() && playbackUrl.isNotBlank()) {
                        onCreate(
                            title, description, sportCategory, streamType, playbackUrl,
                            youtubeId.takeIf { it.isNotBlank() }, ingestProtocol, ingestEndpoint,
                            maskedKey, "OBS Studio 1080p60 CBR", "LIVE", chatEnabled, true
                        )
                        title = ""
                        description = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text(strings.createBroadcast)
            }
        }
    }
}

@Composable
private fun AddWrestlerForm(onAdd: (String, String, String, String, Long, String) -> Unit) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Maradi") }
    var weight by remember { mutableStateOf("108") }
    var bio by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = CardNavySurface), border = BorderStroke(1.dp, BorderSlate), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.addWrestler, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            OutlinedTextField(name, { name = it }, label = { Text("Wrestler Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it }, label = { Text("Weight kg") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(village, { village = it }, label = { Text("Village") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(region, { region = it }, label = { Text("Region") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (name.isNotBlank() && village.isNotBlank()) {
                        onAdd(name, nickname.ifBlank { name }, village, region, weight.toLongOrNull() ?: 105L, bio)
                        name = ""; nickname = ""; village = ""; bio = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) { Text(strings.addWrestler) }
        }
    }
}

@Composable
private fun AddKokowaEventForm(onAdd: (String, String, String, String, String, String, String, String, String?) -> Unit) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("Arène de Maradi") }
    var region by remember { mutableStateOf("Maradi") }
    var wrestlerA by remember { mutableStateOf("") }
    var wrestlerB by remember { mutableStateOf("") }
    var roundLabel by remember { mutableStateOf("Semi-Final") }
    var winner by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = CardNavySurface), border = BorderStroke(1.dp, BorderSlate), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.addKokowaEvent, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            OutlinedTextField(title, { title = it }, label = { Text("Event Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(wrestlerA, { wrestlerA = it }, label = { Text("Wrestler A") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(wrestlerB, { wrestlerB = it }, label = { Text("Wrestler B") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(location, { location = it }, label = { Text("Arena") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(region, { region = it }, label = { Text("Region") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(roundLabel, { roundLabel = it }, label = { Text("Round") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(winner, { winner = it }, label = { Text("Winner (if finished)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Button(
                onClick = {
                    if (title.isNotBlank() && wrestlerA.isNotBlank() && wrestlerB.isNotBlank()) {
                        val status = if (winner.isNotBlank()) "COMPLETED" else "UPCOMING"
                        onAdd(title, location, region, wrestlerA, wrestlerB, roundLabel, "Upcoming", status, winner.takeIf { it.isNotBlank() })
                        title = ""; wrestlerA = ""; wrestlerB = ""; winner = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) { Text(strings.addKokowaEvent) }
        }
    }
}

@Composable
private fun AddSportCategoryForm(onAdd: (String, String, String, String, String, Boolean) -> Unit) {
    val strings = LocalStrings.current
    var slug by remember { mutableStateOf("") }
    var en by remember { mutableStateOf("") }
    var ha by remember { mutableStateOf("") }
    var fr by remember { mutableStateOf("") }
    var ar by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = DeepBlueContainer.copy(alpha = 0.6f)), border = BorderStroke(1.dp, BrightBlueAccent), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.addSportCategory, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(slug, { slug = it }, label = { Text("Slug (e.g. LANGA)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(en, { en = it }, label = { Text("English") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ha, { ha = it }, label = { Text("Hausa") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(fr, { fr = it }, label = { Text("French") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(ar, { ar = it }, label = { Text("Arabic") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (slug.isNotBlank() && en.isNotBlank()) {
                        onAdd(slug, en, ha, fr, ar, true)
                        slug = ""; en = ""; ha = ""; fr = ""; ar = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) { Text(strings.addSportCategory) }
        }
    }
}

@Composable
private fun AddTournamentForm(onAdd: (String, String, String, String, String, String?) -> Unit) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("FOOTBALL") }
    var season by remember { mutableStateOf("2026") }
    var location by remember { mutableStateOf("Niamey") }
    var participants by remember { mutableStateOf("AS FAN, USGN, Sahel SC, AS Douanes") }
    var note by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = CardNavySurface), border = BorderStroke(1.dp, BorderSlate), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.addTournament, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            OutlinedTextField(name, { name = it }, label = { Text("Tournament Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sport, { sport = it }, label = { Text("Sport") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(season, { season = it }, label = { Text("Season") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(participants, { participants = it }, label = { Text("Participants (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Announcement") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(name, sport, season, location, participants, note.takeIf { it.isNotBlank() })
                        name = ""; note = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) { Text(strings.addTournament) }
        }
    }
}

@Composable
private fun PublishNewsForm(onPublish: (String, String, String, String, Boolean) -> Unit) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("FOOTBALL") }
    var breaking by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = CardNavySurface), border = BorderStroke(1.dp, BorderSlate), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.publishNews, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            OutlinedTextField(title, { title = it }, label = { Text("Headline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(summary, { summary = it }, label = { Text("Summary") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Full Story") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sport, { sport = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = breaking, onCheckedChange = { breaking = it })
                    Text(strings.breakingNews, color = PureWhiteText)
                }
            }
            Button(
                onClick = {
                    if (title.isNotBlank() && summary.isNotBlank()) {
                        onPublish(title, summary, body, sport, breaking)
                        title = ""; summary = ""; body = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) { Text(strings.publishNews) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignStaffForm(onAssign: (String, String, String, StaffRole) -> Unit) {
    val strings = LocalStrings.current
    var uid by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(StaffRole.SCORE_MANAGER) }
    var expanded by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = CardNavySurface), border = BorderStroke(1.dp, BrightBlueAccent), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.assignStaffRole, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            OutlinedTextField(uid, { uid = it }, label = { Text("User Firebase UID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Staff Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedRole.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.roleLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    StaffRole.entries.filter { it != StaffRole.VIEWER }.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.displayName) },
                            onClick = {
                                selectedRole = role
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (uid.isNotBlank() && email.isNotBlank() && displayName.isNotBlank()) {
                        onAssign(uid, email, displayName, selectedRole)
                        uid = ""; email = ""; displayName = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) { Text(strings.assignStaffRole) }
        }
    }
}
