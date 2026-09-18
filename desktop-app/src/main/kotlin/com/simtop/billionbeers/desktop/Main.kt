package com.simtop.billionbeers.desktop

import com.simtop.beerdomain.domain.models.BeersQuery
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
  val live = args.contains("--live")
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

private fun Array<String>.findValue(name: String): String? {
  val index = indexOf(name)
  return if (index >= 0) getOrNull(index + 1) else null
}
