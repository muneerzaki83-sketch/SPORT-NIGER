package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.Tournament
import com.example.data.model.UiState
import com.example.data.repository.AuthRepository
import com.example.localization.AppLanguage
import com.example.localization.LocalAppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateView
import com.example.ui.components.TournamentCard
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.ElevatedNavySurface
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.NigerSaffron
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SoftSteelText
import com.example.ui.theme.VictoryEmerald
import com.example.ui.viewmodel.SportNigerViewModel

@Composable
fun TournamentsScreen(
    viewModel: SportNigerViewModel,
    onOpenTournament: (String) -> Unit
) {
    val strings = LocalStrings.current
    val tournamentsState by viewModel.tournaments.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = strings.tournamentsTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhiteText
            )
        }
        when (val state = tournamentsState) {
            UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrightBlueAccent)
                }
            }
            is UiState.Error -> item { EmptyStateView(state.message) }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    item { EmptyStateView(strings.emptyListDefault) }
                } else {
                    items(state.data, key = { it.id }) { t ->
                        TournamentCard(tournament = t, onClick = { onOpenTournament(t.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun TournamentDetailScreen(
    tournamentId: String,
    viewModel: SportNigerViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val tournamentsState by viewModel.tournaments.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val tournament: Tournament? = (tournamentsState as? UiState.Success)?.data?.firstOrNull { it.id == tournamentId }
    val isFavorite = userProfile?.favoriteTournamentIds?.contains(tournamentId) == true

    Column(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = PureWhiteText)
                }
                Text(tournament?.name ?: strings.tournamentsTitle, style = MaterialTheme.typography.titleMedium, color = PureWhiteText, maxLines = 1)
            }
            OutlinedButton(
                onClick = { viewModel.toggleFavoriteTournament(tournamentId) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = strings.favorited,
                    tint = if (isFavorite) BrightBlueAccent else PureWhiteText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (tournament == null) {
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${tournament.sportCategory} • ${tournament.season}", style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent)
                        Text(tournament.name, style = MaterialTheme.typography.headlineMedium, color = PureWhiteText)
                        Text("${tournament.location} • ${tournament.status}", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                        if (!tournament.championName.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NigerSaffron)
                                Text("${strings.championLabel}: ${tournament.championName}", style = MaterialTheme.typography.titleMedium, color = NigerSaffron)
                            }
                        }
                    }
                }
            }

            if (!tournament.announcement.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepBlueContainer.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, BrightBlueAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(strings.announcementsLabel, style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent)
                            Text(tournament.announcement, style = MaterialTheme.typography.bodyMedium, color = PureWhiteText)
                        }
                    }
                }
            }

            item {
                Text(strings.knockoutBracket, style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
            }
            items(tournament.knockoutBracketSummary) { line ->
                val parts = line.split("|", limit = 2)
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
                        Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlue.copy(alpha = 0.25f)) {
                            Text(
                                text = parts.firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrightBlueAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = parts.getOrNull(1) ?: line,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PureWhiteText
                        )
                    }
                }
            }

            item {
                Text("${strings.participantsLabel} (${tournament.participantNames.size})", style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tournament.participantNames.forEachIndexed { idx, name ->
                            Text("${idx + 1}. $name", style = MaterialTheme.typography.bodyLarge, color = PureWhiteText)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SportNigerViewModel,
    onOpenAdmin: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val currentLanguage = LocalAppLanguage.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val staffRole by viewModel.staffRole.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.profileTitle, style = MaterialTheme.typography.headlineMedium, color = PureWhiteText)
        }

        // User Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!userProfile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userProfile?.photoUrl,
                            contentDescription = userProfile?.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = BrightBlueAccent, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(userProfile?.displayName ?: "SPORT NIGER Viewer", style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
                        if (!userProfile?.email.isNullOrBlank()) {
                            Text(userProfile?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (staffRole.isStaff) PrimaryBlue.copy(alpha = 0.25f) else ElevatedNavySurface
                        ) {
                            Text(
                                text = staffRole.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (staffRole.isStaff) BrightBlueAccent else SoftSteelText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Favorites summary
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FavoriteCountChip(strings.favoriteTeamsCount, userProfile?.favoriteTeamIds?.size ?: 0, Modifier.weight(1f))
                FavoriteCountChip(strings.favoriteWrestlersCount, userProfile?.favoriteWrestlerIds?.size ?: 0, Modifier.weight(1f))
                FavoriteCountChip(strings.favoriteTournamentsCount, userProfile?.favoriteTournamentIds?.size ?: 0, Modifier.weight(1f))
            }
        }

        // Staff / Admin Console Button
        if (staffRole.isStaff) {
            item {
                Button(
                    onClick = onOpenAdmin,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("profile_open_admin_button")
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${strings.openAdminDashboard} (${staffRole.displayName})")
                }
            }
        }

        // Language Switcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = BrightBlueAccent)
                        Text(strings.languageSetting, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            val selected = lang == currentLanguage
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) PrimaryBlue else ElevatedNavySurface,
                                border = BorderStroke(1.dp, if (selected) BrightBlueAccent else BorderSlate),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.switchLanguage(lang) }
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_lang_${lang.code}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text(lang.nativeLabel, style = MaterialTheme.typography.labelSmall, color = PureWhiteText)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notification Preferences
        item {
            val profile = userProfile
            val matchStart = profile?.notifyMatchStart ?: true
            val goals = profile?.notifyGoals ?: true
            val kokowa = profile?.notifyKokowa ?: true
            val tournaments = profile?.notifyTournaments ?: true
            val news = profile?.notifyNews ?: false

            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = BrightBlueAccent)
                        Text(strings.notificationPreferences, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                    }
                    NotificationToggleRow(strings.notifyMatchStart, matchStart) {
                        viewModel.updateNotificationPrefs(it, goals, kokowa, tournaments, news)
                    }
                    NotificationToggleRow(strings.notifyGoals, goals) {
                        viewModel.updateNotificationPrefs(matchStart, it, kokowa, tournaments, news)
                    }
                    NotificationToggleRow(strings.notifyKokowa, kokowa) {
                        viewModel.updateNotificationPrefs(matchStart, goals, it, tournaments, news)
                    }
                    NotificationToggleRow(strings.notifyTournaments, tournaments) {
                        viewModel.updateNotificationPrefs(matchStart, goals, kokowa, it, news)
                    }
                    NotificationToggleRow(strings.notifyNews, news) {
                        viewModel.updateNotificationPrefs(matchStart, goals, kokowa, tournaments, it)
                    }
                }
            }
        }

        // Free Public Streaming Transparency Note
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedNavySurface),
                border = BorderStroke(1.dp, VictoryEmerald.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.freeModelNoteTitle, style = MaterialTheme.typography.titleMedium, color = VictoryEmerald)
                    Text(strings.freeModelNoteBody, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                }
            }
        }

        // Sign Out
        item {
            OutlinedButton(
                onClick = {
                    AuthRepository.signOut(context, scope, onSignedOut)
                },
                border = BorderStroke(1.dp, LiveCrimson),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = LiveCrimson)
                Spacer(Modifier.width(8.dp))
                Text(strings.signOut, color = LiveCrimson)
            }
        }
    }
}

@Composable
private fun FavoriteCountChip(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = MaterialTheme.typography.titleLarge, color = BrightBlueAccent)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SoftSteelText, maxLines = 1)
        }
    }
}

@Composable
private fun NotificationToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PureWhiteText, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
        )
    }
}
