package com.auracall.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val AuraColors = darkColorScheme(
    primary = Accent, secondary = Blue, background = Background, surface = Surface,
    onPrimary = Background, onSecondary = Background, onBackground = TextPrimary,
    onSurface = TextPrimary, error = Danger
)

@Composable
fun AuraCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AuraColors, typography = Typography(), content = content)
}
