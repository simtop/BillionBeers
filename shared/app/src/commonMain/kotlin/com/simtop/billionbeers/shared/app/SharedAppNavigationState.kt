package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseBeersViewModel
import com.simtop.billionbeers.shared.beerbrowse.BrowseViewModel
import com.simtop.billionbeers.shared.beerdetail.BeerDetailViewModel
import com.simtop.billionbeers.shared.beersearch.BeersSearchViewModel
import com.simtop.billionbeers.shared.beerslist.BeersListViewModel
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute
import kotlinx.coroutines.cancel

internal data class BrowseSelection(
  val styleId: String? = null,
  val breweryId: String? = null,
  val name: String,
) {
  fun toQuery() = BeersQuery(styleId = styleId, breweryId = breweryId)

  fun toRoute() =
    PortableRoute.BeerBrowseSelection(
      category =
        if (styleId != null) BrowseCategory.Style(styleId, name)
        else BrowseCategory.Brewery(requireNotNull(breweryId), name)
    )

  companion object {
    fun fromCategory(category: BrowseCategory) =
      when (category) {
        is BrowseCategory.Style -> BrowseSelection(styleId = category.id, name = category.name)
        is BrowseCategory.Brewery -> BrowseSelection(breweryId = category.id, name = category.name)
      }
  }
}

internal sealed interface SharedAppEntry {
  val id: Long
  val route: PortableRoute
  val isClosed: Boolean

  fun close()
}

internal class ListEntry(
  override val id: Long,
  repository: BeersRepository,
  pagerFactory: BeersPagerFactory,
) : SharedAppEntry {
  override val route = PortableRoute.BeersList
  val viewModel = BeersListViewModel(repository, pagerFactory)
  val listState = LazyListState()
  private var closed = false
  override val isClosed: Boolean
    get() = closed

  override fun close() {
    if (!closed) {
      closed = true
      viewModel.viewModelScope.cancel()
    }
  }
}

internal class FavoritesEntry(
  override val id: Long,
  repository: BeersRepository,
) : SharedAppEntry {
  override val route = PortableRoute.Favorites
  val viewModel = FavoritesViewModel(repository)
  val listState = LazyListState()
  private var closed = false
  override val isClosed: Boolean
    get() = closed

  override fun close() {
    if (!closed) {
      closed = true
      viewModel.viewModelScope.cancel()
    }
  }
}

internal class SearchEntry(
  override val id: Long,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
) : SharedAppEntry {
  override val route = PortableRoute.BeersSearch
  val viewModel = BeersSearchViewModel(coroutineDispatcher, pagerFactory)
  val listState = LazyListState()
  private var closed = false
  override val isClosed: Boolean
    get() = closed

  override fun close() {
    if (!closed) {
      closed = true
      viewModel.viewModelScope.cancel()
    }
  }
}

internal class BrowseHomeEntry(
  override val id: Long,
  repository: BeersRepository,
) : SharedAppEntry {
  override val route = PortableRoute.BeerBrowse
  val viewModel = BrowseViewModel(repository)
  var selectedTab by mutableStateOf(0)
  private var closed = false
  override val isClosed: Boolean
    get() = closed

  override fun close() {
    if (!closed) {
      closed = true
      viewModel.viewModelScope.cancel()
    }
  }
}

internal class BrowseBeersEntry(
  override val id: Long,
  val selection: BrowseSelection,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
) : SharedAppEntry {
  override val route = selection.toRoute()
  val viewModel = BrowseBeersViewModel(coroutineDispatcher, pagerFactory, selection.toQuery())
  val listState = LazyListState()
  private var closed = false
  override val isClosed: Boolean
    get() = closed

  override fun close() {
    if (!closed) {
      closed = true
      viewModel.viewModelScope.cancel()
    }
  }
}

internal class DetailEntry(
  override val id: Long,
  val beer: Beer,
  repository: BeersRepository,
) : SharedAppEntry {
  override val route = PortableRoute.BeerDetail(beer)
  val viewModel = BeerDetailViewModel(repository, beer)
  private var closed = false
  override val isClosed: Boolean
    get() = closed

  override fun close() {
    if (!closed) {
      closed = true
      viewModel.viewModelScope.cancel()
    }
  }
}

internal class SharedAppNavigationState(
  private val repository: BeersRepository,
  private val pagerFactory: BeersPagerFactory,
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  initialRoute: PortableRoute,
) {
  private var nextId = 0L
  private val rootEntries = mutableMapOf<PortableRoute, SharedAppEntry>()
  private val entriesState: androidx.compose.runtime.MutableState<List<SharedAppEntry>>

  init {
    val initialEntry = createEntry(initialRoute)
    if (initialRoute.isRoot()) rootEntries[initialRoute] = initialEntry
    entriesState = mutableStateOf(listOf(initialEntry))
  }

  val entries: List<SharedAppEntry>
    get() = entriesState.value

  val current: SharedAppEntry
    get() = entries.last()

  fun navigate(route: PortableRoute) {
    if (route.isRoot()) {
      switchRoot(route)
    } else {
      append(createEntry(route))
    }
  }

  fun selectBrowse(selection: BrowseSelection) {
    append(BrowseBeersEntry(nextId(), selection, pagerFactory, coroutineDispatcher))
  }

  fun pop(): Boolean {
    if (entries.size == 1) return false
    val removed = entries.last()
    removed.close()
    entriesState.value = entries.dropLast(1)
    return true
  }

  fun replaceFromRoute(route: PortableRoute) {
    if (route.isRoot()) {
      switchRoot(route)
      return
    }
    val existingIndex = entries.indexOfLast { it.route == route }
    if (existingIndex >= 0) {
      entries.drop(existingIndex + 1).forEach(SharedAppEntry::close)
      entriesState.value = entries.take(existingIndex + 1)
      return
    }
    entries.drop(1).forEach(SharedAppEntry::close)
    entriesState.value = listOf(entries.first(), createEntry(route))
  }

  fun disposeAll() {
    val liveEntries = (entries + rootEntries.values).distinctBy(SharedAppEntry::id)
    liveEntries.forEach(SharedAppEntry::close)
    entriesState.value = emptyList()
    rootEntries.clear()
  }

  private fun switchRoot(route: PortableRoute) {
    val root = rootEntries.getOrPut(route) { createEntry(route) }
    entries.drop(1).forEach(SharedAppEntry::close)
    entriesState.value = listOf(root)
  }

  private fun append(entry: SharedAppEntry) {
    entriesState.value = entries + entry
  }

  private fun createEntry(route: PortableRoute): SharedAppEntry =
    when (route) {
      PortableRoute.BeersList -> ListEntry(nextId(), repository, pagerFactory)
      PortableRoute.Favorites -> FavoritesEntry(nextId(), repository)
      PortableRoute.BeersSearch -> SearchEntry(nextId(), pagerFactory, coroutineDispatcher)
      PortableRoute.BeerBrowse -> BrowseHomeEntry(nextId(), repository)
      is PortableRoute.BeerBrowseSelection ->
        BrowseBeersEntry(
          nextId(),
          BrowseSelection.fromCategory(route.category),
          pagerFactory,
          coroutineDispatcher,
        )
      is PortableRoute.BeerDetail -> DetailEntry(nextId(), route.beer, repository)
    }

  private fun nextId(): Long = ++nextId

  private fun PortableRoute.isRoot(): Boolean =
    this == PortableRoute.BeersList || this == PortableRoute.Favorites
}
