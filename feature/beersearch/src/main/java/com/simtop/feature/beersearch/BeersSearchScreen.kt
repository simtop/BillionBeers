package com.simtop.feature.beersearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun BeersSearchScreen(
  onBeerClick: (Beer) -> Unit,
  onBack: () -> Unit,
  viewModel: BeersSearchViewModel = metroViewModel(),
) {
  val viewState by viewModel.viewState.collectAsState()
  var query by rememberSaveable { mutableStateOf("") }
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(R.string.search_load_more_failed)

  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.events.collect { event ->
        when (event) {
          BeersSearchEvent.ShowLoadMoreError -> showToast(context, loadMoreFailedMessage)
        }
      }
    }
  }

  BeersSearchContent(
    viewState = viewState,
    query = query,
    onQueryChange = {
      query = it
      viewModel.onQueryChange(it)
    },
    onBeerClick = onBeerClick,
    onBack = onBack,
    onScrollToBottom = { viewModel.onScrollToBottom() },
    onRetryLoadMore = { viewModel.onRetryLoadMore() },
    onRetrySearch = { viewModel.onRetrySearch() },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeersSearchContent(
  viewState: CommonUiState<BeersSearchUiModel>,
  query: String,
  onQueryChange: (String) -> Unit,
  onBeerClick: (Beer) -> Unit,
  onBack: () -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetrySearch: () -> Unit,
  autoFocus: Boolean = true,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.search_back),
            )
          }
        },
        title = {
          SearchField(query = query, onQueryChange = onQueryChange, autoFocus = autoFocus)
        },
        actions = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
              Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
            }
          }
        },
      )
    }
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
      when (val state = viewState) {
        CommonUiState.Empty -> CenteredHint(stringResource(R.string.search_prompt))
        CommonUiState.Loading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        is CommonUiState.Error ->
          ComposeErrorView(message = state.message.orEmpty(), onRetry = onRetrySearch)
        is CommonUiState.Success ->
          if (state.data.beers.isEmpty()) {
            CenteredHint(stringResource(R.string.search_no_results, query))
          } else {
            SearchResults(
              model = state.data,
              onBeerClick = onBeerClick,
              onScrollToBottom = onScrollToBottom,
              onRetryLoadMore = onRetryLoadMore,
            )
          }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, autoFocus: Boolean) {
  val focusRequester = remember { FocusRequester() }
  if (autoFocus) {
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
  }
  TextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = { Text(stringResource(R.string.search_hint)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    colors =
      TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
      ),
  )
}

@Composable
private fun SearchResults(
  model: BeersSearchUiModel,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
) {
  Column {
    model.resultCount?.let { count ->
      Text(
        text = stringResource(R.string.search_result_count, count),
        style = MaterialTheme.typography.labelLarge,
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              horizontal = BillionBeersTheme.spacing.medium,
              vertical = BillionBeersTheme.spacing.small,
            ),
      )
    }

    val listState = rememberLazyListState()
    if (model.footer !is SearchFooter.Retry) {
      InfiniteListHandler(listState = listState, onLoadMore = onScrollToBottom)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
      items(model.beers.size) { index ->
        ComposeBeersListItem(beer = model.beers[index], onClick = onBeerClick)
      }
      when {
        model.isLoadingNextPage -> item("loading") { LoadingMoreFooter() }
        model.footer is SearchFooter.Retry -> item("retry") { RetryFooter(onRetryLoadMore) }
        model.footer is SearchFooter.EndReached -> item("end") { EndFooter(model.beers.size) }
        else -> Unit
      }
    }
  }
}

@Composable
private fun CenteredHint(text: String) {
  Box(
    modifier = Modifier.fillMaxSize().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
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
private fun RetryFooter(onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.medium),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = stringResource(R.string.search_load_more_failed),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onRetry) { Text(stringResource(R.string.search_retry)) }
  }
}

@Composable
private fun EndFooter(resultCount: Int) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(R.string.search_end_of_list, resultCount),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

class BeersSearchPreviewParameterProvider :
  PreviewParameterProvider<BeersSearchPreviewParameterProvider.Case> {

  data class Case(val query: String, val state: CommonUiState<BeersSearchUiModel>)

  private val sampleBeers =
    listOf(
      Beer.empty.copy(name = "Punk IPA", tagline = "Post Modern Classic.", abv = 5.6, ibu = 41.5),
      Beer.empty.copy(name = "Hazy Jane", tagline = "New England IPA.", abv = 5.0, ibu = 25.0),
    )

  override val values =
    sequenceOf(
      Case("", CommonUiState.Empty),
      Case("ip", CommonUiState.Loading),
      Case(
        "ipa",
        CommonUiState.Success(BeersSearchUiModel(beers = sampleBeers, resultCount = 159)),
      ),
      Case(
        "xyzzy",
        CommonUiState.Success(BeersSearchUiModel(beers = emptyList(), resultCount = 0)),
      ),
      Case("ipa", CommonUiState.Error(message = "Too many requests. Please wait a moment.")),
    )
}

@PreviewLightDark
@Composable
fun BeersSearchScreenPreview(
  @PreviewParameter(BeersSearchPreviewParameterProvider::class)
  case: BeersSearchPreviewParameterProvider.Case
) {
  BillionBeersTheme {
    BeersSearchContent(
      viewState = case.state,
      query = case.query,
      onQueryChange = {},
      onBeerClick = {},
      onBack = {},
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetrySearch = {},
      autoFocus = false,
    )
  }
}
