package com.simtop.feature.beerdetail.presentation

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.DynamicFeatureContentProvider

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
@Keep
class BeerDetailProviderImpl : DynamicFeatureContentProvider<BeerDetail> {
    @Composable
    override fun Content(key: BeerDetail, onBack: () -> Unit) {
        BeerDetailScreenImpl(beer = key.beer, onBackClick = onBack)
    }
}
