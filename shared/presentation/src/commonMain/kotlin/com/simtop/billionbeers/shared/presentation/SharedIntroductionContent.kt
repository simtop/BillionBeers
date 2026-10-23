package com.simtop.billionbeers.shared.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.controls.BrandButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandSurface
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

private const val INTRODUCTION_ENTRANCE_DURATION_MILLIS = 250
private const val INTRODUCTION_ILLUSTRATION_ASPECT_RATIO = 1.4f
private const val INTRODUCTION_LARGE_FONT_SCALE = 1.5f

data class IntroductionStrings(
  val eyebrow: String,
  val headline: String,
  val body: String,
  val action: String,
  val footnote: String,
)

/** Reusable introduction layout. The host supplies copy, artwork, navigation and completion. */
@Composable
fun SharedIntroductionContent(
  strings: IntroductionStrings,
  onContinue: () -> Unit,
  illustration: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier,
  animateEntrance: Boolean = false,
) {
  var appeared by remember { mutableStateOf(!animateEntrance) }
  LaunchedEffect(Unit) { appeared = true }
  val entrance by
    animateFloatAsState(
      targetValue = if (appeared) 1f else 0f,
      animationSpec = tween(INTRODUCTION_ENTRANCE_DURATION_MILLIS),
      label = "Introduction entrance",
    )
  BrandSurface(modifier.fillMaxSize(), color = BillionBeersTheme.colorScheme.background) {
    BoxWithConstraints(
      Modifier.fillMaxSize().safeDrawingPadding(),
      contentAlignment = Alignment.Center,
    ) {
      val compactArtwork =
        maxHeight < 520.dp || LocalDensity.current.fontScale >= INTRODUCTION_LARGE_FONT_SCALE
      Column(
        modifier =
          Modifier.widthIn(max = 480.dp)
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier =
            Modifier.weight(1f).verticalScroll(rememberScrollState()).graphicsLayer {
              alpha = entrance
              translationY = (1f - entrance) * 12.dp.toPx()
            },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          BrandText(
            strings.eyebrow,
            style = BillionBeersTheme.brand.actionLabel,
            color = BillionBeersTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(if (compactArtwork) 8.dp else 24.dp))
          illustration(
            Modifier.heightIn(max = if (compactArtwork) 140.dp else 280.dp)
              .aspectRatio(INTRODUCTION_ILLUSTRATION_ASPECT_RATIO)
          )
          Spacer(Modifier.height(24.dp))
          BrandText(
            strings.headline,
            style = BillionBeersTheme.brand.introductionTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
          )
          Spacer(Modifier.height(16.dp))
          BrandText(
            strings.body,
            style = BillionBeersTheme.typography.bodyLarge,
            color = BillionBeersTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        BrandButton(
          onClick = onContinue,
          modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("introduction_continue"),
        ) {
          BrandText(
            strings.action,
            style = BillionBeersTheme.brand.actionLabel,
            textAlign = TextAlign.Center,
          )
        }
        Spacer(Modifier.height(12.dp))
        BrandText(
          strings.footnote,
          style = BillionBeersTheme.typography.bodySmall,
          color = BillionBeersTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
