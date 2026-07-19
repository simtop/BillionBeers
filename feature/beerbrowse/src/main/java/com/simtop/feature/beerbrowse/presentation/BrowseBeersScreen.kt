package com.simtop.feature.beerbrowse.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.core.InfiniteListHandler
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import com.simtop.presentation_utils.custom_views.pagedListFooter
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
internal fun BrowseBeersScreen(
  selection: BrowseSelection,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
) {
  // Keyed by the selection: picking a different style/brewery mints a fresh ViewModel (and with
  // it a fresh pager) instead of mutating the old one - the query-surface invalidation rule.
  val viewModel =
    assistedMetroViewModel<BrowseBeersViewModel, BrowseBeersViewModel.Factory>(
      key = selection.key
    ) {
      create(selection.toQuery())
    }
  val viewState by viewModel.viewState.collectAsState()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(R.string.paged_list_load_more_failed)

  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.events.collect { event ->
        when (event) {
          BrowseBeersEvent.ShowLoadMoreError -> showToast(context, loadMoreFailedMessage)
        }
      }
    }
  }

  BrowseBeersContent(
    title = selection.name,
    viewState = viewState,
    onBack = onBack,
    onBeerClick = onBeerClick,
    onScrollToBottom = { viewModel.onScrollToBottom() },
    onRetryLoadMore = { viewModel.onRetryLoadMore() },
    onRetryFirstPage = { viewModel.onRetryFirstPage() },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseBeersContent(
  title: String,
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetryFirstPage: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.browse_back),
            )
          }
        },
        title = { Text(title) },
      )
    }
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
      when (val state = viewState) {
        CommonUiState.Empty,
        CommonUiState.Loading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        is CommonUiState.Error ->
          ComposeErrorView(message = state.resolvedMessage().orEmpty(), onRetry = onRetryFirstPage)
        is CommonUiState.Success ->
          if (state.data.items.isEmpty()) {
            CenteredHint(stringResource(R.string.browse_no_beers))
          } else {
            BrowseBeersResults(
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

@Composable
private fun BrowseBeersResults(
  model: PagedListUiModel<Beer>,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
) {
  Column {
    model.totalCount?.let { count ->
      Text(
        text = stringResource(R.string.browse_beers_count, count),
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

    val endOfListText = stringResource(R.string.browse_beers_end_of_list, model.items.size)
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

class BrowseBeersPreviewParameterProvider :
  PreviewParameterProvider<BrowseBeersPreviewParameterProvider.Case> {

  data class Case(val title: String, val state: CommonUiState<PagedListUiModel<Beer>>)

  private val sampleBeers =
    listOf(
      Beer.empty.copy(name = "Punk IPA", tagline = "Post Modern Classic.", abv = 5.6, ibu = 41.5),
      Beer.empty.copy(name = "Hazy Jane", tagline = "New England IPA.", abv = 5.0, ibu = 25.0),
    )

  override val values =
    sequenceOf(
      Case(
        "IPA (Indian Pale Ale)",
        CommonUiState.Success(PagedListUiModel(items = sampleBeers, totalCount = 9)),
      ),
      Case("Stout", CommonUiState.Success(PagedListUiModel(totalCount = 0))),
      Case("Lager", CommonUiState.Error(messageRes = R.string.error_rate_limited)),
    )
}

@PreviewLightDark
@Composable
fun BrowseBeersScreenPreview(
  @PreviewParameter(BrowseBeersPreviewParameterProvider::class)
  case: BrowseBeersPreviewParameterProvider.Case
) {
  BillionBeersTheme {
    BrowseBeersContent(
      title = case.title,
      viewState = case.state,
      onBack = {},
      onBeerClick = {},
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetryFirstPage = {},
    )
  }
}
