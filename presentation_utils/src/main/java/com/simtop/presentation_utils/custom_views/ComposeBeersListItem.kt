package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.core.noRippleClickable

@CatalogComponent(
    tab = "Utilities",
)
@Composable
fun ComposeBeersListItem(beer: Beer = Beer.empty, onClick: ((Beer) -> Unit)? = null) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = BillionBeersTheme.spacing.medium, vertical = BillionBeersTheme.spacing.small)
        .testTag("beer_list_item")
        .noRippleClickable { onClick?.invoke(beer) },
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    elevation = CardDefaults.cardElevation(defaultElevation = BillionBeersTheme.spacing.extraSmall),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.surface else Color.Red.copy(alpha = 0.1f)
      )
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Beer Image
      BeerImage(imageUrl = beer.imageUrl)

      Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.medium))

      // Beer Details
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = beer.name,
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.extraSmall))

        Text(
          text = beer.tagline,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontStyle = FontStyle.Italic,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))

        Row(verticalAlignment = Alignment.CenterVertically) {
          BeerChip(
            text = "ABV: ${beer.abv}%",
            color = Color(ABV_BG_COLOR),
            textColor = Color(ABV_TEXT_COLOR)
          )
          Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.small))
          BeerChip(
            text = "IBU: ${beer.ibu}",
            color = Color(IBU_BG_COLOR),
            textColor = Color(IBU_TEXT_COLOR)
          )
        }
      }
    }
  }
}

@CatalogComponent(
    tab = "Utilities",
)
@Composable
fun BeerImage(imageUrl: String) {
  Box(
    modifier =
      Modifier.size(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
        .clip(RoundedCornerShape(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall))
        .background(Color.LightGray.copy(alpha = 0.3f))
  ) {
    AsyncImage(
      model =
        ImageRequest.Builder(LocalContext.current)
          .data(imageUrl)
          .crossfade(true)
          .placeholder(R.drawable.blue_image)
          .error(R.drawable.blue_image)
          .build(),
      contentDescription = null,
      modifier = Modifier.matchParentSize()
    )
  }
}

@Composable
fun BeerChip(text: String, color: Color, textColor: Color) {
  Surface(
    color = color,
    shape = RoundedCornerShape(BillionBeersTheme.spacing.small),
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = BillionBeersTheme.spacing.small, vertical = BillionBeersTheme.spacing.extraSmall),
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.SemiBold,
          color = textColor
        )
    )
  }
}

class BeerPreviewParameterProvider : androidx.compose.ui.tooling.preview.PreviewParameterProvider<Beer> {
    override val values = sequenceOf(
        Beer.empty.copy(
            name = "Buzz (Available)", 
            tagline = "A Real Bitter Experience.", 
            abv = 4.5, 
            ibu = 60.0,
            availability = true
        ),
        Beer.empty.copy(
            name = "Trashy Blonde (Unavailable)", 
            tagline = "You Know You Shouldn't", 
            abv = 4.1, 
            ibu = 41.5,
            availability = false
        )
    )
}

@PreviewLightDark
@Composable
fun ComposeBeersListItemPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(BeerPreviewParameterProvider::class) beer: Beer
) {
  BillionBeersTheme {
    ComposeBeersListItem(beer = beer)
  }
}


private const val ABV_BG_COLOR = 0xFFE0F7FA
private const val ABV_TEXT_COLOR = 0xFF006064
private const val IBU_BG_COLOR = 0xFFFBE9E7
private const val IBU_TEXT_COLOR = 0xFFBF360C
