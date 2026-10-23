package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.controls.BrandBackIcon
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandPageIndicator
import com.simtop.billionbeers.shared.designsystem.controls.BrandSurface
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTextButton
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import kotlinx.coroutines.launch

/** The host owns completion; moving between pages never consumes an introduction. */
@Composable
@Suppress("LongParameterList")
fun SharedIntroductionFlow(
  pages: List<SharedIntroductionPage>,
  pagerState: PagerState,
  backLabel: String,
  skipLabel: String,
  pageDescription: (Int) -> String,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  require(pages.isNotEmpty())
  val scope = rememberCoroutineScope()
  BrandSurface(modifier.fillMaxSize(), color = BillionBeersTheme.colorScheme.background) {
    Column(
      Modifier.fillMaxSize().safeDrawingPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
          Modifier.fillMaxWidth().heightIn(min = 48.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (pagerState.currentPage > 0) {
            BrandIconButton(
              onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
              },
              modifier = Modifier.testTag("introduction_back"),
            ) {
              BrandBackIcon(backLabel)
            }
          } else {
            Spacer(Modifier.size(48.dp))
          }
          BrandTextButton(onClick = onComplete, modifier = Modifier.testTag("introduction_skip")) {
            BrandText(skipLabel)
          }
        }
        BrandPageIndicator(
          pageCount = pages.size,
          currentPage = pagerState.currentPage,
          description = pageDescription(pagerState.currentPage),
          modifier = Modifier.align(Alignment.Center).testTag("introduction_progress"),
        )
      }
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f).testTag("introduction_pager"),
      ) { index ->
        val page = pages[index]
        SharedIntroductionContent(
          strings = page.strings,
          illustration = page.illustration,
          onContinue = {
            if (!pagerState.isScrollInProgress && pagerState.settledPage == index) {
              if (index == pages.lastIndex) onComplete()
              else scope.launch { pagerState.animateScrollToPage(index + 1) }
            }
          },
        )
      }
    }
  }
}
