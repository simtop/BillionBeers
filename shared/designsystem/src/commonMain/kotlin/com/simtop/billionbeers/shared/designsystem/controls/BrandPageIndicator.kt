package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
fun BrandPageIndicator(
  pageCount: Int,
  currentPage: Int,
  description: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.semantics {
      contentDescription = description
      liveRegion = LiveRegionMode.Polite
    },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(pageCount) { page ->
      Box(
        Modifier.size(if (page == currentPage) 10.dp else 8.dp)
          .background(
            if (page == currentPage) BillionBeersTheme.colorScheme.primary
            else BillionBeersTheme.colorScheme.outlineVariant,
            CircleShape,
          )
      )
    }
  }
}
