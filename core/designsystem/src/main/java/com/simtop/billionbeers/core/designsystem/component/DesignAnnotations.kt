package com.simtop.billionbeers.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices.PIXEL_7
import androidx.compose.ui.tooling.preview.Devices.PIXEL_FOLD
import androidx.compose.ui.tooling.preview.Devices.PIXEL_TABLET
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Light Mode",
    group = "Themes",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Preview(
    name = "Dark Mode",
    group = "Themes",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Retention(AnnotationRetention.BINARY)
annotation class PreviewLightDark

@Preview(
    name = "Small Font",
    group = "Font Scales",
    fontScale = 0.85f,
    showBackground = true
)
@Preview(
    name = "Normal Font",
    group = "Font Scales",
    fontScale = 1.0f,
    showBackground = true
)
@Preview(
    name = "Large Font",
    group = "Font Scales",
    fontScale = 1.5f,
    showBackground = true
)
@Preview(
    name = "Extra Large Font",
    group = "Font Scales",
    fontScale = 2.0f,
    showBackground = true
)
annotation class PreviewFontScales

@Preview(
    name = "Phone",
    group = "Devices",
    device = PIXEL_7,
    showBackground = true
)
@Preview(
    name = "Foldable",
    group = "Devices",
    device = PIXEL_FOLD,
    showBackground = true
)
@Preview(
    name = "Tablet",
    group = "Devices",
    device = PIXEL_TABLET,
    showBackground = true
)
annotation class PreviewDevices
