package com.simtop.billionbeers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.BeersListScreen
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.BeerDetailProvider
import com.simtop.navigation.BeersList
import com.simtop.navigation.FeatureConstants
import com.simtop.navigation.serializableType
import kotlin.reflect.typeOf

@Composable
fun AppNavigation(
  splitInstallManager: SplitInstallManager = SplitInstallManagerFactory.create(LocalContext.current)
) {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = BeersList) {
    composable<BeersList> {
      BeersListScreen(
        onBeerClick = { beer -> navController.navigate(BeerDetail(beer)) },
        splitInstallManager = splitInstallManager
      )
    }

    composable<BeerDetail>(typeMap = mapOf(typeOf<Beer>() to serializableType<Beer>())) {
      backStackEntry ->
      val args = backStackEntry.toRoute<BeerDetail>()

      // Load the provider via reflection
      // We can assume it's installed because we check before navigating
      val providerClass = Class.forName(FeatureConstants.BEER_DETAIL_PROVIDER_CLASS)
      val provider = providerClass.getDeclaredConstructor().newInstance() as BeerDetailProvider

      provider.BeerDetailScreen(beer = args.beer, onBackClick = { navController.popBackStack() })
    }
  }
}
