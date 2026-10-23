package com.simtop.presentation_utils.custom_views

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.shared.presentation.IntroductionStrings
import com.simtop.billionbeers.shared.presentation.SharedIntroductionFlow
import com.simtop.billionbeers.shared.presentation.SharedIntroductionPage
import kotlinx.coroutines.launch

/**
 * Saves page position across recreation and gives system Back the same behavior as the Back
 * control.
 */
@Composable
@Suppress("LongParameterList")
fun ComposeIntroductionFlow(
  pages: List<IntroductionPage>,
  backLabel: String,
  skipLabel: String,
  pageDescription: (Int) -> String,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier,
  initialPage: Int = 0,
) {
  val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
  val scope = rememberCoroutineScope()
  if (LocalOnBackPressedDispatcherOwner.current != null) {
    BackHandler(enabled = pagerState.currentPage > 0) {
      scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }
  }
  SharedIntroductionFlow(
    pages =
      pages.map { page ->
        SharedIntroductionPage(
          IntroductionStrings(page.eyebrow, page.headline, page.body, page.action, page.footnote),
          page.illustration,
        )
      },
    pagerState = pagerState,
    backLabel = backLabel,
    skipLabel = skipLabel,
    pageDescription = pageDescription,
    onComplete = onComplete,
    modifier = modifier,
  )
}
