package com.simtop.billionbeers.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices.PIXEL_7
import androidx.compose.ui.tooling.preview.Devices.PIXEL_FOLD
import androidx.compose.ui.tooling.preview.Devices.PIXEL_TABLET
import androidx.compose.ui.tooling.preview.Preview

/** Generates light and dark previews for a composable. */
@Preview(
  name = "Light Mode",
  group = "Themes",
  uiMode = Configuration.UI_MODE_NIGHT_NO,
  showBackground = true,
)
@Preview(
  name = "Dark Mode",
  group = "Themes",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PreviewLightDark

/** Generates the standard font-scale previews for a composable. */
@Preview(name = "Small Font", group = "Font Scales", fontScale = 0.85f, showBackground = true)
@Preview(name = "Normal Font", group = "Font Scales", fontScale = 1.0f, showBackground = true)
@Preview(name = "Large Font", group = "Font Scales", fontScale = 1.5f, showBackground = true)
@Preview(name = "Extra Large Font", group = "Font Scales", fontScale = 2.0f, showBackground = true)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PreviewFontScales

/** Generates representative phone, foldable, and tablet previews for a composable. */
@Preview(name = "Phone", group = "Devices", device = PIXEL_7, showBackground = true)
@Preview(name = "Foldable", group = "Devices", device = PIXEL_FOLD, showBackground = true)
@Preview(name = "Tablet", group = "Devices", device = PIXEL_TABLET, showBackground = true)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PreviewDevices

/**
 * Marks a representative screen or component for the screenshot accessibility matrix.
 *
 * The screenshot convention expands this marker into the canonical light/dark, font, locale, and
 * width variants. Keep it on a small number of representative previews; ordinary previews retain
 * their existing one-case behavior.
 */
@Preview(name = "Accessibility Matrix", group = "Accessibility", showBackground = true)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Suppress("PreviewAnnotationNaming")
annotation class AccessibilityMatrixPreview
