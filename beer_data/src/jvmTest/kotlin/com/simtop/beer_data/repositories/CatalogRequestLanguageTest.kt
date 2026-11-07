package com.simtop.beer_data.repositories

import com.simtop.beer_data.fakes.FakeBeersLocalSource
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation
import com.simtop.beer_network.network.BeersService
import com.simtop.beer_network.network.BeersServiceResponse
import com.simtop.beer_network.remotesources.BeersRemoteSourceImpl
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.NoOpLogger
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CatalogRequestLanguageTest {
  @Test
  fun `an in-flight catalog response keeps its request language and bookmark`() = runTest {
    var currentLanguage = "en"
    val languageProvider = LanguageProvider { currentLanguage }
    val logger = NoOpLogger()
    val translations = listOf("en", "fr").map { Translation(Language(it), "$it tagline", "") }
    val service =
      PausedCatalogService(
        BeersApiResponseItem(id = "1", name = "Beer 1", translations = translations)
      )
    val repository =
      BeersRepositoryImpl(
        BeersRemoteSourceImpl(service, languageProvider),
        FakeBeersLocalSource(),
        BeersMapper(languageProvider, logger),
        languageProvider,
        logger,
      )
    val pager = BeersPagerFactoryImpl(repository, languageProvider, logger).create()

    val load = launch { pager.loadFirstPage() }
    assertEquals("en", service.firstRequestLanguage.await())
    currentLanguage = "fr"
    service.resume.complete(Unit)
    load.join()

    assertEquals("en tagline", repository.getBeerById("1")?.tagline)
    assertEquals(null, repository.pagingNextKey("catalog:fr"))
    assertEquals(2, repository.pagingNextKey("catalog:en"))
    assertEquals(CatalogCacheStatus.LanguageMismatch, repository.catalogCacheStatus())

    pager.loadNextPage()
    assertEquals(listOf(1 to "en", 1 to "fr"), service.requests)
    assertEquals("fr tagline", repository.getBeerById("1")?.tagline)
    assertEquals(null, repository.pagingNextKey("catalog:en"))
    assertEquals(2, repository.pagingNextKey("catalog:fr"))

    currentLanguage = "en"
    pager.loadNextPage()
    assertEquals(listOf(1 to "en", 1 to "fr", 1 to "en"), service.requests)
  }
}

private class PausedCatalogService(private val beer: BeersApiResponseItem) : BeersService {
  val firstRequestLanguage = CompletableDeferred<String>()
  val resume = CompletableDeferred<Unit>()
  val requests = mutableListOf<Pair<Int, String>>()

  override suspend fun getListOfBeers(
    page: Int,
    perPage: Int,
    languageCode: String,
    search: String?,
    typologyId: String?,
    breweryId: String?,
  ): BeersServiceResponse<List<BeersApiResponseItem>> {
    requests += page to languageCode
    firstRequestLanguage.complete(languageCode)
    resume.await()
    return BeersServiceResponse(
      body = listOf(beer),
      statusCode = 200,
      headers = mapOf("X-Total-Count" to "206"),
    )
  }

  override suspend fun getTypologies() = error("Unexpected typology request")

  override suspend fun getBreweries() = error("Unexpected brewery request")
}
