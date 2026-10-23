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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.designsystem.components.SharedScrollContainer
import com.simtop.billionbeers.shared.designsystem.controls.BrandExtendedFloatingActionButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandMetricCard
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
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
  favoriteHint: @Composable () -> Unit = {},
) {
  val favoriteLabel = if (beer.isFavorite) strings.removeFromFavorites else strings.addToFavorites
  val headerTitleStyle = titleTextStyle ?: BillionBeersTheme.brand.detailTitle
  val headerButtonModifier = Modifier.size(48.dp)
  val favoriteButton: @Composable () -> Unit = {
    BrandIconButton(
      onClick = onToggleFavorite,
      onHero = true,
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
      BrandIconButton(
        onClick = onBackClick,
        modifier = headerButtonModifier,
        onHero = true,
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
        favoriteHint()
        BrandText(
          text = beer.tagline,
          style = BillionBeersTheme.typography.titleMedium,
          color = BillionBeersTheme.colorScheme.onSurfaceVariant,
        )

        if (beer.styleName.isNotEmpty() && beer.breweryName.isNotEmpty()) {
          BrandText(
            text = strings.styleAndBrewery(beer.styleName, beer.breweryName),
            style = BillionBeersTheme.typography.bodyMedium,
            color = BillionBeersTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val stackMetricCards =
            maxWidth < 500.dp && LocalDensity.current.fontScale >= LARGE_FONT_METRIC_CARD_SCALE
          if (stackMetricCards) {
            Column(verticalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.medium)) {
              BrandMetricCard(
                label = strings.abv,
                value = "${strings.metricValue(beer.abv)}%",
                color = BillionBeersTheme.colors.abvContainer,
                textColor = BillionBeersTheme.colors.onAbvContainer,
                modifier = Modifier.fillMaxWidth(),
              )
              BrandMetricCard(
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
              BrandMetricCard(
                label = strings.abv,
                value = "${strings.metricValue(beer.abv)}%",
                color = BillionBeersTheme.colors.abvContainer,
                textColor = BillionBeersTheme.colors.onAbvContainer,
                modifier = Modifier.weight(1f),
              )
              BrandMetricCard(
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
        BrandText(
          text = strings.description,
          style = BillionBeersTheme.brand.sectionTitle,
        )
        Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))
        BrandText(
          text = beer.description,
          style = BillionBeersTheme.brand.description,
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
          BrandText(
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
  BrandExtendedFloatingActionButton(
    onClick = onToggle,
    containerColor =
      if (available) BillionBeersTheme.colorScheme.primary else BillionBeersTheme.colorScheme.error,
    contentColor =
      if (available) BillionBeersTheme.colorScheme.onPrimary
      else BillionBeersTheme.colorScheme.onError,
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
      BrandText(
        text = if (isAvailable) strings.markAsEmpty else strings.refillBarrels,
        style = BillionBeersTheme.brand.actionLabel,
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
    BrandText(
      text = strings.details,
      style = BillionBeersTheme.brand.sectionTitle,
    )
    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.small))
    detailRows.forEach { (label, value) ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BillionBeersTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        BrandText(
          text = label,
          style = BillionBeersTheme.typography.bodyLarge,
          color = BillionBeersTheme.colorScheme.onSurfaceVariant,
        )
        BrandText(text = value, style = BillionBeersTheme.typography.bodyLarge)
      }
    }
  }
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
  if (items.isEmpty()) return
  Column {
    BrandText(
      text = title,
      style = BillionBeersTheme.brand.sectionTitle,
    )
    Spacer(
      modifier =
        Modifier.height(BillionBeersTheme.spacing.medium - BillionBeersTheme.spacing.extraSmall)
    )
    items.forEach { item ->
      Row(modifier = Modifier.padding(vertical = BillionBeersTheme.spacing.extraSmall)) {
        BrandText(
          text = "•",
          style = BillionBeersTheme.brand.bodyStrong,
          modifier = Modifier.padding(end = BillionBeersTheme.spacing.small),
        )
        BrandText(text = item, style = BillionBeersTheme.typography.bodyLarge)
      }
    }
  }
}

private const val AVAILABILITY_ANIMATION_DURATION_MS = 300
private const val LARGE_FONT_METRIC_CARD_SCALE = 2f

private const val HERO_MAX_HEIGHT_FRACTION = 0.45f
private const val HERO_TITLE_MAX_LINES = 3

private const val HERO_SINGLE_LINE_COLLAPSE_FRACTION = 0.5f
