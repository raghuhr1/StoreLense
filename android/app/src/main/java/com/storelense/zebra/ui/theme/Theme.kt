package com.storelense.zebra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue600  = Color(0xFF2563EB)
private val Blue700  = Color(0xFF1D4ED8)
private val Blue50   = Color(0xFFEFF6FF)
private val Gray900  = Color(0xFF111827)
private val Gray700  = Color(0xFF374151)
private val Gray100  = Color(0xFFF3F4F6)
private val Green600 = Color(0xFF16A34A)
private val Red500   = Color(0xFFEF4444)
private val Yellow500= Color(0xFFF59E0B)

val StoreLenseColors = lightColorScheme(
    primary         = Blue600,
    onPrimary       = Color.White,
    primaryContainer= Blue50,
    secondary       = Gray700,
    onSecondary     = Color.White,
    background      = Gray100,
    surface         = Color.White,
    error           = Red500,
    onBackground    = Gray900,
    onSurface       = Gray900,
)

@Composable
fun StoreLenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StoreLenseColors,
        content     = content,
    )
}

val SuccessColor = Green600
val WarningColor = Yellow500
val ErrorColor   = Red500
