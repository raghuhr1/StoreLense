package com.storelense.mobile.ui

/**
 * Toggle between the new (v2) UI and the legacy UI.
 *
 * HOW TO SWITCH BACK TO OLD UI:
 *   Change  USE_NEW_UI = true  →  USE_NEW_UI = false
 *   then rebuild the app.
 *
 * The flag is read once at navigation setup time, so a hot-reload / full
 * recompose is sufficient; no app restart required in debug builds.
 */
object UiConfig {
    const val USE_NEW_UI: Boolean = true
}
