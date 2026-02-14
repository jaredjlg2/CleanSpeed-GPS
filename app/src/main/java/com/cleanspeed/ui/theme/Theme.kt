package com.cleanspeed.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val darkScheme = darkColorScheme()

@Composable
fun CleanSpeedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkScheme,
        content = content
    )
}
