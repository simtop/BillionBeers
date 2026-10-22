package com.simtop.feature.beerbrowse.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseBeersContent
import com.simtop.billionbeers.shared.designsystem.controls.BrandIcon
import com.simtop.billionbeers.shared.presentation.BrowseBeersEvent as SharedBrowseBeersEvent
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import com.simtop.presentation_utils.custom_views.ComposeSaveFilterAction
import com.simtop.presentation_utils.custom_views.ComposeSaveFilterForm
import com.simtop.presentation_utils.custom_views.rememberSaveFilterState
import com.simtop.presentation_utils.custom_views.saveFilterActionNeedsOwnRow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
internal fun BrowseBeersScreen(
  selection: BrowseSelection,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  onSaveQuery: suspend (String) -> Boolean,
) {
  val viewModel =
    assistedMetroViewModel<BrowseBeersViewModel, BrowseBeersViewModel.Factory>(
      key = selection.key
    ) {
      create(selection.toQuery())
    }
  val viewState by viewModel.viewState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(R.string.paged_list_load_more_failed)
  val refreshFailedMessage = stringResource(R.string.paged_list_refresh_failed)

  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.events.collect { event ->
        when (event) {
          SharedBrowseBeersEvent.ShowLoadMoreError -> showToast(context, loadMoreFailedMessage)
          SharedBrowseBeersEvent.ShowRefreshError -> showToast(context, refreshFailedMessage)
        }
      }
    }
  }

  BrowseBeersContent(
    title = selection.name,
    viewState = viewState,
    onBack = onBack,
    onBeerClick = onBeerClick,
    onSaveQuery = onSaveQuery,
    onScrollToBottom = viewModel::onScrollToBottom,
    onRetryLoadMore = viewModel::onRetryLoadMore,
    onRetryFirstPage = viewModel::onRetryFirstPage,
  )
}

@Composable
@Suppress("LongParameterList")
internal fun BrowseBeersContent(
  title: String,
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  onSaveQuery: suspend (String) -> Boolean,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetryFirstPage: () -> Unit,
  saveInitiallyExpanded: Boolean = false,
) {
  val saveFilter = rememberSaveFilterState(saveInitiallyExpanded)
  val stackActions = saveFilterActionNeedsOwnRow()
  Column(Modifier.fillMaxSize()) {
    SharedBrowseBeersContent(
      modifier = Modifier.weight(1f),
      headerActions = { if (!stackActions) ComposeSaveFilterAction(saveFilter) },
      headerContent = {
        if (stackActions) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ComposeSaveFilterAction(saveFilter, maxWidth = Dp.Unspecified)
          }
        }
        ComposeSaveFilterForm(
          state = saveFilter,
          onSave = onSaveQuery,
          modifier = Modifier.padding(horizontal = BillionBeersTheme.spacing.medium),
        )
      },
      strings = browseStrings,
      title = title,
      viewState = viewState,
      onBack = { if (saveFilter.expanded) saveFilter.close() else onBack() },
      backIcon = { contentDescription ->
        BrandIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription)
      },
      onBeerClick = onBeerClick,
      onScrollToBottom = onScrollToBottom,
      onRetryLoadMore = onRetryLoadMore,
      onRetryFirstPage = onRetryFirstPage,
      errorContent = { state, retry ->
        ComposeErrorView(
          message = state.resolvedMessage().orEmpty(),
          onRetry = retry,
          modifier = Modifier.fillMaxSize(),
        )
      },
      beerRow = { beer, onClick ->
        ComposeBeersListItem(beer = beer, onClick = { onClick() })
      },
    )
  }
}

class BrowseBeersPreviewParameterProvider :
  PreviewParameterProvider<BrowseBeersPreviewParameterProvider.Case> {

  data class Case(val title: String, val state: CommonUiState<PagedListUiModel<Beer>>)

  private val sampleBeers =
    listOf(
      Beer.empty.copy(
        id = "beerbrowse-punk-ipa",
        name = "Punk IPA",
        tagline = "Post Modern Classic.",
        abv = 5.6,
        ibu = 41.5,
      ),
      Beer.empty.copy(
        id = "beerbrowse-hazy-jane",
        name = "Hazy Jane",
        tagline = "New England IPA.",
        abv = 5.0,
        ibu = 25.0,
      ),
    )

  override val values =
    sequenceOf(
      Case(
        "IPA (Indian Pale Ale)",
        CommonUiState.Success(PagedListUiModel(items = sampleBeers, totalCount = 9)),
      ),
      Case("Stout", CommonUiState.Success(PagedListUiModel(totalCount = 0))),
      Case("Lager", CommonUiState.Error(errorKey = CommonUiErrorKey.RateLimited)),
    )
}

@PreviewLightDark
@Composable
internal fun BrowseBeersScreenPreview(
  @PreviewParameter(BrowseBeersPreviewParameterProvider::class)
  case: BrowseBeersPreviewParameterProvider.Case
) {
  BillionBeersTheme {
    BrowseBeersContent(
      title = case.title,
      viewState = case.state,
      onBack = {},
      onBeerClick = {},
      onSaveQuery = { false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetryFirstPage = {},
    )
  }
}

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun BrowseBeersAccessibilityMatrixPreview() {
  BillionBeersTheme {
    BrowseBeersContent(
      title = "A Very Long Beer Style Name That Must Wrap Correctly",
      viewState =
        CommonUiState.Success(
          PagedListUiModel(
            items =
              listOf(
                Beer.empty.copy(
                  id = "beerbrowse-accessibility-long-name",
                  name = "A Very Long Beer Name That Must Wrap Correctly",
                  tagline = "A long tagline exercises the browse result layout.",
                ),
                Beer.empty.copy(
                  id = "beerbrowse-accessibility-second",
                  name = "Second Beer",
                  tagline = "Another beer",
                ),
              ),
            totalCount = 2,
            footer = PagedListFooter.EndReached,
          )
        ),
      onBack = {},
      onBeerClick = {},
      onSaveQuery = { false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetryFirstPage = {},
    )
  }
}
