package com.simtop.presentation_utils.custom_views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.presentation.SharedTitle

@CatalogComponent(tab = "Utilities")
@Composable
fun ComposeTitle(name: String, modifier: Modifier = Modifier) {
  SharedTitle(name = name, modifier = modifier)
}

@PreviewLightDark
@Composable
internal fun ComposeTitlePreview() {
  BillionBeersTheme { ComposeTitle("Hello") }
}
