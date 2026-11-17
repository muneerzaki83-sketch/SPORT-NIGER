package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Broadcast
import com.example.data.model.HighlightVideo
import com.example.data.model.KokowaEvent
import com.example.data.model.NewsArticle
import com.example.data.model.SponsorBanner
import com.example.data.model.SportCategory
import com.example.data.model.SportMatch
import com.example.data.model.Tournament
import com.example.data.model.UiState
import com.example.data.model.Wrestler
import com.example.localization.LocalAppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HeroBroadcastCard
import com.example.ui.components.HighlightCard
import com.example.ui.components.MatchScoreCard
import com.example.ui.components.NewsCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SponsorBannerCard
import com.example.ui.components.SportNigerTopBranding
import com.example.ui.components.TournamentCard
import com.example.ui.components.WrestlerCard
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.ElevatedNavySurface
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.NigerSaffron
import com.example.ui.theme.PitchNavySurface
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SoftSteelText
import com.example.ui.viewmodel.SportNigerViewModel

@Composable
fun HomeScreen(
    viewModel: SportNigerViewModel,
    onOpenBroadcast: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenKokowa: () -> Unit,
    onOpenWrestler: (String) -> Unit,
    onOpenTournament: (String) -> Unit,
    onNavigateToLive: () -> Unit,
    onNavigateToScores: () -> Unit,
    onNavigateToTournaments: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategorySlug.collectAsStateWithLifecycle()
    val staffRole by viewModel.staffRole.collectAsStateWithLifecycle()

    val categoriesState by viewModel.categories.collectAsStateWithLifecycle()
    val broadcastsState by viewModel.broadcasts.collectAsStateWithLifecycle()
    val matchesState by viewModel.matches.collectAsStateWithLifecycle()
    val wrestlersState by viewModel.wrestlers.collectAsStateWithLifecycle()
    val kokowaState by viewModel.kokowaEvents.collectAsStateWithLifecycle()
    val tournamentsState by viewModel.tournaments.collectAsStateWithLifecycle()
    val newsState by viewModel.newsArticles.collectAsStateWithLifecycle()
    val highlightsState by viewModel.highlights.collectAsStateWithLifecycle()
    val bannersState by viewModel.banners.collectAsStateWithLifecycle()

    var activeArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var activeHighlight by remember { mutableStateOf<HighlightVideo?>(null) }

    val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
    val broadcasts = (broadcastsState as? UiState.Success)?.data.orEmpty()
    val matches = (matchesState as? UiState.Success)?.data.orEmpty()
    val wrestlers = (wrestlersState as? UiState.Success)?.data.orEmpty()
    val kokowaEvents = (kokowaState as? UiState.Success)?.data.orEmpty()
    val tournaments = (tournamentsState as? UiState.Success)?.data.orEmpty()
    val news = (newsState as? UiState.Success)?.data.orEmpty()
    val highlights = (highlightsState as? UiState.Success)?.data.orEmpty()
    val banners = (bannersState as? UiState.Success)?.data.orEmpty()

    val q = searchQuery.trim().lowercase()
    fun matchesFilter(category: String, text: String): Boolean {
        val catOk = selectedCategory == "ALL" || category.equals(selectedCategory, ignoreCase = true)
        val searchOk = q.isBlank() || text.lowercase().contains(q)
        return catOk && searchOk
    }

    val filteredBroadcasts = broadcasts.filter { matchesFilter(it.sportCategory, "${it.title} ${it.description}") }
    val filteredMatches = matches.filter { matchesFilter(it.sportCategory, "${it.homeTeamName} ${it.awayTeamName} ${it.leagueName}") }
    val filteredWrestlers = wrestlers.filter {
        (selectedCategory == "ALL" || selectedCategory == "KOKOWA") &&
            (q.isBlank() || "${it.name} ${it.nickname} ${it.village} ${it.region}".lowercase().contains(q))
    }
    val filteredKokowaEvents = kokowaEvents.filter {
        (selectedCategory == "ALL" || selectedCategory == "KOKOWA") &&
            (q.isBlank() || "${it.title} ${it.wrestlerAName} ${it.wrestlerBName} ${it.region}".lowercase().contains(q))
    }
    val filteredTournaments = tournaments.filter { matchesFilter(it.sportCategory, "${it.name} ${it.location}") }
    val filteredNews = news.filter { matchesFilter(it.sportCategory, "${it.title} ${it.summary}") }
    val filteredHighlights = highlights.filter { matchesFilter(it.sportCategory, it.title) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header + Admin Shortcut if Staff
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SportNigerTopBranding()
                if (staffRole.isStaff) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PrimaryBlue.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, BrightBlueAccent),
                        modifier = Modifier
                            .clickable(onClick = onOpenAdmin)
                            .minimumInteractiveComponentSize()
                            .testTag("home_admin_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = strings.openAdminDashboard,
                                tint = BrightBlueAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = staffRole.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhiteText
                            )
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text(strings.searchPlaceholder, color = MutedSteelText) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = BrightBlueAccent)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = strings.close, tint = SoftSteelText)
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardNavySurface,
                    unfocusedContainerColor = CardNavySurface,
                    focusedBorderColor = BrightBlueAccent,
                    unfocusedBorderColor = BorderSlate,
                    focusedTextColor = PureWhiteText,
                    unfocusedTextColor = PureWhiteText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("home_search_input")
            )
        }

        // Dynamic Sports Category Filter Row
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == "ALL",
                        onClick = { viewModel.onCategorySelected("ALL") },
                        label = { Text(strings.allSports) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = PureWhiteText,
                            containerColor = CardNavySurface,
                            labelColor = SoftSteelText
                        ),
                        modifier = Modifier.testTag("category_chip_all")
                    )
                }
                if (categories.isEmpty()) {
                    item {
                        FilterChip(
                            selected = selectedCategory == "FOOTBALL",
                            onClick = { viewModel.onCategorySelected("FOOTBALL") },
                            label = { Text(strings.football) },
                            modifier = Modifier.testTag("category_chip_football")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == "KOKOWA",
                            onClick = { viewModel.onCategorySelected("KOKOWA") },
                            label = { Text(strings.kokowa) },
                            modifier = Modifier.testTag("category_chip_kokowa")
                        )
                    }
                } else {
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.slug,
                            onClick = { viewModel.onCategorySelected(cat.slug) },
                            label = { Text(cat.localizedName(language)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = PureWhiteText,
                                containerColor = CardNavySurface,
                                labelColor = SoftSteelText
                            ),
                            modifier = Modifier.testTag("category_chip_${cat.slug.lowercase()}")
                        )
                    }
                }
            }
        }

        // Staff one-tap catalog initializer when Firestore is fresh/empty
        if (staffRole.isStaff && broadcastsState is UiState.Success && broadcasts.isEmpty() && matches.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepBlueContainer),
                    border = BorderStroke(1.dp, BrightBlueAccent)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SPORT NIGER Catalog Ready to Initialize",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhiteText
                        )
                        Text(
                            text = "Seed official Super Ligue Niger clubs, live HLS streams, Sabre National Kokowa wrestlers, tournaments, and news into Cloud Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftSteelText
                        )
                        Button(
                            onClick = viewModel::seedOfficialCatalog,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("seed_catalog_home_button")
                        ) {
                            Text(strings.seedCatalogButton)
                        }
                    }
                }
            }
        }

        // Loading indicator if initial streams are loading
        if (broadcastsState is UiState.Loading && matchesState is UiState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrightBlueAccent)
                }
            }
        }

        // Optional Sponsor Banner
        if (banners.isNotEmpty()) {
            item {
                SponsorBannerCard(
                    banner = banners.first(),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Featured Live Broadcasts
        if (filteredBroadcasts.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.liveBroadcasts,
                    actionLabel = strings.seeAll,
                    onActionClick = onNavigateToLive
                )
            }
            items(filteredBroadcasts.take(3), key = { it.id }) { bc ->
                HeroBroadcastCard(
                    broadcast = bc,
                    onWatchClick = { onOpenBroadcast(bc.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Dedicated Kokowa Spotlight Entry Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = onOpenKokowa)
                    .testTag("home_kokowa_banner"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                border = BorderStroke(1.dp, NigerSaffron.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(NigerSaffron.copy(alpha = 0.22f), CardNavySurface, PitchNavySurface)
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NigerSaffron.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SportsKabaddi,
                                    contentDescription = strings.kokowa,
                                    tint = NigerSaffron,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = strings.kokowaSpotlight,
                                style = MaterialTheme.typography.titleLarge,
                                color = PureWhiteText
                            )
                            Text(
                                text = strings.kokowaSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftSteelText,
                                maxLines = 2
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onOpenKokowa,
                        colors = ButtonDefaults.buttonColors(containerColor = NigerSaffron),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(strings.openKokowaHub, color = PitchNavySurface)
                    }
                }
            }
        }

        // Featured Live & Upcoming Matches + Results
        val liveOrUpcoming = filteredMatches.filter { it.status != "COMPLETED" }
        val completedMatches = filteredMatches.filter { it.status == "COMPLETED" }

        if (liveOrUpcoming.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.featuredLiveMatches,
                    actionLabel = strings.seeAll,
                    onActionClick = onNavigateToScores
                )
            }
            items(liveOrUpcoming.take(4), key = { it.id }) { match ->
                MatchScoreCard(
                    match = match,
                    onClick = { onOpenMatch(match.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (completedMatches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.latestResults,
                    actionLabel = strings.seeAll,
                    onActionClick = onNavigateToScores
                )
            }
            items(completedMatches.take(3), key = { it.id }) { match ->
                MatchScoreCard(
                    match = match,
                    onClick = { onOpenMatch(match.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Featured Wrestlers
        if (filteredWrestlers.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.featuredWrestlers,
                    actionLabel = strings.openKokowaHub,
                    onActionClick = onOpenKokowa
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredWrestlers, key = { it.id }) { wrestler ->
                        WrestlerCard(
                            wrestler = wrestler,
                            onClick = { onOpenWrestler(wrestler.id) }
                        )
                    }
                }
            }
        }

        // Upcoming Kokowa Events
        if (filteredKokowaEvents.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.upcomingKokowa,
                    actionLabel = strings.openKokowaHub,
                    onActionClick = onOpenKokowa
                )
            }
            items(filteredKokowaEvents.take(3), key = { it.id }) { ev ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onOpenKokowa),
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(ev.roundLabel, style = MaterialTheme.typography.labelSmall, color = NigerSaffron)
                        Text("${ev.wrestlerAName} vs ${ev.wrestlerBName}", style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
                        Text("${ev.location} (${ev.region}) • ${ev.scheduledLabel}", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
                    }
                }
            }
        }

        // Popular Tournaments
        if (filteredTournaments.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.popularTournaments,
                    actionLabel = strings.seeAll,
                    onActionClick = onNavigateToTournaments
                )
            }
            items(filteredTournaments.take(3), key = { it.id }) { tournament ->
                TournamentCard(
                    tournament = tournament,
                    onClick = { onOpenTournament(tournament.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Featured Video Highlights
        if (filteredHighlights.isNotEmpty()) {
            item {
                SectionHeader(title = strings.featuredHighlights)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredHighlights, key = { it.id }) { hl ->
                        HighlightCard(
                            highlight = hl,
                            onClick = { activeHighlight = hl }
                        )
                    }
                }
            }
        }

        // Sports News & Announcements
        if (filteredNews.isNotEmpty()) {
            item {
                SectionHeader(title = strings.sportsNews)
            }
            items(filteredNews, key = { it.id }) { article ->
                NewsCard(
                    article = article,
                    onClick = { activeArticle = article },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (filteredBroadcasts.isEmpty() && filteredMatches.isEmpty() && filteredWrestlers.isEmpty() &&
            filteredTournaments.isEmpty() && filteredNews.isEmpty() && broadcastsState is UiState.Success
        ) {
            item {
                EmptyStateView(
                    message = strings.emptyListDefault,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    activeArticle?.let { article ->
        AlertDialog(
            onDismissRequest = { activeArticle = null },
            containerColor = CardNavySurface,
            title = { Text(article.title, color = PureWhiteText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${article.sportCategory} • ${article.authorName}", style = MaterialTheme.typography.labelSmall, color = BrightBlueAccent)
                    Text(article.body, style = MaterialTheme.typography.bodyLarge, color = SoftSteelText)
                }
            },
            confirmButton = {
                TextButton(onClick = { activeArticle = null }) {
                    Text(strings.close, color = BrightBlueAccent)
                }
            }
        )
    }

    activeHighlight?.let { hl ->
        AlertDialog(
            onDismissRequest = { activeHighlight = null },
            containerColor = CardNavySurface,
            title = { Text(hl.title, color = PureWhiteText) },
            text = {
                com.example.ui.components.HlsVideoPlayer(
                    playbackUrl = hl.videoUrl,
                    streamType = if (!hl.youtubeVideoId.isNullOrBlank()) "YOUTUBE_LIVE" else "HLS",
                    youtubeVideoId = hl.youtubeVideoId,
                    isFullscreen = false,
                    onToggleFullscreen = {}
                )
            },
            confirmButton = {
                TextButton(onClick = { activeHighlight = null }) {
                    Text(strings.close, color = BrightBlueAccent)
                }
            }
        )
    }
}
