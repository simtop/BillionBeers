package com.simtop.presentation_utils.core

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Long-pressing the top bar title calls this to show/hide the debug drawer's floating trigger
 * (app/src/debug's DebugDrawerHost provides the real toggle) - keeps it out of the way until
 * needed, rather than always floating over the content. Defaults to null so trees without a debug
 * host (release builds, Paparazzi, etc.) don't turn the title into a silent touch target.
 */
val LocalDebugDrawerToggle = staticCompositionLocalOf<(() -> Unit)?> { null }
