package com.simtop.billionbeers.shared.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.shared.designsystem.controls.BrandBackIcon

@Composable
fun SharedBackIcon(contentDescription: String, modifier: Modifier = Modifier) =
  BrandBackIcon(contentDescription, modifier)
