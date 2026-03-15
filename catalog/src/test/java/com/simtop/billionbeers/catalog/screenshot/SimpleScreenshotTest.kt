package com.simtop.billionbeers.catalog.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.Text
import org.junit.Rule
import org.junit.Test

class SimpleScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun simple_text_screenshot() {
        paparazzi.snapshot {
            Text("Hello Paparazzi")
        }
    }
}
