package com.simtop.feature.beerslist

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.shimmerBrush
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.core.LocalDebugDrawerToggle
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import com.simtop.presentation_utils.custom_views.pagedListFooter
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersListScreen(
  onBeerClick: (Beer) -> Unit,
  onSearchClick: () -> Unit,
  onBrowseClick: () -> Unit,
  viewModel: BeersListViewModel = metroViewModel(),
) {
  val rawState by viewModel.beerListViewState.collectAsState()
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
  onScrollToBottom: () -> Unit,
  onRefresh: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  onRetryLoadMore: () -> Unit,
) {
  val context = LocalContext.current
  val toggleDebugDrawer = LocalDebugDrawerToggle.current
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          // The label is clickable to reveal the debug drawer; the count line only appears once the
          // server total is known, so states without a total render exactly as before.
          val titleModifier =
            toggleDebugDrawer?.let { toggle ->
              Modifier.combinedClickable(onClick = {}, onLongClick = toggle)
            } ?: Modifier
          val loaded = (viewState as? CommonUiState.Success)?.data
          val total = loaded?.totalCount
          if (total != null) {
            Column(modifier = titleModifier) {
              Text(text = stringResource(R.string.billion_beers_list))
              Text(
                text = stringResource(R.string.beers_count_of_total, loaded.items.size, total),
                style = MaterialTheme.typography.labelMedium,
              )
            }
          } else {
            Text(text = stringResource(R.string.billion_beers_list), modifier = titleModifier)
          }
        },
        actions = {
          IconButton(onClick = onBrowseClick) {
            Icon(
              Icons.AutoMirrored.Filled.List,
              contentDescription = stringResource(R.string.beers_browse),
            )
          }
          IconButton(onClick = onSearchClick) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.beers_search))
          }
        },
      )
    },
    contentWindowInsets = WindowInsets.statusBars,
  ) { paddingValues ->
    val dataVisibility = rememberSaveable { mutableStateOf(false) }
    // Honours the system "Remove animations" accessibility setting (animator duration scale 0)
    // instead of always animating for a fixed duration. Read via Settings.Global (API 17+) rather
    // than ValueAnimator.getDurationScale(), which is API 33 and crashes on our minSdk 28.
    val animationsDisabled =
      Settings.Global.getFloat(
        LocalContext.current.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      ) == 0f
    val animationDurationMs = if (animationsDisabled) 0 else SCREEN_STATE_ANIMATION_DURATION_MS

    AnimatedContent(
      targetState = viewState,
      label = "ScreenStateAnimation",
      // Keyed by *which* state, not by its contents. AnimatedContent wraps each content in a
      // `key(contentKey(state))`, and the default contentKey is the state itself - so every
      // appended page, being an `equals`-different CommonUiState.Success, replaced the group
      // holding `rememberLazyListState()` and dropped the user back at the top of the catalog
      // (and crossfaded the whole list on the way). Measured, not reasoned:
      // BeersListStateRestorationUiTest.theScrollPositionSurvivesANewPageArriving found only
      // items 0-6 composed after a page arrived at scroll position 39.
      contentKey = { state -> state::class },
      transitionSpec = {
        fadeIn(animationSpec = tween(animationDurationMs)) togetherWith
          fadeOut(animationSpec = tween(animationDurationMs))
      },
    ) { state ->
      when (state) {
        CommonUiState.Empty -> {
          ComposeErrorView(
            onRetry = onRetry,
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
          )
        }

        is CommonUiState.Error -> {
          val errorMessage = state.resolvedMessage()
          ComposeErrorView(
            message = errorMessage ?: stringResource(PresentationUtilsR.string.empty_state),
            onRetry = onRetry,
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
          )
          errorMessage?.let { message -> LaunchedEffect(message) { showToast(context, message) } }
        }

        CommonUiState.Loading -> {
          BeersListSkeleton(modifier = Modifier.padding(top = paddingValues.calculateTopPadding()))
        }

        is CommonUiState.Success -> {
          dataVisibility.value = true

          val beers = state.data.items
          PullToRefreshBox(
            isRefreshing = state.data.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
          ) {
            val listState = rememberLazyListState()

            // Suspend auto-load while the retry footer is up: the user is parked at the bottom
            // after a failure, so scrolling away and back would re-trigger a load instead of
            // waiting for an explicit Retry tap. Tapping Retry clears the footer and re-arms it.
            if (state.data.footer !is PagedListFooter.Retry) {
              InfiniteListHandler(listState = listState, onLoadMore = onScrollToBottom)
            }

            val layoutDirection = LocalLayoutDirection.current
            val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()
            val endOfListText =
              pluralStringResource(R.plurals.beers_end_of_list, beers.size, beers.size)

            LazyColumn(
              state = listState,
              modifier = Modifier.testTag("beer_list"),
              contentPadding =
                PaddingValues(
                  start = navBarsPadding.calculateStartPadding(layoutDirection),
                  top = navBarsPadding.calculateTopPadding(),
                  end = navBarsPadding.calculateEndPadding(layoutDirection),
                  bottom =
                    navBarsPadding.calculateBottomPadding() + BillionBeersTheme.spacing.medium,
                ),
            ) {
              items(beers.count()) { index ->
                ComposeBeersListItem(beer = beers[index], onClick = onBeerClick)
              }

              pagedListFooter(
                model = state.data,
                endOfListText = endOfListText,
                onRetryLoadMore = onRetryLoadMore,
              )
            }
          }
        }
      }
    }
  }
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
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  ),
                  Beer.empty.copy(
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
                  name = "A Very Long Beer Name That Must Wrap Correctly",
                  tagline = "A detailed bitter experience with a deliberately long description.",
                  availability = true,
                ),
                Beer.empty.copy(
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

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = BillionBeersTheme.spacing.medium,
          vertical = BillionBeersTheme.spacing.small,
        ),
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    elevation = CardDefaults.cardElevation(defaultElevation = BillionBeersTheme.spacing.extraSmall),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

const val SCREEN_STATE_ANIMATION_DURATION_MS = 300
const val SKELETON_ITEM_COUNT = 10
const val SHIMMER_TARGET_VALUE = 1300f
const val TITLE_WIDTH_FRACTION = 0.7f
const val TAGLINE_WIDTH_FRACTION = 0.5f
