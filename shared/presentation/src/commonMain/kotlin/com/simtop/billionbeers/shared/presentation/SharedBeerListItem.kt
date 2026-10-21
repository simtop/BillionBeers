package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

/** Hosts supply localized/formatted copy and their image loader; layout is shared. */
@Immutable data class BeerListItemLabels(val abv: String, val ibu: String, val availability: String)

/**
 * Supported Android/Web beer row: one button with merged beer text and availability state.
 * Metrics/status wrap; the title defaults to two lines and the tagline to two. Hosts can retain a
 * longer title policy. Full text stays in semantics and the row opens detail. Hosts supply
 * localized labels and an image slot that honors its modifier. The rounded portrait tile owns
 * geometry; loading, failure and decoding stay in the host.
 */
@Composable
// The host image slot and title policy are explicit parts of this UI boundary.
@Suppress("LongParameterList")
fun SharedBeerListItem(
  beer: Beer,
  labels: BeerListItemLabels,
  onClick: () -> Unit,
  imageContent: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier,
  titleMaxLines: Int = 2,
) {
  Card(
    onClick = onClick,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = BillionBeersTheme.spacing.medium,
          vertical = BillionBeersTheme.spacing.small,
        )
        .testTag("beer_list_item")
        .minimumInteractiveComponentSize()
        .semantics {
          role = Role.Button
          stateDescription = labels.availability
        },
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    elevation =
      CardDefaults.cardElevation(defaultElevation = BillionBeersTheme.brand.beerCardElevation),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.width(BillionBeersTheme.brand.beerArtworkSize.width)
            .height(BillionBeersTheme.brand.beerArtworkSize.height)
            .clip(
              RoundedCornerShape(
                BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall
              )
            )
      ) {
        imageContent(Modifier.matchParentSize())
      }

      Spacer(modifier = Modifier.width(BillionBeersTheme.spacing.medium))

      // Beer Details
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = beer.name,
          style = BillionBeersTheme.brand.beerTitle,
          maxLines = titleMaxLines,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.extraSmall))

        Text(
          text = beer.tagline,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontStyle = FontStyle.Normal,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.small),
          verticalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.small),
        ) {
          Text(
            text = labels.abv + "  ·  " + labels.ibu,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (beer.availability) {
            BeerChip(
              text = labels.availability,
              color = Color(AVAILABLE_BG_COLOR),
              textColor = Color(AVAILABLE_TEXT_COLOR),
            )
          } else {
            BeerChip(
              text = labels.availability,
              color = MaterialTheme.colorScheme.surfaceVariant,
              textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BeerChip(text: String, color: Color, textColor: Color, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    color = color,
    shape = RoundedCornerShape(BillionBeersTheme.spacing.small),
  ) {
    Text(
      text = text,
      modifier =
        Modifier.padding(
          horizontal = BillionBeersTheme.spacing.small,
          vertical = BillionBeersTheme.spacing.extraSmall,
        ),
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.SemiBold,
          color = textColor,
        ),
    )
  }
}

private const val AVAILABLE_BG_COLOR = 0xFFE8F5E9
private const val AVAILABLE_TEXT_COLOR = 0xFF1B5E20
