package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.localization.AppLanguage
import com.example.localization.LocalAppLanguage
import com.example.localization.LocalStrings
import com.example.localization.Translations

private val SportNigerColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = PureWhiteText,
    primaryContainer = DeepBlueContainer,
    onPrimaryContainer = PureWhiteText,
    secondary = BrightBlueAccent,
    onSecondary = SecondaryBlack,
    secondaryContainer = ElevatedNavySurface,
    onSecondaryContainer = PureWhiteText,
    tertiary = NigerSaffron,
    onTertiary = SecondaryBlack,
    background = DarkNavyBackground,
    onBackground = PureWhiteText,
    surface = PitchNavySurface,
    onSurface = PureWhiteText,
    surfaceVariant = CardNavySurface,
    onSurfaceVariant = SoftSteelText,
    outline = BorderSlate,
    error = LiveCrimson,
    onError = PureWhiteText
)

private val SportNigerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun SportNigerTheme(
    language: AppLanguage = AppLanguage.ENGLISH,
    content: @Composable () -> Unit
) {
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val strings = Translations.forLanguage(language)

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppLanguage provides language,
        LocalStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = SportNigerColorScheme,
            typography = sportNigerTypography(isArabic = language == AppLanguage.ARABIC),
            shapes = SportNigerShapes,
            content = content
        )
    }
}
