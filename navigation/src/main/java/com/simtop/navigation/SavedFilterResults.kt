package com.simtop.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class SavedFilterResults(
  val id: String,
  val name: String,
  val search: String? = null,
  val styleId: String? = null,
  val breweryId: String? = null,
) : NavKey {
  fun toQuery() = com.simtop.beerdomain.domain.models.BeersQuery(search, styleId, breweryId)
}
