@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
// Preserve the text/input behavior parameters used by product screens.
@Suppress("LongParameterList")
fun BrandOutlinedTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  label: (@Composable () -> Unit)? = null,
  placeholder: (@Composable () -> Unit)? = null,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  singleLine: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
) =
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    label = label,
    placeholder = placeholder,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    singleLine = singleLine,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
  )

/** Inline header search deliberately has no filled container or underline. */
@Composable
fun BrandSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: (@Composable () -> Unit)? = null,
  singleLine: Boolean = true,
) =
  TextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    placeholder = placeholder,
    singleLine = singleLine,
    colors =
      TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
      ),
  )
