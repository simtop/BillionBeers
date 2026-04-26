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

@CatalogComponent(tab = "Design System", name = "Colors")
@Composable
fun ColorCatalogDemo() {
    val colorRoles = listOf(
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
        "OnError" to BillionBeersTheme.colors.onError
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(colorRoles) { (name, color) ->
            ColorRow(name, color)
        }
    }
}

@Composable
private fun ColorRow(name: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(text = "#${Integer.toHexString(color.toArgb()).uppercase()}", style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color)
        )
    }
}

@CatalogComponent(tab = "Design System", name = "Typography")
@Composable
fun TypographyCatalogDemo() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
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
