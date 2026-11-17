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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MatchEvent
import com.example.data.model.SportMatch
import com.example.data.model.Team
import com.example.data.model.UiState
import com.example.data.remote.FootballApiFeedResult
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.MatchScoreCard
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.CardYellow
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.ElevatedNavySurface
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.PitchNavySurface
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.ScoreMonoFont
import com.example.ui.theme.SoftSteelText
import com.example.ui.theme.VictoryEmerald
import com.example.ui.viewmodel.SportNigerViewModel

@Composable
fun ScoresScreen(
    viewModel: SportNigerViewModel,
    onOpenMatch: (String) -> Unit
) {
    val strings = LocalStrings.current
    val matchesState by viewModel.matches.collectAsStateWithLifecycle()
    val teamsState by viewModel.teams.collectAsStateWithLifecycle()
    val apiFeed by viewModel.apiFeedResult.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.tabLiveScores,
        strings.tabFixtures,
        strings.tabResults,
        strings.tabStandings,
        strings.tabApiFeed
    )

    Column(modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.testTag("scores_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0, 1, 2 -> {
                    when (val state = matchesState) {
                        UiState.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrightBlueAccent)
                            }
                        }
                        is UiState.Error -> item { EmptyStateView(state.message) }
                        is UiState.Success -> {
                            val list = when (selectedTab) {
                                0 -> state.data.filter { it.status == "LIVE" || it.status == "HALFTIME" }
                                1 -> state.data.filter { it.status == "UPCOMING" }
                                else -> state.data.filter { it.status == "COMPLETED" }
                            }
                            if (list.isEmpty()) {
                                item { EmptyStateView(strings.emptyListDefault) }
                            } else {
                                items(list, key = { it.id }) { match ->
                                    MatchScoreCard(match = match, onClick = { onOpenMatch(match.id) })
                                }
                            }
                        }
                    }
                }
                3 -> {
                    when (val ts = teamsState) {
                        UiState.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrightBlueAccent)
                            }
                        }
                        is UiState.Error -> item { EmptyStateView(ts.message) }
                        is UiState.Success -> {
                            if (ts.data.isEmpty()) {
                                item { EmptyStateView(strings.emptyListDefault) }
                            } else {
                                item { StandingsTable(teams = ts.data) }
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        ApiFeedSection(
                            apiFeed = apiFeed,
                            onRefresh = viewModel::refreshFootballApiFeed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingsTable(teams: List<Team>) {
    val strings = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("#", style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.width(24.dp))
                Text(strings.teamCol, style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.weight(1f))
                Text(strings.playedCol, style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.width(28.dp))
                Text(strings.wonCol, style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.width(28.dp))
                Text(strings.drawnCol, style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.width(28.dp))
                Text(strings.lostCol, style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.width(28.dp))
                Text(strings.gdCol, style = MaterialTheme.typography.labelSmall, color = MutedSteelText, modifier = Modifier.width(34.dp))
                Text(strings.ptsCol, style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent, modifier = Modifier.width(34.dp))
            }
            HorizontalDivider(color = BorderSlate)
            teams.forEachIndexed { index, team ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText, modifier = Modifier.width(24.dp))
                    Text(team.name, style = MaterialTheme.typography.bodyMedium, color = PureWhiteText, modifier = Modifier.weight(1f))
                    Text("${team.played}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText, modifier = Modifier.width(28.dp))
                    Text("${team.won}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText, modifier = Modifier.width(28.dp))
                    Text("${team.drawn}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText, modifier = Modifier.width(28.dp))
                    Text("${team.lost}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText, modifier = Modifier.width(28.dp))
                    Text("${team.goalDifference}", style = MaterialTheme.typography.labelSmall, color = SoftSteelText, modifier = Modifier.width(34.dp))
                    Text("${team.points}", style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent, modifier = Modifier.width(34.dp))
                }
                if (index < teams.lastIndex) {
                    HorizontalDivider(color = BorderSlate.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun ApiFeedSection(
    apiFeed: FootballApiFeedResult?,
    onRefresh: () -> Unit
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardNavySurface),
            border = BorderStroke(1.dp, BorderSlate),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = BrightBlueAccent)
                    Text(strings.apiNotConfiguredTitle, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                }
                val bodyText = when (apiFeed) {
                    is FootballApiFeedResult.Unavailable -> "${strings.apiUnavailableBody} (${apiFeed.reason})"
                    is FootballApiFeedResult.Available -> "${strings.apiSourceBadge}: ${apiFeed.matches.size} matches loaded"
                    else -> strings.apiNotConfiguredBody
                }
                Text(bodyText, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.retry, color = BrightBlueAccent)
                }
            }
        }

        if (apiFeed is FootballApiFeedResult.Available) {
            apiFeed.matches.forEach { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.competition?.name ?: "League", style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent)
                        Text(
                            "${m.homeTeam?.name.orEmpty()} ${m.score?.fullTime?.home ?: 0} - ${m.score?.fullTime?.away ?: 0} ${m.awayTeam?.name.orEmpty()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhiteText
                        )
                        Text(m.status, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchDetailScreen(
    matchId: String,
    viewModel: SportNigerViewModel,
    onWatchBroadcast: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val matchesState by viewModel.matches.collectAsStateWithLifecycle()
    val staffRole by viewModel.staffRole.collectAsStateWithLifecycle()
    val match: SportMatch? = (matchesState as? UiState.Success)?.data?.firstOrNull { it.id == matchId }
    val eventsFlow = remember(matchId) { viewModel.observeEventsForMatch(matchId) }
    val eventsState by eventsFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)

    var homeScoreInput by remember(match) { mutableStateOf(match?.homeScore?.toString() ?: "0") }
    var awayScoreInput by remember(match) { mutableStateOf(match?.awayScore?.toString() ?: "0") }
    var minuteInput by remember(match) { mutableStateOf(match?.matchMinute ?: "LIVE") }
    var playerNameInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("match_detail_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = PureWhiteText)
            }
            Text(match?.leagueName ?: strings.tabLiveScores, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
        }

        if (match == null) {
            EmptyStateView(strings.emptyListDefault, modifier = Modifier.padding(16.dp))
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, PrimaryBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LivePulseBadge(
                            label = "${match.status} • ${match.matchMinute}",
                            isLive = match.status == "LIVE" || match.status == "HALFTIME"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(match.homeTeamName, style = MaterialTheme.typography.titleMedium, color = PureWhiteText, modifier = Modifier.weight(1f))
                            Text(
                                "${match.homeScore} - ${match.awayScore}",
                                fontFamily = ScoreMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                color = BrightBlueAccent,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Text(match.awayTeamName, style = MaterialTheme.typography.titleMedium, color = PureWhiteText, modifier = Modifier.weight(1f))
                        }
                        Text("${match.venue} • ${strings.manualSourceBadge}", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                        if (!match.broadcastId.isNullOrBlank()) {
                            Button(
                                onClick = { onWatchBroadcast(match.broadcastId) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("match_watch_live_btn")
                            ) {
                                Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.watchLive)
                            }
                        }
                    }
                }
            }

            // Stats Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(strings.matchStats, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                        StatComparisonBar(strings.possession, "${match.possessionHome}%", "${100 - match.possessionHome}%", match.possessionHome / 100f)
                        val totalShots = (match.shotsHome + match.shotsAway).coerceAtLeast(1L)
                        StatComparisonBar(strings.shots, "${match.shotsHome}", "${match.shotsAway}", match.shotsHome.toFloat() / totalShots)
                        val totalCorners = (match.cornersHome + match.cornersAway).coerceAtLeast(1L)
                        StatComparisonBar(strings.corners, "${match.cornersHome}", "${match.cornersAway}", match.cornersHome.toFloat() / totalCorners)
                    }
                }
            }

            // Events Timeline
            item {
                Text(strings.matchTimeline, style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
            }
            when (val es = eventsState) {
                UiState.Loading -> item { CircularProgressIndicator(color = BrightBlueAccent) }
                is UiState.Error -> item { Text(es.message, color = LiveCrimson) }
                is UiState.Success -> {
                    if (es.data.isEmpty()) {
                        item { EmptyStateView(strings.emptyListDefault) }
                    } else {
                        items(es.data, key = { it.id }) { ev ->
                            MatchEventRow(ev)
                        }
                    }
                }
            }

            // Staff Quick Score & Event Entry
            if (staffRole.canManageScores()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepBlueContainer.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, BrightBlueAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Score Manager Controls (${staffRole.displayName})", style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = homeScoreInput,
                                    onValueChange = { homeScoreInput = it },
                                    label = { Text(match.homeTeamName.take(10)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = awayScoreInput,
                                    onValueChange = { awayScoreInput = it },
                                    label = { Text(match.awayTeamName.take(10)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = minuteInput,
                                    onValueChange = { minuteInput = it },
                                    label = { Text("Min") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.updateMatchScore(
                                            match.id,
                                            homeScoreInput.toLongOrNull() ?: match.homeScore,
                                            awayScoreInput.toLongOrNull() ?: match.awayScore,
                                            "LIVE",
                                            minuteInput
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.updateScore)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateMatchScore(
                                            match.id,
                                            homeScoreInput.toLongOrNull() ?: match.homeScore,
                                            awayScoreInput.toLongOrNull() ?: match.awayScore,
                                            "COMPLETED",
                                            "FT"
                                        )
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("FT", color = VictoryEmerald)
                                }
                            }
                            OutlinedTextField(
                                value = playerNameInput,
                                onValueChange = { playerNameInput = it },
                                label = { Text("Player Name (Goal / Card)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (playerNameInput.isNotBlank()) {
                                            viewModel.addMatchEvent(match.id, 75L, "GOAL", "HOME", playerNameInput, null)
                                            playerNameInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VictoryEmerald),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("+ ${strings.goalLabel}", color = DarkNavyBackground)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (playerNameInput.isNotBlank()) {
                                            viewModel.addMatchEvent(match.id, 75L, "YELLOW_CARD", "AWAY", playerNameInput, null)
                                            playerNameInput = ""
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("+ ${strings.yellowCardLabel}", color = CardYellow)
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
private fun StatComparisonBar(label: String, left: String, right: String, ratio: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, style = MaterialTheme.typography.labelSmall, color = PureWhiteText)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
            Text(right, style = MaterialTheme.typography.labelSmall, color = PureWhiteText)
        }
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            color = PrimaryBlue,
            trackColor = ElevatedNavySurface,
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

@Composable
private fun MatchEventRow(event: MatchEvent) {
    val strings = LocalStrings.current
    val (label, badgeColor) = when (event.type) {
        "GOAL" -> strings.goalLabel to VictoryEmerald
        "YELLOW_CARD" -> strings.yellowCardLabel to CardYellow
        "RED_CARD" -> strings.redCardLabel to LiveCrimson
        else -> strings.substitutionLabel to BrightBlueAccent
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardNavySurface,
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("${event.minute}'", fontFamily = ScoreMonoFont, color = BrightBlueAccent, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(4.dp), color = badgeColor.copy(alpha = 0.2f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = badgeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(event.playerName, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                if (!event.detail.isNullOrBlank()) {
                    Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                }
            }
            Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = SoftSteelText, modifier = Modifier.size(16.dp))
        }
    }
}
