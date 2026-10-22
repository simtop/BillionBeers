package com.simtop.billionbeers

import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.billionbeers.core.designsystem.R as BrandR
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersBrand
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersDarkColors
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersLightColors
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandResourceTest {
  @Test
  fun splashAndArtworkFollowThePortablePaletteInBothModes() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    for (dark in listOf(false, true)) {
      val configuration =
        Configuration(context.resources.configuration).apply {
          uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
      val themed = context.createConfigurationContext(configuration)
      val palette = if (dark) BillionBeersDarkColors else BillionBeersLightColors
      assertEquals(palette.background.toArgb(), themed.getColor(BrandR.color.brand_background))
      assertEquals(palette.primary.toArgb(), themed.getColor(BrandR.color.brand_primary))
      assertEquals(
        BillionBeersBrand.detailHeroAccent.toArgb(),
        themed.getColor(BrandR.color.brand_accent),
      )
      assertEquals(
        BillionBeersBrand.detailHeroForeground.toArgb(),
        themed.getColor(BrandR.color.brand_foam),
      )
      assertEquals(palette.background.toArgb(), themed.getColor(R.color.splash_background))
    }
  }
}
