package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseBeersContent
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseHomeContent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailError
import com.simtop.billionbeers.shared.beerdetail.BeerDetailEvent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailContent
import com.simtop.billionbeers.shared.beersearch.BeersSearchStyleFilter
import com.simtop.billionbeers.shared.beersearch.SharedBeersSearchContent
import com.simtop.billionbeers.shared.beerslist.SharedBeersListContent
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.favorites.SharedFavoritesContent
import com.simtop.billionbeers.shared.presentation.FilterPresetFormLabels
import com.simtop.billionbeers.shared.presentation.SharedFilterPresetForm
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.core.core.SystemEpochTimeProvider
import com.simtop.navigation.contract.PortableRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Host-owned copy and formatting strings for the portable application shell. */
data class SharedAppStrings(
  val appTitle: String,
  val back: String,
  val list: String,
  val favorites: String,
  val search: String,
  val browse: String,
  val savedFilters: String,
  val savedFiltersEmpty: String,
  val saveFilter: String,
  val filterPresetLimitReached: String,
  val saveFilterFailed: String,
  val mutateFilterFailed: String,
  val filterNameHint: String,
  val renameFilter: String,
  val deleteFilter: String,
  val retry: String,
  val error: String,
  val listLoadMoreFailed: String,
  val listEndOfList: (Int) -> String,
  val searchHint: String,
  val styleFilter: String,
  val allStyles: String,
  val clearStyle: String,
  val clearFilters: String,
  val searchPrompt: String,
  val searchNoResults: (String) -> String,
  val searchResultCount: (Int) -> String,
  val searchEndOfList: (Int) -> String,
  val favoritesEmpty: String,
  val browseStrings: BrowseStrings,
  val detailStrings: BeerDetailStrings,
)

sealed interface SharedAppNavigationEvent {
  data class Push(val route: PortableRoute, val savedFilter: SavedFilterPreset? = null) :
    SharedAppNavigationEvent

  data class Pop(val route: PortableRoute) : SharedAppNavigationEvent

  data class Replace(val route: PortableRoute) : SharedAppNavigationEvent
}

/** Host restoration commands; saved results remain internal, not a portable public route. */
sealed interface SharedAppNavigationRequest {
  data class Route(val route: PortableRoute) : SharedAppNavigationRequest

  data class SavedFilter(val preset: SavedFilterPreset) : SharedAppNavigationRequest
}

