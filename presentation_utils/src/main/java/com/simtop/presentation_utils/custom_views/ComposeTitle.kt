package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ComposeTitle(name: String) {
  Text(
    text = name,
    modifier =
      Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 24.dp).fillMaxWidth(),
    fontWeight = FontWeight.ExtraBold,
    textAlign = TextAlign.Center,
    fontSize = 24.sp
  )
}

@Preview
@Composable
fun ComposeTitlePreview() {
  ComposeTitle("Hello")
}
