package com.simtop.feature.beerbrowse.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerbrowse.presentation.di.FeatureBrowseComponent
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.FeatureConstants
import com.simtop.presentation_utils.core.DynamicFeatureLoader
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One browse drill-in: the beers of a single style or brewery. Exactly one id is set; [name] is the
 * results screen title. Kept as plain saveable state *inside* the module - only [BeerDetail]
 * crosses the module boundary onto the app back stack.
 */
internal data class BrowseSelection(
  val styleId: String? = null,
  val breweryId: String? = null,
  val name: String,
) {
  fun toQuery() = BeersQuery(styleId = styleId, breweryId = breweryId)

  val key: String
    get() = styleId ?: breweryId.orEmpty()
}

private val BrowseSelectionSaver =
  listSaver<BrowseSelection?, String>(
    save = { selection ->
      selection?.let { listOf(it.styleId.orEmpty(), it.breweryId.orEmpty(), it.name) }
        ?: emptyList()
    },
    restore = { saved ->
      if (saved.isEmpty()) null
      else
        BrowseSelection(
          styleId = saved[0].ifEmpty { null },
          breweryId = saved[1].ifEmpty { null },
          name = saved[2],
        )
    },
  )

// Same recipe as the catalog's saver: Beer is @Serializable, so a JSON round-trip survives
// process death while the beerdetail install is in flight.
private val BeerSaver: Saver<Beer?, String> =
  Saver(
    save = { beer -> beer?.let { Json.encodeToString(it) }.orEmpty() },
    restore = { json -> json.takeIf { it.isNotEmpty() }?.let { Json.decodeFromString<Beer>(it) } },
  )

@Composable
fun BeerBrowseScreenImpl(onBack: () -> Unit, onNavigate: (NavKey) -> Unit) {
  val context = LocalContext.current

  val factory = remember {
    val appGraph =
      (context.applicationContext as BillionBeersApplication).appGraph as DynamicDependencies
    val component = createGraphFactory<FeatureBrowseComponent.Factory>().create(appGraph)
    component.metroViewModelFactory
  }

  CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
    var selection by
      rememberSaveable(stateSaver = BrowseSelectionSaver) {
        mutableStateOf<BrowseSelection?>(null)
      }
    // A tapped beer pending the beerdetail install - the same gate the catalog list uses, saved
    // across process death so the in-flight navigation resumes instead of silently dropping.
    var installingBeer by rememberSaveable(stateSaver = BeerSaver) { mutableStateOf<Beer?>(null) }

    when (val current = selection) {
      null ->
        BrowseHomeScreen(
          onBack = onBack,
          onStyleClick = { style ->
            selection = BrowseSelection(styleId = style.id, name = style.name)
          },
          onBreweryClick = { brewery ->
            selection = BrowseSelection(breweryId = brewery.id, name = brewery.name)
          },
        )
      else ->
        BrowseBeersScreen(
          selection = current,
          onBack = { selection = null },
          onBeerClick = { beer -> installingBeer = beer },
        )
    }

    installingBeer?.let { beer ->
      DynamicFeatureLoader(
        featureName = FeatureConstants.BEER_DETAIL_MODULE,
        onCancelled = { installingBeer = null },
      ) {
        LaunchedEffect(beer) {
          onNavigate(BeerDetail(beer))
          installingBeer = null
        }
      }
    }
  }
}
