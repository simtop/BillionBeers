package com.simtop.billionbeers.composefixture

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Minimal common-source proof that Navigation 3's back-stack primitive resolves on every fixture
 * target. This intentionally uses local keys instead of production routes or host integrations.
 */
@Serializable
sealed interface Navigation3ProbeKey : NavKey {
  @Serializable data object Home : Navigation3ProbeKey

  @Serializable data class Detail(val id: String) : Navigation3ProbeKey
}

fun navigation3ProbeBackStack(): NavBackStack<Navigation3ProbeKey> =
  NavBackStack(Navigation3ProbeKey.Home)
