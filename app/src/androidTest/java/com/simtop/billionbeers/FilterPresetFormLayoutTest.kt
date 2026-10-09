package com.simtop.billionbeers

import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel
import com.simtop.feature.beersearch.BeersSearchContent
import com.simtop.feature.beersearch.SEARCH_FIELD_TAG
import com.simtop.feature.beerslist.BeersListContent
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FilterPresetFormLayoutTest(private val screen: String) {
  @get:Rule val compose = createComposeRule()

  @Test
  fun frenchLargeTextNameFieldRemainsUsableAndSaveRetainsFailureDraft() {
    var saveSucceeds = false
    val savedNames = mutableListOf<String>()
    val save: suspend (String) -> Boolean = {
      savedNames += it
      saveSucceeds
    }
    showContent(save)

    val editor = compose.onNode(hasSetTextAction() and !hasTestTag(SEARCH_FIELD_TAG))
    editor.assertIsDisplayed().assertWidthIsAtLeast(240.dp)
    compose.onNodeWithText("Nom du filtre").assertIsDisplayed()
    editor.performTextReplacement("IPA françaises")
    compose.onNodeWithText("Enregistrer le filtre").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(listOf("IPA françaises"), savedNames) }
    assertEquals(
      "IPA françaises",
      editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
    )

    compose.runOnIdle { saveSucceeds = true }
    compose.onNodeWithText("Enregistrer le filtre").performClick()
    compose.runOnIdle { assertEquals(listOf("IPA françaises", "IPA françaises"), savedNames) }
    assertEquals("", editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
  }

  private fun showContent(save: suspend (String) -> Boolean) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val configuration =
      Configuration(context.resources.configuration).apply { setLocale(Locale.FRENCH) }
    val frenchContext = context.createConfigurationContext(configuration)
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(
        LocalContext provides frenchContext,
        LocalConfiguration provides configuration,
        LocalDensity provides Density(density, fontScale = 2f),
      ) {
        BillionBeersTheme {
          ScreenContent(save)
        }
      }
    }
  }

  @Composable
  private fun ScreenContent(save: suspend (String) -> Boolean) {
    if (screen == "catalog") {
      BeersListContent(
        viewState = CommonUiState.Empty,
        onBeerClick = {},
        onSearchClick = {},
        onBrowseClick = {},
        onSaveQuery = save,
        onScrollToBottom = {},
        onRefresh = {},
        onRetry = {},
        onRetryLoadMore = {},
        modifier = Modifier.width(360.dp),
      )
    } else {
      BeersSearchContent(
        viewState =
          when (screen) {
            "search-loading" -> CommonUiState.Loading
            "search-error" -> CommonUiState.Error(message = "No internet connection")
            "search-no-results" -> CommonUiState.Success(PagedListUiModel(items = emptyList()))
            "search-results" ->
              CommonUiState.Success(
                PagedListUiModel(items = listOf(Beer.empty.copy(id = "qa", name = "IPA")))
              )
            else -> CommonUiState.Empty
          },
        query = "ipa",
        onQueryChange = {},
        activeQuery = BeersQuery(search = "ipa"),
        onBeerClick = {},
        onBack = {},
        onSaveQuery = { name, _ -> save(name) },
        onScrollToBottom = {},
        onRetryLoadMore = {},
        onRetrySearch = {},
        modifier = Modifier.width(360.dp),
        autoFocus = false,
      )
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun screens() =
      listOf(
          "catalog",
          "search-empty",
          "search-loading",
          "search-error",
          "search-no-results",
          "search-results",
        )
        .map { arrayOf(it) }
  }
}
