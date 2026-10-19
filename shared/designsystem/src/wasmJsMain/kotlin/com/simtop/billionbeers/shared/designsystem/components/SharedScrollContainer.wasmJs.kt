package com.simtop.billionbeers.shared.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun SharedScrollContainer(
  state: LazyListState,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  Row(modifier) {
    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(state),
      modifier = Modifier.fillMaxHeight().width(12.dp),
      style =
        defaultScrollbarStyle()
          .copy(
            unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            hoverColor = MaterialTheme.colorScheme.primary,
          ),
    )
  }
}

@Composable
actual fun SharedScrollContainer(
  state: ScrollState,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  Row(modifier) {
    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(state),
      modifier = Modifier.fillMaxHeight().width(12.dp),
      style =
        defaultScrollbarStyle()
          .copy(
            unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            hoverColor = MaterialTheme.colorScheme.primary,
          ),
    )
  }
}
