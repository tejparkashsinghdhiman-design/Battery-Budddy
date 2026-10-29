package com.example.batterybuddy

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Sakura = Color(0xFFFF7FAE)
val SakuraSoft = Color(0xFFFFD6E7)
val Lavender = Color(0xFF9B8CFF)
val LavenderSoft = Color(0xFFEAE5FF)
val Mint = Color(0xFF6FE3C1)
val Blush = Color(0xFFFFF4F9)
val Plum = Color(0xFF3B2A4D)
val PlumSoft = Color(0xFF7A6A8C)

private val Scheme = lightColorScheme(
    primary = Sakura,
    onPrimary = Color.White,
    primaryContainer = SakuraSoft,
    onPrimaryContainer = Plum,
    secondary = Lavender,
    onSecondary = Color.White,
    secondaryContainer = LavenderSoft,
    onSecondaryContainer = Plum,
    tertiary = Mint,
    background = Blush,
    onBackground = Plum,
    surface = Color.White,
    onSurface = Plum,
    surfaceVariant = LavenderSoft,
    onSurfaceVariant = PlumSoft,
    outline = Color(0xFFE3D5F2)
)

/** Soft pastel theme: sakura pink, lavender and mint on a blush background. */
@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
