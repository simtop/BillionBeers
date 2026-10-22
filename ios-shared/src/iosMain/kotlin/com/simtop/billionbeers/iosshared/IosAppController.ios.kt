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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.app.SharedAppHost
import com.simtop.billionbeers.shared.app.SharedAppShell
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.designsystem.controls.BrandAvailabilityBadge
import com.simtop.billionbeers.shared.designsystem.controls.BrandButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandCard
import com.simtop.billionbeers.shared.designsystem.controls.BrandIcon
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.runSuspendCatching
import com.simtop.navigation.contract.PortableRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import platform.UIKit.UIViewController

private const val DEFAULT_IOS_API_BASE_URL = "https://brewbuddy.dev/"

/** Owns one iOS data graph and its Compose controller for the lifetime of a host scene. */
public class IosAppSession(languageCode: String = "en") {
  private val runtime =
    IosDataRuntime.open(
      IosDataConfig(apiBaseUrl = DEFAULT_IOS_API_BASE_URL, languageCode = languageCode)
    )
  private val strings = IosLocalizedStrings.forLanguage(languageCode)
  private val routeRequests = IosRouteRequestBuffer()
  private val scope = CoroutineScope(SupervisorJob() + runtime.coroutineDispatcherProvider.io)
  private var closed = false

  public val viewController: UIViewController = ComposeUIViewController {
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
  // Graph getters for the pager factory and dispatchers are unscoped. Keep the identities stable
  // across host recompositions so SharedAppShell does not discard its navigation state.
  val pagerFactory = remember(runtime) { runtime.pagerFactory }
  val coroutineDispatcher = remember(runtime) { runtime.coroutineDispatcherProvider }
  SharedAppShell(
    repository = runtime.repository,
    catalogMetadataRepository = runtime.catalogMetadataRepository,
    savedFilterPresetsRepository = runtime.savedFilterPresetsRepository,
    pagerFactory = pagerFactory,
    coroutineDispatcher = coroutineDispatcher,
    strings = strings,
    host =
      iosHost(
        runtime = runtime,
        darkTheme = darkTheme,
        routeRequests = routeRequests,
        strings = strings,
      ),
  )
}

private fun iosHost(
  runtime: IosDataRuntime,
  darkTheme: Boolean,
  routeRequests: kotlinx.coroutines.flow.Flow<PortableRoute>,
  strings: SharedAppStrings,
) =
  SharedAppHost(
    beerRow = { beer, onClick ->
      IosBeerRow(
        runtime,
        beer,
        onClick,
        strings.detailStrings.available,
        strings.detailStrings.outOfStock,
      )
    },
    errorContent = { state, retry ->
      IosError(state, retry, strings.list.retry, strings.chrome.error)
    },
    backIcon = { contentDescription ->
      BrandIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription)
    },
    favoriteIcon = { isFavorite, contentDescription ->
      BrandIcon(
        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = contentDescription,
      )
    },
    catalogNavigationIcon = { contentDescription ->
      BrandIcon(Icons.Filled.Home, contentDescription = contentDescription)
    },
    favoritesNavigationIcon = { contentDescription ->
      BrandIcon(Icons.Filled.Favorite, contentDescription = contentDescription)
    },
    savedFiltersNavigationIcon = { contentDescription ->
      BrandIcon(Icons.AutoMirrored.Filled.List, contentDescription = contentDescription)
    },
    imageContent = { imageUrl, description, modifier ->
      IosImage(runtime, imageUrl, description, modifier)
    },
    darkTheme = darkTheme,
    routeRequests = routeRequests,
    detailAnimationsDisabled = true,
    detailTitleTextStyle = {
      BillionBeersTheme.brand.iosDetailTitle
    },
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
  BrandCard(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {
          contentDescription = "${beer.name}. $availability"
          role = Role.Button
          stateDescription = availability
        },
    containerColor = BillionBeersTheme.colorScheme.surface,
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
        BrandText(beer.name, style = BillionBeersTheme.brand.beerTitle)
        if (beer.tagline.isNotBlank())
          BrandText(beer.tagline, style = BillionBeersTheme.typography.bodyMedium)
        BrandText(
          "ABV ${beer.abv}% · IBU ${beer.ibu}",
          style = BillionBeersTheme.typography.labelMedium,
          color = BillionBeersTheme.colorScheme.onSurfaceVariant,
        )
        BrandAvailabilityBadge(text = availability, available = beer.availability)
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
        runSuspendCatching {
          runtime.loadImage(url)?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
        }
          .getOrNull()
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
    BrandText("Beer", style = BillionBeersTheme.typography.headlineMedium)
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
      BrandText(
        state.message ?: errorText,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      BrandButton(onClick = retry) { BrandText(retryText) }
    }
  }
}
