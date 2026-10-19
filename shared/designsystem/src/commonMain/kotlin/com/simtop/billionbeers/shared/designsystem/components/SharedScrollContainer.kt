package com.simtop.billionbeers.shared.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Lists share their state with a draggable scrollbar on pointer-driven hosts. */
@Composable
fun SharedLazyColumn(
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  content: LazyListScope.() -> Unit,
) {
  SharedScrollContainer(state, Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = modifier.fillMaxSize(),
      state = state,
      contentPadding = contentPadding,
      content = content,
    )
  }
}

@Composable
expect fun SharedScrollContainer(
  state: LazyListState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
)

@Composable
expect fun SharedScrollContainer(
  state: ScrollState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
)
