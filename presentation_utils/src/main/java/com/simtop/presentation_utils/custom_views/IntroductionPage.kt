package com.simtop.presentation_utils.custom_views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class IntroductionPage(
  val eyebrow: String,
  val headline: String,
  val body: String,
  val action: String,
  val footnote: String,
  val illustration: @Composable (Modifier) -> Unit,
)
