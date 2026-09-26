package com.simtop.billionbeers.shared.beerdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

/** Localized strings and formatted values supplied by the platform resource adapter. */
data class BeerDetailStrings(
  val back: String,
  val imageDescription: @Composable (String) -> String,
  val addToFavorites: String,
  val removeFromFavorites: String,
  val available: String,
  val outOfStock: String,
  val markAsEmpty: String,
  val refillBarrels: String,
  val styleAndBrewery: @Composable (String, String) -> String,
  val description: String,
  val foodPairing: String,
  val abv: String,
  val ibu: String,
  val details: String,
  val srm: String,
  val released: String,
  val servingTemperature: String,
  val servingTemperatureValue: @Composable (minTemperature: Int, maxTemperature: Int) -> String,
  val fermentation: String,
  val ingredients: String,
  val recommendedGlasses: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SharedBeerDetailContent(
  beer: Beer,
  strings: BeerDetailStrings,
  onBackClick: () -> Unit,
  onToggleAvailability: () -> Unit,
  onToggleFavorite: () -> Unit,
  backIcon: @Composable (contentDescription: String) -> Unit,
  favoriteIcon: @Composable (isFavorite: Boolean, contentDescription: String) -> Unit,
  imageContent:
    @Composable
    (imageUrl: String, contentDescription: String?, modifier: Modifier) -> Unit,
  modifier: Modifier = Modifier,
  favoriteModifier: Modifier = Modifier,
  availabilityModifier: Modifier = Modifier,
  contentModifier: Modifier = Modifier,
  showBackButton: Boolean = true,
  animationsDisabled: Boolean = false,
  collapsingToolbarEnabled: Boolean = true,
  titleTextStyle: TextStyle? = null,
) {
  val scrollBehavior =
    if (collapsingToolbarEnabled) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null
  val animationDurationMs = if (animationsDisabled) 0 else AVAILABILITY_ANIMATION_DURATION_MS
  val favoriteLabel = if (beer.isFavorite) strings.removeFromFavorites else strings.addToFavorites

  Scaffold(
    modifier =
      if (scrollBehavior != null) modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
      else modifier,
    topBar = {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val headerHeight =
          if (maxWidth < 500.dp && LocalDensity.current.fontScale > 1f) 360.dp else 350.dp
        Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
          imageContent(
            beer.imageUrl,
            strings.imageDescription(beer.name),
            Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = GRADIENT_ALPHA))
                  )
                )
          )
          LargeTopAppBar(
            title = {
              Text(
                text = beer.name,
                style =
                  titleTextStyle
                    ?: MaterialTheme.typography.headlineMedium.copy(
                      fontWeight = FontWeight.ExtraBold
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            },
            expandedHeight = 350.dp,
            navigationIcon = {
              if (showBackButton) {
                IconButton(onClick = onBackClick) {
                  backIcon(strings.back)
                }
              }
            },
            actions = {
              IconButton(
                onClick = onToggleFavorite,
                modifier =
                  favoriteModifier.semantics {
                    role = Role.Button
                    stateDescription = favoriteLabel
                  },
              ) {
                favoriteIcon(beer.isFavorite, favoriteLabel)
              }
            },
            colors =
              TopAppBarDefaults.largeTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
              ),
            scrollBehavior = scrollBehavior,
          )
        }
      }
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onToggleAvailability,
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.error,
        contentColor =
          if (beer.availability) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onError,
        shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
        modifier =
          availabilityModifier.semantics {
            role = Role.Button
            stateDescription = if (beer.availability) strings.available else strings.outOfStock
          },
      ) {
        AnimatedContent(
          targetState = beer.availability,
          label = "availability_animation",
          transitionSpec = {
            fadeIn(animationSpec = tween(animationDurationMs)) togetherWith
              fadeOut(animationSpec = tween(animationDurationMs))
          },
        ) { isAvailable ->
          Text(
            text = if (isAvailable) strings.markAsEmpty else strings.refillBarrels,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    },
  ) { paddingValues ->
    Column(
      modifier =
        contentModifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(rememberScrollState())
          .padding(BillionBeersTheme.spacing.medium)
          .semantics {}
    ) {
      Text(
        text = beer.tagline,
        style =
          MaterialTheme.typography.titleMedium.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
      )

      if (beer.styleName.isNotEmpty() && beer.breweryName.isNotEmpty()) {
        Text(
          text = strings.styleAndBrewery(beer.styleName, beer.breweryName),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))

      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackMetricCards =
          maxWidth < 500.dp && LocalDensity.current.fontScale >= LARGE_FONT_METRIC_CARD_SCALE
        if (stackMetricCards) {
          Column(verticalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.medium)) {
            StatCard(
              label = strings.abv,
              value = "${formatBeerMetric(beer.abv)}%",
              color = Color(ABV_BG_COLOR),
              textColor = Color(ABV_TEXT_COLOR),
              modifier = Modifier.fillMaxWidth(),
            )
            StatCard(
              label = strings.ibu,
              value = formatBeerMetric(beer.ibu),
              color = Color(IBU_BG_COLOR),
              textColor = Color(IBU_TEXT_COLOR),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.medium),
          ) {
            StatCard(
              label = strings.abv,
              value = "${formatBeerMetric(beer.abv)}%",
              color = Color(ABV_BG_COLOR),
              textColor = Color(ABV_TEXT_COLOR),
              modifier = Modifier.weight(1f),
            )
            StatCard(
              label = strings.ibu,
              value = formatBeerMetric(beer.ibu),
              color = Color(IBU_BG_COLOR),
              textColor = Color(IBU_TEXT_COLOR),
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))
      Text(
        text = strings.description,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      )
      Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))
      Text(
        text = beer.description,
        style =
          MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TEXT_ALPHA),
          ),
      )

      Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))
      BulletSection(strings.foodPairing, beer.foodPairing)

      val detailRows = buildList {
        beer.releasedYear?.let { add(strings.released to "$it") }
        val minTemp = beer.minServingTemperature
        val maxTemp = beer.maxServingTemperature
        if (minTemp != null && maxTemp != null) {
          add(strings.servingTemperature to strings.servingTemperatureValue(minTemp, maxTemp))
        }
        if (beer.fermentationMethod.isNotEmpty()) {
          add(strings.fermentation to beer.fermentationMethod)
        }
        beer.srm?.let { add(strings.srm to "$it") }
      }
      if (detailRows.isNotEmpty()) {
        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))
        Text(
          text = strings.details,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))
        detailRows.forEach { (label, value) ->
          Row(
            modifier =
              Modifier.fillMaxWidth().padding(vertical = BillionBeersTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }

      Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))
      BulletSection(strings.ingredients, beer.ingredients)
      Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))
      BulletSection(strings.recommendedGlasses, beer.recommendedGlasses)
      Spacer(modifier = Modifier.height(96.dp))
    }
  }
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
  if (items.isEmpty()) return
  Column {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
    Spacer(
      modifier =
        Modifier.height(BillionBeersTheme.spacing.medium - BillionBeersTheme.spacing.extraSmall)
    )
    items.forEach { item ->
      Row(modifier = Modifier.padding(vertical = BillionBeersTheme.spacing.extraSmall)) {
        Text(
          text = "•",
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
          modifier = Modifier.padding(end = BillionBeersTheme.spacing.small),
        )
        Text(text = item, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

@Composable
private fun StatCard(
  label: String,
  value: String,
  color: Color,
  textColor: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = color),
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(BillionBeersTheme.spacing.medium),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style =
          MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = textColor),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
      )
      Text(
        text = label,
        style =
          MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.7f),
          ),
      )
    }
  }
}

private const val GRADIENT_ALPHA = 0.7f
private const val TEXT_ALPHA = 0.8f
private const val ABV_BG_COLOR = 0xFFE0F7FA
private const val ABV_TEXT_COLOR = 0xFF006064
private const val IBU_BG_COLOR = 0xFFFBE9E7
private const val IBU_TEXT_COLOR = 0xFFBF360C
private const val AVAILABILITY_ANIMATION_DURATION_MS = 300
private const val LARGE_FONT_METRIC_CARD_SCALE = 2f
