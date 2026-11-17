package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val BroadcastDisplayFont = FontFamily(
    Font(R.font.barlow_condensed, FontWeight.Bold)
)

val BodySansFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val ScoreMonoFont = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val ArabicBodyFont = FontFamily(
    Font(R.font.noto_sans_arabic, FontWeight.Normal),
    Font(R.font.noto_sans_arabic, FontWeight.Bold)
)

fun sportNigerTypography(isArabic: Boolean): Typography {
    val display = if (isArabic) ArabicBodyFont else BroadcastDisplayFont
    val body = if (isArabic) ArabicBodyFont else BodySansFont
    return Typography(
        displayLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.2.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.2.sp
        ),
        titleLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        labelSmall = TextStyle(
            fontFamily = ScoreMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        )
    )
}
