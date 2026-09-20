package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
fun SharedTitle(name: String, modifier: Modifier = Modifier) {
  Text(
    text = name,
    modifier =
      modifier
        .padding(
          start = BillionBeersTheme.spacing.large,
          top = BillionBeersTheme.spacing.small,
          bottom = BillionBeersTheme.spacing.small,
          end = BillionBeersTheme.spacing.large,
        )
        .fillMaxWidth(),
    style = MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
  )
}