/** Platform slots for resources, images, rows, and errors that cannot live in common code. */
data class SharedAppHost(
  val beerRow: @Composable (Beer, () -> Unit) -> Unit,
  val errorContent: @Composable (CommonUiState.Error, () -> Unit) -> Unit,
  val backIcon: @Composable (String) -> Unit,
  val favoriteIcon: @Composable (Boolean, String) -> Unit,
  val imageContent: @Composable (String, String?, Modifier) -> Unit,
  val detailAnimationsDisabled: Boolean = false,
  val detailCollapsingToolbarEnabled: Boolean = true,
  val detailTitleTextStyle: TextStyle? = null,
  val darkTheme: Boolean = false,
  val routeRequests: Flow<PortableRoute> = emptyFlow(),
  val navigationRequests: Flow<SharedAppNavigationRequest> = emptyFlow(),
  val onNavigationEvent: (SharedAppNavigationEvent) -> Unit = {},
  val messageContent: @Composable (String) -> Unit = { message ->
    Surface(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
      Text(text = message, modifier = Modifier.padding(16.dp))
    }
  },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAppShell(
  repository: BeersRepository,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
  strings: SharedAppStrings,
  host: SharedAppHost,
  modifier: Modifier = Modifier,
  initialRoute: PortableRoute = PortableRoute.BeersList,
  onClose: () -> Unit = {},
) {
  val onNavigationEvent by rememberUpdatedState(host.onNavigationEvent)
  val navigation =
    remember(repository, pagerFactory, coroutineDispatcher) {
      SharedAppNavigationState(
        repository,
        pagerFactory,
        coroutineDispatcher,
        initialRoute,
        onNavigationEvent = { onNavigationEvent(it) },
      )
    }
  DisposableEffect(navigation) { onDispose { navigation.disposeAll() } }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(host.routeRequests, host.navigationRequests) {
    merge(
        host.routeRequests.map { SharedAppNavigationRequest.Route(it) },
        host.navigationRequests,
      )
      .collectLatest { request ->
        when (request) {
          is SharedAppNavigationRequest.Route -> navigation.replaceFromRoute(request.route)
          is SharedAppNavigationRequest.SavedFilter ->
            navigation.replaceFromSavedFilter(request.preset)
        }
      }
  }

  val entry = navigation.current
  val route = entry.route
  val canGoBack = navigation.entries.size > 1 || route is PortableRoute.BeersSearch
  var message by remember(entry) { mutableStateOf<String?>(null) }
  LaunchedEffect(message) {
    if (message != null) {
      delay(4_000L)
      message = null
    }
  }

  fun pop() {
    if (!navigation.pop()) {
      onClose()
    }
  }

  fun navigate(next: PortableRoute) {
    navigation.navigate(next)
  }

  suspend fun savePreset(name: String, query: BeersQuery): Boolean {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty() || normalizedName.length > SavedFilterPreset.MAX_NAME_LENGTH) {
      return false
    }
    val updatedAt = SystemEpochTimeProvider().epochMillis()
    return when (
      val result =
        repository.saveFilterPreset(
          SavedFilterPreset(
            id = "preset-${normalizedName.hashCode()}-${query.hashCode()}",
            name = normalizedName,
            query = query,
            updatedAt = updatedAt,
          )
        )
    ) {
      is Either.Right -> true
      is Either.Left -> {
        message =
          when (result.value) {
            SaveFilterPresetError.CapacityReached -> strings.filterPresetLimitReached
            is SaveFilterPresetError.Unknown -> strings.saveFilterFailed
          }
        false
      }
    }
  }

  fun applyPreset(preset: SavedFilterPreset) {
    navigation.selectSavedFilter(preset)
  }

  BillionBeersTheme(darkTheme = host.darkTheme) {
    Scaffold(
      modifier = modifier,
      topBar = {
        if (shouldShowShellTopBar(entry)) {
          ShellTopBar(
            title =
              when (route) {
                PortableRoute.BeersList -> strings.list
                PortableRoute.Favorites -> strings.favorites
                PortableRoute.BeersSearch -> strings.search
                PortableRoute.SavedFilterPresets ->
                  (entry as? SavedFilterResultsEntry)?.preset?.name ?: strings.savedFilters
                PortableRoute.BeerBrowse -> strings.browse
                is PortableRoute.BeerBrowseSelection -> route.category.name
                is PortableRoute.BeerDetail -> route.beer.name
              },
            showBack = canGoBack,
            back = strings.back,
            backIcon = host.backIcon,
            onBack = ::pop,
          )
        }
      },
      bottomBar = {
        Column {
          message?.let { currentMessage ->
            Box(Modifier.fillMaxWidth().padding(16.dp).testTag("shell_message")) {
              host.messageContent(currentMessage)
            }
          }
          if (
            route == PortableRoute.BeersList ||
              route == PortableRoute.Favorites ||
              route == PortableRoute.SavedFilterPresets
          ) {
            NavigationBar {
              NavigationBarItem(
                selected = route == PortableRoute.BeersList,
                onClick = { navigate(PortableRoute.BeersList) },
                icon = {},
                label = { Text(strings.list) },
              )
              NavigationBarItem(
                selected = route == PortableRoute.Favorites,
                onClick = { navigate(PortableRoute.Favorites) },
                icon = {},
                label = { Text(strings.favorites) },
              )
              NavigationBarItem(
                selected = route == PortableRoute.SavedFilterPresets,
                onClick = { navigate(PortableRoute.SavedFilterPresets) },
                icon = {},
                label = { Text(strings.savedFilters) },
              )
            }
          }
        }
      },
    ) { padding ->
      BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (shouldShowExpandedCatalogDetail(maxWidth, navigation.entries)) {
          ExpandedCatalogDetail(
            catalog = navigation.entries.first() as ListEntry,
            detail = entry as DetailEntry,
            strings = strings,
            host = host,
            onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
            onSearch = { navigate(PortableRoute.BeersSearch) },
            onBrowse = { navigate(PortableRoute.BeerBrowse) },
            onBack = ::pop,
            animationsDisabled = host.detailAnimationsDisabled,
            onMessage = { message = it },
          )
        } else {
          when (entry) {
            is ListEntry ->
              ListDestination(
                entry = entry,
                strings = strings,
                host = host,
                onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
                onSearch = { navigate(PortableRoute.BeersSearch) },
                onBrowse = { navigate(PortableRoute.BeerBrowse) },
                onSaveQuery = { name -> savePreset(name, BeersQuery()) },
              )
            is FavoritesEntry ->
              FavoritesDestination(
                entry = entry,
                strings = strings,
                host = host,
                onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
              )
            is SavedFiltersEntry ->
              SavedFiltersDestination(
                entry = entry,
                strings = strings,
                onApply = ::applyPreset,
                onRename = { preset, name ->
                  val normalizedName = name.trim()
                  if (
                    normalizedName.isNotEmpty() &&
                      normalizedName.length <= SavedFilterPreset.MAX_NAME_LENGTH
                  ) {
                    val result =
                      repository.renameFilterPreset(
                        preset.id,
                        normalizedName,
                        SystemEpochTimeProvider().epochMillis(),
                      )
                    val succeeded = result is Either.Right
                    if (!succeeded) message = strings.mutateFilterFailed
                    succeeded
                  } else false
                },
                onDelete = { preset ->
                  val succeeded = repository.deleteFilterPreset(preset.id) is Either.Right
                  if (!succeeded) message = strings.mutateFilterFailed
                  succeeded
                },
              )
            is SearchEntry ->
              SearchDestination(
                entry = entry,
                strings = strings,
                host = host,
                onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
                onSaveQuery = { name -> savePreset(name, entry.viewModel.activeQuery.value) },
              )
            is BrowseHomeEntry ->
              BrowseHomeDestination(
                entry = entry,
                strings = strings,
                host = host,
                onSelection = navigation::selectBrowse,
                onBack = ::pop,
              )
            is BrowseBeersEntry ->
              BrowseBeersDestination(
                entry = entry,
                strings = strings,
                host = host,
                onBack = ::pop,
                onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
                onSaveQuery = { name -> savePreset(name, entry.selection.toQuery()) },
              )
            is SavedFilterResultsEntry ->
              SavedFilterResultsDestination(
                entry = entry,
                strings = strings,
                host = host,
                onBack = ::pop,
                onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
              )
            is DetailEntry ->
              DetailDestination(
                entry = entry,
                strings = strings,
                host = host,
                onBack = ::pop,
                animationsDisabled = host.detailAnimationsDisabled,
                onMessage = { message = it },
              )
          }
        }
      }
    }
  }
}

