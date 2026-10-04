package com.simtop.billionbeers.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WebMissingBeer(onOpenCatalog: () -> Unit) {
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.widthIn(max = 480.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      ) {
        Text(
          text = stringResource(Res.string.web_missing_beer_title),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          text = stringResource(Res.string.web_missing_beer_message),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenCatalog) { Text(stringResource(Res.string.web_open_catalog)) }
      }
    }
  }
}
