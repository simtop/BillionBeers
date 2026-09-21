package com.simtop.billionbeers.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.shared.favorites.FavoritesViewModel
import com.simtop.core.core.CommonUiState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
  val config = desktopConfig(args)
  if (args.contains("--cli") || args.contains("--live") || args.contains("--favorites")) {
    runCli(args, config)
    return
  }

  val runtime = DesktopDataRuntime.open(config)
  try {
    application {
      Window(
        onCloseRequest = ::exitApplication,
        title = "Billion Beers",
      ) {
        DesktopShell(runtime = runtime)
      }
    }
  } finally {
    runtime.close()
  }
}

private fun runCli(args: Array<String>, config: DesktopDataConfig) = runBlocking {
  DesktopDataRuntime.open(config).use { runtime ->
    if (args.contains("--favorites")) {
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
        if (args.contains("--live")) {
          val pager = runtime.pagerFactory.create(BeersQuery())
          pager.loadFirstPage()
          pager.data.first()
        } else {
          println("Reading the local catalog at ${config.databasePath}")
          runtime.repository.getAllBeersFromDB()
        }
      println(
        "Loaded ${beers.size} beers from ${if (args.contains("--live")) config.apiBaseUrl else "local data"}"
      )
    }
  }
}

private fun desktopConfig(args: Array<String>): DesktopDataConfig {
  val databasePath =
    args.findValue("--data-dir")?.let { File(it, "beers_database.db").path }
      ?: File(System.getProperty("user.home"), ".billionbeers/beers_database.db").path
  return DesktopDataConfig(
    databasePath = databasePath,
    apiBaseUrl = args.findValue("--base-url") ?: DEFAULT_API_BASE_URL,
  )
}

private fun Array<String>.findValue(name: String): String? {
  val index = indexOf(name)
  return if (index >= 0) getOrNull(index + 1) else null
}