internal fun shouldShowShellTopBar(entry: SharedAppEntry): Boolean {
  if (entry is SavedFilterResultsEntry) return false
  return entry.route !is PortableRoute.BeerBrowse &&
    entry.route !is PortableRoute.BeerBrowseSelection &&
    entry.route !is PortableRoute.BeerDetail
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellTopBar(
  title: String,
  showBack: Boolean,
  back: String,
  backIcon: @Composable (String) -> Unit,
  onBack: () -> Unit,
) {
  TopAppBar(
    navigationIcon = {
      if (showBack) {
        IconButton(onClick = onBack) { backIcon(back) }
      }
    },
    title = { Text(title) },
  )
}

@Composable
private fun ExpandedCatalogDetail(
  catalog: ListEntry,
  detail: DetailEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
  onSearch: () -> Unit,
  onBrowse: () -> Unit,
  onBack: () -> Unit,
  animationsDisabled: Boolean,
  onMessage: (String) -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().widthIn(max = 1440.dp)) {
      Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
        ListDestination(
          entry = catalog,
          strings = strings,
          host = host,
          onBeerClick = onBeerClick,
          onSearch = onSearch,
          onBrowse = onBrowse,
        )
      }
      Box(
        modifier =
          Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant)
      )
      Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
        DetailDestination(
          entry = detail,
          strings = strings,
          host = host,
          onBack = onBack,
          showBackButton = false,
          animationsDisabled = animationsDisabled,
          onMessage = onMessage,
        )
      }
    }
  }
}

