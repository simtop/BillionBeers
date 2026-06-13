package com.simtop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey

/**
+ * Contract implemented by a screen that lives inside a dynamic feature module.
+ *
+ * The base module only knows this interface; the concrete implementation is loaded by
+ * reflection once the module is installed. Being generic over the [NavKey] keeps the
+ * contract reusable for every future dynamic feature instead of needing a bespoke
+ * interface per feature.
+ */
fun interface DynamicFeatureContentProvider<T : NavKey> {
    @Composable
    fun Content(key: T, onBack: () -> Unit)
}

/**
 * Loads and renders the [DynamicFeatureContentProvider] implementation named by [className]
 * from an installed dynamic feature module.
 *
 * The reflective lookup is [remember]ed against [className], so `Class.forName` +
 * `newInstance()` runs once per entry rather than on every recomposition.
 *
 * Precondition: the owning module is already installed (gated on the calling screen via
 * [com.simtop.presentation_utils.core.DynamicFeatureLoader]) before the [key] is added to
 * the back stack, so the lookup here is guaranteed to succeed.
 */
@Composable
fun <T : NavKey> DynamicFeatureContent(key: T, className: String, onBack: () -> Unit) {
    val provider =
        remember(className) {
            @Suppress("UNCHECKED_CAST")
            (Class.forName(className).getDeclaredConstructor().newInstance()
                    as DynamicFeatureContentProvider<T>)
        }
    provider.Content(key = key, onBack = onBack)
}