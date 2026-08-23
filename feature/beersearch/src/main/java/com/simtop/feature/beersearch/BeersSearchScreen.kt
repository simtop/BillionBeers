package com.simtop.feature.beersearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import com.simtop.presentation_utils.custom_views.pagedListFooter
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The search input, addressed by tag rather than by its placeholder so an instrumented test does
 * not break when the hint copy changes. Same reasoning as `beer_list` on the catalog's list.
 */
const val SEARCH_FIELD_TAG = "search_field"

@Composable
fun BeersSearchScreen(
  onBeerClick: (Beer) -> Unit,
  onBack: () -> Unit,
  viewModel: BeersSearchViewModel = metroViewModel(),
) {
  val viewState by viewModel.viewState.collectAsState()
  // VM-owned (SavedStateHandle-backed) so process death restores the search, not just the text.
  val query by viewModel.query.collectAsState()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(PresentationUtilsR.string.paged_list_load_more_failed)

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
    onQueryChange = viewModel::onQueryChange,
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
  viewState: CommonUiState<PagedListUiModel<Beer>>,
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
          ComposeErrorView(message = state.resolvedMessage().orEmpty(), onRetry = onRetrySearch)
        is CommonUiState.Success ->
          if (state.data.items.isEmpty()) {
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
    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(SEARCH_FIELD_TAG),
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
  model: PagedListUiModel<Beer>,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
) {
  Column {
    model.totalCount?.let { count ->
      Text(
        text = pluralStringResource(R.plurals.search_result_count, count, count),
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
    if (model.footer !is PagedListFooter.Retry) {
      InfiniteListHandler(listState = listState, onLoadMore = onScrollToBottom)
    }

    val endOfListText =
      pluralStringResource(R.plurals.search_end_of_list, model.items.size, model.items.size)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
      items(model.items.size) { index ->
        ComposeBeersListItem(beer = model.items[index], onClick = onBeerClick)
      }
      pagedListFooter(
        model = model,
        endOfListText = endOfListText,
        onRetryLoadMore = onRetryLoadMore,
      )
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

class BeersSearchPreviewParameterProvider :
  PreviewParameterProvider<BeersSearchPreviewParameterProvider.Case> {

  data class Case(val query: String, val state: CommonUiState<PagedListUiModel<Beer>>)

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
        CommonUiState.Success(PagedListUiModel(items = sampleBeers, totalCount = 159)),
      ),
      Case(
        "xyzzy",
        CommonUiState.Success(PagedListUiModel<Beer>(totalCount = 0)),
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

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun BeersSearchAccessibilityMatrixPreview() {
  BillionBeersTheme {
    BeersSearchContent(
      viewState =
        CommonUiState.Success(
          PagedListUiModel(
            items =
              listOf(
                Beer.empty.copy(
                  name = "A Very Long Search Result Name That Must Wrap",
                  tagline =
                    "A long tagline exercises the search result layout at large font sizes.",
                ),
                Beer.empty.copy(name = "Second Result", tagline = "Another result"),
              ),
            totalCount = 2,
          )
        ),
      query = "a very long query that should remain understandable",
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
