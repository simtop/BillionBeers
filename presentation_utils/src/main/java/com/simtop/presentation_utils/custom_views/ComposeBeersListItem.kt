package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.shimmerBrush
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.presentation.BeerListItemLabels
import com.simtop.billionbeers.shared.presentation.SharedBeerListItem
import com.simtop.presentation_utils.R

@CatalogComponent(tab = "Utilities")
@Composable
fun ComposeBeersListItem(
  modifier: Modifier = Modifier,
  beer: Beer = Beer.empty,
  onClick: ((Beer) -> Unit)? = null,
) {
  SharedBeerListItem(
    modifier = modifier,
    beer = beer,
    labels =
      BeerListItemLabels(
        abv = stringResource(R.string.abv_chip, beer.abv),
        ibu = stringResource(R.string.ibu_chip, beer.ibu),
        availability =
          if (beer.availability) stringResource(R.string.beer_available)
          else stringResource(R.string.beer_out_of_stock),
      ),
    onClick = { onClick?.invoke(beer) },
    imageContent = { imageModifier ->
      BeerImage(
        imageUrl = beer.imageUrl,
        modifier = imageModifier,
        contentDescription = stringResource(R.string.beer_list_item_image_description, beer.name),
      )
    },
  )
}

@CatalogComponent(tab = "Utilities")
@Composable
fun BeerImage(
  imageUrl: String,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
) {
  // Keeps the list item's skeleton shimmer alive on the tile until Coil resolves, so the image
  // crossfades in from the shimmer instead of flashing a static placeholder first.
  var isLoading by remember { mutableStateOf(true) }
  val background = if (isLoading) shimmerBrush() else SolidColor(Color.LightGray.copy(alpha = 0.3f))

  Box(
    modifier =
      modifier
        .size(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
        .clip(
          RoundedCornerShape(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall)
        )
        .background(background)
  ) {
    AsyncImage(
      model =
        ImageRequest.Builder(LocalContext.current)
          .data(imageUrl)
          .crossfade(true)
          .error(R.drawable.blue_image)
          .build(),
      contentDescription = contentDescription,
      // Bottle shots are tall and narrow; Fit shows the whole bottle inside the square tile
      // (Crop would zoom into the label and cut the bottle off).
      contentScale = ContentScale.Fit,
      onState = { state -> isLoading = state is AsyncImagePainter.State.Loading },
      modifier = Modifier.matchParentSize(),
    )
  }
}

class BeerPreviewParameterProvider : PreviewParameterProvider<Beer> {
  override val values =
    sequenceOf(
      Beer.empty.copy(
        name = "Buzz (Available)",
        tagline = "A Real Bitter Experience.",
        abv = 4.5,
        ibu = 60.0,
        availability = true,
      ),
      Beer.empty.copy(
        name = "Trashy Blonde (Unavailable)",
        tagline = "You Know You Shouldn't",
        abv = 4.1,
        ibu = 41.5,
        availability = false,
      ),
    )
}

@PreviewLightDark
@Composable
internal fun ComposeBeersListItemPreview(
  @androidx.compose.ui.tooling.preview.PreviewParameter(BeerPreviewParameterProvider::class)
  beer: Beer
) {
  BillionBeersTheme { ComposeBeersListItem(beer = beer) }
}
