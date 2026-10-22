package com.simtop.feature.beerslist

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.shimmerBrush
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.beerslist.BeersListEvent
import com.simtop.billionbeers.shared.beerslist.SharedBeersListContent
import com.simtop.billionbeers.shared.designsystem.controls.BrandCard
import com.simtop.billionbeers.shared.designsystem.controls.BrandIcon
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandOutlinedTextField
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTextButton
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.LocalDebugDrawerToggle
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import com.simtop.presentation_utils.custom_views.ComposeSaveFilterAction
import com.simtop.presentation_utils.custom_views.ComposeSaveFilterForm
import com.simtop.presentation_utils.custom_views.SaveFilterState
import com.simtop.presentation_utils.custom_views.rememberSaveFilterState
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersListScreen(
  onBeerClick: (Beer) -> Unit,
  onSearchClick: () -> Unit,
  onBrowseClick: () -> Unit,
  onSaveQuery: suspend (String, BeersQuery) -> Boolean,
  viewModel: BeersListViewModel = metroViewModel(),
) {
  val rawState by viewModel.beerListViewState.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()
  val activeQuery by viewModel.activeQuery.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(PresentationUtilsR.string.paged_list_load_more_failed)
  val refreshFailedMessage = stringResource(R.string.beers_refresh_failed)

  // One-shot toasts: a failed "load more" (the footer owns the retry affordance) and a failed
  // refresh (the list stays; pulling again is the retry).
  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.events.collect { event ->
        when (event) {
          BeersListEvent.ShowLoadMoreError -> showToast(context, loadMoreFailedMessage)
          BeersListEvent.ShowRefreshError -> showToast(context, refreshFailedMessage)
        }
      }
    }
  }

  BeersListContent(
    viewState = rawState,
    onBeerClick = onBeerClick,
    onSearchClick = onSearchClick,
    onBrowseClick = onBrowseClick,
    onSaveQuery = { name ->
      viewModel.submitSearch()
      onSaveQuery(name, viewModel.activeQuery.value)
    },
    searchQuery = query,
    activeSearchQuery = activeQuery.search,
    onSearchQueryChange = viewModel::onQueryChange,
    onSearchSubmit = viewModel::submitSearch,
    onScrollToBottom = { viewModel.onScrollToBottom() },
    onRefresh = { viewModel.refresh() },
    onRetry = { viewModel.refresh() },
    onRetryLoadMore = { viewModel.onRetryLoadMore() },
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
// The screen API keeps its event callbacks explicit so callers cannot accidentally omit a user
// action. This is a deliberate UI boundary; the design-system components below remain compact.
@Suppress("ComposableParamOrder", "LambdaParameterEventTrailing", "LongParameterList")
fun BeersListContent(
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  onBeerClick: (Beer) -> Unit,
  onSearchClick: () -> Unit,
  onBrowseClick: () -> Unit,
  onSaveQuery: suspend (String) -> Boolean,
  onScrollToBottom: () -> Unit,
  onRefresh: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  onRetryLoadMore: () -> Unit,
  searchInitiallyExpanded: Boolean = false,
  saveInitiallyExpanded: Boolean = false,
  searchQuery: String = "",
  onSearchQueryChange: (String) -> Unit = {},
  activeSearchQuery: String? = null,
  onSearchSubmit: () -> Unit = {},
) {
  val context = LocalContext.current
  var searchExpanded by rememberSaveable { mutableStateOf(searchInitiallyExpanded) }
  val saveFilter = rememberSaveFilterState(saveInitiallyExpanded)
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val catalogListState = rememberLazyListState()
  val searchListState = rememberLazyListState()
  val closeSearch: () -> Unit = {
    searchExpanded = false
    saveFilter.close()
    onSearchQueryChange("")
    focus.clearFocus()
    keyboard?.hide()
  }
  BackHandler(enabled = searchExpanded || saveFilter.expanded) {
    if (saveFilter.expanded) saveFilter.close() else closeSearch()
  }
  LaunchedEffect(activeSearchQuery) {
    if (activeSearchQuery != null) searchListState.scrollToItem(0)
  }
  Scaffold(
    modifier = modifier,
    topBar = {
      CatalogHeader(
        loaded = (viewState as? CommonUiState.Success)?.data,
        searchExpanded = searchExpanded,
        saveFilter = saveFilter,
        onBrowse = onBrowseClick,
        onSearch = { if (searchExpanded) closeSearch() else searchExpanded = true },
      )
    },
    contentWindowInsets = WindowInsets.statusBars,
  ) { paddingValues ->
    val layoutDirection = LocalLayoutDirection.current
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val animationsDisabled =
      Settings.Global.getFloat(
        LocalContext.current.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      ) == 0f
    val contentPadding =
      PaddingValues(
        start = navBarsPadding.calculateStartPadding(layoutDirection),
        top = BillionBeersTheme.spacing.extraSmall,
        end = navBarsPadding.calculateEndPadding(layoutDirection),
        bottom =
          paddingValues.calculateBottomPadding() +
            navBarsPadding.calculateBottomPadding() +
            BillionBeersTheme.spacing.medium,
      )
    val stateContentPadding = PaddingValues()
    val itemCount = (viewState as? CommonUiState.Success)?.data?.items?.size ?: 0

    Column(Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
      if (searchExpanded) {
        CatalogSearchField(
          query = searchQuery,
          onQueryChange = onSearchQueryChange,
          onSubmit = onSearchSubmit,
          onAdvancedSearch = onSearchClick,
        )
      }
      ComposeSaveFilterForm(
        state = saveFilter,
        onSave = onSaveQuery,
        modifier = Modifier.padding(horizontal = BillionBeersTheme.spacing.medium),
      )

      SharedBeersListContent(
        viewState = viewState,
        beerRow = { beer -> ComposeBeersListItem(beer = beer, onClick = onBeerClick) },
        loadingContent = { BeersListSkeleton(modifier = Modifier.padding(stateContentPadding)) },
        emptyContent = { retry ->
          if (activeSearchQuery != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
              ) {
                BrandText(
                  stringResource(R.string.beers_search_no_results),
                  style = BillionBeersTheme.typography.titleMedium,
                )
                BrandTextButton(onClick = { onSearchQueryChange("") }) {
                  BrandText(stringResource(R.string.beers_clear_search))
                }
              }
            }
          } else {
            ComposeErrorView(onRetry = retry, modifier = Modifier.padding(stateContentPadding))
          }
        },
        errorContent = { error, retry ->
          val errorMessage = error.resolvedMessage()
          ComposeErrorView(
            message = errorMessage ?: stringResource(PresentationUtilsR.string.empty_state),
            onRetry = retry,
            modifier = Modifier.padding(stateContentPadding),
          )
          errorMessage?.let { message -> LaunchedEffect(message) { showToast(context, message) } }
        },
        loadMoreFailedText = stringResource(PresentationUtilsR.string.paged_list_load_more_failed),
        retryText = stringResource(PresentationUtilsR.string.retry),
        endOfListText = pluralStringResource(R.plurals.beers_end_of_list, itemCount, itemCount),
        onScrollToBottom = onScrollToBottom,
        onRefresh = onRefresh,
        onRetry = onRetry,
        onRetryLoadMore = onRetryLoadMore,
        listContentPadding = contentPadding,
        animationsDisabled = animationsDisabled,
        listState = if (activeSearchQuery == null) catalogListState else searchListState,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CatalogHeader(
  loaded: PagedListUiModel<Beer>?,
  searchExpanded: Boolean,
  saveFilter: SaveFilterState,
  onBrowse: () -> Unit,
  onSearch: () -> Unit,
) {
  val toggleDebugDrawer = LocalDebugDrawerToggle.current
  val fontScale = LocalDensity.current.fontScale
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stackActions = maxWidth < 600.dp && fontScale > HEADER_ACTION_STACK_FONT_SCALE
    val saveMaxWidth = if (stackActions) maxOf(96.dp, maxWidth - 112.dp) else 128.dp
    val actions: @Composable () -> Unit = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ComposeSaveFilterAction(saveFilter, maxWidth = saveMaxWidth)
        BrandIconButton(onClick = onBrowse) {
          BrandIcon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(R.string.beers_browse),
          )
        }
        BrandIconButton(onClick = onSearch) {
          BrandIcon(
            if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
            contentDescription =
              stringResource(
                if (searchExpanded) R.string.beers_close_search else R.string.beers_search
              ),
          )
        }
      }
    }
    Column(Modifier.background(BillionBeersTheme.colorScheme.surface)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 80.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val titleModifier =
          toggleDebugDrawer?.let { toggle ->
            Modifier.combinedClickable(onClick = {}, onLongClick = toggle)
          } ?: Modifier
        Column(Modifier.weight(1f).then(titleModifier)) {
          BrandText(
            stringResource(R.string.billion_beers_list),
            style = BillionBeersTheme.brand.catalogTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          loaded?.totalCount?.let { total ->
            BrandText(
              stringResource(R.string.beers_count_of_total, loaded.items.size, total),
              style = BillionBeersTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (!stackActions) actions()
      }
      if (stackActions) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
          actions()
        }
      }
    }
  }
}

