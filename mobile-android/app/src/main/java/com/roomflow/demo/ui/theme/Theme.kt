package com.roomflow.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MeetingRoomColorScheme = darkColorScheme(
    primary = AccentRed,
    secondary = AvailableGreen,
    tertiary = AvailableAccentBar,
    background = DarkNavy,
    surface = DarkerNavy,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = ErrorText
)

@Composable
fun MeetingRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeetingRoomColorScheme,
        typography = MeetingTypography,
        content = content
    )
}
