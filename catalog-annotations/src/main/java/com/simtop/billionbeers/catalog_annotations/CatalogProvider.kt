package com.simtop.billionbeers.catalog_annotations

import androidx.compose.runtime.Composable

data class CatalogItem(
    val tab: String,
    val name: String,
    val content: @Composable () -> Unit
)

interface CatalogProvider {
    val items: List<CatalogItem>
}
