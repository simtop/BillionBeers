@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
fun BrandTopAppBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  scrollBehavior: TopAppBarScrollBehavior? = null,
) =
  TopAppBar(
    title = { ProvideTextStyle(BillionBeersTheme.brand.screenTitle, content = title) },
    modifier = modifier,
    navigationIcon = navigationIcon,
    actions = actions,
    scrollBehavior = scrollBehavior,
  )

@Composable
fun BrandNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
  NavigationBar(
    modifier = modifier,
    containerColor = BillionBeersTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    content = content,
  )

@Composable
fun RowScope.BrandNavigationBarItem(
  selected: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  label: (@Composable () -> Unit)? = null,
) =
  NavigationBarItem(
    selected = selected,
    onClick = onClick,
    icon = icon,
    modifier = modifier,
    label = label,
    colors =
      NavigationBarItemDefaults.colors(
        selectedIconColor = BillionBeersTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = BillionBeersTheme.colorScheme.onSurface,
        indicatorColor = BillionBeersTheme.colorScheme.primaryContainer,
        unselectedIconColor = BillionBeersTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = BillionBeersTheme.colorScheme.onSurfaceVariant,
      ),
  )

@Composable
fun BrandNavigationRail(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) =
  NavigationRail(
    modifier = modifier,
    containerColor = BillionBeersTheme.colorScheme.surface,
    content = content,
  )

@Composable
fun BrandNavigationRailItem(
  selected: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  label: (@Composable () -> Unit)? = null,
) =
  NavigationRailItem(
    selected = selected,
    onClick = onClick,
    icon = icon,
    modifier = modifier,
    label = label,
    alwaysShowLabel = true,
  )

@Composable
fun BrandTabRow(
  selectedTabIndex: Int,
  modifier: Modifier = Modifier,
  tabs: @Composable () -> Unit,
) = TabRow(selectedTabIndex = selectedTabIndex, modifier = modifier, tabs = tabs)

@Composable
fun BrandTab(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: (@Composable () -> Unit)? = null,
) = Tab(selected = selected, onClick = onClick, modifier = modifier, text = text)