@Composable
private fun CatalogSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onAdvancedSearch: () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val inspectionMode = LocalInspectionMode.current
  LaunchedEffect(Unit) {
    if (!inspectionMode) {
      focusRequester.requestFocus()
      keyboard?.show()
    }
  }
  BrandOutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    label = { BrandText(stringResource(R.string.beers_search)) },
    singleLine = true,
    leadingIcon = { BrandIcon(Icons.Filled.Search, contentDescription = null) },
    trailingIcon = {
      Row {
        if (query.isNotEmpty()) {
          BrandIconButton(onClick = { onQueryChange("") }) {
            BrandIcon(
              Icons.Filled.Close,
              contentDescription = stringResource(R.string.beers_clear_search),
            )
          }
        }
        BrandIconButton(onClick = onAdvancedSearch) {
          BrandIcon(
            Icons.Filled.Settings,
            contentDescription = stringResource(R.string.beers_advanced_search),
          )
        }
      }
    },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions =
      KeyboardActions(
        onSearch = {
          onSubmit()
          focus.clearFocus()
          keyboard?.hide()
        }
      ),
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = BillionBeersTheme.spacing.medium)
        .focusRequester(focusRequester)
        .testTag("search_field"),
  )
}

class BeersListPreviewParameterProvider :
  PreviewParameterProvider<BeersListPreviewParameterProvider.State> {

  sealed interface State {
    val uiState: CommonUiState<PagedListUiModel<Beer>>

    enum class Preview(override val uiState: CommonUiState<PagedListUiModel<Beer>>) : State {
      LOADING(CommonUiState.Loading),
      EMPTY(CommonUiState.Empty),
      ERROR(CommonUiState.Error(message = "Failed to load beers. Please check your connection.")),
      SUCCESS_MULTIPLE_ITEMS(
        CommonUiState.Success(
          data =
            PagedListUiModel(
              items =
                listOf(
                  Beer.empty.copy(
                    id = "beerslist-buzz",
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  ),
                  Beer.empty.copy(
                    id = "beerslist-trashy-blonde",
                    name = "Trashy Blonde",
                    tagline = "You Know You Shouldn't",
                    abv = 4.1,
                    ibu = 41.5,
                    availability = false,
                  ),
                ),
              isLoadingNextPage = false,
            )
        )
      ),
      SUCCESS_LOADING_MORE(
        CommonUiState.Success(
          data =
            PagedListUiModel(
              items =
                listOf(
                  Beer.empty.copy(
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  )
                ),
              isLoadingNextPage = true,
            )
        )
      ),
      SUCCESS_LOAD_MORE_FAILED(
        CommonUiState.Success(
          data =
            PagedListUiModel(
              items =
                listOf(
                  Beer.empty.copy(
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  )
                ),
              footer = PagedListFooter.Retry,
            )
        )
      ),
      SUCCESS_END_OF_LIST(
        CommonUiState.Success(
          data =
            PagedListUiModel(
              items =
                listOf(
                  Beer.empty.copy(
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  )
                ),
              footer = PagedListFooter.EndReached,
            )
        )
      ),
    }
  }

  override val values = State.Preview.entries.asSequence()

  override fun getDisplayName(index: Int): String = State.Preview.entries[index].name
}

