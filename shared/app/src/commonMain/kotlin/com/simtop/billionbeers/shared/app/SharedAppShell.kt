package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseBeersContent
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseHomeContent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailEvent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailContent
import com.simtop.billionbeers.shared.beersearch.SharedBeersSearchContent
import com.simtop.billionbeers.shared.beerslist.SharedBeersListContent
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.favorites.SharedFavoritesContent
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.navigation.contract.PortableRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow

/** Host-owned copy and formatting strings for the portable application shell. */
data class SharedAppStrings(
  val appTitle: String,
  val back: String,
  val list: String,
  val favorites: String,
  val search: String,
  val browse: String,
  val retry: String,
  val error: String,
  val listLoadMoreFailed: String,
  val listEndOfList: (Int) -> String,
  val searchHint: String,
  val searchPrompt: String,
  val searchNoResults: (String) -> String,
  val searchResultCount: (Int) -> String,
  val searchEndOfList: (Int) -> String,
  val favoritesEmpty: String,
  val browseStrings: BrowseStrings,
  val detailStrings: BeerDetailStrings,
)

sealed interface SharedAppNavigationEvent {
  data class Push(val route: PortableRoute) : SharedAppNavigationEvent

  data class Pop(val route: PortableRoute) : SharedAppNavigationEvent

  data class Replace(val route: PortableRoute) : SharedAppNavigationEvent
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
  val onNavigationEvent: (SharedAppNavigationEvent) -> Unit = {},
  val onMessage: (String) -> Unit = {},
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
  val navigation =
    remember(repository, pagerFactory, coroutineDispatcher) {
      SharedAppNavigationState(repository, pagerFactory, coroutineDispatcher, initialRoute)
    }
  DisposableEffect(navigation) { onDispose { navigation.disposeAll() } }

  LaunchedEffect(host.routeRequests) {
    host.routeRequests.collectLatest(navigation::replaceFromRoute)
  }

  val entry = navigation.current
  val route = entry.route
  val canGoBack = navigation.entries.size > 1 || route is PortableRoute.BeersSearch

  fun pop() {
    if (navigation.pop()) {
      host.onNavigationEvent(SharedAppNavigationEvent.Pop(navigation.current.route))
    } else {
      onClose()
    }
  }

  fun navigate(next: PortableRoute) {
    navigation.navigate(next)
    host.onNavigationEvent(SharedAppNavigationEvent.Push(navigation.current.route))
  }

  BillionBeersTheme(darkTheme = host.darkTheme) {
    Scaffold(
      modifier = modifier,
      topBar = {
        if (
          route !is PortableRoute.BeerBrowse &&
            route !is PortableRoute.BeerBrowseSelection &&
            route !is PortableRoute.BeerDetail
        ) {
          ShellTopBar(
            title =
              when (route) {
                PortableRoute.BeersList -> strings.list
                PortableRoute.Favorites -> strings.favorites
                PortableRoute.BeersSearch -> strings.search
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
        if (route == PortableRoute.BeersList || route == PortableRoute.Favorites) {
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
          }
        }
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when (entry) {
          is ListEntry ->
            ListDestination(
              entry = entry,
              strings = strings,
              host = host,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
              onSearch = { navigate(PortableRoute.BeersSearch) },
              onBrowse = { navigate(PortableRoute.BeerBrowse) },
            )
          is FavoritesEntry ->
            FavoritesDestination(
              entry = entry,
              strings = strings,
              host = host,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
            )
          is SearchEntry ->
            SearchDestination(
              entry = entry,
              strings = strings,
              host = host,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
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
            )
          is DetailEntry ->
            DetailDestination(
              entry = entry,
              strings = strings,
              host = host,
              onBack = ::pop,
              animationsDisabled = host.detailAnimationsDisabled,
            )
        }
      }
    }
  }
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
private fun ListDestination(
  entry: ListEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
  onSearch: () -> Unit,
  onBrowse: () -> Unit,
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
    ShellActions(strings, onSearch, onBrowse)
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
) {
  val query by entry.viewModel.query.collectAsState()
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
) {
  val viewState by entry.viewModel.viewState.collectAsState()
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

@Composable
private fun DetailDestination(
  entry: DetailEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBack: () -> Unit,
  animationsDisabled: Boolean,
) {
  val state by entry.viewModel.beerDetailViewState.collectAsState()
  LaunchedEffect(entry.viewModel) {
    entry.viewModel.events.collectLatest { event ->
      if (event is BeerDetailEvent.ShowError) host.onMessage(event.message)
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
      )
  }
}

@Composable
private fun ShellActions(strings: SharedAppStrings, onSearch: () -> Unit, onBrowse: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
    Button(onClick = onSearch, modifier = Modifier.weight(1f)) { Text(strings.search) }
    Button(onClick = onBrowse, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
      Text(strings.browse)
    }
  }
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
