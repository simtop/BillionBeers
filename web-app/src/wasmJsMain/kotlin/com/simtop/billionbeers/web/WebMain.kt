@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.simtop.billionbeers.web

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.app.SharedAppHost
import com.simtop.billionbeers.shared.app.SharedAppNavigationEvent
import com.simtop.billionbeers.shared.app.SharedAppShell
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.core.core.CommonUiState
import com.simtop.navigation.contract.PortableRoute
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import org.w3c.dom.events.Event

fun main() {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  scope.launch {
    val runtime = WebDataRuntime.open(webDataConfigForHost())
    val session = WebRouteSession(runtime, scope)
    ComposeViewport("root") {
      WebShell(runtime, session)
      DisposableEffect(Unit) { onDispose { session.close() } }
    }
  }
}

private fun webDataConfigForHost(): WebDataConfig =
  WebDataConfig(
    imageProxyBaseUrl =
      if (
        window.location.hostname == "localhost" ||
          window.location.hostname == "127.0.0.1" ||
          window.location.hostname == "::1"
      ) {
        DEFAULT_WEB_IMAGE_PROXY_BASE_URL
      } else {
        null
      }
  )

private class WebRouteSession(
  private val runtime: WebDataRuntime,
  private val scope: CoroutineScope,
) {
  private val routeRequests = MutableSharedFlow<PortableRoute>(extraBufferCapacity = 1)
  private var closed = false
  private var listener: ((Event) -> Unit)? = null
  private var lastObservedHash: String? = null

  fun routes() = routeRequests.asSharedFlow()

  suspend fun initialRoute(): PortableRoute =
    resolveWebHash(window.location.hash, runtime.repository)

  fun onNavigationEvent(event: SharedAppNavigationEvent) {
    if (closed) return
    when (event) {
      is SharedAppNavigationEvent.Push -> {
        val hash = event.route.toWebHash()
        if (window.location.hash != hash) window.history.pushState(null, "", hash)
      }
      is SharedAppNavigationEvent.Pop -> window.history.back()
      is SharedAppNavigationEvent.Replace ->
        window.history.replaceState(null, "", event.route.toWebHash())
    }
  }

  fun goBack() {
    if (!closed) window.history.back()
  }

  fun markObservedHash() {
    lastObservedHash = window.location.hash
  }

  fun start() {
    val callback: (Event) -> Unit = {
      val hash = window.location.hash
      if (hash != lastObservedHash) {
        lastObservedHash = hash
        scope.launch {
          if (!closed) routeRequests.emit(resolveWebHash(hash, runtime.repository))
        }
      }
    }
    listener = callback
    window.addEventListener("hashchange", callback)
    window.addEventListener("popstate", callback)
  }

  fun close() {
    if (closed) return
    closed = true
    listener?.let {
      window.removeEventListener("hashchange", it)
      window.removeEventListener("popstate", it)
    }
    runtime.close()
    scope.cancel()
  }
}

private suspend fun resolveWebHash(
  hash: String,
  repository: com.simtop.beerdomain.domain.repositories.BeersRepository,
): PortableRoute =
  when (val destination = parseWebHash(hash)) {
    WebRouteDestination.Catalog,
    null -> PortableRoute.BeersList
    WebRouteDestination.Favorites -> PortableRoute.Favorites
    WebRouteDestination.Search -> PortableRoute.BeersSearch
    WebRouteDestination.Browse -> PortableRoute.BeerBrowse
    is WebRouteDestination.BrowseSelection ->
      PortableRoute.BeerBrowseSelection(
        category =
          when (destination.kind) {
            WebRouteDestination.BrowseSelection.Kind.Style ->
              com.simtop.navigation.contract.BrowseCategory.Style(destination.id, destination.id)
            WebRouteDestination.BrowseSelection.Kind.Brewery ->
              com.simtop.navigation.contract.BrowseCategory.Brewery(destination.id, destination.id)
          }
      )
    is WebRouteDestination.BeerDetail ->
      repository.getBeerById(destination.id)?.let(PortableRoute::BeerDetail)
        ?: PortableRoute.BeersList
  }

