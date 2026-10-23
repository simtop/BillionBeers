package com.simtop.billionbeers.shared.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SharedIntroductionPage(
  val strings: IntroductionStrings,
  val illustration: @Composable (Modifier) -> Unit,
)
