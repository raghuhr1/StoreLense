package com.storelense.c66.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TealPrimary   = Color(0xFF0F766E)
private val TealSecondary = Color(0xFF14B8A6)
private val TealContainer = Color(0xFFCCFBF1)

private val LightColors = lightColorScheme(
    primary          = TealPrimary,
    onPrimary        = Color.White,
    primaryContainer = TealContainer,
    secondary        = TealSecondary,
    onSecondary      = Color.White,
    background       = Color(0xFFF5F7FA),
    surface          = Color.White,
    error            = Color(0xFFDC2626),
)

@Composable
fun C66Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
