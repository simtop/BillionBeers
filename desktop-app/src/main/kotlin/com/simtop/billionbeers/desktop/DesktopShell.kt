package com.simtop.billionbeers.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.app.SharedAppHost
import com.simtop.billionbeers.shared.app.SharedAppShell
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.DefaultCoroutineDispatcherProvider

@Composable
fun DesktopShell(runtime: DesktopDataRuntime) {
  SharedAppShell(
    repository = runtime.repository,
    pagerFactory = runtime.pagerFactory,
    coroutineDispatcher = DefaultCoroutineDispatcherProvider(),
    strings = desktopStrings,
    host = desktopHost,
  )
}

private val desktopHost =
  SharedAppHost(
    beerRow = { beer, onClick -> DesktopBeerRow(beer, onClick) },
    errorContent = { state, retry -> DesktopError(state, retry) },
    backIcon = { contentDescription -> Text("←", modifier = Modifier.padding(8.dp)) },
    favoriteIcon = { isFavorite, contentDescription -> Text(if (isFavorite) "♥" else "♡") },
    imageContent = { imageUrl, contentDescription, modifier ->
      if (imageUrl.isNullOrBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No image") }
      } else {
        AsyncImage(
          model = imageUrl,
          contentDescription = contentDescription,
          contentScale = ContentScale.Crop,
          modifier = modifier,
        )
      }
    },
    detailAnimationsDisabled = false,
    detailCollapsingToolbarEnabled = false,
    onMessage = { message -> println(message) },
  )

private val desktopStrings =
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
private fun DesktopBeerRow(beer: Beer, onClick: () -> Unit) {
  Card(
    modifier =
      Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onClick() },
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.surface
          else MaterialTheme.colorScheme.errorContainer
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (beer.imageUrl.isBlank()) {
        Box(
          modifier = Modifier.width(72.dp).height(96.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text("No image", style = MaterialTheme.typography.labelSmall)
        }
      } else {
        AsyncImage(
          model = beer.imageUrl,
          contentDescription = "${beer.name} image",
          contentScale = ContentScale.Crop,
          modifier = Modifier.width(72.dp).height(96.dp),
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(beer.name, style = MaterialTheme.typography.titleMedium)
        if (beer.tagline.isNotBlank()) {
          Text(beer.tagline, style = MaterialTheme.typography.bodyMedium)
        }
        val classification = listOf(beer.styleName, beer.breweryName).filter(String::isNotBlank)
        if (classification.isNotEmpty()) {
          Text(
            classification.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
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
private fun DesktopError(state: CommonUiState.Error, retry: () -> Unit) {
  Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(state.message ?: "Unable to load beers")
      Button(onClick = retry) { Text("Retry") }
    }
  }
}
