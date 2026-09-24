package com.simtop.billionbeers.shared.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.CommonUiState

@Composable
fun SharedFavoritesContent(
  viewState: CommonUiState<List<Beer>>,
  emptyText: String,
  errorText: String,
  beerRow: @Composable (Beer) -> Unit,
  listState: LazyListState? = null,
  modifier: Modifier = Modifier,
) {
  val resolvedListState = listState ?: rememberLazyListState()
  when (viewState) {
    CommonUiState.Loading ->
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    CommonUiState.Empty ->
      Box(
        modifier.fillMaxSize().testTag("favorites_empty"),
        contentAlignment = Alignment.Center,
      ) {
        Text(emptyText)
      }
    is CommonUiState.Error ->
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(viewState.message ?: errorText)
      }
    is CommonUiState.Success ->
      LazyColumn(
        state = resolvedListState,
        modifier = modifier.fillMaxSize().testTag("favorites_list"),
      ) {
        items(viewState.data.size, key = { index -> "${viewState.data[index].id}:$index" }) { index
          ->
          Box(Modifier.padding(vertical = 4.dp)) { beerRow(viewState.data[index]) }
        }
      }
  }
}
