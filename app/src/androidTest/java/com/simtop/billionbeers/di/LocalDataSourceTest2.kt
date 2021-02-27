package com.simtop.billionbeers.di


import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.beerdomain.data.databases.BeersDao
import com.simtop.beerdomain.data.databases.BeersDatabase
import com.simtop.beerdomain.data.localsources.BeersLocalSource
import com.simtop.beerdomain.data.mappers.BeersMapper
import com.simtop.beerdomain.data.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

@RunWith(AndroidJUnit4ClassRunner::class)
@HiltAndroidTest
@UninstallModules(com.simtop.beerdomain.di.BeersDatabaseModule::class)
class LocalDataSourceTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var localSource: com.simtop.beerdomain.data.localsources.BeersLocalSource

    @Inject
    lateinit var db: com.simtop.beerdomain.data.databases.BeersDatabase


    @Before
    fun setUp() {
        hiltRule.inject()
        localSource = com.simtop.beerdomain.data.localsources.BeersLocalSource(db)
    }

    @Test
    fun insertListToDB() {
        runBlocking {
            localSource.insertAllToDB(fakeDbBeerList)

            val repositoriesByName = localSource.getAllBeersFromDB()
            assertEquals(repositoriesByName.first().id, fakeDbBeerList[0].id)
        }
    }

    @Test
    fun getAllFromDBInsertingTwiceSameList() {
        runBlocking {
            localSource.insertAllToDB(fakeDbBeerList)
            localSource.insertAllToDB(fakeDbBeerList)

            val repositoriesByName = localSource.getAllBeersFromDB()

            val count = localSource.getCountFromDB()
            assertEquals(repositoriesByName.first().id, fakeDbBeerList[0].id)
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
            val result = localSource.getAllBeersFromDB()
            assertEquals(result[0].availability, false)
        }
    }

    @Module
    @InstallIn(ApplicationComponent::class)
    object TestModule {

        @Provides
        @Singleton
        fun provideBeersDao(db: com.simtop.beerdomain.data.databases.BeersDatabase) : com.simtop.beerdomain.data.databases.BeersDao = db.beersDao()

        @Singleton
        @Provides
        fun provideDatabase(@ApplicationContext app: Context): com.simtop.beerdomain.data.databases.BeersDatabase {
            return Room
                .inMemoryDatabaseBuilder(app, com.simtop.beerdomain.data.databases.BeersDatabase::class.java)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

val fakeBeersApiResponseItem2 = com.simtop.beerdomain.data.models.BeersApiResponseItem(
    1,
    "Buzz",
    "A Real Bitter Experience.",
    "",
    "",
    0.0,
    0.0,
    emptyList()
)

val fakeBeerApiResponse2 = listOf(fakeBeersApiResponseItem2.copy())

val fakeBeerModel2 = com.simtop.beerdomain.domain.models.Beer(
    1,
    "Buzz",
    "A Real Bitter Experience.",
    "",
    "",
    0.0,
    0.0,
    emptyList()
)


val fakeBeerListModel2 = listOf(fakeBeerModel2.copy())

val fakeDbBeerList = listOf(com.simtop.beerdomain.data.mappers.BeersMapper.fromBeerToBeerDbModel(fakeBeerModel2))

