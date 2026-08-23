package com.simtop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey

/**
 * Contract implemented by a screen that lives inside a dynamic feature module.
 *
 * The base module only knows this interface; the concrete implementation is loaded by reflection
 * once the module is installed. Being generic over the [NavKey] keeps the contract reusable for
 * every future dynamic feature instead of needing a bespoke interface per feature.
 *
 * [onNavigate] pushes a key onto the app back stack, so a non-leaf feature screen (browse) can
 * navigate forward to another destination (a beer's detail). Leaf screens (detail) simply ignore
 * it.
 */
fun interface DynamicFeatureContentProvider<T : NavKey> {
  @Composable
  fun Content(
    key: T,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    showBackButton: Boolean,
  )
}

/**
 * Loads and renders the [DynamicFeatureContentProvider] that [key]'s [DynamicFeature] exposes.
 *
 * Precondition: the module is installed. A [DynamicFeatureKey] only reaches the back stack through
 * the app's navigator, which installs the module first.
 *
 * The reflective lookup is [remember]ed, so `Class.forName` + `newInstance()` runs once per entry
 * rather than on every recomposition.
 */
@Composable
fun <T : DynamicFeatureKey> DynamicFeatureContent(
  key: T,
  onBack: () -> Unit,
  onNavigate: (NavKey) -> Unit = {},
  showBackButton: Boolean = true,
) {
  val className = key.feature.providerClass
  val provider =
    remember(className) {
      @Suppress("UNCHECKED_CAST")
      (Class.forName(className).getDeclaredConstructor().newInstance()
        as DynamicFeatureContentProvider<T>)
    }
  provider.Content(
    key = key,
    onBack = onBack,
    onNavigate = onNavigate,
    showBackButton = showBackButton,
  )
}
