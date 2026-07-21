package com.simtop.billionbeers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.DynamicFeatureKey
import com.simtop.presentation_utils.core.DynamicFeatureLoader

/**
 * The app's single navigation entry point: returns a `navigate(key)` that installs the key's
 * dynamic feature module first, when it needs one.
 *
 * Because every destination is pushed through this, a [DynamicFeatureKey] cannot reach the back
 * stack - and from there a reflective provider lookup - before its module is on device. Screens
 * stay ignorant of split installs: they just navigate, and the install UI renders here, over
 * whichever screen is currently showing.
 *
 * The pending key is [retain]ed, matching the installer it drives: rotating mid-download keeps the
 * same install and the same dialog. It is deliberately not saved across process death - restoring
 * it would mean serialising an arbitrary [NavKey] - so a process death mid-install returns the user
 * to the current screen. The Play install continues regardless, so navigating again is instant.
 */
@Composable
fun rememberDynamicFeatureNavigator(backStack: MutableList<NavKey>): (NavKey) -> Unit {
  var pending by retain { mutableStateOf<DynamicFeatureKey?>(null) }

  pending?.let { key ->
    DynamicFeatureLoader(featureName = key.feature.moduleName, onCancelled = { pending = null }) {
      LaunchedEffect(key) {
        backStack.add(key)
        pending = null
      }
    }
  }

  return remember(backStack) {
    { key -> if (key is DynamicFeatureKey) pending = key else backStack.add(key) }
  }
}
