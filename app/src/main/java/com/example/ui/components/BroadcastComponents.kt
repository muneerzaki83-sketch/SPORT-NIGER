package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Broadcast
import com.example.data.model.HighlightVideo
import com.example.data.model.NewsArticle
import com.example.data.model.SponsorBanner
import com.example.data.model.SportMatch
import com.example.data.model.Tournament
import com.example.data.model.Wrestler
import com.example.localization.LocalStrings
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.ElevatedNavySurface
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.NigerGreen
import com.example.ui.theme.NigerSaffron
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.ScoreMonoFont
import com.example.ui.theme.SoftSteelText
import com.example.ui.theme.VictoryEmerald

@Composable
fun SportNigerTopBranding(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.sport_niger_logo),
            contentDescription = LocalStrings.current.appName,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Column {
            Text(
                text = LocalStrings.current.appName,
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhiteText
            )
            Text(
                text = LocalStrings.current.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = BrightBlueAccent
            )
        }
    }
}

@Composable
fun LivePulseBadge(
    label: String,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val dotColor by transition.animateColor(
        initialValue = LiveCrimson,
        targetValue = Color(0xFFFF859D),
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse_dot"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isLive) LiveCrimson.copy(alpha = 0.18f) else ElevatedNavySurface,
        border = BorderStroke(1.dp, if (isLive) LiveCrimson else BorderSlate)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isLive) LiveCrimson else SoftSteelText
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrightBlueAccent)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = PureWhiteText
            )
        }
        if (actionLabel != null && onActionClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = BrightBlueAccent
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = actionLabel,
                    tint = BrightBlueAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun HeroBroadcastCard(
    broadcast: Broadcast,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val isLive = broadcast.status == "LIVE"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onWatchClick)
            .testTag("hero_broadcast_card_${broadcast.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            DeepBlueContainer,
                            CardNavySurface,
                            Color(0xFF07132A)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LivePulseBadge(
                            label = if (isLive) strings.statusLive else broadcast.status,
                            isLive = isLive
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryBlue.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = broadcast.sportCategory,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrightBlueAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = SoftSteelText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${broadcast.viewerCount} ${strings.viewersWatching}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftSteelText
                        )
                    }
                }

                Text(
                    text = broadcast.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhiteText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = broadcast.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftSteelText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${broadcast.streamType} • ${broadcast.ingestProtocol}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteelText
                    )
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("watch_live_button_${broadcast.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = strings.watchLive, color = PureWhiteText)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchScoreCard(
    match: SportMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val isLive = match.status == "LIVE" || match.status == "HALFTIME"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("match_card_${match.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, if (isLive) PrimaryBlue.copy(alpha = 0.7f) else BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.leagueName,
                    style = MaterialTheme.typography.labelLarge,
                    color = BrightBlueAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LivePulseBadge(
                    label = when (match.status) {
                        "LIVE" -> "${strings.statusLive} • ${match.matchMinute}"
                        "HALFTIME" -> strings.statusHalftime
                        "COMPLETED" -> strings.statusCompleted
                        else -> match.matchMinute
                    },
                    isLive = isLive
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TeamBadgeName(
                    name = match.homeTeamName,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.Start
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedNavySurface,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = if (match.status == "UPCOMING") "VS" else "${match.homeScore} - ${match.awayScore}",
                        fontFamily = ScoreMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (isLive) BrightBlueAccent else PureWhiteText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                TeamBadgeName(
                    name = match.awayTeamName,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.venue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteelText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!match.broadcastId.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = strings.watchLive,
                            tint = BrightBlueAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = strings.watchLive,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrightBlueAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamBadgeName(
    name: String,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryBlue.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, BrightBlueAccent.copy(alpha = 0.4f)),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SportsSoccer,
                    contentDescription = null,
                    tint = PureWhiteText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = PureWhiteText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WrestlerCard(
    wrestler: Wrestler,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick)
            .testTag("wrestler_card_${wrestler.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, NigerSaffron.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = NigerSaffron.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, NigerSaffron),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SportsKabaddi,
                            contentDescription = wrestler.name,
                            tint = NigerSaffron,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wrestler.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhiteText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "\"${wrestler.nickname}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NigerSaffron,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "${wrestler.village} • ${wrestler.region}",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftSteelText,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${wrestler.wins}W-${wrestler.losses}L-${wrestler.draws}D",
                    style = MaterialTheme.typography.labelSmall,
                    color = VictoryEmerald
                )
                Text(
                    text = "${wrestler.titlesCount} ${strings.titlesLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrightBlueAccent
                )
            }
        }
    }
}

@Composable
fun TournamentCard(
    tournament: Tournament,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tournament_card_${tournament.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryBlue.copy(alpha = 0.2f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = NigerSaffron,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tournament.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhiteText
                )
                Text(
                    text = "${tournament.sportCategory} • ${tournament.location} • ${tournament.season}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftSteelText
                )
                Text(
                    text = "${tournament.participantNames.size} ${strings.participantsLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrightBlueAccent
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (tournament.status == "ONGOING") VictoryEmerald.copy(alpha = 0.16f) else ElevatedNavySurface
            ) {
                Text(
                    text = tournament.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tournament.status == "ONGOING") VictoryEmerald else SoftSteelText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun NewsCard(
    article: NewsArticle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("news_card_${article.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, if (article.breaking) LiveCrimson.copy(alpha = 0.55f) else BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Newspaper,
                    contentDescription = null,
                    tint = BrightBlueAccent,
                    modifier = Modifier.size(16.dp)
                )
                if (article.breaking) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = LiveCrimson
                    ) {
                        Text(
                            text = strings.breakingNews,
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhiteText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = article.sportCategory,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrightBlueAccent
                )
            }
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhiteText
            )
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftSteelText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HighlightCard(
    highlight: HighlightVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(240.dp)
            .clickable(onClick = onClick)
            .testTag("highlight_card_${highlight.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(listOf(DeepBlueContainer, ElevatedNavySurface))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryBlue,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = highlight.title,
                            tint = PureWhiteText
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = highlight.durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhiteText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = highlight.title,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhiteText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${highlight.sportCategory} • ${highlight.viewCount} views",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSteelText
            )
        }
    }
}

@Composable
fun SponsorBannerCard(
    banner: SponsorBanner,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ElevatedNavySurface),
        border = BorderStroke(1.dp, NigerGreen.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = NigerGreen.copy(alpha = 0.2f)
            ) {
                Text(
                    text = strings.sponsorLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = VictoryEmerald,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = banner.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = PureWhiteText
                )
                Text(
                    text = banner.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftSteelText,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = MutedSteelText,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = SoftSteelText
            )
        }
    }
}

@Composable
fun ErrorRetryView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardNavySurface),
        border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhiteText
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.retry)
            }
        }
    }
}
