package com.simtop.feature.beerbrowse.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerbrowse.presentation.di.FeatureBrowseComponent
import com.simtop.navigation.BeerDetail
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

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
          // The host gates the beerdetail install before pushing this key.
          onBeerClick = { beer -> onNavigate(BeerDetail(beer)) },
        )
    }
  }
}
