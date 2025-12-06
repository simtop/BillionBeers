package com.simtop.navigation

import com.simtop.beerdomain.domain.models.Beer
import kotlinx.serialization.Serializable

@Serializable
object BeersList

@Serializable
data class BeerDetail(val beer: Beer)
