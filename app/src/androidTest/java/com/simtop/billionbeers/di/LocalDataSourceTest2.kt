package com.simtop.billionbeers.di

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.database.BeersDao
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.di.BeersDatabaseModule
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
@HiltAndroidTest
@UninstallModules(BeersDatabaseModule::class)
class LocalDataSourceTest {

  @get:Rule var hiltRule = HiltAndroidRule(this)

  private lateinit var localSource: BeersLocalSource

  @Inject lateinit var db: BeersDatabase

  @Before
  fun setUp() {
    hiltRule.inject()
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

  @Module
  @InstallIn(SingletonComponent::class)
  object TestModule {

    @Provides @Singleton fun provideBeersDao(db: BeersDatabase): BeersDao = db.beersDao()

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext app: Context): BeersDatabase {
      return Room.inMemoryDatabaseBuilder(app, BeersDatabase::class.java)
        .fallbackToDestructiveMigration()
        .build()
    }
  }
}

val fakeBeersApiResponseItem2 =
  BeersApiResponseItem("1", "Buzz", 4.5, 60.0, "", emptyList(), emptyList())

val fakeBeerApiResponse2 = listOf(fakeBeersApiResponseItem2.copy())

val fakeBeerModel2 = Beer("1", "Buzz", "A Real Bitter Experience.", "", "", 4.5, 60.0, emptyList())

val fakeBeerListModel2 = listOf(fakeBeerModel2.copy())

val fakeDbBeerList = listOf(BeersMapper.fromBeerToBeerDbModel(fakeBeerModel2))
