@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

/** Brand-owned control defaults; screens supply actions, state and layout modifiers. */
@Composable
fun BrandButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit,
) = Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)

@Composable
fun BrandTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit,
) = TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)

@Composable
fun BrandIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onHero: Boolean = false,
  content: @Composable () -> Unit,
) =
  IconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    colors =
      IconButtonDefaults.iconButtonColors(
        contentColor =
          if (onHero) BillionBeersTheme.brand.detailHeroForeground else LocalContentColor.current
      ),
    content = content,
  )

@Composable
fun BrandExtendedFloatingActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = BillionBeersTheme.colorScheme.primary,
  contentColor: Color = BillionBeersTheme.colorScheme.onPrimary,
  content: @Composable RowScope.() -> Unit,
) =
  ExtendedFloatingActionButton(
    onClick = onClick,
    modifier = modifier,
    containerColor = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(BillionBeersTheme.spacing.medium),
    content = content,
  )

@Composable
fun BrandFilterChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) = FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier)

@Composable
fun BrandDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) =
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    content = content,
  )

@Composable
fun BrandDropdownMenuItem(
  text: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  trailingIcon: (@Composable () -> Unit)? = null,
) =
  DropdownMenuItem(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    trailingIcon = trailingIcon,
  )
