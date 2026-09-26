package com.simtop.billionbeers.shared.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class SharedAppWidthClass {
  Compact,
  Medium,
  Expanded,
}

internal fun classifySharedAppWidth(width: Dp): SharedAppWidthClass =
  when {
    width < 600.dp -> SharedAppWidthClass.Compact
    width < 840.dp -> SharedAppWidthClass.Medium
    else -> SharedAppWidthClass.Expanded
  }

internal fun shouldShowExpandedCatalogDetail(
  width: Dp,
  entries: List<SharedAppEntry>,
): Boolean =
  classifySharedAppWidth(width) == SharedAppWidthClass.Expanded &&
    entries.firstOrNull() is ListEntry &&
    entries.lastOrNull() is DetailEntry &&
    entries.drop(1).all { it is DetailEntry }
