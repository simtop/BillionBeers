package com.simtop.billionbeers.core.designsystem.component

import android.content.res.Configuration
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
    device = "spec:width=411dp,height=891dp,navigationButtons=true,cutout=mask",
    showBackground = true
)
@Preview(
    name = "Foldable",
    group = "Devices",
    device = "spec:width=673dp,height=841dp,navigationButtons=true,cutout=mask",
    showBackground = true
)
@Preview(
    name = "Tablet",
    group = "Devices",
    device = "spec:width=1280dp,height=800dp,navigationButtons=true,cutout=mask",
    showBackground = true
)
annotation class PreviewDevices
