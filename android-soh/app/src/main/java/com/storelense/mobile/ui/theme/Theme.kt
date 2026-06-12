package com.storelense.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Base brand colors (unchanged) ────────────────────────────────────────────
val Primary   = Color(0xFF1565C0)   // deep blue
val Secondary = Color(0xFF00897B)   // teal
val Error     = Color(0xFFC62828)   // red

// ── V2 workflow accent colors ─────────────────────────────────────────────────
val GreenComplete  = Color(0xFF2E7D32)   // Receive DC / completion actions
val AmberReplenish = Color(0xFFE65100)   // Replenish / urgency
val BlueAudit      = Color(0xFF1565C0)   // Store Audit (same as Primary)
val AmberWarning   = Color(0xFFF57C00)   // Warning badges
val RedCritical    = Color(0xFFC62828)   // Critical badges / missing items

// ── Surface / background tokens ───────────────────────────────────────────────
val DarkNavy       = Color(0xFF0D1A2E)   // Home dashboard dark background
val CardDark       = Color(0xFF162032)   // Cards on dark background
val GreenGlow      = Color(0xFF00E676)   // Health score accent on dark bg

// ── Tinted card backgrounds (workflow) ───────────────────────────────────────
val GreenTint      = Color(0xFFF1F8E9)
val AmberTint      = Color(0xFFFFF3E0)
val BlueTint       = Color(0xFFE3F2FD)
val RedTint        = Color(0xFFFFEBEE)

// ── Light scheme (default) ────────────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary            = Primary,
    secondary          = Secondary,
    error              = Error,
    background         = Color(0xFFF8F9FA),
    surface            = Color(0xFFFFFFFF),
    surfaceVariant     = Color(0xFFF1F3F5),
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = Color(0xFF212121),
    onSurface          = Color(0xFF212121),
    onSurfaceVariant   = Color(0xFF757575),
    outline            = Color(0xFFBDBDBD),
)

// ── Dark scheme (Home dashboard) ─────────────────────────────────────────────
val DarkColors = darkColorScheme(
    primary            = GreenGlow,
    secondary          = Secondary,
    error              = RedCritical,
    background         = DarkNavy,
    surface            = CardDark,
    surfaceVariant     = Color(0xFF1E2D40),
    onPrimary          = Color(0xFF003300),
    onSecondary        = Color.White,
    onBackground       = Color.White,
    onSurface          = Color.White,
    onSurfaceVariant   = Color(0xFFB0BEC5),
    outline            = Color(0xFF37474F),
)

@Composable
fun StoreLenseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content     = content
    )
}
