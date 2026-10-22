package com.simtop.billionbeers.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import billionbeers.web_app.generated.resources.Res
import billionbeers.web_app.generated.resources.web_missing_beer_message
import billionbeers.web_app.generated.resources.web_missing_beer_title
import billionbeers.web_app.generated.resources.web_open_catalog
import com.simtop.billionbeers.shared.designsystem.controls.BrandButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandSurface
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WebMissingBeer(onOpenCatalog: () -> Unit) {
  BillionBeersTheme(darkTheme = false) {
    BrandSurface(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.widthIn(max = 480.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      ) {
        BrandText(
          text = stringResource(Res.string.web_missing_beer_title),
          style = BillionBeersTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { heading() },
        )
        BrandText(
          text = stringResource(Res.string.web_missing_beer_message),
          style = BillionBeersTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
        BrandButton(onClick = onOpenCatalog) {
          BrandText(stringResource(Res.string.web_open_catalog))
        }
      }
    }
  }
}
