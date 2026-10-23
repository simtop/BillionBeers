package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.controls.BrandCard
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTextButton
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
fun SharedIntroductionHint(
  text: String,
  dismissLabel: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    BrandCard(
      Modifier.fillMaxWidth().testTag("introduction_hint"),
      containerColor = BillionBeersTheme.colorScheme.primaryContainer,
    ) {
      Column(Modifier.padding(16.dp)) {
        BrandText(text, color = BillionBeersTheme.colorScheme.onPrimaryContainer)
        BrandTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          BrandText(dismissLabel)
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}
