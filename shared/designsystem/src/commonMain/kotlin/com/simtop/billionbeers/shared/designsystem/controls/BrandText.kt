@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** Product text and icons inherit the active brand and the surrounding control's content color. */
@Composable
// Preserve the text/input behavior parameters used by product screens.
@Suppress("LongParameterList")
fun BrandText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  style: TextStyle = LocalTextStyle.current,
  textAlign: TextAlign? = null,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  softWrap: Boolean = true,
  overflow: TextOverflow = TextOverflow.Clip,
  onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) =
  Text(
    text = text,
    modifier = modifier,
    color = color,
    style = style,
    textAlign = textAlign,
    maxLines = maxLines,
    minLines = minLines,
    softWrap = softWrap,
    overflow = overflow,
    onTextLayout = onTextLayout,
  )

@Composable
fun BrandIcon(
  imageVector: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
) = Icon(imageVector, contentDescription, modifier, tint)
