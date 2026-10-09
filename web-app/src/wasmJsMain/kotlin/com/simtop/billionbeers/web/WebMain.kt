@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.simtop.billionbeers.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.simtop.billionbeers.shared.app.SharedAppNavigationRequest
import com.simtop.billionbeers.shared.app.SharedAppShell
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.app.formatCount
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.formatServingTemperatureRange
import com.simtop.billionbeers.shared.presentation.BeerListItemLabels
import com.simtop.billionbeers.shared.presentation.SharedBeerListItem
import com.simtop.core.core.CommonUiState
import com.simtop.navigation.contract.PortableRoute
import kotlin.js.toJsString
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import org.w3c.dom.events.Event

fun main() {
  ComposeViewport("root") { WebStartupHost() }
}

private sealed interface WebStartupState {
  data object Loading : WebStartupState

  data class Failed(val message: String) : WebStartupState

  data class Ready(val runtime: WebDataRuntime, val session: WebRouteSession) : WebStartupState
}

@Composable
private fun WebStartupHost() {
  var attempt by remember { mutableStateOf(0) }
  var state by remember { mutableStateOf<WebStartupState>(WebStartupState.Loading) }
  val currentState by rememberUpdatedState(state)

  DisposableEffect(Unit) {
    onDispose { (currentState as? WebStartupState.Ready)?.session?.close() }
  }
  LaunchedEffect(attempt) {
    state = WebStartupState.Loading
    when (
      val result =
        WebStartupCoordinator(
            openRuntime = { WebDataRuntime.open(webDataConfigForHost()) },
            createSession = { runtime ->
              val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
              WebRouteSession(runtime, sessionScope)
            },
            closeRuntime = WebDataRuntime::close,
          )
          .start()
    ) {
      is WebStartupResult.Failed -> state = WebStartupState.Failed(result.message)
      is WebStartupResult.Ready -> state = WebStartupState.Ready(result.runtime, result.session)
    }
  }

  when (val current = state) {
    WebStartupState.Loading -> WebStartupLoading()
    is WebStartupState.Failed -> WebStartupFailure(current.message) { attempt += 1 }
    is WebStartupState.Ready -> {
      WebShell(current.runtime, current.session)
      DisposableEffect(current.session) { onDispose { current.session.close() } }
    }
  }
}

@Composable
private fun WebStartupLoading() {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text("Starting Billion Beers")
  }
}

@Composable
private fun WebStartupFailure(message: String, retry: () -> Unit) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(message)
      Button(onClick = retry) { Text("Retry") }
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

@JsFun("() => crypto.randomUUID()") private external fun webHistorySessionId(): String

@JsFun("() => typeof history.state === 'string' ? history.state : ''")
private external fun webHistoryToken(): String

private class WebRouteSession(
  private val runtime: WebDataRuntime,
  private val scope: CoroutineScope,
) {
  private val routeRequests = Channel<SharedAppNavigationRequest>(Channel.BUFFERED)
  private val routeFlow = routeRequests.receiveAsFlow()
  private val resolutionState = MutableStateFlow<WebRouteResolution?>(null)
  val resolution = resolutionState.asStateFlow()
  private var closed = false
  private var listener: ((Event) -> Unit)? = null
  private var lastObservedHash: String? = null
  private var lastObservedToken = ""
  private val savedFilterHistory = WebSavedFilterHistory(webHistorySessionId())
  private var resolutionJob: Job? = null

  fun routes() = routeFlow

  fun onNavigationEvent(event: SharedAppNavigationEvent) {
    if (closed) return
    when (event) {
      is SharedAppNavigationEvent.Push -> {
        val hash = event.route.toWebHash()
        val preset = event.savedFilter
        if (preset != null) {
          val token = savedFilterHistory.remember(preset)
          window.history.pushState(token.toJsString(), "", hash)
        } else if (window.location.hash != hash || webHistoryToken().isNotEmpty()) {
          window.history.pushState(null, "", hash)
        }
        markObservedHash()
      }
      is SharedAppNavigationEvent.Pop -> window.history.back()
      is SharedAppNavigationEvent.Replace -> {
        window.history.replaceState(null, "", event.route.toWebHash())
        markObservedHash()
      }
    }
  }

  fun goBack() {
    if (!closed) window.history.back()
  }

  private fun markObservedHash() {
    resolutionJob?.cancel()
    lastObservedHash = window.location.hash
    lastObservedToken = webHistoryToken()
  }

  fun start() {
    if (closed || listener != null) return
    val callback: (Event) -> Unit = {
      val hash = window.location.hash
      if (hash != lastObservedHash || webHistoryToken() != lastObservedToken) {
        requestRoute(hash)
      }
    }
    listener = callback
    window.addEventListener("hashchange", callback)
    window.addEventListener("popstate", callback)
    requestRoute(window.location.hash, canonicalize = true)
  }

  fun openCatalog() {
    if (closed) return
    // Replace the unavailable entry so recovery cannot loop back to the same missing link.
    window.history.replaceState(null, "", PortableRoute.BeersList.toWebHash())
    requestRoute(window.location.hash)
  }

  private fun requestRoute(hash: String, canonicalize: Boolean = false) {
    lastObservedHash = hash
    val token = webHistoryToken()
    lastObservedToken = token
    resolutionJob?.cancel()
    resolutionJob = scope.launch {
      val result = resolveWebHash(hash, runtime.repository::getBeerById)
      if (closed || window.location.hash != hash || webHistoryToken() != token) return@launch
      if (canonicalize && result is WebRouteResolution.Ready) {
        window.history.replaceState(null, "", result.route.toWebHash())
        lastObservedHash = window.location.hash
        lastObservedToken = ""
      }
      resolutionState.value = result
      if (result is WebRouteResolution.Ready) {
        val preset =
          if (result.route == PortableRoute.SavedFilterPresets && !canonicalize) {
            savedFilterHistory.resolve(token)
          } else null
        routeRequests.send(
          if (preset != null) SharedAppNavigationRequest.SavedFilter(preset)
          else SharedAppNavigationRequest.Route(result.route)
        )
      }
    }
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
    routeRequests.close()
    savedFilterHistory.clear()
  }
}

