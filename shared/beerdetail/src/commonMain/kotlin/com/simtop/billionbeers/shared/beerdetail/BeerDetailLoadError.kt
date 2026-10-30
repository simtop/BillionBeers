package com.simtop.billionbeers.shared.beerdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedBeerDetailLoadError(
  onBackClick: () -> Unit,
  backIcon: @Composable () -> Unit,
  errorContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = true,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      BrandTopAppBar(
        title = {},
        navigationIcon = {
          if (showBackButton) {
            BrandIconButton(onClick = onBackClick) { backIcon() }
          }
        },
      )
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
      errorContent()
    }
  }
}
