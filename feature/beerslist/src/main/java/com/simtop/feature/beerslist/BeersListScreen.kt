package com.simtop.feature.beerslist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.navigation.FeatureConstants
import com.simtop.presentation_utils.core.DynamicFeatureLoader
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.core.showToast
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeTitle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersListScreen(
    viewModel: BeersListViewModel = hiltViewModel(),
    splitInstallManager: SplitInstallManager? = null,
    onBeerClick: (Beer) -> Unit
) {
    val context = LocalContext.current
    val viewState by viewModel.beerListViewState.collectAsState()

    // State to track if we are installing the feature for a specific beer
    var installingBeer by remember { mutableStateOf<Beer?>(null) }

    LaunchedEffect(Unit) {
        if (viewModel.beerListViewState.value is CommonUiState.Empty) {
            viewModel.getAllBeers()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = context.getString(com.simtop.core.R.string.billion_beers_list)) })
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->

        val dataVisibility = rememberSaveable { mutableStateOf(false) }

        when (val state = viewState) {
            CommonUiState.Empty -> {
                ComposeTitle(name = "Empty State")
            }

            is CommonUiState.Error -> {
                if (!dataVisibility.value) viewModel.showEmptyState()
                state.message?.let { context.showToast(it) }
            }

            CommonUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is CommonUiState.Success -> {
                dataVisibility.value = true

                val beers = state.data.beers
                Box(
                    modifier = Modifier.fillMaxSize().padding(
                        top = paddingValues.calculateTopPadding()
                    )
                ) {
                    val listState = rememberLazyListState()

                    InfiniteListHandler(
                        listState = listState,
                        isLoadingNextPage = state.data.isLoadingNextPage,
                        onLoadMore = { viewModel.onScrollToBottom() }
                    )

                    val layoutDirection = LocalLayoutDirection.current
                    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.testTag("beer_list"),
                        contentPadding = PaddingValues(
                            start = navBarsPadding.calculateStartPadding(layoutDirection),
                            top = navBarsPadding.calculateTopPadding(),
                            end = navBarsPadding.calculateEndPadding(layoutDirection),
                            bottom = navBarsPadding.calculateBottomPadding() + 16.dp
                        )
                    ) {
                        items(beers.count()) { index ->
                            ComposeBeersListItem(
                                beer = beers[index],
                                onClick = { beer ->
                                    installingBeer = beer
                                }
                            )
                        }

                        if (state.data.isLoadingNextPage) {
                            item(key = "loading_footer") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
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
