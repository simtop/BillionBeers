package com.simtop.billionbeers.iosshared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.app.SharedAppHost
import com.simtop.billionbeers.shared.app.SharedAppShell
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import platform.UIKit.UIViewController

private const val DEFAULT_IOS_API_BASE_URL = "https://brewbuddy.dev/"

/** Owns one iOS data graph and its Compose controller for the lifetime of a host scene. */
public class IosAppSession {
  private val runtime =
    IosDataRuntime.open(
      IosDataConfig(apiBaseUrl = DEFAULT_IOS_API_BASE_URL),
    )
  private var closed = false

  public val viewController: UIViewController =
    ComposeUIViewController {
      IosShell(runtime)
    }

  public fun close() {
    if (!closed) {
      closed = true
      runtime.close()
    }
  }
}

@Composable
private fun IosShell(runtime: IosDataRuntime) {
  SharedAppShell(
    repository = runtime.repository,
    pagerFactory = runtime.pagerFactory,
    coroutineDispatcher = DefaultCoroutineDispatcherProvider(),
    strings = iosStrings,
    host = iosHost,
  )
}

private val iosHost =
  SharedAppHost(
    beerRow = { beer, onClick -> IosBeerRow(beer, onClick) },
    errorContent = { state, retry -> IosError(state, retry) },
    backIcon = { contentDescription -> Text("‹") },
    favoriteIcon = { isFavorite, contentDescription -> Text(if (isFavorite) "♥" else "♡") },
    imageContent = { _, _, modifier -> IosImagePlaceholder(modifier) },
    detailAnimationsDisabled = false,
    detailCollapsingToolbarEnabled = false,
  )

private val iosStrings =
  SharedAppStrings(
    appTitle = "Billion Beers",
    back = "Back",
    list = "Catalog",
    favorites = "Favorites",
    search = "Search",
    browse = "Browse",
    retry = "Retry",
    error = "Unable to load beers",
    listLoadMoreFailed = "More beers could not be loaded",
    listEndOfList = { count -> "End of list · $count beers" },
    searchHint = "Search beers",
    searchPrompt = "Type at least two characters to search",
    searchNoResults = { term -> "No beers found for \"$term\"" },
    searchResultCount = { count -> "$count results" },
    searchEndOfList = { count -> "End of results · $count beers" },
    favoritesEmpty = "No favorite beers yet",
    browseStrings =
      BrowseStrings(
        back = "Back",
        title = "Browse",
        stylesTab = "Styles",
        breweriesTab = "Breweries",
        emptyState = "Nothing to browse",
        noBeers = "No beers found",
        retry = "Retry",
        loadMoreFailed = "More beers could not be loaded",
        breweryFounded = { country, year -> "$country · founded $year" },
        beersCount = { count -> "$count beers" },
        endOfList = { count -> "End of list · $count beers" },
      ),
    detailStrings =
      BeerDetailStrings(
        back = "Back",
        imageDescription = { name -> "$name image" },
        addToFavorites = "Add to favorites",
        removeFromFavorites = "Remove from favorites",
        available = "Available",
        outOfStock = "Out of stock",
        markAsEmpty = "Mark as empty",
        refillBarrels = "Refill barrels",
        styleAndBrewery = { style, brewery -> "$style · $brewery" },
        description = "Description",
        foodPairing = "Food pairing",
        abv = "ABV",
        ibu = "IBU",
        details = "Details",
        srm = "SRM",
        released = "Released",
        servingTemperature = "Serving temperature",
        servingTemperatureValue = { value, unit -> "$value°$unit" },
        fermentation = "Fermentation",
        ingredients = "Ingredients",
        recommendedGlasses = "Recommended glasses",
      ),
  )

@Composable
private fun IosBeerRow(beer: Beer, onClick: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.surface
          else MaterialTheme.colorScheme.errorContainer,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      IosImagePlaceholder(Modifier.width(72.dp).height(96.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(beer.name, style = MaterialTheme.typography.titleMedium)
        if (beer.tagline.isNotBlank()) Text(beer.tagline, style = MaterialTheme.typography.bodyMedium)
        Text(
          "ABV ${beer.abv}% · IBU ${beer.ibu}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          if (beer.availability) "Available" else "Out of stock",
          style = MaterialTheme.typography.labelMedium,
          color =
            if (beer.availability) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}

@Composable
private fun IosImagePlaceholder(modifier: Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) {
    Text("Beer", style = MaterialTheme.typography.headlineMedium)
  }
}

@Composable
private fun IosError(state: CommonUiState.Error, retry: () -> Unit) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(state.message ?: "Unable to load beers")
      Button(onClick = retry) { Text("Retry") }
    }
  }
}
