package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.FakeSavedFilterPresetsRepository
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ExpandedDetailNavigationTest {
  @Test
  fun railStaysAccessibleAndBackAfterResizeReturnsToCatalog() =
    runSkikoComposeUiTest(size = Size(1200f, 900f)) {
      val repository =
        FakeBeersRepository(
          listOf(
            Beer.empty.copy(id = "a", name = "First beer"),
            Beer.empty.copy(id = "b", name = "Second beer"),
          )
        )
      val pagerFactory = FakeBeersPagerFactory(repository)
      val presets = FakeSavedFilterPresetsRepository()
      val dispatcher = DefaultCoroutineDispatcherProvider()
      var width by mutableStateOf(1200.dp)
      val base = savedFilterStrings("Rename", "Delete")
      val strings =
        base.copy(
          chrome = base.chrome.copy(favorites = "Favorites"),
          favorites = base.favorites.copy(empty = "No favorites"),
        )
      setContent {
        Box(Modifier.width(width).fillMaxHeight()) {
          SharedAppShell(
            repository = repository,
            catalogMetadataRepository = repository,
            savedFilterPresetsRepository = presets,
            pagerFactory = pagerFactory,
            coroutineDispatcher = dispatcher,
            strings = strings,
            host =
              SharedAppHost(
                beerRow = { beer, click ->
                  Text("Open ${beer.name}", Modifier.clickable(onClick = click))
                },
                errorContent = { _, _ -> Text("Load error") },
                backIcon = { Text("Go back") },
                favoriteIcon = { _, _ -> Text("Heart") },
                imageContent = { _, _, _ -> },
                detailCollapsingToolbarEnabled = false,
              ),
          )
        }
      }
      onNodeWithText("Open First beer").performClick()
      onNodeWithText("First beer").assertIsDisplayed()
      onNodeWithText(strings.chrome.favorites).assertIsDisplayed().performClick()
      onNodeWithText(strings.favorites.empty).assertIsDisplayed()
      onNode(hasText(strings.chrome.list) and hasClickAction()).performClick()
      onNodeWithText("Open First beer").performClick()
      onNodeWithText("Open Second beer").performClick()
      onNodeWithText("Second beer").assertIsDisplayed()
      runOnIdle { width = 390.dp }
      onNodeWithText("Go back").performClick()
      onNodeWithText("Open First beer").assertIsDisplayed()
      onNodeWithText(strings.chrome.favorites).performClick()
      onNodeWithText(strings.favorites.empty).assertIsDisplayed()
    }
}
