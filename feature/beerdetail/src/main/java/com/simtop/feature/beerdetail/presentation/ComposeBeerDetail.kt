@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.feature.beerdetail.presentation

import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.MAX_BEER_METRIC_FRACTION_DIGITS
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailContent
import com.simtop.billionbeers.shared.beerdetail.formatServingTemperatureRange
import com.simtop.presentation_utils.R
import java.text.NumberFormat

@Composable
@Suppress("LongParameterList")
fun ComposeBeerDetail(
  beer: Beer,
  onBackClick: () -> Unit,
  onToggleAvailability: () -> Unit,
  modifier: Modifier = Modifier,
  onToggleFavorite: () -> Unit = {},
  showBackButton: Boolean = true,
  scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
) {
  val context = LocalContext.current
  val locale = LocalConfiguration.current.locales[0]
  val animationsDisabled =
    remember(context) {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      ) == 0f
    }

  SharedBeerDetailContent(
    beer = beer,
    strings =
      BeerDetailStrings(
        back = stringResource(R.string.beer_detail_back),
        imageDescription = { name ->
          stringResource(R.string.beer_list_item_image_description, name)
        },
        addToFavorites = stringResource(R.string.add_to_favorites),
        removeFromFavorites = stringResource(R.string.remove_from_favorites),
        available = stringResource(R.string.beer_available),
        outOfStock = stringResource(R.string.beer_out_of_stock),
        markAsEmpty = stringResource(R.string.mark_as_empty),
        refillBarrels = stringResource(R.string.refill_barrels),
        styleAndBrewery = { style, brewery ->
          stringResource(R.string.beer_detail_style_and_brewery, style, brewery)
        },
        description = stringResource(R.string.beer_detail_description),
        foodPairing = stringResource(R.string.beer_detail_food_pairing),
        abv = stringResource(R.string.beer_detail_abv_label),
        ibu = stringResource(R.string.beer_detail_ibu_label),
        details = stringResource(R.string.beer_detail_details),
        srm = stringResource(R.string.beer_detail_srm_label),
        released = stringResource(R.string.beer_detail_released_year_label),
        servingTemperature = stringResource(R.string.beer_detail_serving_temperature_label),
        servingTemperatureValue = { min, max -> formatServingTemperatureRange(min, max) },
        metricValue = { value ->
          NumberFormat.getNumberInstance(locale)
            .apply {
              minimumFractionDigits = 0
              maximumFractionDigits = MAX_BEER_METRIC_FRACTION_DIGITS
            }
            .format(value)
        },
        fermentation = stringResource(R.string.beer_detail_fermentation_method_label),
        ingredients = stringResource(R.string.beer_detail_ingredients),
        recommendedGlasses = stringResource(R.string.beer_detail_recommended_glasses),
      ),
    onBackClick = onBackClick,
    onToggleAvailability = onToggleAvailability,
    onToggleFavorite = onToggleFavorite,
    backIcon = { contentDescription ->
      androidx.compose.material3.Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = contentDescription,
        tint = Color.White,
      )
    },
    favoriteIcon = { isFavorite, contentDescription ->
      androidx.compose.material3.Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = contentDescription,
        tint = Color.White,
      )
    },
    imageContent = { imageUrl, contentDescription, imageModifier ->
      BeerDetailImage(imageUrl, contentDescription, imageModifier)
    },
    modifier = modifier,
    favoriteModifier = Modifier.testTag("toggle_favorite"),
    availabilityModifier = Modifier.testTag("toggle_availability"),
    contentModifier = Modifier.testTag("detail_scroll_view"),
    headerModifier = Modifier.testTag("detail_header"),
    scrollBehavior = scrollBehavior,
    showBackButton = showBackButton,
    animationsDisabled = animationsDisabled,
  )
}

@Composable
fun BeerDetailImage(imageUrl: String, contentDescription: String?, modifier: Modifier = Modifier) {
  AsyncImage(
    model =
      ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        .crossfade(true)
        .placeholder(R.drawable.blue_image)
        .error(R.drawable.blue_image)
        .build(),
    contentDescription = contentDescription,
    contentScale = ContentScale.Fit,
    modifier = modifier,
  )
}
