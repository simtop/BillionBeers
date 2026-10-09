package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
 * Metrics/status wrap; the title defaults to one line and the tagline to two. Hosts can retain a
 * longer title policy. Full text stays in semantics and the row opens detail. Hosts supply
 * localized labels and an image slot that honors its modifier. The rounded 80dp tile owns geometry;
 * loading, failure and decoding stay in the host.
 */
@Composable
fun SharedBeerListItem(
  beer: Beer,
  labels: BeerListItemLabels,
  onClick: () -> Unit,
  imageContent: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier,
  titleMaxLines: Int = 1,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = BillionBeersTheme.spacing.medium,
          vertical = BillionBeersTheme.spacing.small,
        )
        .testTag("beer_list_item")
        .minimumInteractiveComponentSize()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          role = Role.Button,
          onClick = onClick,
        )
        .semantics { stateDescription = labels.availability },
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    elevation = CardDefaults.cardElevation(defaultElevation = BillionBeersTheme.spacing.extraSmall),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.surface
          else MaterialTheme.colorScheme.errorContainer
      ),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(BillionBeersTheme.spacing.small + BillionBeersTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(BillionBeersTheme.spacing.extraHuge + BillionBeersTheme.spacing.medium)
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
          style = MaterialTheme.typography.titleLarge,
          maxLines = titleMaxLines,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.extraSmall))

        Text(
          text = beer.tagline,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontStyle = FontStyle.Italic,
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
          BeerChip(
            text = labels.abv,
            color = Color(ABV_BG_COLOR),
            textColor = Color(ABV_TEXT_COLOR),
          )
          BeerChip(
            text = labels.ibu,
            color = Color(IBU_BG_COLOR),
            textColor = Color(IBU_TEXT_COLOR),
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
              color = Color(UNAVAILABLE_BG_COLOR),
              textColor = Color(UNAVAILABLE_TEXT_COLOR),
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

private const val ABV_BG_COLOR = 0xFFE0F7FA
private const val ABV_TEXT_COLOR = 0xFF006064
private const val IBU_BG_COLOR = 0xFFFBE9E7
private const val IBU_TEXT_COLOR = 0xFFBF360C
private const val AVAILABLE_BG_COLOR = 0xFFE8F5E9
private const val AVAILABLE_TEXT_COLOR = 0xFF1B5E20
private const val UNAVAILABLE_BG_COLOR = 0xFFFFEBEE
private const val UNAVAILABLE_TEXT_COLOR = 0xFFB71C1C
