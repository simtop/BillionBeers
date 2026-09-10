package com.simtop.billionbeers.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
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

private const val WIDGET_BACKGROUND_COLOR = 0xFFF1F0F4
private const val WIDGET_ON_BACKGROUND_COLOR = 0xFF1B1B1F
private const val WIDGET_PRIMARY_COLOR = 0xFF0047AB
private const val AVAILABLE_BG_COLOR = 0xFFE8F5E9
private const val AVAILABLE_TEXT_COLOR = 0xFF1B5E20
private const val UNAVAILABLE_BG_COLOR = 0xFFFFEBEE
private const val UNAVAILABLE_TEXT_COLOR = 0xFFB71C1C
private const val WIDGET_IMAGE_SIZE_DP = 44
private const val WIDGET_IMAGE_TIMEOUT_MS = 1_500L

private val WidgetBackground = ColorProvider(Color(WIDGET_BACKGROUND_COLOR))
private val WidgetOnBackground = ColorProvider(Color(WIDGET_ON_BACKGROUND_COLOR))
private val WidgetPrimary = ColorProvider(Color(WIDGET_PRIMARY_COLOR))
private val AvailableBackground = ColorProvider(Color(AVAILABLE_BG_COLOR))
private val AvailableText = ColorProvider(Color(AVAILABLE_TEXT_COLOR))
private val UnavailableBackground = ColorProvider(Color(UNAVAILABLE_BG_COLOR))
private val UnavailableText = ColorProvider(Color(UNAVAILABLE_TEXT_COLOR))

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
  Column(
    modifier =
      GlanceModifier.fillMaxWidth().background(WidgetBackground).cornerRadius(24.dp).padding(16.dp),
    verticalAlignment = Alignment.Vertical.Top,
  ) {
    Row(
      modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<OpenFavoritesAction>()),
      verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
      Text(text = title, style = TextStyle(color = WidgetPrimary, fontSize = 16.sp))
    }
    Spacer(GlanceModifier.height(8.dp))
    if (favorites.isEmpty()) {
      Text(
        modifier = GlanceModifier.clickable(actionRunCallback<OpenFavoritesAction>()),
        text = emptyMessage,
        style = TextStyle(color = WidgetOnBackground, fontSize = 14.sp),
      )
    } else {
      favorites.forEach { favorite ->
        val statusBackground =
          if (favorite.item.availability) AvailableBackground else UnavailableBackground
        val statusText = if (favorite.item.availability) AvailableText else UnavailableText
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
          Image(
            provider = favorite.imageProvider,
            contentDescription = favorite.imageDescription,
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.size(44.dp).cornerRadius(10.dp),
          )
          Spacer(GlanceModifier.size(10.dp))
          Column {
            Text(
              text = favorite.item.name,
              style = TextStyle(color = WidgetOnBackground, fontSize = 15.sp),
            )
            Text(
              text = favorite.availabilityLabel,
              modifier =
                GlanceModifier.padding(top = 3.dp)
                  .background(statusBackground)
                  .cornerRadius(8.dp)
                  .padding(horizontal = 6.dp, vertical = 2.dp),
              style = TextStyle(color = statusText, fontSize = 11.sp),
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
