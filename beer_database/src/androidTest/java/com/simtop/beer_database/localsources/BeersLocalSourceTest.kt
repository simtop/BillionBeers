package com.simtop.beer_database.localsources

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.StoredBeer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeersLocalSourceTest {

  private lateinit var db: BeersDatabase
  private lateinit var localSource: BeersStorage

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          BeersDatabase::class.java,
        )
        .build()
    localSource = BeersLocalSourceImpl(db)
  }

  @After fun tearDown() = db.close()

  @Test
  fun insertListToDb() = runBlocking {
    localSource.insertAll(listOf(beer()))

    assertEquals(listOf(beer()), localSource.observeBeers().first())
  }

  @Test
  fun insertingTheSameListTwiceKeepsOneRow() = runBlocking {
    localSource.insertAll(listOf(beer()))
    localSource.insertAll(listOf(beer()))

    assertEquals(1, localSource.count())
  }

  @Test
  fun deleteFromDb() = runBlocking {
    localSource.insertAll(listOf(beer()))
    localSource.deleteAll()

    assertEquals(0, localSource.count())
  }

  @Test(expected = SQLiteConstraintException::class)
  fun directDuplicateInsertViolatesPrimaryKeyConstraint() = runBlocking {
    localSource.insertAll(listOf(beer()))
    db.openHelper.writableDatabase.execSQL(
      "INSERT INTO beers " +
        "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability) " +
        "VALUES ('1', 'Duplicate', '', '', '', 0.0, 0.0, '[]', 1)"
    )
  }

  @Test
  fun updateAvailability() = runBlocking {
    localSource.insertAll(listOf(beer()))
    localSource.upsertAvailability(beer().copy(availability = false))

    assertEquals(false, localSource.observeBeers().first().single().availability)
  }

  @Test
  fun refreshSeedsAvailabilityOnFirstInsertButNeverOverwritesIt() = runBlocking {
    localSource.insertAll(listOf(beer().copy(availability = false)))
    localSource.insertAll(listOf(beer().copy(availability = true, name = "Refreshed")))

    val result = localSource.observeBeers().first().single()
    assertEquals(false, result.availability)
    assertEquals("Refreshed", result.name)
  }

  @Test
  fun refreshPreservesAvailabilityAndFavoriteWhileUpdatingCatalogFields() = runBlocking {
    localSource.insertAll(
      listOf(beer(name = "Before").copy(availability = false, isFavorite = true))
    )

    localSource.insertAll(
      listOf(beer(name = "After").copy(availability = true, isFavorite = false))
    )

    val refreshed = localSource.observeBeers().first().single()
    assertEquals("After", refreshed.name)
    assertEquals(false, refreshed.availability)
    assertEquals(true, refreshed.isFavorite)
  }

  @Test
  fun favoriteEditForUncachedBeerInsertsCompleteRow() = runBlocking {
    val uncached = beer(id = "42", name = "Uncached").copy(isFavorite = true, availability = false)

    localSource.upsertFavorite(uncached)

    assertEquals(uncached, localSource.observeBeers().first().single())
  }

  @Test
  fun uncachedAvailabilityAndFavoriteEditsAreBothPersisted() = runBlocking {
    val uncached = beer(id = "42", name = "Uncached").copy(isFavorite = true, availability = false)

    localSource.upsertAvailability(uncached)
    localSource.upsertFavorite(uncached)

    val stored = localSource.observeBeers().first().single()
    assertEquals(false, stored.availability)
    assertEquals(true, stored.isFavorite)
    assertEquals("Uncached", stored.name)
  }

  @Test
  fun favoriteFlowReflectsCommittedWritesInDisplayOrder() = runBlocking {
    assertEquals(emptyList<StoredBeer>(), localSource.observeFavoriteBeers().first())

    localSource.upsertFavorite(beer(id = "2", name = "Bravo").copy(isFavorite = true))
    assertEquals(
      listOf("Bravo"),
      withTimeout(1_000) { localSource.observeFavoriteBeers().first { it.size == 1 } }
        .map(StoredBeer::name),
    )

    localSource.upsertFavorite(beer(id = "1", name = "Alpha").copy(isFavorite = true))
    assertEquals(
      listOf("Alpha", "Bravo"),
      withTimeout(1_000) { localSource.observeFavoriteBeers().first { it.size == 2 } }
        .map(StoredBeer::name),
    )

    localSource.upsertAvailability(
      beer(id = "1", name = "Alpha").copy(isFavorite = true, availability = false)
    )
    assertEquals(
      false,
      withTimeout(1_000) {
          localSource.observeFavoriteBeers().first { favorites ->
            favorites.any { it.id == "1" && !it.availability }
          }
        }
        .first { it.id == "1" }
        .availability,
    )

    localSource.upsertFavorite(
      beer(id = "1", name = "Alpha").copy(isFavorite = false, availability = false)
    )
    assertEquals(
      listOf("Bravo"),
      withTimeout(1_000) { localSource.observeFavoriteBeers().first { it.size == 1 } }
        .map(StoredBeer::name),
    )
  }

  private fun beer(id: String = "1", name: String = "Buzz") =
    StoredBeer(
      id = id,
      name = name,
      tagline = "A Real Bitter Experience.",
      description = "",
      imageUrl = "",
      abv = 0.0,
      ibu = 0.0,
      foodPairing = emptyList(),
    )
}
