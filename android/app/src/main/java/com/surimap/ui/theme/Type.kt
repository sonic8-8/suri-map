package com.surimap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.surimap.R

private val PretendardGovFontFamily =
    FontFamily(
        Font(R.font.pretendard_gov_variable, FontWeight.Normal),
        Font(R.font.pretendard_gov_variable, FontWeight.Medium),
        Font(R.font.pretendard_gov_variable, FontWeight.SemiBold),
        Font(R.font.pretendard_gov_variable, FontWeight.Bold),
        Font(R.font.pretendard_gov_variable, FontWeight.ExtraBold)
    )

private val baseTextStyle =
    TextStyle(
        fontFamily = PretendardGovFontFamily,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

val PoliTypography =
    Typography(
        displaySmall =
        baseTextStyle.copy(
            fontSize = 25.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold
        ),
        titleLarge =
        baseTextStyle.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold
        ),
        titleMedium =
        baseTextStyle.copy(
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold
        ),
        bodyLarge =
        baseTextStyle.copy(
            fontSize = 17.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Medium
        ),
        bodyMedium =
        baseTextStyle.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        ),
        labelLarge =
        baseTextStyle.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium =
        baseTextStyle.copy(
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    )
