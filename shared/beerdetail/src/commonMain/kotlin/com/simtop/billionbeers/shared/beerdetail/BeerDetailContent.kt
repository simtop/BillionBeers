package com.simtop.billionbeers.shared.beerdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
  headerModifier: Modifier = Modifier,
  scrollBehavior: TopAppBarScrollBehavior? = null,
  scrollState: ScrollState = rememberScrollState(),
) {
  val favoriteLabel = if (beer.isFavorite) strings.removeFromFavorites else strings.addToFavorites
  val headerTitleStyle = titleTextStyle ?: BillionBeersTheme.brand.detailTitle
  val headerButtonModifier = Modifier.size(48.dp)
  val headerButtonColors =
    IconButtonDefaults.iconButtonColors(contentColor = BillionBeersTheme.brand.detailHeroForeground)
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
    modifier =
      modifier.then(
        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
      ),
    topBar = {
      BeerDetailHeader(
        beer = beer,
        strings = strings,
        titleStyle = headerTitleStyle,
        backButton = backButton,
        favoriteButton = favoriteButton,
        imageContent = imageContent,
        showBackButton = showBackButton,
        scrollBehavior = scrollBehavior,
        modifier = headerModifier,
      )
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

/** A static hero by default; Android supplies its native exit-until-collapsed policy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun BeerDetailHeader(
  beer: Beer,
  strings: BeerDetailStrings,
  titleStyle: TextStyle,
  backButton: @Composable () -> Unit,
  favoriteButton: @Composable () -> Unit,
  imageContent: @Composable (String, String?, Modifier) -> Unit,
  showBackButton: Boolean,
  scrollBehavior: TopAppBarScrollBehavior?,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val density = LocalDensity.current
    val preferredHeight =
      if (density.fontScale > 1f) BillionBeersTheme.brand.largeTextDetailHeroHeight
      else BillionBeersTheme.brand.detailHeroHeight
    val compactHeight = 56.dp + with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val expandedHeight =
      minOf(preferredHeight, maxHeight * HERO_MAX_HEIGHT_FRACTION).coerceAtLeast(compactHeight)
    val collapseRange = with(density) { (expandedHeight - compactHeight).toPx() }
    val state = scrollBehavior?.state
    SideEffect {
      state?.let {
        it.heightOffsetLimit = -collapseRange
        it.heightOffset = it.heightOffset.coerceIn(-collapseRange, 0f)
      }
    }
    val offset = state?.heightOffset?.coerceIn(-collapseRange, 0f) ?: 0f
    val fraction = if (collapseRange > 0f) -offset / collapseRange else 0f
    val currentHeight = expandedHeight + with(density) { offset.toDp() }
    Box(
      Modifier.fillMaxWidth()
        .height(currentHeight)
        .windowInsetsPadding(WindowInsets.statusBars)
        .background(BillionBeersTheme.brand.detailHeroBackground)
        .clipToBounds()
        .padding(horizontal = 16.dp)
    ) {
      Row(
        Modifier.fillMaxSize()
          .padding(
            top = lerp(56.dp, 0.dp, fraction),
            bottom = lerp(16.dp, 0.dp, fraction),
            start = lerp(0.dp, if (showBackButton) 48.dp else 0.dp, fraction),
            end = lerp(0.dp, 48.dp, fraction),
          ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f).padding(end = lerp(8.dp, 0.dp, fraction))) {
          Text(
            text = beer.name,
            color = BillionBeersTheme.brand.detailHeroForeground,
            style =
              lerp(
                titleStyle,
                BillionBeersTheme.brand.compactDetailTitle,
                fraction,
              ),
            maxLines =
              if (expandedHeight < 220.dp || fraction > HERO_SINGLE_LINE_COLLAPSE_FRACTION) 1
              else HERO_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
          )
          if (fraction < 1f) {
            Spacer(Modifier.height(lerp(16.dp, 0.dp, fraction)))
            Box(
              Modifier.width(BillionBeersTheme.brand.detailHeroAccentSize.width)
                .height(lerp(BillionBeersTheme.brand.detailHeroAccentSize.height, 0.dp, fraction))
                .background(BillionBeersTheme.brand.detailHeroAccent)
            )
          }
        }
        if (fraction < 1f) {
          imageContent(
            beer.imageUrl,
            strings.imageDescription(beer.name),
            Modifier.width(lerp(BillionBeersTheme.brand.detailArtworkSize.width, 0.dp, fraction))
              .height(lerp(BillionBeersTheme.brand.detailArtworkSize.height, 0.dp, fraction)),
          )
        }
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

private const val HERO_MAX_HEIGHT_FRACTION = 0.45f
private const val HERO_TITLE_MAX_LINES = 3

private const val HERO_SINGLE_LINE_COLLAPSE_FRACTION = 0.5f