private suspend fun WebRouteSession.initialRouteAndStart(): PortableRoute {
  val route = initialRoute()
  if (window.location.hash != route.toWebHash()) {
    window.history.replaceState(null, "", route.toWebHash())
  }
  markObservedHash()
  start()
  return route
}

@Composable
private fun WebShell(runtime: WebDataRuntime, session: WebRouteSession) {
  var initialRoute by remember { mutableStateOf<PortableRoute?>(null) }
  LaunchedEffect(Unit) { initialRoute = session.initialRouteAndStart() }
  val route = initialRoute ?: return
  SharedAppShell(
    repository = runtime.repository,
    pagerFactory = runtime.pagerFactory,
    coroutineDispatcher = com.simtop.core.core.DefaultCoroutineDispatcherProvider(),
    strings = webStrings,
    initialRoute = route,
    host = webHost(runtime, session),
    onClose = session::goBack,
  )
}

private fun webHost(runtime: WebDataRuntime, session: WebRouteSession) =
  SharedAppHost(
    beerRow = { beer, onClick -> WebBeerRow(runtime, beer, onClick) },
    errorContent = { state, retry -> WebError(state, retry) },
    backIcon = { description ->
      Text("←", modifier = Modifier.semantics { contentDescription = description })
    },
    favoriteIcon = { isFavorite, description ->
      Text(
        if (isFavorite) "♥" else "♡",
        modifier =
          Modifier.semantics {
            contentDescription = description
            role = Role.Button
            stateDescription = description
          },
      )
    },
    imageContent = { imageUrl, description, modifier ->
      WebImage(runtime, imageUrl, description, modifier)
    },
    routeRequests = session.routes(),
    onNavigationEvent = session::onNavigationEvent,
    detailCollapsingToolbarEnabled = false,
    detailAnimationsDisabled = true,
  )

@Composable
private fun WebBeerRow(runtime: WebDataRuntime, beer: Beer, onClick: () -> Unit) {
  val availability = if (beer.availability) "Available" else "Out of stock"
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {
          contentDescription = "${beer.name}. $availability"
          role = Role.Button
          stateDescription = availability
        },
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.surface
          else MaterialTheme.colorScheme.errorContainer
      ),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      WebImage(runtime, beer.imageUrl, null, Modifier.width(72.dp).height(96.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(beer.name, style = MaterialTheme.typography.titleMedium)
        if (beer.tagline.isNotBlank())
          Text(beer.tagline, style = MaterialTheme.typography.bodyMedium)
        Text("ABV ${beer.abv}% · IBU ${beer.ibu}", style = MaterialTheme.typography.labelMedium)
        Text(
          availability,
          color =
            if (beer.availability) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onErrorContainer,
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }
}

@Composable
private fun WebImage(
  runtime: WebDataRuntime,
  url: String,
  description: String?,
  modifier: Modifier,
) {
  var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(url) {
    bitmap =
      if (url.isBlank()) {
        null
      } else {
        runCatching {
            runtime.loadImage(url)?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }
          }
          .getOrNull()
      }
  }
  val loadedBitmap = bitmap
  if (loadedBitmap == null) {
    WebImagePlaceholder(description, modifier)
  } else {
    Image(
      bitmap = loadedBitmap,
      contentDescription = description,
      contentScale = ContentScale.Crop,
      modifier = modifier,
    )
  }
}

@Composable
private fun WebImagePlaceholder(description: String?, modifier: Modifier) {
  Box(
    modifier.semantics {
      if (description == null) invisibleToUser() else contentDescription = description
    },
    contentAlignment = Alignment.Center,
  ) {
    Text("Beer", style = MaterialTheme.typography.headlineMedium)
  }
}

@Composable
private fun WebError(state: CommonUiState.Error, retry: () -> Unit) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(state.message ?: "Unable to load beers")
      Button(onClick = retry) { Text("Retry") }
    }
  }
}

private val webStrings =
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
