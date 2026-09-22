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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseBeersViewModel
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseBeersContent
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseHomeContent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailEvent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailViewModel
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailContent
import com.simtop.billionbeers.shared.beersearch.BeersSearchViewModel
import com.simtop.billionbeers.shared.beersearch.SharedBeersSearchContent
import com.simtop.billionbeers.shared.beerslist.BeersListViewModel
import com.simtop.billionbeers.shared.beerslist.SharedBeersListContent
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel
import com.simtop.billionbeers.shared.favorites.SharedFavoritesContent
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.navigation.contract.PortableRoute
import kotlinx.coroutines.cancel
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
  var backStack by remember { mutableStateOf(listOf(initialRoute)) }
  var browseSelection by remember { mutableStateOf<BrowseSelection?>(null) }
  val route = backStack.lastOrNull() ?: initialRoute
  val canGoBack =
    backStack.size > 1 || route is PortableRoute.BeersSearch || route is PortableRoute.BeerBrowse

  fun pop() {
    when {
      browseSelection != null -> browseSelection = null
      backStack.size > 1 -> backStack = backStack.dropLast(1)
      else -> onClose()
    }
  }

  fun navigate(next: PortableRoute) {
    backStack =
      when (next) {
        PortableRoute.BeersList,
        PortableRoute.Favorites -> listOf(next)
        else -> backStack + next
      }
  }

  LaunchedEffect(host.routeRequests) {
    host.routeRequests.collectLatest { requestedRoute ->
      backStack =
        when (requestedRoute) {
          PortableRoute.BeersList,
          PortableRoute.Favorites -> listOf(requestedRoute)
          else -> backStack.dropLast(1).ifEmpty { listOf(PortableRoute.BeersList) } + requestedRoute
        }
      browseSelection = null
    }
  }

  BillionBeersTheme(darkTheme = host.darkTheme) {
    Scaffold(
      modifier = modifier,
      topBar = {
        if (route !is PortableRoute.BeerBrowse && route !is PortableRoute.BeerDetail) {
          ShellTopBar(
            title =
              when (route) {
                PortableRoute.BeersList -> strings.list
                PortableRoute.Favorites -> strings.favorites
                PortableRoute.BeersSearch -> strings.search
                PortableRoute.BeerBrowse -> strings.browse
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
        when (route) {
          PortableRoute.BeersList ->
            ListDestination(
              repository = repository,
              pagerFactory = pagerFactory,
              strings = strings,
              host = host,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
              onSearch = { navigate(PortableRoute.BeersSearch) },
              onBrowse = { navigate(PortableRoute.BeerBrowse) },
            )
          PortableRoute.Favorites ->
            FavoritesDestination(
              repository = repository,
              strings = strings,
              host = host,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
            )
          PortableRoute.BeersSearch ->
            SearchDestination(
              pagerFactory = pagerFactory,
              coroutineDispatcher = coroutineDispatcher,
              strings = strings,
              host = host,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
            )
          PortableRoute.BeerBrowse ->
            BrowseDestination(
              repository = repository,
              pagerFactory = pagerFactory,
              coroutineDispatcher = coroutineDispatcher,
              strings = strings,
              host = host,
              selection = browseSelection,
              onSelection = { browseSelection = it },
              onBack = ::pop,
              onBeerClick = { navigate(PortableRoute.BeerDetail(it)) },
            )
          is PortableRoute.BeerDetail ->
            DetailDestination(
              repository = repository,
              strings = strings,
              host = host,
              beer = route.beer,
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
  repository: BeersRepository,
  pagerFactory: BeersPagerFactory,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
  onSearch: () -> Unit,
  onBrowse: () -> Unit,
) {
  val viewModel = rememberViewModel { BeersListViewModel(repository, pagerFactory) }
  val viewState by viewModel.beerListViewState.collectAsState()
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
        onScrollToBottom = viewModel::onScrollToBottom,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::refresh,
        onRetryLoadMore = viewModel::onRetryLoadMore,
      )
    }
    ShellActions(strings, onSearch, onBrowse)
  }
}

@Composable
private fun FavoritesDestination(
  repository: BeersRepository,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
) {
  val viewModel = rememberViewModel { FavoritesViewModel(repository) }
  val viewState by viewModel.viewState.collectAsState()
  SharedFavoritesContent(
    viewState = viewState,
    emptyText = strings.favoritesEmpty,
    errorText = strings.error,
    beerRow = { beer -> host.beerRow(beer) { onBeerClick(beer) } },
  )
}

@Composable
private fun SearchDestination(
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBeerClick: (Beer) -> Unit,
) {
  val viewModel = rememberViewModel { BeersSearchViewModel(coroutineDispatcher, pagerFactory) }
  val query by viewModel.query.collectAsState()
  val viewState by viewModel.viewState.collectAsState()
  val count = (viewState as? CommonUiState.Success)?.data?.items?.size ?: 0
  Column(Modifier.fillMaxSize()) {
    OutlinedTextField(
      value = query,
      onValueChange = viewModel::onQueryChange,
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
        onScrollToBottom = viewModel::onScrollToBottom,
        onRetryLoadMore = viewModel::onRetryLoadMore,
        onRetrySearch = viewModel::onRetrySearch,
        contentPadding = PaddingValues(),
      )
    }
  }
}

@Composable
private fun BrowseDestination(
  repository: BeersRepository,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
  strings: SharedAppStrings,
  host: SharedAppHost,
  selection: BrowseSelection?,
  onSelection: (BrowseSelection?) -> Unit,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
) {
  if (selection == null) {
    var selectedTab by remember { mutableStateOf(0) }
    val viewModel = rememberViewModel {
      com.simtop.billionbeers.shared.beerbrowse.BrowseViewModel(repository)
    }
    val styles by viewModel.styles.collectAsState()
    val breweries by viewModel.breweries.collectAsState()
    SharedBrowseHomeContent(
      strings = strings.browseStrings,
      styles = styles,
      breweries = breweries,
      selectedTab = selectedTab,
      onTabSelected = {
        selectedTab = it
        if (it == 1) viewModel.onBreweriesTabSelected()
      },
      onStyleClick = { onSelection(BrowseSelection(styleId = it.id, name = it.name)) },
      onBreweryClick = { onSelection(BrowseSelection(breweryId = it.id, name = it.name)) },
      onBack = onBack,
      backIcon = host.backIcon,
      onRetryStyles = viewModel::retryStyles,
      onRetryBreweries = viewModel::retryBreweries,
      errorContent = host.errorContent,
    )
  } else {
    val viewModel =
      rememberViewModel(selection) {
        BrowseBeersViewModel(
          coroutineDispatcher,
          pagerFactory,
          selection.toQuery(),
        )
      }
    val viewState by viewModel.viewState.collectAsState()
    SharedBrowseBeersContent(
      strings = strings.browseStrings,
      title = selection.name,
      viewState = viewState,
      onBack = { onSelection(null) },
      backIcon = host.backIcon,
      onBeerClick = onBeerClick,
      onScrollToBottom = viewModel::onScrollToBottom,
      onRetryLoadMore = viewModel::onRetryLoadMore,
      onRetryFirstPage = viewModel::onRetryFirstPage,
      errorContent = host.errorContent,
      beerRow = { beer, onClick -> host.beerRow(beer, onClick) },
    )
  }
}

@Composable
private fun DetailDestination(
  repository: BeersRepository,
  strings: SharedAppStrings,
  host: SharedAppHost,
  beer: Beer,
  onBack: () -> Unit,
  animationsDisabled: Boolean,
) {
  val viewModel = rememberViewModel(beer) { BeerDetailViewModel(repository, beer) }
  val state by viewModel.beerDetailViewState.collectAsState()
  LaunchedEffect(viewModel) {
    viewModel.events.collectLatest { event ->
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
        onToggleAvailability = { viewModel.updateAvailability(currentState.data) },
        onToggleFavorite = { viewModel.updateFavorite(currentState.data) },
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

private data class BrowseSelection(
  val styleId: String? = null,
  val breweryId: String? = null,
  val name: String,
) {
  fun toQuery() = BeersQuery(styleId = styleId, breweryId = breweryId)
}

@Composable
private fun <T : ViewModel> rememberViewModel(factory: () -> T): T {
  val viewModel = remember { factory() }
  DisposableEffect(viewModel) { onDispose { viewModel.viewModelScope.cancel() } }
  return viewModel
}

@Composable
private fun <K, T : ViewModel> rememberViewModel(key: K, factory: () -> T): T {
  val viewModel = remember(key) { factory() }
  DisposableEffect(viewModel) { onDispose { viewModel.viewModelScope.cancel() } }
  return viewModel
}
