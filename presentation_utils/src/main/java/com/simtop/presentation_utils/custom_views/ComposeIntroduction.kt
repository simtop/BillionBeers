package com.simtop.presentation_utils.custom_views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.shared.presentation.IntroductionStrings
import com.simtop.billionbeers.shared.presentation.SharedIntroductionContent

/** Android-facing adapter keeps shared presentation implementation details inside this module. */
@Composable
@Suppress("LongParameterList")
fun ComposeIntroduction(
  eyebrow: String,
  headline: String,
  body: String,
  action: String,
  footnote: String,
  onContinue: () -> Unit,
  illustration: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier,
  animateEntrance: Boolean = false,
) {
  SharedIntroductionContent(
    strings = IntroductionStrings(eyebrow, headline, body, action, footnote),
    onContinue = onContinue,
    illustration = illustration,
    modifier = modifier,
    animateEntrance = animateEntrance,
  )
}