@Composable
private fun ListDestination(
  entry: ListEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
  onSearch: () -> Unit,
  onBrowse: () -> Unit,
  onSaveQuery: suspend (String) -> Boolean = { false },
) {
  val viewState by entry.viewModel.beerListViewState.collectAsState()
  Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
      SharedBeersListContent(
        viewState = viewState,
        beerRow = { beer -> host.beerRow(beer) { onBeerClick(beer) } },
        loadingContent = { LoadingContent() },
        emptyContent = { retry ->
          host.errorContent(CommonUiState.Error(errorKey = CommonUiErrorKey.NoBeersFound), retry)
        },
        errorContent = host.errorContent,
        loadMoreFailedText = strings.listLoadMoreFailed,
        retryText = strings.retry,
        endOfListText =
          strings.listEndOfList((viewState as? CommonUiState.Success)?.data?.items?.size ?: 0),
        onScrollToBottom = entry.viewModel::onScrollToBottom,
        onRefresh = entry.viewModel::refresh,
        onRetry = entry.viewModel::refresh,
        onRetryLoadMore = entry.viewModel::onRetryLoadMore,
        listState = entry.listState,
      )
    }
    ShellActions(strings, onSearch, onBrowse, onSaveQuery)
  }
}

@Composable
private fun FavoritesDestination(
  entry: FavoritesEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
) {
  val viewState by entry.viewModel.viewState.collectAsState()
  SharedFavoritesContent(
    viewState = viewState,
    emptyText = strings.favoritesEmpty,
    errorText = strings.error,
    beerRow = { beer -> host.beerRow(beer) { onBeerClick(beer) } },
    listState = entry.listState,
  )
}

