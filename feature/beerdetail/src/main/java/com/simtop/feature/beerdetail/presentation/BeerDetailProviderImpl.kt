package com.simtop.feature.beerdetail.presentation

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.BeerDetailProvider

@Keep
class BeerDetailProviderImpl : BeerDetailProvider {
  @Composable
  override fun BeerDetailScreen(beer: Beer, onBackClick: () -> Unit) {
    BeerDetailScreenImpl(beer, onBackClick)
  }
}
