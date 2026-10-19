package com.simtop.billionbeers.shared.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun SharedScrollContainer(
  state: LazyListState,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier) { content() }
}

@Composable
actual fun SharedScrollContainer(
  state: ScrollState,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier) { content() }
}
