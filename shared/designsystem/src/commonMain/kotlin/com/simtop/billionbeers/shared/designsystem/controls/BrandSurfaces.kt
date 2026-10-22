@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
fun BrandCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  containerColor: Color = BillionBeersTheme.colorScheme.surface,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(BillionBeersTheme.spacing.medium)
  val colors = CardDefaults.cardColors(containerColor = containerColor)
  val elevation =
    CardDefaults.cardElevation(defaultElevation = BillionBeersTheme.brand.beerCardElevation)
  if (onClick == null)
    Card(
      modifier = modifier,
      shape = shape,
      colors = colors,
      elevation = elevation,
      content = content,
    )
  else
    Card(
      onClick = onClick,
      modifier = modifier,
      shape = shape,
      colors = colors,
      elevation = elevation,
      content = content,
    )
}

/** Flat container for a screen or feedback region. */
@Composable
fun BrandSurface(
  modifier: Modifier = Modifier,
  color: Color = BillionBeersTheme.colorScheme.surface,
  content: @Composable () -> Unit,
) = Surface(modifier = modifier, color = color, content = content)

/** Raised notice panel, including dynamic-feature installation failures. */
@Composable
fun BrandNotice(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
  Surface(
    modifier = modifier,
    color = BillionBeersTheme.colorScheme.surface,
    shape = RoundedCornerShape(BillionBeersTheme.spacing.small),
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
    content = content,
  )

@Composable
fun BrandAvailabilityBadge(text: String, available: Boolean, modifier: Modifier = Modifier) {
  Surface(
    modifier,
    color =
      if (available) BillionBeersTheme.colors.availableContainer
      else BillionBeersTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(BillionBeersTheme.spacing.small),
  ) {
    BrandText(
      text,
      modifier =
        Modifier.padding(
          horizontal = BillionBeersTheme.spacing.small,
          vertical = BillionBeersTheme.spacing.extraSmall,
        ),
      color =
        if (available) BillionBeersTheme.colors.onAvailableContainer
        else BillionBeersTheme.colorScheme.onSurfaceVariant,
      style = BillionBeersTheme.brand.badgeLabel,
    )
  }
}

@Composable
fun BrandMetricCard(
  label: String,
  value: String,
  color: Color,
  textColor: Color,
  modifier: Modifier = Modifier,
) {
  BrandCard(modifier = modifier.fillMaxWidth(), containerColor = color) {
    Column(
      Modifier.padding(BillionBeersTheme.spacing.small),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BrandText(
        value,
        style = BillionBeersTheme.brand.sectionTitle,
        color = textColor,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
      )
      BrandText(label, style = BillionBeersTheme.brand.metricLabel, color = textColor)
    }
  }
}

@Composable
fun BrandListItem(
  headlineContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  supportingContent: (@Composable () -> Unit)? = null,
) =
  ListItem(
    headlineContent = headlineContent,
    modifier = modifier,
    supportingContent = supportingContent,
  )

@Composable fun BrandHorizontalDivider() = HorizontalDivider()

@Composable
fun BrandCircularProgressIndicator(
  modifier: Modifier = Modifier,
  strokeWidth: androidx.compose.ui.unit.Dp = 4.dp,
) = CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)

@Composable
fun BrandSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) =
  SnackbarHost(hostState, modifier)

@Composable
fun BrandPullToRefreshBox(
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) =
  androidx.compose.material3.pulltorefresh.PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier,
    content = content,
  )
