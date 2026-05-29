package com.simtop.navigation

import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import kotlinx.serialization.Serializable

@Serializable object BeersList : NavKey

@Serializable data class BeerDetail(val beer: Beer) : NavKey
