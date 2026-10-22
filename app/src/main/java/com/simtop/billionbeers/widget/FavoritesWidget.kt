package com.simtop.billionbeers.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.simtop.billionbeers.core.designsystem.widget.BrandWidgetAvailabilityBadge
import com.simtop.billionbeers.core.designsystem.widget.BrandWidgetContainer
import com.simtop.billionbeers.core.designsystem.widget.BrandWidgetImage
import com.simtop.billionbeers.core.designsystem.widget.BrandWidgetText
import com.simtop.billionbeers.core.designsystem.widget.BrandWidgetTextRole
import com.simtop.presentation_utils.R
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

private const val WIDGET_IMAGE_SIZE_DP = 44
private const val WIDGET_IMAGE_TIMEOUT_MS = 1_500L

internal data class FavoritesWidgetDisplayItem(
  val item: FavoritesWidgetItem,
  val imageProvider: ImageProvider,
  val imageDescription: String,
  val availabilityLabel: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId): Nothing = coroutineScope {
    val imageLoader = SingletonImageLoader.get(context)
    val imageSizePx = (WIDGET_IMAGE_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
    val displayItems =
      context
        .favoritesWidgetRepository()
        .observeFavoritesWidgetItems()
        .mapLatest { favorites ->
          coroutineScope {
            favorites
              .map { item ->
                async {
                  FavoritesWidgetDisplayItem(
                    item = item,
                    imageProvider =
                      imageLoader.loadWidgetImage(context, item.imageUrl, imageSizePx),
                    imageDescription =
                      context.getString(R.string.beer_list_item_image_description, item.name),
                    availabilityLabel =
                      context.getString(
                        if (item.availability) {
                          R.string.beer_available
                        } else {
                          R.string.beer_out_of_stock
                        }
                      ),
                  )
                }
              }
              .awaitAll()
          }
        }
        .stateIn(this)

    provideContent {
      val favorites by displayItems.collectAsState()
      FavoritesWidgetContent(
        favorites = favorites,
        title = context.getString(R.string.favorites_title),
        emptyMessage = context.getString(R.string.favorites_empty),
      )
    }
  }
}

@Composable
internal fun FavoritesWidgetContent(
  favorites: List<FavoritesWidgetDisplayItem>,
  title: String,
  emptyMessage: String,
) {
  BrandWidgetContainer {
    Row(
      modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<OpenFavoritesAction>()),
      verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
      BrandWidgetText(text = title, role = BrandWidgetTextRole.Title)
    }
    Spacer(GlanceModifier.height(8.dp))
    if (favorites.isEmpty()) {
      BrandWidgetText(
        modifier = GlanceModifier.clickable(actionRunCallback<OpenFavoritesAction>()),
        text = emptyMessage,
        role = BrandWidgetTextRole.Body,
      )
    } else {
      favorites.forEach { favorite ->
        Row(
          modifier =
            GlanceModifier.fillMaxWidth()
              .padding(vertical = 6.dp)
              .clickable(
                actionRunCallback<OpenFavoriteBeerAction>(
                  actionParametersOf(OpenFavoriteBeerAction.BEER_ID to favorite.item.id)
                )
              ),
          verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
          BrandWidgetImage(
            provider = favorite.imageProvider,
            contentDescription = favorite.imageDescription,
          )
          Spacer(GlanceModifier.size(10.dp))
          Column {
            BrandWidgetText(text = favorite.item.name, role = BrandWidgetTextRole.BeerTitle)
            BrandWidgetAvailabilityBadge(
              text = favorite.availabilityLabel,
              available = favorite.item.availability,
            )
          }
        }
      }
    }
  }
}

private suspend fun ImageLoader.loadWidgetImage(
  context: Context,
  imageUrl: String,
  imageSizePx: Int,
): ImageProvider {
  if (imageUrl.isBlank()) return ImageProvider(R.drawable.blue_image)

  return try {
    val result =
      withTimeoutOrNull(WIDGET_IMAGE_TIMEOUT_MS) {
        execute(ImageRequest.Builder(context).data(imageUrl).size(imageSizePx, imageSizePx).build())
      }
    val bitmap = (result?.image as? BitmapImage)?.bitmap
    if (bitmap != null) ImageProvider(bitmap) else ImageProvider(R.drawable.blue_image)
  } catch (exception: CancellationException) {
    throw exception
  } catch (_: Exception) {
    ImageProvider(R.drawable.blue_image)
  }
}