@PreviewLightDark
@Composable
internal fun BeersListScreenPreview(
  @PreviewParameter(BeersListPreviewParameterProvider::class)
  state: BeersListPreviewParameterProvider.State
) {
  BillionBeersTheme {
    BeersListContent(
      viewState = state.uiState,
      onBeerClick = {},
      onSearchClick = {},
      onBrowseClick = {},
      onSaveQuery = { false },
      onScrollToBottom = {},
      onRefresh = {},
      onRetry = {},
      onRetryLoadMore = {},
    )
  }
}

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun BeersListAccessibilityMatrixPreview() {
  BillionBeersTheme {
    BeersListContent(
      viewState =
        CommonUiState.Success(
          PagedListUiModel(
            items =
              listOf(
                Beer.empty.copy(
                  id = "beerslist-accessibility-long-name",
                  name = "A Very Long Beer Name That Must Wrap Correctly",
                  tagline = "A detailed bitter experience with a deliberately long description.",
                  availability = true,
                ),
                Beer.empty.copy(
                  id = "beerslist-accessibility-seasonal",
                  name = "Another Seasonal Beer",
                  tagline = "A second item keeps list spacing and actions visible.",
                  availability = false,
                ),
              ),
            footer = PagedListFooter.Retry,
            totalCount = 24,
          )
        ),
      onBeerClick = {},
      onSearchClick = {},
      onBrowseClick = {},
      onSaveQuery = { false },
      onScrollToBottom = {},
      onRefresh = {},
      onRetry = {},
      onRetryLoadMore = {},
    )
  }
}

