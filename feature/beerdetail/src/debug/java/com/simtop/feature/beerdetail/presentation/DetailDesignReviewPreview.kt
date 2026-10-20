package com.simtop.feature.beerdetail.presentation

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.decode.DataSource
import coil3.request.SuccessResult
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme

/** Full real detail composition with offline synthetic artwork and fixed enriched data. */
@Preview(name = "Detail Light", group = "Design Review", showBackground = true)
@Preview(
  name = "Detail Dark",
  group = "Design Review",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
)
@Preview(
  name = "Detail Large Text",
  group = "Design Review",
  fontScale = 1.5f,
  showBackground = true,
)
@Composable
internal fun DetailDesignReviewPreview() {
  DetailReviewFrame(detailReviewBeer)
}

@Preview(name = "Detail Long Content", group = "Design Review", showBackground = true)
@Composable
internal fun DetailLongContentDesignReviewPreview() {
  DetailReviewFrame(
    detailReviewBeer.copy(
      name = "A Very Long Seasonal Beer Name",
      breweryName = "A Brewery With A Deliberately Long Name",
    )
  )
}

@Preview(name = "Detail Sparse", group = "Design Review", showBackground = true)
@Composable
internal fun DetailSparseDesignReviewPreview() {
  DetailReviewFrame(
    Beer.empty.copy(
      name = "Buzz",
      tagline = "A Real Bitter Experience.",
      imageUrl = "preview:amber",
      abv = 4.5,
      ibu = 60.0,
    )
  )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun DetailReviewFrame(beer: Beer) {
  val imageHandler = remember {
    // Override the suite's loading handler only here. No network or third-party artwork is used.
    AsyncImagePreviewHandler { _, request ->
      val tint =
        if (request.data.toString().endsWith("dark")) 0xFF53413A.toInt() else 0xFFAD6B22.toInt()
      val bitmap = createBitmap(72, 128)
      val canvas = Canvas(bitmap)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG)
      paint.color = tint
      canvas.drawRoundRect(16f, 32f, 56f, 122f, 7f, 7f, paint)
      canvas.drawRect(27f, 8f, 45f, 40f, paint)
      paint.color = 0xFF292929.toInt()
      canvas.drawRect(25f, 6f, 47f, 13f, paint)
      paint.color = 0xFFF5E8CD.toInt()
      canvas.drawRect(18f, 62f, 54f, 102f, paint)
      // A cached result bypasses Coil's crossfade so a captured frame has fixed opacity.
      AsyncImagePainter.State.Success(
        painter = BitmapPainter(bitmap.asImageBitmap()),
        result = SuccessResult(bitmap.asImage(), request, dataSource = DataSource.MEMORY_CACHE),
      )
    }
  }
  CompositionLocalProvider(
    LocalInspectionMode provides true,
    LocalAsyncImagePreviewHandler provides imageHandler,
  ) {
    BillionBeersTheme {
      ComposeBeerDetail(
        beer = beer,
        onBackClick = {},
        onToggleAvailability = {},
        onToggleFavorite = {},
      )
    }
  }
}

private val detailReviewBeer =
  Beer.empty.copy(
    id = "review-buzz",
    name = "Buzz",
    tagline = "A Real Bitter Experience.",
    imageUrl = "preview:amber",
    availability = true,
    description =
      "A light, crisp and bitter IPA brewed with English and American hops. A small batch brewed only once.",
    abv = 4.5,
    ibu = 60.0,
    styleName = "IPA (Indian Pale Ale)",
    breweryName = "ChuckleCraft Brewery",
    foodPairing =
      listOf("Spicy chicken tikka masala", "Grilled chicken quesadilla", "Pastrami on rye"),
    srm = 9,
    releasedYear = 1980,
    minServingTemperature = 4,
    maxServingTemperature = 8,
    fermentationMethod = "Ale",
    ingredients = listOf("Pale malt", "Cascade hops", "American ale yeast"),
    recommendedGlasses = listOf("Pint glass", "Tulip"),
  )
