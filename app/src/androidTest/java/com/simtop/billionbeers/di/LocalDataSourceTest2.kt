package com.simtop.billionbeers.di

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

import androidx.test.platform.app.InstrumentationRegistry

class LocalDataSourceTest {

  private lateinit var localSource: BeersLocalSource
  private lateinit var db: BeersDatabase

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    db = Room.inMemoryDatabaseBuilder(context, BeersDatabase::class.java)
        .fallbackToDestructiveMigration()
        .build()
    localSource = BeersLocalSourceImpl(db)
  }

  @Test
  fun insertListToDB() {
    runBlocking {
      localSource.insertAllToDB(fakeDbBeerList)

      val repositoriesByName = localSource.getAllBeersFromDB().first()
      assertEquals(repositoriesByName[0].id, fakeDbBeerList[0].id)
    }
  }

  @Test
  fun getAllFromDBInsertingTwiceSameList() {
    runBlocking {
      localSource.insertAllToDB(fakeDbBeerList)
      localSource.insertAllToDB(fakeDbBeerList)

      val repositoriesByName = localSource.getAllBeersFromDB().first()

      val count = localSource.getCountFromDB()
      assertEquals(repositoriesByName[0].id, fakeDbBeerList[0].id)
      assertEquals(count, 1)
    }
  }

  @Test
  fun deleteFromDB() {
    runBlocking {
      localSource.insertAllToDB(fakeDbBeerList)
      localSource.deleteAllFromDB()
      val count = localSource.getCountFromDB()

      assertEquals(count, 0)
    }
  }

  @Test(expected = SQLiteConstraintException::class)
  fun shouldThrowExceptionWhenInserting() {
    runBlocking {
      localSource.insertAllToDB(fakeDbBeerList)
      throw SQLiteConstraintException()
    }
  }

  @Test
  fun updateDB() {
    runBlocking {
      localSource.insertAllToDB(fakeDbBeerList)
      localSource.updateBeer(fakeBeerModel2.id, false)
      val result = localSource.getAllBeersFromDB().first()
      assertEquals(result[0].availability, false)
    }
  }

}

val fakeBeersApiResponseItem2 =
  BeersApiResponseItem("1", "Buzz", 4.5, 60.0, "", emptyList(), emptyList())

val fakeBeerApiResponse2 = listOf(fakeBeersApiResponseItem2.copy())

val fakeBeerModel2 = Beer("1", "Buzz", "A Real Bitter Experience.", "", "", 4.5, 60.0, emptyList())

val fakeBeerListModel2 = listOf(fakeBeerModel2.copy())

val fakeDbBeerList = listOf(BeersMapper.fromBeerToBeerDbModel(fakeBeerModel2))
