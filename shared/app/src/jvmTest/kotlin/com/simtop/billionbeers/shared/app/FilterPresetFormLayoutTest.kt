package com.simtop.billionbeers.shared.app

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class FilterPresetFormLayoutTest {
  @Test fun catalogFormKeepsItsNameUsableAtDoubleTextSize() = checkForm("catalog")

  @Test fun searchFormKeepsItsNameUsableAndSavesTheActiveQuery() = checkForm("search")

  @Test fun browseFormKeepsItsNameUsableAndSavesTheSelectedStyle() = checkForm("browse")

  @Test
  fun compactEnglishFormKeepsTheNameUsable() = checkForm("catalog", fontScale = 1f, french = false)

  @Test
  fun rtlFormKeepsItsNameAndSaveActionUsable() =
    checkForm("catalog", direction = LayoutDirection.Rtl)

  private fun checkForm(
    screen: String,
    fontScale: Float = 2f,
    french: Boolean = true,
    direction: LayoutDirection = LayoutDirection.Ltr,
  ) =
    runSkikoComposeUiTest(size = Size(320f, 844f), density = Density(1f, fontScale)) {
      val backing = FakeBeersRepository()
      val repository = SavingRepository(backing)
      val pagers = FakeBeersPagerFactory(backing)
      val strings =
        savedFilterStrings("Rename", "Delete")
          .copy(
            filterNameHint = if (french) "Nom du filtre" else "Filter name",
            saveFilter = if (french) "Enregistrer le filtre" else "Save",
            searchHint = "Search beers",
            list = "Catalog",
            favorites = "Favorites",
            browse = "Browse",
            back = "Back",
          )
      val route =
        when (screen) {
          "search" -> PortableRoute.BeersSearch
          "browse" -> PortableRoute.BeerBrowseSelection(BrowseCategory.Style("ipa-style", "IPA"))
          else -> PortableRoute.BeersList
        }
      setContent {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
          SharedAppShell(
            repository = repository,
            pagerFactory = pagers,
            coroutineDispatcher = DefaultCoroutineDispatcherProvider(),
            strings = strings,
            initialRoute = route,
            host =
              SharedAppHost(
                beerRow = { _, _ -> },
                errorContent = { _, _ -> Text("Load error") },
                backIcon = { text -> Text(text) },
                favoriteIcon = { _, _ -> },
                imageContent = { _, _, _ -> },
              ),
          )
        }
      }
      if (screen == "search") {
        val inactiveName = onNode(hasSetTextAction() and hasText(strings.filterNameHint))
        inactiveName.performTextReplacement("Inactive query")
        onNodeWithText(strings.saveFilter).assertIsNotEnabled()
        inactiveName.performTextReplacement("")
        onNode(hasSetTextAction() and hasText(strings.searchHint)).performTextReplacement("ipa")
        waitUntil(timeoutMillis = 5_000) { pagers.createdQueries.lastOrNull()?.search == "ipa" }
      }
      System.getenv("FILTER_FORM_QA_DIR")?.let { directory ->
        val file = File(directory, "$screen-$fontScale-$french-$direction.png")
        file.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
      }
      val editor = onNode(hasSetTextAction() and hasText(strings.filterNameHint))
      editor.assertIsDisplayed().assertWidthIsAtLeast(240.dp)
      val save = onNodeWithText(strings.saveFilter)
      save.assertIsDisplayed().assertIsNotEnabled()
      editor.performTextReplacement("  ")
      save.assertIsNotEnabled()
      val longestName = "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH)
      editor.performTextReplacement(longestName)
      editor.performTextReplacement(longestName + "x")
      assertEquals(
        longestName,
        editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
      )
      editor.performTextReplacement("IPA françaises")
      save.performClick()
      waitUntil { repository.attempts.size == 1 }
      assertEquals(
        "IPA françaises",
        editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
      )
      onNodeWithText(strings.saveFilterFailed).assertIsDisplayed()
      val feedbackBounds = onNodeWithTag("shell_message").fetchSemanticsNode().boundsInRoot
      assertFalse(
        save.fetchSemanticsNode().boundsInRoot.overlaps(feedbackBounds),
        "Feedback must not cover Save",
      )
      System.getenv("FILTER_FORM_QA_DIR")?.let { directory ->
        val file = File(directory, "$screen-$fontScale-$french-$direction-failed.png")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
      }
      runOnIdle { repository.succeeds = true }
      save.performClick()
      waitUntil { repository.attempts.size == 2 }
      runOnIdle {
        val query =
          when (screen) {
            "search" -> BeersQuery(search = "ipa")
            "browse" -> BeersQuery(styleId = "ipa-style")
            else -> BeersQuery()
          }
        assertEquals(listOf(query, query), repository.attempts.map { it.query })
        assertEquals(
          listOf("IPA françaises", "IPA françaises"),
          repository.attempts.map { it.name },
        )
      }
      assertEquals("", editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

  private class SavingRepository(private val backing: FakeBeersRepository) :
    BeersRepository by backing {
    var succeeds = false
    val attempts = mutableListOf<SavedFilterPreset>()

    override suspend fun saveFilterPreset(
      preset: SavedFilterPreset
    ): Either<SaveFilterPresetError, Unit> {
      attempts += preset
      return if (succeeds) backing.saveFilterPreset(preset)
      else Either.Left(SaveFilterPresetError.Unknown(IllegalStateException("fixture failure")))
    }
  }
}