@Composable
fun BeersListSkeleton(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize()) {
    repeat(SKELETON_ITEM_COUNT) { BeersListItemSkeleton() }
  }
}

@Composable
fun BeersListItemSkeleton(modifier: Modifier = Modifier) {
  val shimmerBrush = shimmerBrush(targetValue = SHIMMER_TARGET_VALUE)

  BrandCard(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = BillionBeersTheme.spacing.medium,
          vertical = BillionBeersTheme.spacing.small,
        ),
    containerColor = BillionBeersTheme.colorScheme.surface,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Image placeholder
      Box(
        modifier =
          Modifier.size(BillionBeersTheme.spacing.huge + BillionBeersTheme.spacing.extraLarge)
            .clip(
              RoundedCornerShape(
                BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall
              )
            )
            .background(shimmerBrush)
      )

      Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.medium))

      Column(modifier = Modifier.weight(1f)) {
        Box(
          modifier =
            Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION)
              .height(BillionBeersTheme.spacing.medium + BillionBeersTheme.spacing.extraSmall)
              .clip(RoundedCornerShape(BillionBeersTheme.spacing.extraSmall))
              .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))

        // Tagline placeholder
        Box(
          modifier =
            Modifier.fillMaxWidth(TAGLINE_WIDTH_FRACTION)
              .height(BillionBeersTheme.spacing.medium)
              .clip(RoundedCornerShape(BillionBeersTheme.spacing.extraSmall))
              .background(shimmerBrush)
        )

        Spacer(
          modifier =
            Modifier.height(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall)
        )

        // Chips placeholder
        Row {
          Box(
            modifier =
              Modifier.width(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
                .height(BillionBeersTheme.spacing.large)
                .clip(RoundedCornerShape(BillionBeersTheme.spacing.small))
                .background(shimmerBrush)
          )
          Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.small))
          Box(
            modifier =
              Modifier.width(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
                .height(BillionBeersTheme.spacing.large)
                .clip(RoundedCornerShape(BillionBeersTheme.spacing.small))
                .background(shimmerBrush)
          )
        }
      }
    }
  }
}

const val SKELETON_ITEM_COUNT = 10
const val SHIMMER_TARGET_VALUE = 1300f
const val TITLE_WIDTH_FRACTION = 0.7f
const val TAGLINE_WIDTH_FRACTION = 0.5f

private const val HEADER_ACTION_STACK_FONT_SCALE = 1.2f
