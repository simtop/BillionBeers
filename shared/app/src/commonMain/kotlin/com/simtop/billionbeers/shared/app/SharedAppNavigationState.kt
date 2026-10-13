package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerbrowse.BrowseViewModel
import com.simtop.billionbeers.shared.beerdetail.BeerDetailViewModel
import com.simtop.billionbeers.shared.beersearch.BeersSearchViewModel
import com.simtop.billionbeers.shared.beerslist.BeersListViewModel
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel
import com.simtop.billionbeers.shared.presentation.BrowseBeersViewModel
import com.simtop.billionbeers.shared.savedfilters.SavedFiltersModel
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

internal abstract class ManagedSharedAppEntry(
  final override val id: Long,
  final override val route: PortableRoute,
) : SharedAppEntry {
  private var closed = false
  final override val isClosed: Boolean
    get() = closed

  final override fun close() {
    if (closed) return
    closed = true
    onClose()
  }

  protected open fun onClose() {}
}

internal abstract class ViewModelSharedAppEntry<VM : ViewModel>(
  id: Long,
  route: PortableRoute,
  val viewModel: VM,
) : ManagedSharedAppEntry(id, route) {
  override fun onClose() {
    viewModel.viewModelScope.cancel()
  }
}

internal class ListEntry(
  id: Long,
  repository: BeersRepository,
  pagerFactory: BeersPagerFactory,
) :
  ViewModelSharedAppEntry<BeersListViewModel>(
    id = id,
    route = PortableRoute.BeersList,
    viewModel = BeersListViewModel(repository, pagerFactory),
  ) {
  val listState = LazyListState()
}

internal class FavoritesEntry(
  id: Long,
  repository: BeersRepository,
) :
  ViewModelSharedAppEntry<FavoritesViewModel>(
    id = id,
    route = PortableRoute.Favorites,
    viewModel = FavoritesViewModel(repository),
  ) {
  val listState = LazyListState()
}

internal class SearchEntry(
  id: Long,
  repository: BeersRepository,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
) :
  ViewModelSharedAppEntry<BeersSearchViewModel>(
    id = id,
    route = PortableRoute.BeersSearch,
    viewModel = BeersSearchViewModel(coroutineDispatcher, pagerFactory, repository),
  ) {
  val listState = LazyListState()
}

internal class SavedFiltersEntry(
  id: Long,
  repository: BeersRepository,
) : ManagedSharedAppEntry(id, PortableRoute.SavedFilterPresets) {
  private val savedFilters = SavedFiltersModel(repository)
  val presets = savedFilters.presets

  suspend fun rename(id: String, name: String) = savedFilters.rename(id, name)

  suspend fun delete(id: String) = savedFilters.delete(id)
}

internal class SavedFilterResultsEntry(
  id: Long,
  val preset: SavedFilterPreset,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
) :
  ViewModelSharedAppEntry<BrowseBeersViewModel>(
    id = id,
    route = PortableRoute.SavedFilterPresets,
    viewModel = BrowseBeersViewModel(coroutineDispatcher, pagerFactory, preset.query),
  ) {
  val listState = LazyListState()
}

internal class BrowseHomeEntry(
  id: Long,
  repository: BeersRepository,
) :
  ViewModelSharedAppEntry<BrowseViewModel>(
    id = id,
    route = PortableRoute.BeerBrowse,
    viewModel = BrowseViewModel(repository),
  ) {
  var selectedTab by mutableStateOf(0)
}

internal class BrowseBeersEntry(
  id: Long,
  val selection: BrowseSelection,
  pagerFactory: BeersPagerFactory,
  coroutineDispatcher: CoroutineDispatcherProvider,
) :
  ViewModelSharedAppEntry<BrowseBeersViewModel>(
    id = id,
    route = selection.toRoute(),
    viewModel = BrowseBeersViewModel(coroutineDispatcher, pagerFactory, selection.toQuery()),
  ) {
  val listState = LazyListState()
}

internal class DetailEntry(
  id: Long,
  val beer: Beer,
  repository: BeersRepository,
) :
  ViewModelSharedAppEntry<BeerDetailViewModel>(
    id = id,
    route = PortableRoute.BeerDetail(beer),
    viewModel = BeerDetailViewModel(repository, beer),
  )

internal class SharedAppNavigationState(
  private val repository: BeersRepository,
  private val pagerFactory: BeersPagerFactory,
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  initialRoute: PortableRoute,
  private val onNavigationEvent: (SharedAppNavigationEvent) -> Unit = {},
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
    onNavigationEvent(SharedAppNavigationEvent.Push(current.route))
  }

  fun selectBrowse(selection: BrowseSelection) {
    navigate(selection.toRoute())
  }

  fun selectSavedFilter(preset: SavedFilterPreset) {
    append(SavedFilterResultsEntry(nextId(), preset, pagerFactory, coroutineDispatcher))
    onNavigationEvent(SharedAppNavigationEvent.Push(current.route, savedFilter = preset))
  }

  fun pop(): Boolean {
    if (entries.size == 1) return false
    val removed = entries.last()
    removed.close()
    entriesState.value = entries.dropLast(1)
    onNavigationEvent(SharedAppNavigationEvent.Pop(current.route))
    return true
  }

  // Host history already changed. Restore the stack without emitting an event back to the host.
  fun replaceFromRoute(route: PortableRoute) {
    if (route.isRoot()) {
      switchRoot(route)
      return
    }
    val existingIndex = entries.indexOfLast { it.route.matchesDestination(route) }
    if (existingIndex >= 0) {
      entries.drop(existingIndex + 1).forEach(SharedAppEntry::close)
      entriesState.value = entries.take(existingIndex + 1)
      return
    }
    entries.drop(1).forEach(SharedAppEntry::close)
    entriesState.value = listOf(entries.first(), createEntry(route))
  }

  // A host's transient history entry identifies the exact applied query snapshot.
  fun replaceFromSavedFilter(preset: SavedFilterPreset) {
    val existingIndex = entries.indexOfLast { it is SavedFilterResultsEntry && it.preset == preset }
    if (existingIndex >= 0) {
      entries.drop(existingIndex + 1).forEach(SharedAppEntry::close)
      entriesState.value = entries.take(existingIndex + 1)
    } else {
      switchRoot(PortableRoute.SavedFilterPresets)
      append(SavedFilterResultsEntry(nextId(), preset, pagerFactory, coroutineDispatcher))
    }
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
      PortableRoute.BeersSearch ->
        SearchEntry(nextId(), repository, pagerFactory, coroutineDispatcher)
      PortableRoute.SavedFilterPresets -> SavedFiltersEntry(nextId(), repository)
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
    this == PortableRoute.BeersList ||
      this == PortableRoute.Favorites ||
      this == PortableRoute.SavedFilterPresets
}

// Browser hashes contain browse kind and id, but omit the display name held by a live entry.
private fun PortableRoute.matchesDestination(other: PortableRoute): Boolean =
  when {
    this is PortableRoute.BeerBrowseSelection && other is PortableRoute.BeerBrowseSelection ->
      category.matchesSelection(other.category)
    else -> this == other
  }

private fun BrowseCategory.matchesSelection(other: BrowseCategory): Boolean =
  when (this) {
    is BrowseCategory.Style -> other is BrowseCategory.Style && id == other.id
    is BrowseCategory.Brewery -> other is BrowseCategory.Brewery && id == other.id
  }
