package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.components.SharedLazyColumn
import com.simtop.billionbeers.shared.designsystem.components.SharedScrollContainer
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScrollbarInteractionTest {
  @Test
  fun draggingScrollbarChangesTheDetailScrollPosition() =
    runSkikoComposeUiTest(size = Size(320f, 400f)) {
      val state = ScrollState(0)
      setContent {
        SharedScrollContainer(state) {
          Column(Modifier.verticalScroll(state)) {
            repeat(100) { Text("Detail paragraph $it", Modifier.height(40.dp)) }
          }
        }
      }
      onRoot().performTouchInput {
        down(Offset(314f, 8f))
        moveTo(Offset(314f, 280f), delayMillis = 200)
        up()
      }
      waitUntil { state.value > 1000 }
      runOnIdle { assertTrue(state.value > 1000) }
    }

  @Test
  fun draggingScrollbarChangesTheActualLazyListPosition() =
    runSkikoComposeUiTest(size = Size(320f, 400f)) {
      val state = LazyListState()
      setContent {
        SharedLazyColumn(state = state) {
          items(100) { Text("Beer $it", Modifier.height(40.dp)) }
        }
      }
      onRoot().performTouchInput {
        down(Offset(314f, 8f))
        moveTo(Offset(314f, 280f), delayMillis = 200)
        up()
      }
      waitUntil { state.firstVisibleItemIndex > 30 }
      runOnIdle { assertTrue(state.firstVisibleItemIndex > 30) }
    }
}
