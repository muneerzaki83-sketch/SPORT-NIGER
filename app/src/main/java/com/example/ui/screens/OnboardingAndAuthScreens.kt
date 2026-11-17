package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.localization.AppLanguage
import com.example.localization.LocalAppLanguage
import com.example.localization.LocalStrings
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

@Composable
fun LanguageOnboardingScreen(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onContinue: () -> Unit
) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepBlueContainer, DarkNavyBackground, PitchNavySurface)
                )
            )
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.sport_niger_logo),
                contentDescription = strings.appName,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = strings.chooseLanguageTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhiteText,
                textAlign = TextAlign.Center
            )
            Text(
                text = strings.chooseLanguageSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftSteelText,
                textAlign = TextAlign.Center
            )

            AppLanguage.entries.forEach { lang ->
                val isSelected = lang == selectedLanguage
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLanguage(lang) }
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_lang_${lang.code}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.22f) else CardNavySurface
                    ),
                    border = BorderStroke(1.5.dp, if (isSelected) BrightBlueAccent else BorderSlate)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) PrimaryBlue else PitchNavySurface
                            ) {
                                Text(
                                    text = lang.regionTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PureWhiteText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = lang.nativeLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureWhiteText
                                )
                                Text(
                                    text = if (lang.isRtl) "${lang.englishLabel} • RTL" else lang.englishLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SoftSteelText
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = lang.nativeLabel,
                                tint = BrightBlueAccent
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_continue_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = strings.continueAction,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhiteText
                )
            }
        }
    }
}

@Composable
fun AuthScreen(
    onSelectLanguage: (AppLanguage) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val currentLanguage = LocalAppLanguage.current
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepBlueContainer, DarkNavyBackground, PitchNavySurface)
                )
            )
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick language switcher bar at top of Auth screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = strings.languageSetting,
                    tint = BrightBlueAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                AppLanguage.entries.forEach { lang ->
                    val active = lang == currentLanguage
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectLanguage(lang) }
                            .minimumInteractiveComponentSize()
                            .testTag("auth_lang_${lang.code}"),
                        color = if (active) PrimaryBlue else CardNavySurface,
                        border = BorderStroke(1.dp, if (active) BrightBlueAccent else BorderSlate),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = lang.nativeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhiteText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Image(
                painter = painterResource(id = R.drawable.sport_niger_logo),
                contentDescription = strings.appName,
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop
            )

            Text(
                text = strings.appName,
                style = MaterialTheme.typography.displayLarge,
                color = PureWhiteText,
                textAlign = TextAlign.Center
            )
            Text(
                text = strings.welcomeSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = SoftSteelText,
                textAlign = TextAlign.Center
            )

            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CardNavySurface),
                border = BorderStroke(1.dp, VictoryEmerald.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = VictoryEmerald
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = strings.freePublicAccessNote,
                            style = MaterialTheme.typography.labelLarge,
                            color = VictoryEmerald
                        )
                        Text(
                            text = strings.authRequiredNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftSteelText
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = LiveCrimson.copy(alpha = 0.16f)),
                    border = BorderStroke(1.dp, LiveCrimson),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhiteText,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    AuthRepository.onGoogleSignInClicked(
                        context = context,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        onAuthCancelled = {
                            isLoading = false
                        },
                        scope = scope
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = PureWhiteText,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(strings.signingIn, color = PureWhiteText)
                } else {
                    Text(
                        text = strings.signInWithGoogle,
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhiteText
                    )
                }
            }
        }
    }
}
