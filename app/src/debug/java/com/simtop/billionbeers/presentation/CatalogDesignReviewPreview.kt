package com.simtop.billionbeers.presentation

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel
import com.simtop.feature.beerslist.BeersListContent
import com.simtop.navigation.contract.PortableRoute

/** Full compact home composition with synthetic, offline data and loaded-image geometry. */
@Preview(name = "Catalog Light", group = "Design Review", showBackground = true)
@Preview(
  name = "Catalog Dark",
  group = "Design Review",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
)
@Preview(
  name = "Catalog Large Text",
  group = "Design Review",
  fontScale = 1.5f,
  showBackground = true,
)
@Composable
internal fun CatalogDesignReviewPreview() {
  CatalogReviewFrame(CommonUiState.Success(PagedListUiModel(items = reviewBeers)))
}

@Preview(name = "Catalog Long Content", group = "Design Review", showBackground = true)
@Composable
internal fun CatalogLongContentDesignReviewPreview() {
  val longContent = reviewBeers.map {
    it.copy(
      name = "A Very Long Seasonal Beer Name",
      tagline =
        "A longer description checks wrapping and the information available before opening detail.",
    )
  }
  CatalogReviewFrame(CommonUiState.Success(PagedListUiModel(items = longContent)))
}

@Preview(name = "Catalog Loading", group = "Design Review", showBackground = true)
@Composable
internal fun CatalogLoadingDesignReviewPreview() {
  CatalogReviewFrame(CommonUiState.Loading)
}

@Preview(name = "Catalog Error", group = "Design Review", showBackground = true)
@Composable
internal fun CatalogErrorDesignReviewPreview() {
  CatalogReviewFrame(CommonUiState.Error(message = "Unable to load the catalog. Please try again."))
}

@Preview(name = "Catalog Empty", group = "Design Review", showBackground = true)
@Composable
internal fun CatalogEmptyDesignReviewPreview() {
  CatalogReviewFrame(CommonUiState.Empty)
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun CatalogReviewFrame(state: CommonUiState<PagedListUiModel<Beer>>) {
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
      Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
          AppBottomNavigation(selectedTab = PortableRoute.BeersList, onTabSelect = {})
        },
      ) { padding ->
        BeersListContent(
          viewState = state,
          onBeerClick = {},
          onSearchClick = {},
          onBrowseClick = {},
          onSaveQuery = { false },
          onScrollToBottom = {},
          onRefresh = {},
          onRetry = {},
          onRetryLoadMore = {},
          modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
      }
    }
  }
}

// Synthetic preview content: stable names, numbers and local availability, not live API records.
private val reviewBeers =
  listOf(
    Beer.empty.copy(
      id = "review-buzz",
      name = "Buzz",
      tagline = "A Real Bitter Experience.",
      abv = 4.5,
      ibu = 60.0,
      availability = true,
      imageUrl = "preview:amber",
    ),
    Beer.empty.copy(
      id = "review-blonde",
      name = "Trashy Blonde",
      tagline = "You Know You Shouldn't",
      abv = 4.1,
      ibu = 41.5,
      availability = false,
      imageUrl = "preview:amber",
    ),
    Beer.empty.copy(
      id = "review-pilsner",
      name = "Avery Brown Dredge",
      tagline = "Bloggers Imperial Pilsner.",
      abv = 7.2,
      ibu = 59.0,
      availability = true,
      imageUrl = "preview:dark",
    ),
    Beer.empty.copy(
      id = "review-pale",
      name = "North Coast Pale",
      tagline = "Bright hops, crisp finish.",
      abv = 5.2,
      ibu = 38.0,
      availability = false,
      imageUrl = "preview:amber",
    ),
    Beer.empty.copy(
      id = "review-stout",
      name = "Evening Stout",
      tagline = "Roasted malt and a smooth finish.",
      abv = 6.0,
      ibu = 32.0,
      availability = true,
      imageUrl = "preview:dark",
    ),
    Beer.empty.copy(
      id = "review-amber",
      name = "Autumn Amber",
      tagline = "A balanced seasonal ale.",
      abv = 4.8,
      ibu = 28.0,
      availability = true,
      imageUrl = "preview:amber",
    ),
  )
