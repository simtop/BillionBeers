package com.simtop.beerdomain.fakes

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.UpdateFavoriteError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerPage
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.core.core.Either
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeBeersRepositoryTest {

  @Test
  fun `fixtures expose the expected default beer`() {
    assertEquals("1", fakeBeerModel.id)
    assertEquals(fakeBeerModel, fakeBeerListModel.single())
    assertEquals("Error getting list of beers", fakeErrorName)
    assertEquals(fakeErrorName, fakeException.message)
  }

  @Test
  fun `favorite observation sorts favorite beers`() = runTest {
    val repository = FakeBeersRepository(
      listOf(
        beer(id = "2", name = "Same", favorite = true),
        beer(id = "1", name = "Same", favorite = true),
        beer(id = "3", name = "Other"),
      ),
    )

    repository.observeFavoriteBeers().test {
      assertEquals(listOf("Same" to "1", "Same" to "2"), awaitItem().map { it.name to it.id })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `favorite update preserves availability and is observable`() = runTest {
    val existing = beer(id = "1", availability = false)
    val repository = FakeBeersRepository(listOf(existing))

    repository.observeFavoriteBeers().test {
      assertEquals(emptyList(), awaitItem())
      repository.updateFavorite(existing.copy(isFavorite = true))
      assertEquals(listOf(existing.copy(isFavorite = true)), awaitItem())
      assertEquals(existing.copy(isFavorite = true), repository.getBeerById("1"))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `availability update preserves favorite and insert upsert preserves local fields`() =
    runTest {
      val existing = beer(id = "1", availability = false, favorite = true)
      val repository = FakeBeersRepository(listOf(existing))

      repository.updateAvailability(existing.copy(availability = true, isFavorite = false))
      assertEquals(existing.copy(availability = true), repository.getBeerById("1"))

      repository.insertAllToDB(
        listOf(existing.copy(name = "Updated", availability = false, isFavorite = false)),
      )
      assertEquals(
        existing.copy(name = "Updated", availability = true),
        repository.getBeerById("1"),
      )
    }

  @Test
  fun `paging bookmark merges monotonically and cache status derives from rows`() = runTest {
    val repository = FakeBeersRepository()
    assertEquals(CatalogCacheStatus.Empty, repository.catalogCacheStatus())

    repository.insertPage(listOf(beer(id = "1")), "catalog", nextKey = 3, totalCount = 4)
    repository.insertPage(emptyList(), "catalog", nextKey = 2, totalCount = 4)

    assertEquals(3, repository.pagingNextKey("catalog"))
    assertEquals(CatalogCacheStatus.Fresh, repository.catalogCacheStatus())
  }

  @Test
  fun `api and browse controls record requests and calls`() = runTest {
    val repository = FakeBeersRepository()
    repository.apiPage = BeerPage(listOf(fakeBeerModel), 1)
    val query = BeersQuery(search = "ipa")

    assertEquals(repository.apiPage, repository.getBeersPageFromApi(2, query))
    assertEquals(listOf(2 to query), repository.apiRequests)
    repository.getBeerStyles()
    repository.getBreweries()
    assertEquals(1, repository.beerStylesCallCount)
    assertEquals(1, repository.breweriesCallCount)
  }

  @Test
  fun `configured exception produces typed update failure`() = runTest {
    val repository = FakeBeersRepository(listOf(fakeBeerModel))
    repository.setExceptionToThrow(fakeException)

    val result = repository.updateFavorite(fakeBeerModel.copy(isFavorite = true))

    val failure = assertIs<Either.Left<UpdateFavoriteError>>(result)
    assertEquals(fakeException, assertIs<UpdateFavoriteError.Unknown>(failure.value).cause)
  }

  private fun beer(
    id: String,
    name: String = "Beer $id",
    availability: Boolean = true,
    favorite: Boolean = false,
  ) = Beer(
    id = id,
    name = name,
    tagline = "",
    description = "",
    imageUrl = "",
    abv = 0.0,
    ibu = 0.0,
    foodPairing = emptyList(),
    availability = availability,
    isFavorite = favorite,
  )
}
