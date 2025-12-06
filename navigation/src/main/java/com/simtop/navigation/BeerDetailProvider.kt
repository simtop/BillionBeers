package com.simtop.navigation

import androidx.compose.runtime.Composable
import com.simtop.beerdomain.domain.models.Beer

interface BeerDetailProvider {
    @Composable
    fun BeerDetailScreen(beer: Beer, onBackClick: () -> Unit)
}
