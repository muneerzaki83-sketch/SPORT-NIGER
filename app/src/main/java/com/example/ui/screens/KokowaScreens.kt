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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.KokowaEvent
import com.example.data.model.UiState
import com.example.data.model.Wrestler
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HeroBroadcastCard
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.NewsCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.WrestlerCard
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.NigerSaffron
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SoftSteelText
import com.example.ui.theme.VictoryEmerald
import com.example.ui.viewmodel.SportNigerViewModel

@Composable
fun KokowaScreen(
    viewModel: SportNigerViewModel,
    onOpenBroadcast: (String) -> Unit,
    onOpenWrestler: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val broadcastsState by viewModel.broadcasts.collectAsStateWithLifecycle()
    val wrestlersState by viewModel.wrestlers.collectAsStateWithLifecycle()
    val eventsState by viewModel.kokowaEvents.collectAsStateWithLifecycle()
    val newsState by viewModel.newsArticles.collectAsStateWithLifecycle()

    val kokowaBroadcasts = (broadcastsState as? UiState.Success)?.data
        ?.filter { it.sportCategory.equals("KOKOWA", ignoreCase = true) }.orEmpty()
    val wrestlers = (wrestlersState as? UiState.Success)?.data.orEmpty()
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val kokowaNews = (newsState as? UiState.Success)?.data
        ?.filter { it.sportCategory.equals("KOKOWA", ignoreCase = true) }.orEmpty()

    Column(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("kokowa_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = PureWhiteText)
            }
            Column {
                Text(strings.kokowaTitle, style = MaterialTheme.typography.titleLarge, color = PureWhiteText)
                Text(strings.kokowaSubtitle, style = MaterialTheme.typography.bodyMedium, color = NigerSaffron, maxLines = 1)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (kokowaBroadcasts.isNotEmpty()) {
                item { SectionHeader(title = strings.liveKokowaBroadcasts) }
                items(kokowaBroadcasts, key = { it.id }) { bc ->
                    HeroBroadcastCard(broadcast = bc, onWatchClick = { onOpenBroadcast(bc.id) })
                }
            }

            item { SectionHeader(title = strings.wrestlerProfiles) }
            when (wrestlersState) {
                UiState.Loading -> item { CircularProgressIndicator(color = NigerSaffron) }
                is UiState.Error -> item { EmptyStateView((wrestlersState as UiState.Error).message) }
                is UiState.Success -> {
                    if (wrestlers.isEmpty()) {
                        item { EmptyStateView(strings.emptyListDefault) }
                    } else {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(wrestlers, key = { it.id }) { w ->
                                    WrestlerCard(wrestler = w, onClick = { onOpenWrestler(w.id) })
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = strings.kokowaResults) }
            if (events.isEmpty()) {
                item { EmptyStateView(strings.emptyListDefault) }
            } else {
                items(events, key = { it.id }) { ev ->
                    KokowaEventCard(
                        event = ev,
                        onWatch = { ev.broadcastId?.let(onOpenBroadcast) }
                    )
                }
            }

            if (kokowaNews.isNotEmpty()) {
                item { SectionHeader(title = strings.sportsNews) }
                items(kokowaNews, key = { it.id }) { article ->
                    NewsCard(article = article, onClick = {})
                }
            }
        }
    }
}

@Composable
private fun KokowaEventCard(
    event: KokowaEvent,
    onWatch: () -> Unit
) {
    val strings = LocalStrings.current
    val isLive = event.status == "LIVE"
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, if (isLive) NigerSaffron else BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(event.roundLabel, style = MaterialTheme.typography.labelSmall, color = NigerSaffron)
                LivePulseBadge(
                    label = if (isLive) strings.statusLive else event.status,
                    isLive = isLive
                )
            }
            Text(event.title, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            Text(
                "${event.wrestlerAName}  VS  ${event.wrestlerBName}",
                style = MaterialTheme.typography.titleLarge,
                color = BrightBlueAccent
            )
            Text("${event.location} (${event.region}) • ${event.scheduledLabel}", style = MaterialTheme.typography.bodyMedium, color = SoftSteelText)
            if (!event.winnerName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = VictoryEmerald, modifier = Modifier.size(18.dp))
                    Text("${strings.winnerLabel}: ${event.winnerName}", style = MaterialTheme.typography.labelLarge, color = VictoryEmerald)
                }
            }
            if (!event.broadcastId.isNullOrBlank()) {
                Button(
                    onClick = onWatch,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.watchLive)
                }
            }
        }
    }
}

@Composable
fun WrestlerDetailScreen(
    wrestlerId: String,
    viewModel: SportNigerViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val wrestlersState by viewModel.wrestlers.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val wrestler: Wrestler? = (wrestlersState as? UiState.Success)?.data?.firstOrNull { it.id == wrestlerId }
    val isFavorite = userProfile?.favoriteWrestlerIds?.contains(wrestlerId) == true

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
                Text(wrestler?.name ?: strings.kokowa, style = MaterialTheme.typography.titleMedium, color = PureWhiteText)
            }
            OutlinedButton(
                onClick = { viewModel.toggleFavoriteWrestler(wrestlerId) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("favorite_wrestler_button")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = strings.favoriteWrestler,
                    tint = if (isFavorite) NigerSaffron else PureWhiteText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isFavorite) strings.favorited else strings.favoriteWrestler, color = PureWhiteText)
            }
        }

        if (wrestler == null) {
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
                    border = BorderStroke(1.dp, NigerSaffron),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NigerSaffron.copy(alpha = 0.2f),
                            border = BorderStroke(2.dp, NigerSaffron),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SportsKabaddi, contentDescription = wrestler.name, tint = NigerSaffron, modifier = Modifier.size(38.dp))
                            }
                        }
                        Text(wrestler.name, style = MaterialTheme.typography.headlineMedium, color = PureWhiteText)
                        Text("\"${wrestler.nickname}\"", style = MaterialTheme.typography.titleMedium, color = NigerSaffron)
                        Text("${strings.villageLabel}: ${wrestler.village} • ${strings.regionLabel}: ${wrestler.region}", style = MaterialTheme.typography.bodyLarge, color = SoftSteelText)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(strings.weightLabel, "${wrestler.weightKg} kg", Modifier.weight(1f))
                    StatBox(strings.recordLabel, "${wrestler.wins}W-${wrestler.losses}L-${wrestler.draws}D", Modifier.weight(1f))
                    StatBox(strings.titlesLabel, "${wrestler.titlesCount}", Modifier.weight(1f))
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = wrestler.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PureWhiteText,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SoftSteelText)
            Text(value, style = MaterialTheme.typography.titleMedium, color = BrightBlueAccent)
        }
    }
}
