package com.simtop.beer_database.localsources

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeersLocalSourceTest {

  private lateinit var db: BeersDatabase
  private lateinit var localSource: BeersLocalSource

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
    localSource.insertAllToDB(listOf(beer()))

    assertEquals(listOf(beer()), localSource.getAllBeersFromDB().first())
  }

  @Test
  fun insertingTheSameListTwiceKeepsOneRow() = runBlocking {
    localSource.insertAllToDB(listOf(beer()))
    localSource.insertAllToDB(listOf(beer()))

    assertEquals(1, localSource.getCountFromDB())
  }

  @Test
  fun deleteFromDb() = runBlocking {
    localSource.insertAllToDB(listOf(beer()))
    localSource.deleteAllFromDB()

    assertEquals(0, localSource.getCountFromDB())
  }

  @Test(expected = SQLiteConstraintException::class)
  fun directDuplicateInsertViolatesPrimaryKeyConstraint() = runBlocking {
    localSource.insertAllToDB(listOf(beer()))
    db.openHelper.writableDatabase.execSQL(
      "INSERT INTO beers " +
        "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability) " +
        "VALUES ('1', 'Duplicate', '', '', '', 0.0, 0.0, '[]', 1)"
    )
  }

  @Test
  fun updateAvailability() = runBlocking {
    localSource.insertAllToDB(listOf(beer()))
    localSource.upsertAvailability(beer().copy(availability = false))

    assertEquals(false, localSource.getAllBeersFromDB().first().single().availability)
  }

  @Test
  fun refreshSeedsAvailabilityOnFirstInsertButNeverOverwritesIt() = runBlocking {
    localSource.insertAllToDB(listOf(beer().copy(availability = false)))
    localSource.insertAllToDB(listOf(beer().copy(availability = true, name = "Refreshed")))

    val result = localSource.getAllBeersFromDB().first().single()
    assertEquals(false, result.availability)
    assertEquals("Refreshed", result.name)
  }

  private fun beer() =
    BeerDbModel(
      id = "1",
      name = "Buzz",
      tagline = "A Real Bitter Experience.",
      description = "",
      imageUrl = "",
      abv = 0.0,
      ibu = 0.0,
      foodPairing = "[]",
    )
}
