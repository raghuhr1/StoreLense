package com.storelense.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary   = Color(0xFF1565C0)   // deep blue
private val Secondary = Color(0xFF00897B)   // teal
private val Error     = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary   = Primary,
    secondary = Secondary,
    error     = Error,
)

@Composable
fun StoreLenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
