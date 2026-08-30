package com.simtop.billionbeers

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.EmbeddedImage
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import com.simtop.navigation.DeepLinkDestination
import com.simtop.navigation.toDeepLinkDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Runs against the minified release-smoke graph, without requiring a Play signing identity. */
class ReleaseGraphSmokeTest {

  @Test
  fun startupDatabaseGraphMappingAndRoutesSurviveR8() {
    runBlocking<Unit> {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      val application = context.applicationContext as BillionBeersApplication

      // Application startup creates the production graph; this call also opens its Room-backed local
      // source rather than merely checking that the generated graph object exists.
      val graph = application.appGraph
      check(graph.beersRepository.getBeerById("release-smoke-missing") == null)

      val mappedBeer =
        BeersMapper(LanguageProvider { "en" }, NoOpLogger())
          .fromBeersApiResponseItemToBeer(
            BeersApiResponseItem(
              id = "release-smoke-beer",
              name = "Smoke IPA",
              image = EmbeddedImage("https://example.test/smoke.png"),
              translations = listOf(Translation(Language("en"), "Crisp.", "A smoke beer.")),
            )
          )
      assertEquals("Smoke IPA", mappedBeer.name)
      assertEquals("A smoke beer.", mappedBeer.description)

      assertEquals(
        DeepLinkDestination.BeersList,
        Uri.parse("billionbeers://beers").toDeepLinkDestination(),
      )
      assertEquals(
        DeepLinkDestination.BeerDetail("release-smoke-beer"),
        Uri.parse("billionbeers://beers/release-smoke-beer").toDeepLinkDestination(),
      )

      val scenario: ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
      scenario.onActivity { activity: MainActivity -> assertNotNull(activity.splitInstallManager) }
      scenario.close()
    }
  }
}