@Composable
private fun SearchDestination(
  entry: SearchEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
  onSaveQuery: suspend (String) -> Boolean,
) {
  val query by entry.viewModel.query.collectAsState()
  val styles by entry.viewModel.styles.collectAsState()
  val selectedStyle by entry.viewModel.selectedStyle.collectAsState()
  val activeQuery by entry.viewModel.activeQuery.collectAsState()
  val viewState by entry.viewModel.viewState.collectAsState()
  val count = (viewState as? CommonUiState.Success)?.data?.items?.size ?: 0
  Column(Modifier.fillMaxSize()) {
    OutlinedTextField(
      value = query,
      onValueChange = entry.viewModel::onQueryChange,
      label = { Text(strings.searchHint) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BeersSearchStyleFilter(
        styles = styles,
        selectedStyle = selectedStyle,
        styleLabel = strings.styleFilter,
        allStylesLabel = strings.allStyles,
        clearStyleLabel = strings.clearStyle,
        retryLabel = strings.retry,
        onStyleChange = entry.viewModel::onStyleSelected,
        onRetryStyles = entry.viewModel::retryStyles,
      )
      if (activeQuery.search != null || activeQuery.styleId != null) {
        TextButton(onClick = entry.viewModel::onResetFilters) { Text(strings.clearFilters) }
      }
    }
    SaveFilterForm(
      strings = strings,
      onSaveQuery = onSaveQuery,
      modifier = Modifier.padding(horizontal = 12.dp),
      enabled = activeQuery.search != null || activeQuery.styleId != null,
    )
    Box(Modifier.weight(1f)) {
      SharedBeersSearchContent(
        viewState = viewState,
        query = query,
        beerRow = { beer -> host.beerRow(beer) { onBeerClick(beer) } },
        loadingContent = { LoadingContent() },
        emptyContent = { ShellHint(strings.searchPrompt) },
        noResultsContent = { ShellHint(strings.searchNoResults(it)) },
        errorContent = host.errorContent,
        resultCountContent = {
          Text(strings.searchResultCount(it), modifier = Modifier.padding(12.dp))
        },
        loadMoreFailedText = strings.listLoadMoreFailed,
        retryText = strings.retry,
        endOfListText = strings.searchEndOfList(count),
        onScrollToBottom = entry.viewModel::onScrollToBottom,
        onRetryLoadMore = entry.viewModel::onRetryLoadMore,
        onRetrySearch = entry.viewModel::onRetrySearch,
        contentPadding = PaddingValues(),
        listState = entry.listState,
      )
    }
  }
}

@Composable
private fun BrowseHomeDestination(
  entry: BrowseHomeEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onSelection: (BrowseSelection) -> Unit,
  onBack: () -> Unit,
) {
  val styles by entry.viewModel.styles.collectAsState()
  val breweries by entry.viewModel.breweries.collectAsState()
  SharedBrowseHomeContent(
    strings = strings.browseStrings,
    styles = styles,
    breweries = breweries,
    selectedTab = entry.selectedTab,
    onTabSelected = {
      entry.selectedTab = it
      if (it == 1) entry.viewModel.onBreweriesTabSelected()
    },
    onStyleClick = { onSelection(BrowseSelection(styleId = it.id, name = it.name)) },
    onBreweryClick = { onSelection(BrowseSelection(breweryId = it.id, name = it.name)) },
    onBack = onBack,
    backIcon = host.backIcon,
    onRetryStyles = entry.viewModel::retryStyles,
    onRetryBreweries = entry.viewModel::retryBreweries,
    errorContent = host.errorContent,
  )
}

@Composable
private fun BrowseBeersDestination(
  entry: BrowseBeersEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  onSaveQuery: suspend (String) -> Boolean,
) {
  val viewState by entry.viewModel.viewState.collectAsState()
  Column(Modifier.fillMaxSize()) {
    SaveFilterForm(
      strings = strings,
      onSaveQuery = onSaveQuery,
      modifier = Modifier.padding(horizontal = 12.dp),
    )
    Box(Modifier.weight(1f)) {
      SharedBrowseBeersContent(
        strings = strings.browseStrings,
        title = entry.selection.name,
        viewState = viewState,
        onBack = onBack,
        backIcon = host.backIcon,
        onBeerClick = onBeerClick,
        onScrollToBottom = entry.viewModel::onScrollToBottom,
        onRetryLoadMore = entry.viewModel::onRetryLoadMore,
        onRetryFirstPage = entry.viewModel::onRetryFirstPage,
        errorContent = host.errorContent,
        beerRow = { beer, onClick -> host.beerRow(beer, onClick) },
        listState = entry.listState,
      )
    }
  }
}

@Composable
private fun DetailDestination(
  entry: DetailEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBack: () -> Unit,
  showBackButton: Boolean = true,
  animationsDisabled: Boolean,
  onMessage: (String) -> Unit,
) {
  val state by entry.viewModel.beerDetailViewState.collectAsState()
  LaunchedEffect(entry.viewModel) {
    entry.viewModel.events.collectLatest { event ->
      if (event is BeerDetailEvent.ShowError) {
        onMessage(
          when (event.error) {
            BeerDetailError.FavoriteUpdate,
            BeerDetailError.AvailabilityUpdate -> strings.error
          }
        )
      }
    }
  }
  when (val currentState = state) {
    CommonUiState.Loading,
    CommonUiState.Empty -> LoadingContent()
    is CommonUiState.Error -> host.errorContent(currentState, {})
    is CommonUiState.Success ->
      SharedBeerDetailContent(
        beer = currentState.data,
        strings = strings.detailStrings,
        onBackClick = onBack,
        onToggleAvailability = { entry.viewModel.updateAvailability(currentState.data) },
        onToggleFavorite = { entry.viewModel.updateFavorite(currentState.data) },
        backIcon = host.backIcon,
        favoriteIcon = host.favoriteIcon,
        imageContent = host.imageContent,
        animationsDisabled = animationsDisabled,
        collapsingToolbarEnabled = host.detailCollapsingToolbarEnabled,
        titleTextStyle = host.detailTitleTextStyle,
        showBackButton = showBackButton,
      )
  }
}

@Composable
private fun ShellActions(
  strings: SharedAppStrings,
  onSearch: () -> Unit,
  onBrowse: () -> Unit,
  onSaveQuery: suspend (String) -> Boolean,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
    Row(Modifier.fillMaxWidth()) {
      Button(onClick = onSearch, modifier = Modifier.weight(1f)) { Text(strings.search) }
      Button(onClick = onBrowse, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
        Text(strings.browse)
      }
    }
    SaveFilterForm(strings, onSaveQuery, modifier = Modifier.padding(top = 8.dp))
  }
}

@Composable
private fun SaveFilterForm(
  strings: SharedAppStrings,
  onSaveQuery: suspend (String) -> Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  var name by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  SharedFilterPresetForm(
    name = name,
    onNameChange = { name = it },
    onSave = {
      scope.launch {
        if (onSaveQuery(name)) name = ""
      }
    },
    labels = FilterPresetFormLabels(strings.filterNameHint, strings.saveFilter),
    modifier = modifier,
    enabled = enabled,
  )
}

@Composable
private fun LoadingContent() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ShellHint(text: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}
