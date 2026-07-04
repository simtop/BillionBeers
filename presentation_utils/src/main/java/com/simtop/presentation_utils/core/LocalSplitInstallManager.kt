package com.simtop.presentation_utils.core

import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.play.core.splitinstall.SplitInstallManager

/**
 * The single [SplitInstallManager] instance for the process, provided once at the composition
 * root (see `MainActivity`) from the DI graph. Registering a listener on one manager instance
 * and starting an install on another means the listener never fires - reaching for
 * `SplitInstallManagerFactory.create(...)` anywhere below the root creates exactly that bug, so
 * every consumer reads this instead.
 */
val LocalSplitInstallManager =
  staticCompositionLocalOf<SplitInstallManager> {
    error("LocalSplitInstallManager not provided")
  }
