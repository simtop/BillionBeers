package com.simtop.billionbeers.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark

@CatalogComponent(tab = "Design System", name = "Colors")
@Composable
fun ColorCatalogDemo(modifier: Modifier = Modifier) {
  val colorRoles =
    listOf(
      "Primary" to BillionBeersTheme.colors.primary,
      "OnPrimary" to BillionBeersTheme.colors.onPrimary,
      "PrimaryContainer" to BillionBeersTheme.colors.primaryContainer,
      "OnPrimaryContainer" to BillionBeersTheme.colors.onPrimaryContainer,
      "Secondary" to BillionBeersTheme.colors.secondary,
      "OnSecondary" to BillionBeersTheme.colors.onSecondary,
      "Background" to BillionBeersTheme.colors.background,
      "OnBackground" to BillionBeersTheme.colors.onBackground,
      "Surface" to BillionBeersTheme.colors.surface,
      "OnSurface" to BillionBeersTheme.colors.onSurface,
      "Error" to BillionBeersTheme.colors.error,
      "OnError" to BillionBeersTheme.colors.onError,
      "AbvContainer" to BillionBeersTheme.colors.abvContainer,
      "OnAbvContainer" to BillionBeersTheme.colors.onAbvContainer,
      "IbuContainer" to BillionBeersTheme.colors.ibuContainer,
      "OnIbuContainer" to BillionBeersTheme.colors.onIbuContainer,
    )

  LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
    items(colorRoles) { (name, color) -> ColorRow(name, color) }
  }
}

@Composable
private fun ColorRow(name: String, color: Color) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column {
      Text(text = name, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = "#${Integer.toHexString(color.toArgb()).uppercase()}",
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Box(modifier = Modifier.size(40.dp).background(color))
  }
}

/** Catalog-only paired metric colors, including the small-label contrast contract. */
@CatalogComponent(tab = "Design System", name = "Metric colors")
@Composable
fun MetricColorCatalogDemo(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(BillionBeersTheme.spacing.medium),
    verticalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.medium),
  ) {
    val metrics =
      listOf(
        Triple(
          "ABV",
          BillionBeersTheme.colors.abvContainer,
          BillionBeersTheme.colors.onAbvContainer,
        ),
        Triple(
          "IBU",
          BillionBeersTheme.colors.ibuContainer,
          BillionBeersTheme.colors.onIbuContainer,
        ),
      )
    metrics.forEach { (label, container, foreground) ->
      androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = foreground,
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(BillionBeersTheme.spacing.medium)) {
          Text(
            text = if (label == "ABV") "5.6%" else "41.5",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@PreviewLightDark
@Composable
internal fun MetricColorsPreview() {
  BillionBeersTheme { MetricColorCatalogDemo() }
}

@CatalogComponent(tab = "Design System", name = "Typography")
@Composable
fun TypographyCatalogDemo(modifier: Modifier = Modifier) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(androidx.compose.foundation.rememberScrollState())
  ) {
    Text(text = "Headline Large", style = BillionBeersTheme.typography.headlineLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Headline Medium", style = BillionBeersTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Title Large", style = BillionBeersTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Body Large", style = BillionBeersTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Body Medium", style = BillionBeersTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Label Medium", style = BillionBeersTheme.typography.labelMedium)
  }
}
