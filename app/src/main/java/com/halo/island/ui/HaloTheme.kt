package com.halo.island.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF080A0D)
val Silver = Color(0xFFEAF0EE)
val Muted = Color(0xFFA3ABA9)
val Mint = Color(0xFFBEDDD1)

@Composable
fun HaloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Mint,
                onPrimary = Ink,
                background = Ink,
                onBackground = Silver,
                surface = Color(0xFF12161B),
                onSurface = Silver,
                surfaceVariant = Color(0xFF20262C),
                onSurfaceVariant = Muted,
                secondary = Color(0xFFB2C7D9),
                outline = Color(0xFF353D45),
            ),
        typography =
            Typography(
                displayLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 56.sp,
                        letterSpacing = (-2).sp,
                    ),
                headlineLarge =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = 34.sp,
                        letterSpacing = (-1).sp,
                    ),
                headlineMedium =
                    TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 26.sp,
                        letterSpacing = (-.6).sp,
                    ),
                titleLarge =
                    TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        letterSpacing = (-.4).sp,
                    ),
                titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge =
                    TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = .2.sp,
                    ),
            ),
        content = content,
    )
}
