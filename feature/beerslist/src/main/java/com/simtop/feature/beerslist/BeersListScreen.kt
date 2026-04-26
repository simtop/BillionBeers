package com.simtop.feature.beerslist

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.core.core.CommonUiState
import com.simtop.navigation.FeatureConstants
import com.simtop.presentation_utils.core.DynamicFeatureLoader
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.core.shimmerBrush
import com.simtop.presentation_utils.core.showToast
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersListScreen(
    viewModel: BeersListViewModel = hiltViewModel(),
    splitInstallManager: SplitInstallManager? = null,
    onBeerClick: (Beer) -> Unit
) {
    val viewState by viewModel.beerListViewState.collectAsState()

    // State to track if we are installing the feature for a specific beer
    var installingBeer by remember { mutableStateOf<Beer?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (viewModel.beerListViewState.value is CommonUiState.Success || viewModel.beerListViewState.value is CommonUiState.Error) {
                viewModel.refresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.beerListViewState.value is CommonUiState.Empty) {
            viewModel.getAllBeers()
        }
    }

    BeersListContent(
        viewState = viewState,
        onBeerClick = { beer -> installingBeer = beer },
        onScrollToBottom = { viewModel.onScrollToBottom() },
        onRetry = { viewModel.getAllBeers() }
    )

    // Handle dynamic feature loading overlay
    installingBeer?.let { beer: Beer ->
        DynamicFeatureLoader(
            featureName = FeatureConstants.BEER_DETAIL_MODULE,
            splitInstallManager = splitInstallManager
        ) {
            LaunchedEffect(beer) {
                onBeerClick(beer)
                installingBeer = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersListContent(
    viewState: CommonUiState<BeersListUiModel>,
    onBeerClick: (Beer) -> Unit,
    onScrollToBottom: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(com.simtop.core.R.string.billion_beers_list)) }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        val dataVisibility = rememberSaveable { mutableStateOf(false) }

        when (val state = viewState) {
            CommonUiState.Empty -> {
                ComposeErrorView(
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
                )
            }

            is CommonUiState.Error -> {
                ComposeErrorView(
                    message = state.message ?: "Empty State",
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
                )
                state.message?.let { message ->
                    LaunchedEffect(message) {
                        context.showToast(message)
                    }
                }
            }

            CommonUiState.Loading -> {
                BeersListSkeleton(modifier = Modifier.padding(top = paddingValues.calculateTopPadding()))
            }

            is CommonUiState.Success -> {
                dataVisibility.value = true

                val beers = state.data.beers
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    val listState = rememberLazyListState()

                    InfiniteListHandler(
                        listState = listState,
                        isLoadingNextPage = state.data.isLoadingNextPage,
                        onLoadMore = onScrollToBottom
                    )

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
                                bottom = navBarsPadding.calculateBottomPadding() + BillionBeersTheme.spacing.medium
                            )
                    ) {
                        items(beers.count()) { index ->
                            ComposeBeersListItem(
                                beer = beers[index],
                                onClick = onBeerClick
                            )
                        }

                        if (state.data.isLoadingNextPage) {
                            item(key = "loading_footer") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(BillionBeersTheme.spacing.large),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(BillionBeersTheme.spacing.extraLarge),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class BeersListPreviewParameterProvider :
    androidx.compose.ui.tooling.preview.PreviewParameterProvider<BeersListPreviewParameterProvider.State> {

    sealed interface State {
        val uiState: CommonUiState<BeersListUiModel>

        enum class Preview(
            override val uiState: CommonUiState<BeersListUiModel>
        ) : State {
            LOADING(CommonUiState.Loading),
            EMPTY(CommonUiState.Empty),
            ERROR(CommonUiState.Error(message = "Failed to load beers. Please check your connection.")),
            SUCCESS_MULTIPLE_ITEMS(
                CommonUiState.Success(
                    data = BeersListUiModel(
                        beers = listOf(
                            Beer.empty.copy(
                                name = "Buzz",
                                tagline = "A Real Bitter Experience.",
                                abv = 4.5,
                                ibu = 60.0,
                                availability = true
                            ),
                            Beer.empty.copy(
                                name = "Trashy Blonde",
                                tagline = "You Know You Shouldn't",
                                abv = 4.1,
                                ibu = 41.5,
                                availability = false
                            )
                        ),
                        isLoadingNextPage = false
                    )
                )
            ),
            SUCCESS_LOADING_MORE(
                CommonUiState.Success(
                    data = BeersListUiModel(
                        beers = listOf(
                            Beer.empty.copy(
                                name = "Buzz",
                                tagline = "A Real Bitter Experience.",
                                abv = 4.5,
                                ibu = 60.0,
                                availability = true
                            )
                        ),
                        isLoadingNextPage = true
                    )
                )
            )
        }
    }

    override val values = State.Preview.entries.asSequence()
}

@PreviewLightDark
@androidx.compose.runtime.Composable
fun BeersListScreenPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(BeersListPreviewParameterProvider::class) state: BeersListPreviewParameterProvider.State
) {
    BillionBeersTheme {
        BeersListContent(
            viewState = state.uiState,
            onBeerClick = {},
            onScrollToBottom = {},
            onRetry = {}
        )
    }
}

@Composable
fun BeersListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        repeat(SKELETON_ITEM_COUNT) {
            BeersListItemSkeleton()
        }
    }
}

@Composable
fun BeersListItemSkeleton() {
    val shimmerBrush = shimmerBrush(targetValue = SHIMMER_TARGET_VALUE)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BillionBeersTheme.spacing.medium,
                vertical = BillionBeersTheme.spacing.small
            ),
        shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
        elevation = CardDefaults.cardElevation(defaultElevation = BillionBeersTheme.spacing.extraSmall),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image placeholder
            Box(
                modifier = Modifier
                    .size(BillionBeersTheme.spacing.huge + BillionBeersTheme.spacing.extraLarge)
                    .clip(RoundedCornerShape(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall))
                    .background(shimmerBrush)
            )

            Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(TITLE_WIDTH_FRACTION)
                        .height(BillionBeersTheme.spacing.medium + BillionBeersTheme.spacing.extraSmall)
                        .clip(RoundedCornerShape(BillionBeersTheme.spacing.extraSmall))
                        .background(shimmerBrush)
                )

                Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))

                // Tagline placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(TAGLINE_WIDTH_FRACTION)
                        .height(BillionBeersTheme.spacing.medium)
                        .clip(RoundedCornerShape(BillionBeersTheme.spacing.extraSmall))
                        .background(shimmerBrush)
                )

                Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall))

                // Chips placeholder
                Row {
                    Box(
                        modifier = Modifier
                            .width(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
                            .height(BillionBeersTheme.spacing.large)
                            .clip(RoundedCornerShape(BillionBeersTheme.spacing.small))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.small))
                    Box(
                        modifier = Modifier
                            .width(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
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
