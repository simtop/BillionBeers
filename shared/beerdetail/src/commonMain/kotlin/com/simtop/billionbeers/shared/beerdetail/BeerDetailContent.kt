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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.designsystem.components.SharedScrollContainer
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
  val metricValue: (Double) -> String,
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
  titleTextStyle: TextStyle? = null,
) {
  val favoriteLabel = if (beer.isFavorite) strings.removeFromFavorites else strings.addToFavorites
  val scrollState = rememberScrollState()
  val headerTitleStyle =
    titleTextStyle
      ?: MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.Serif,
      )
  val headerButtonModifier = Modifier.size(48.dp)
  val headerButtonColors =
    IconButtonDefaults.iconButtonColors(contentColor = Color(HERO_FOREGROUND))
  val favoriteButton: @Composable () -> Unit = {
    IconButton(
      onClick = onToggleFavorite,
      colors = headerButtonColors,
      modifier =
        favoriteModifier.then(headerButtonModifier).semantics {
          role = Role.Button
          stateDescription = favoriteLabel
        },
    ) {
      favoriteIcon(beer.isFavorite, favoriteLabel)
    }
  }
  val backButton: @Composable () -> Unit = {
    if (showBackButton) {
      IconButton(
        onClick = onBackClick,
        modifier = headerButtonModifier,
        colors = headerButtonColors,
      ) {
        backIcon(strings.back)
      }
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val preferredHeight = if (LocalDensity.current.fontScale > 1f) 340.dp else 260.dp
        val heroHeight = minOf(preferredHeight, maxHeight * HERO_MAX_HEIGHT_FRACTION)
        Box(
          Modifier.fillMaxWidth()
            .height(heroHeight)
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(Color(HERO_BACKGROUND))
            .padding(horizontal = 16.dp)
        ) {
          Row(
            Modifier.fillMaxSize().padding(top = 56.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = beer.name,
                color = Color(HERO_FOREGROUND),
                style = headerTitleStyle,
                maxLines = if (heroHeight < 220.dp) 1 else HERO_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.height(16.dp))
              Box(Modifier.width(48.dp).height(4.dp).background(Color(HERO_ACCENT)))
            }
            imageContent(
              beer.imageUrl,
              strings.imageDescription(beer.name),
              Modifier.width(112.dp).height(200.dp),
            )
          }
          Row(
            Modifier.fillMaxWidth().height(56.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box { backButton() }
            favoriteButton()
          }
        }
      }
    },
    floatingActionButton = {
      BeerAvailabilityAction(
        available = beer.availability,
        strings = strings,
        animationsDisabled = animationsDisabled,
        modifier = availabilityModifier,
        onToggle = onToggleAvailability,
      )
    },
  ) { paddingValues ->
    SharedScrollContainer(
      state = scrollState,
      modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
      Column(
        modifier =
          contentModifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(BillionBeersTheme.spacing.medium)
            .semantics {}
      ) {
        Text(
          text = beer.tagline,
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontStyle = FontStyle.Normal,
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

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val stackMetricCards =
            maxWidth < 500.dp && LocalDensity.current.fontScale >= LARGE_FONT_METRIC_CARD_SCALE
          if (stackMetricCards) {
            Column(verticalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.medium)) {
              StatCard(
                label = strings.abv,
                value = "${strings.metricValue(beer.abv)}%",
                color = BillionBeersTheme.colors.abvContainer,
                textColor = BillionBeersTheme.colors.onAbvContainer,
                modifier = Modifier.fillMaxWidth(),
              )
              StatCard(
                label = strings.ibu,
                value = strings.metricValue(beer.ibu),
                color = BillionBeersTheme.colors.ibuContainer,
                textColor = BillionBeersTheme.colors.onIbuContainer,
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
                value = "${strings.metricValue(beer.abv)}%",
                color = BillionBeersTheme.colors.abvContainer,
                textColor = BillionBeersTheme.colors.onAbvContainer,
                modifier = Modifier.weight(1f),
              )
              StatCard(
                label = strings.ibu,
                value = strings.metricValue(beer.ibu),
                color = BillionBeersTheme.colors.ibuContainer,
                textColor = BillionBeersTheme.colors.onIbuContainer,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))
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

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))
        BulletSection(strings.foodPairing, beer.foodPairing)

        BeerDetailFacts(beer, strings)

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))
        BulletSection(strings.ingredients, beer.ingredients)
        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))
        BulletSection(strings.recommendedGlasses, beer.recommendedGlasses)
        Spacer(modifier = Modifier.height(96.dp))
      }
    }
  }
}

@Composable
private fun BeerAvailabilityAction(
  available: Boolean,
  strings: BeerDetailStrings,
  animationsDisabled: Boolean,
  modifier: Modifier = Modifier,
  onToggle: () -> Unit,
) {
  val animationDurationMs = if (animationsDisabled) 0 else AVAILABILITY_ANIMATION_DURATION_MS
  ExtendedFloatingActionButton(
    onClick = onToggle,
    containerColor =
      if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    contentColor =
      if (available) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    modifier =
      modifier.semantics {
        role = Role.Button
        stateDescription = if (available) strings.available else strings.outOfStock
      },
  ) {
    AnimatedContent(
      targetState = available,
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
}

@Composable
private fun BeerDetailFacts(beer: Beer, strings: BeerDetailStrings) {
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
    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))
    Text(
      text = strings.details,
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))
    detailRows.forEach { (label, value) ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BillionBeersTheme.spacing.extraSmall),
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
      modifier = Modifier.padding(BillionBeersTheme.spacing.small),
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
            color = textColor,
          ),
      )
    }
  }
}

private const val TEXT_ALPHA = 0.8f
private const val AVAILABILITY_ANIMATION_DURATION_MS = 300
private const val LARGE_FONT_METRIC_CARD_SCALE = 2f

private const val HERO_FOREGROUND = 0xFFFFF7E6
private const val HERO_BACKGROUND = 0xFF302719
private const val HERO_ACCENT = 0xFFE6AF64
private const val HERO_MAX_HEIGHT_FRACTION = 0.45f
private const val HERO_TITLE_MAX_LINES = 3