@Composable
private fun WebShell(runtime: WebDataRuntime, session: WebRouteSession) {
  val resolution by session.resolution.collectAsState()
  // The graph's unscoped pager-factory getter creates a new instance on every access.
  // Keep shell dependencies stable while external route resolution recomposes this host.
  val repository = remember(runtime) { runtime.repository }
  val pagerFactory = remember(runtime) { runtime.pagerFactory }
  val coroutineDispatcher = remember { com.simtop.core.core.DefaultCoroutineDispatcherProvider() }
  LaunchedEffect(session) { session.start() }
  when (val current = resolution) {
    null -> WebStartupLoading()
    is WebRouteResolution.MissingBeer -> WebMissingBeer(onOpenCatalog = session::openCatalog)
    is WebRouteResolution.Ready ->
      SharedAppShell(
        repository = repository,
        pagerFactory = pagerFactory,
        coroutineDispatcher = coroutineDispatcher,
        strings = webStrings,
        initialRoute = current.route,
        host = webHost(runtime, session),
        onClose = session::goBack,
      )
  }
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
    navigationRequests = session.routes(),
    onNavigationEvent = session::onNavigationEvent,
    detailCollapsingToolbarEnabled = false,
    detailAnimationsDisabled = true,
  )

@Composable
private fun WebBeerRow(runtime: WebDataRuntime, beer: Beer, onClick: () -> Unit) {
  val availability = if (beer.availability) "Available" else "Out of stock"
  SharedBeerListItem(
    modifier = Modifier.semantics { contentDescription = "${beer.name}. $availability" },
    titleMaxLines = Int.MAX_VALUE,
    beer = beer,
    labels =
      BeerListItemLabels(
        abv = "ABV: ${beer.abv}%",
        ibu = "IBU: ${beer.ibu}",
        availability = availability,
      ),
    onClick = onClick,
    imageContent = { modifier ->
      WebImage(
        runtime,
        beer.imageUrl,
        null,
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = ContentScale.Fit,
      )
    },
  )
}

@Composable
private fun WebImage(
  runtime: WebDataRuntime,
  url: String,
  description: String?,
  modifier: Modifier,
  contentScale: ContentScale = ContentScale.Crop,
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
      contentScale = contentScale,
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
    savedFilters = "Saved filters",
    savedFiltersEmpty = "No saved filters yet",
    saveFilter = "Save",
    filterPresetLimitReached = "You can save up to 10 filters. Delete one before saving another.",
    saveFilterFailed = "The filter could not be saved.",
    mutateFilterFailed = "This saved filter could not be changed. Try again.",
    filterNameHint = "Filter name",
    renameFilter = "Rename",
    deleteFilter = "Delete",
    retry = "Retry",
    error = "Unable to load beers",
    listLoadMoreFailed = "More beers could not be loaded",
    listEndOfList = { count -> "End of list · ${formatCount(count, "beer", "beers")}" },
    searchHint = "Search beers",
    styleFilter = "Styles",
    allStyles = "All styles",
    clearStyle = "Clear style",
    clearFilters = "Clear filters",
    searchPrompt = "Type at least two characters to search",
    searchNoResults = { term -> "No beers found for \"$term\"" },
    searchResultCount = { count -> formatCount(count, "result", "results") },
    searchEndOfList = { count -> "End of results · ${formatCount(count, "beer", "beers")}" },
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
        beersCount = { count -> formatCount(count, "beer", "beers") },
        endOfList = { count -> "End of list · ${formatCount(count, "beer", "beers")}" },
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
        servingTemperatureValue = { minTemperature, maxTemperature ->
          formatServingTemperatureRange(minTemperature, maxTemperature)
        },
        fermentation = "Fermentation",
        ingredients = "Ingredients",
        recommendedGlasses = "Recommended glasses",
      ),
  )
