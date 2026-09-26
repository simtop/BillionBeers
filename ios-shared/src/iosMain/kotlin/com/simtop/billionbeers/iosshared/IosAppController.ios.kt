package com.simtop.billionbeers.iosshared

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.app.SharedAppHost
import com.simtop.billionbeers.shared.app.SharedAppShell
import com.simtop.navigation.contract.PortableRoute
import com.simtop.core.core.CommonUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import platform.UIKit.UIViewController

private const val DEFAULT_IOS_API_BASE_URL = "https://brewbuddy.dev/"

/** Owns one iOS data graph and its Compose controller for the lifetime of a host scene. */
public class IosAppSession(
  languageCode: String = "en",
) {
  private val runtime =
    IosDataRuntime.open(
      IosDataConfig(apiBaseUrl = DEFAULT_IOS_API_BASE_URL, languageCode = languageCode),
    )
  private val strings = IosLocalizedStrings.forLanguage(languageCode)
  private val routeRequests = IosRouteRequestBuffer()
  private val scope = CoroutineScope(SupervisorJob() + runtime.coroutineDispatcherProvider.io)
  private var closed = false

  public val viewController: UIViewController =
    ComposeUIViewController {
      IosShell(runtime, strings, routeRequests.routes)
    }

  /** Delivers a supported URL without exposing repositories or navigation internals to Swift. */
  public fun handleDeepLink(url: String) {
    if (closed) return
    scope.launch {
      resolveIosDeepLink(url, runtime.repository)?.let { route ->
        if (!closed) routeRequests.trySend(route)
      }
    }
  }

  public fun close() {
    if (!closed) {
      closed = true
      scope.cancel()
      routeRequests.close()
      runtime.close()
    }
  }
}

@Composable
private fun IosShell(
  runtime: IosDataRuntime,
  strings: com.simtop.billionbeers.shared.app.SharedAppStrings,
  routeRequests: kotlinx.coroutines.flow.Flow<PortableRoute>,
) {
  val darkTheme = isSystemInDarkTheme()
  SharedAppShell(
    repository = runtime.repository,
    pagerFactory = runtime.pagerFactory,
    coroutineDispatcher = runtime.coroutineDispatcherProvider,
    strings = strings,
    host =
      iosHost(
        runtime = runtime,
        darkTheme = darkTheme,
        routeRequests = routeRequests,
        retryText = strings.retry,
        availableText = strings.detailStrings.available,
        unavailableText = strings.detailStrings.outOfStock,
        errorText = strings.error,
        titleTextStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
      ),
  )
}

private fun iosHost(
  runtime: IosDataRuntime,
  darkTheme: Boolean,
  routeRequests: kotlinx.coroutines.flow.Flow<PortableRoute>,
  retryText: String,
  availableText: String,
  unavailableText: String,
  errorText: String,
  titleTextStyle: androidx.compose.ui.text.TextStyle,
) =
  SharedAppHost(
    beerRow = { beer, onClick ->
      IosBeerRow(runtime, beer, onClick, availableText, unavailableText)
    },
    errorContent = { state, retry -> IosError(state, retry, retryText, errorText) },
    backIcon = { contentDescription ->
      Text(
        text = "‹",
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
      )
    },
    favoriteIcon = { isFavorite, contentDescription ->
      Text(
        text = if (isFavorite) "♥" else "♡",
        modifier =
          Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Button
            stateDescription = contentDescription
          },
      )
    },
    imageContent = { imageUrl, description, modifier ->
      IosImage(runtime, imageUrl, description, modifier)
    },
    darkTheme = darkTheme,
    routeRequests = routeRequests,
    detailAnimationsDisabled = true,
    detailCollapsingToolbarEnabled = false,
    detailTitleTextStyle = titleTextStyle,
  )

@Composable
private fun IosBeerRow(
  runtime: IosDataRuntime,
  beer: Beer,
  onClick: () -> Unit,
  availableText: String,
  unavailableText: String,
) {
  val availability = if (beer.availability) availableText else unavailableText
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {
          contentDescription = "${beer.name}. $availability"
          role = Role.Button
          stateDescription = availability
        },
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (beer.availability) MaterialTheme.colorScheme.surface
          else MaterialTheme.colorScheme.errorContainer,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      IosImage(
        runtime,
        beer.imageUrl,
        null,
        Modifier.width(72.dp).height(96.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(beer.name, style = MaterialTheme.typography.titleMedium)
        if (beer.tagline.isNotBlank()) Text(beer.tagline, style = MaterialTheme.typography.bodyMedium)
        Text(
          "ABV ${beer.abv}% · IBU ${beer.ibu}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          availability,
          style = MaterialTheme.typography.labelMedium,
          color =
            if (beer.availability) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}

@Composable
private fun IosImage(
  runtime: IosDataRuntime,
  url: String,
  description: String?,
  modifier: Modifier,
) {
  var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(url) {
    bitmap =
      if (url.isBlank()) {
        null
      } else {
        runCatching {
          runtime.loadImage(url)?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
        }.getOrNull()
      }
  }
  val loadedBitmap = bitmap
  if (loadedBitmap == null) {
    IosImagePlaceholder(description, modifier)
  } else {
    Image(
      bitmap = loadedBitmap,
      contentDescription = description,
      contentScale = ContentScale.Crop,
      modifier = modifier,
    )
  }
}

@Composable
private fun IosImagePlaceholder(description: String?, modifier: Modifier) {
  Box(
    modifier =
      modifier.semantics {
        if (description == null) invisibleToUser() else contentDescription = description
      },
    contentAlignment = Alignment.Center,
  ) {
    Text("Beer", style = MaterialTheme.typography.headlineMedium)
  }
}

@Composable
private fun IosError(
  state: CommonUiState.Error,
  retry: () -> Unit,
  retryText: String,
  errorText: String,
) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        state.message ?: errorText,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      Button(onClick = retry) { Text(retryText) }
    }
  }
}
