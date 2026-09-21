package com.simtop.billionbeers.desktop

import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel
import com.simtop.core.core.CommonUiState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
  val live = args.contains("--live")
  val favorites = args.contains("--favorites")
  val baseUrl = args.findValue("--base-url") ?: DEFAULT_API_BASE_URL
  val databasePath =
    args.findValue("--data-dir")?.let { File(it, "beers_database.db").path }
      ?: File(System.getProperty("user.home"), ".billionbeers/beers_database.db").path

  DesktopDataRuntime.open(
      DesktopDataConfig(
        databasePath = databasePath,
        apiBaseUrl = baseUrl,
      )
    )
    .use { runtime ->
      if (favorites) {
        val state =
          FavoritesViewModel(runtime.repository).viewState.first { it !is CommonUiState.Loading }
        when (state) {
          CommonUiState.Empty -> println("No favorite beers")
          is CommonUiState.Error ->
            println("Failed to load favorites: ${state.message ?: "unknown error"}")
          is CommonUiState.Success ->
            state.data.forEach { beer -> println("${beer.id}: ${beer.name}") }
          CommonUiState.Loading -> error("Favorites state did not load")
        }
      } else {
        val beers =
          if (live) {
            val pager = runtime.pagerFactory.create(BeersQuery())
            pager.loadFirstPage()
            pager.data.first()
          } else {
            println("Reading the local catalog at $databasePath")
            runtime.repository.getAllBeersFromDB()
          }
        println("Loaded ${beers.size} beers from ${if (live) baseUrl else "local data"}")
      }
    }
}

private fun Array<String>.findValue(name: String): String? {
  val index = indexOf(name)
  return if (index >= 0) getOrNull(index + 1) else null
}
