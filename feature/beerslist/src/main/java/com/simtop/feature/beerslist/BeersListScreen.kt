package com.simtop.feature.beerslist

import android.animation.ValueAnimator
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.shimmerBrush
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.navigation.FeatureConstants
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.DynamicFeatureLoader
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.core.LocalDebugDrawerToggle
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val BeerSaver: Saver<Beer?, String> =
  Saver(
    save = { beer -> beer?.let { Json.encodeToString(it) }.orEmpty() },
    restore = { json -> json.takeIf { it.isNotEmpty() }?.let { Json.decodeFromString<Beer>(it) } },
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersListScreen(
  onBeerClick: (Beer) -> Unit,
  onSearchClick: () -> Unit,
  viewModel: BeersListViewModel = metroViewModel(),
) {
  val rawState by viewModel.beerListViewState.collectAsState()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(R.string.beers_load_more_failed)
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

  // State to track if we are installing the feature for a specific beer - saved across process
  // death so the install flow resumes instead of silently dropping the in-flight navigation.
  var installingBeer by rememberSaveable(stateSaver = BeerSaver) { mutableStateOf<Beer?>(null) }

  BeersListContent(
    viewState = rawState,
    onBeerClick = { beer -> installingBeer = beer },
    onSearchClick = onSearchClick,
    onScrollToBottom = { viewModel.onScrollToBottom() },
    onRefresh = { viewModel.refresh() },
    onRetry = { viewModel.refresh() },
    onRetryLoadMore = { viewModel.onRetryLoadMore() },
  )

  // Handle dynamic feature loading overlay
  installingBeer?.let { beer: Beer ->
    DynamicFeatureLoader(
      featureName = FeatureConstants.BEER_DETAIL_MODULE,
      onCancelled = { installingBeer = null },
    ) {
      LaunchedEffect(beer) {
        onBeerClick(beer)
        installingBeer = null
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BeersListContent(
  viewState: CommonUiState<BeersListUiModel>,
  onBeerClick: (Beer) -> Unit,
  onSearchClick: () -> Unit,
  onScrollToBottom: () -> Unit,
  onRefresh: () -> Unit,
  onRetry: () -> Unit,
  onRetryLoadMore: () -> Unit,
) {
  val context = LocalContext.current
  val toggleDebugDrawer = LocalDebugDrawerToggle.current
  Scaffold(
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
                text = stringResource(R.string.beers_count_of_total, loaded.beers.size, total),
                style = MaterialTheme.typography.labelMedium,
              )
            }
          } else {
            Text(text = stringResource(R.string.billion_beers_list), modifier = titleModifier)
          }
        },
        actions = {
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
    // instead of always animating for a fixed duration.
    val animationDurationMs =
      if (ValueAnimator.getDurationScale() == 0f) 0 else SCREEN_STATE_ANIMATION_DURATION_MS

    AnimatedContent(
      targetState = viewState,
      label = "ScreenStateAnimation",
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
          ComposeErrorView(
            message = state.message ?: stringResource(PresentationUtilsR.string.empty_state),
            onRetry = onRetry,
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
          )
          state.message?.let { message -> LaunchedEffect(message) { showToast(context, message) } }
        }

        CommonUiState.Loading -> {
          BeersListSkeleton(modifier = Modifier.padding(top = paddingValues.calculateTopPadding()))
        }

        is CommonUiState.Success -> {
          dataVisibility.value = true

          val beers = state.data.beers
          PullToRefreshBox(
            isRefreshing = state.data.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
          ) {
            val listState = rememberLazyListState()

            // Suspend auto-load while the retry footer is up: the user is parked at the bottom
            // after a failure, so scrolling away and back would re-trigger a load instead of
            // waiting for an explicit Retry tap. Tapping Retry clears the footer and re-arms it.
            if (state.data.footer !is ListFooter.Retry) {
              InfiniteListHandler(listState = listState, onLoadMore = onScrollToBottom)
            }

            val layoutDirection = LocalLayoutDirection.current
            val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()

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

              // Bottom-of-list affordance: spinner while loading, a retry row after a failed
              // "load more", or the end caption once the whole catalog is in.
              when {
                state.data.isLoadingNextPage -> item(key = "loading_footer") { LoadingMoreFooter() }
                state.data.footer is ListFooter.Retry ->
                  item(key = "retry_footer") { LoadMoreRetryFooter(onRetry = onRetryLoadMore) }
                state.data.footer is ListFooter.EndReached ->
                  item(key = "end_footer") { EndOfListFooter(beerCount = beers.size) }
                else -> Unit
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LoadingMoreFooter() {
  Box(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(BillionBeersTheme.spacing.extraLarge),
      strokeWidth = 3.dp,
    )
  }
}

@Composable
private fun LoadMoreRetryFooter(onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.medium),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.beers_load_more_failed),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onRetry) { Text(text = stringResource(R.string.beers_load_more_retry)) }
  }
}

@Composable
private fun EndOfListFooter(beerCount: Int) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(R.string.beers_end_of_list, beerCount),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

class BeersListPreviewParameterProvider :
  PreviewParameterProvider<BeersListPreviewParameterProvider.State> {

  sealed interface State {
    val uiState: CommonUiState<BeersListUiModel>

    enum class Preview(override val uiState: CommonUiState<BeersListUiModel>) : State {
      LOADING(CommonUiState.Loading),
      EMPTY(CommonUiState.Empty),
      ERROR(CommonUiState.Error(message = "Failed to load beers. Please check your connection.")),
      SUCCESS_MULTIPLE_ITEMS(
        CommonUiState.Success(
          data =
            BeersListUiModel(
              beers =
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
            BeersListUiModel(
              beers =
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
            BeersListUiModel(
              beers =
                listOf(
                  Beer.empty.copy(
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  )
                ),
              footer = ListFooter.Retry,
            )
        )
      ),
      SUCCESS_END_OF_LIST(
        CommonUiState.Success(
          data =
            BeersListUiModel(
              beers =
                listOf(
                  Beer.empty.copy(
                    name = "Buzz",
                    tagline = "A Real Bitter Experience.",
                    abv = 4.5,
                    ibu = 60.0,
                    availability = true,
                  )
                ),
              footer = ListFooter.EndReached,
            )
        )
      ),
    }
  }

  override val values = State.Preview.entries.asSequence()
}

@PreviewLightDark
@Composable
fun BeersListScreenPreview(
  @PreviewParameter(BeersListPreviewParameterProvider::class)
  state: BeersListPreviewParameterProvider.State
) {
  BillionBeersTheme {
    BeersListContent(
      viewState = state.uiState,
      onBeerClick = {},
      onSearchClick = {},
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
fun BeersListItemSkeleton() {
  val shimmerBrush = shimmerBrush(targetValue = SHIMMER_TARGET_VALUE)

  Card(
    modifier =
      Modifier.fillMaxWidth()
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
