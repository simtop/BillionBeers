package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.FakeSavedFilterPresetsRepository
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DetailLoadRecoveryTest {
  @Test
  fun retryReloadsTheBeerAfterADetailReadFailure() =
    runSkikoComposeUiTest(size = Size(390f, 844f), density = Density(1f)) {
      val fixture = Fixture()
      showShell(fixture)
      onNodeWithText("Open Audit beer").performClick()
      onNodeWithText("Detail load failed").assertIsDisplayed()
      runOnIdle { fixture.failReads = false }

      onNodeWithText("Retry").performClick()

      onNodeWithText("Audit beer").assertIsDisplayed()
      runOnIdle { assertEquals(2, fixture.reads) }
    }

  @Test
  fun backReturnsToTheListAfterADetailReadFailure() =
    runSkikoComposeUiTest(size = Size(390f, 844f), density = Density(1f)) {
      val fixture = Fixture()
      showShell(fixture)
      onNodeWithText("Open Audit beer").performClick()
      onNodeWithText("Detail load failed").assertIsDisplayed()

      onNodeWithText("Go back").performClick()

      onNodeWithText("Open Audit beer").assertIsDisplayed()
      runOnIdle { assertEquals(1, fixture.reads) }
    }

  private fun ComposeUiTest.showShell(fixture: Fixture) {
    val pagerFactory = FakeBeersPagerFactory(fixture.backing)
    val presets = FakeSavedFilterPresetsRepository()
    val dispatcher = DefaultCoroutineDispatcherProvider()
    val base = savedFilterStrings("Rename", "Delete")
    val strings =
      base.copy(
        chrome = base.chrome.copy(back = "Go back"),
        detailStrings = base.detailStrings.copy(back = "Go back"),
      )
    setContent {
      SharedAppShell(
        repository = fixture.repository,
        catalogMetadataRepository = fixture.backing,
        savedFilterPresetsRepository = presets,
        pagerFactory = pagerFactory,
        coroutineDispatcher = dispatcher,
        strings = strings,
        host =
          SharedAppHost(
            beerRow = { beer, click ->
              Text("Open ${beer.name}", Modifier.clickable(onClick = click))
            },
            errorContent = { _, retry ->
              Column {
                Text("Detail load failed")
                TextButton(onClick = retry) { Text("Retry") }
              }
            },
            backIcon = { Text(it) },
            favoriteIcon = { _, _ -> },
            imageContent = { _, _, _ -> },
          ),
      )
    }
  }

  private class Fixture {
    val backing = FakeBeersRepository(listOf(Beer.empty.copy(id = "audit", name = "Audit beer")))
    var failReads = true
    var reads = 0
    val repository =
      object : BeersRepository by backing {
        override suspend fun getBeerById(id: String): Beer? {
          reads++
          if (failReads) error("temporary detail read failure")
          return backing.getBeerById(id)
        }
      }
  }
}
