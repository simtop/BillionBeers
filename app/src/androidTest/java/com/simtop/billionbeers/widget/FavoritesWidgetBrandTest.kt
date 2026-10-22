package com.simtop.billionbeers.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ImageProvider
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersDarkColors
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersLightColors
import com.simtop.presentation_utils.R
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class FavoritesWidgetBrandTest {
  @Test
  fun widgetRendersBrandedTextAndBothStatusesInLightAndDark() = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    for (dark in listOf(false, true)) {
      val configuration =
        Configuration(context.resources.configuration).apply {
          uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
      val themed = context.createConfigurationContext(configuration)
      val palette = if (dark) BillionBeersDarkColors else BillionBeersLightColors
      val items =
        listOf(true, false).map { available ->
          FavoritesWidgetDisplayItem(
            FavoritesWidgetItem(
              "$available",
              if (available) "Punk IPA" else "Long beer title for the second favorite",
              "",
              available,
            ),
            ImageProvider(R.drawable.blue_image),
            "Beer",
            if (available) "Available" else "Out of stock",
          )
        }
      val result =
        GlanceRemoteViews().compose(themed, DpSize(320.dp, 240.dp)) {
          FavoritesWidgetContent(items, "Favorites", "No favorites")
        }
      instrumentation.runOnMainSync {
        val view = result.remoteViews.apply(themed, null)
        val texts = textViews(view).associateBy { it.text.toString() }
        assertEquals(palette.primary.toArgb(), texts.getValue("Favorites").currentTextColor)
        val beerTitle = texts.getValue("Punk IPA").text as Spanned
        val titlePaint = TextPaint(texts.getValue("Punk IPA").paint)
        beerTitle.getSpans(0, beerTitle.length, MetricAffectingSpan::class.java).forEach {
          it.updateMeasureState(titlePaint)
        }
        assertTrue(
          "Beer title draws bold after Glance applies its text spans",
          titlePaint.typeface.weight >= 700 || titlePaint.isFakeBoldText,
        )
        assertEquals(
          palette.onAvailableContainer.toArgb(),
          texts.getValue("Available").currentTextColor,
        )
        assertEquals(
          palette.material.onSurfaceVariant.toArgb(),
          texts.getValue("Out of stock").currentTextColor,
        )
        capture(view, themed, dark)
      }
    }
  }

  private fun capture(view: View, context: Context, dark: Boolean) {
    val width = (320 * context.resources.displayMetrics.density).toInt()
    val height = (240 * context.resources.displayMetrics.density).toInt()
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    val file =
      File(
        context.getExternalFilesDir(null),
        "brand-widget-${if (dark) "dark" else "light"}.png",
      )
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun textViews(view: View): List<TextView> =
    when (view) {
      is TextView -> listOf(view)
      is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
      else -> emptyList()
    }
}
