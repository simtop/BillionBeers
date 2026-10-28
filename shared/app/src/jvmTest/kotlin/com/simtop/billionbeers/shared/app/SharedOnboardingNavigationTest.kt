package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.FakeSavedFilterPresetsRepository
import com.simtop.billionbeers.shared.presentation.OnboardingController
import com.simtop.billionbeers.shared.presentation.OnboardingStore
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(ExperimentalTestApi::class)
class SharedOnboardingNavigationTest {
  @Test
  fun welcomeLeadsToCatalogAndTheFavoriteHintIsConsumedOnceAcrossDetailEntries() =
    runSkikoComposeUiTest(size = Size(390f, 844f)) {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
      var saved: Set<String> = emptySet()
      val onboarding =
        OnboardingController(
          object : OnboardingStore {
            override suspend fun read() = saved

            override suspend fun write(completedIds: Set<String>) {
              saved = completedIds
            }
          },
          scope,
        )
      val repository =
        FakeBeersRepository(
          listOf(
            Beer.empty.copy(id = "a", name = "First beer"),
            Beer.empty.copy(id = "b", name = "Second beer"),
          )
        )
      val pagers = FakeBeersPagerFactory(repository)
      val presets = FakeSavedFilterPresetsRepository()
      val dispatchers = DefaultCoroutineDispatcherProvider()
      try {
        setContent {
          SharedAppShell(
            repository = repository,
            catalogMetadataRepository = repository,
            savedFilterPresetsRepository = presets,
            pagerFactory = pagers,
            coroutineDispatcher = dispatchers,
            strings = savedFilterStrings("Rename", "Delete"),
            host =
              SharedAppHost(
                beerRow = { beer, click ->
                  Text("Open ${beer.name}", Modifier.clickable(onClick = click))
                },
                errorContent = { _, _ -> Text("Load error") },
                backIcon = { Text("Go back") },
                favoriteIcon = { _, _ -> Text("Heart") },
                imageContent = { _, _, _ -> },
                onboarding = onboarding,
              ),
          )
        }
        onNodeWithTag("introduction_skip").performClick()
        onNodeWithText("Open First beer").performClick()
        onNodeWithTag("introduction_hint").assertIsDisplayed()
        onNodeWithText("Got it").performClick()
        onNodeWithTag("introduction_hint").assertDoesNotExist()
        onNodeWithText("Go back").performClick()
        onNodeWithText("Open Second beer").performClick()
        onNodeWithText("Second beer").assertIsDisplayed()
        onNodeWithTag("introduction_hint").assertDoesNotExist()
        assertEquals(setOf("welcome", "favorites"), saved)
      } finally {
        scope.cancel()
      }
    }
}
