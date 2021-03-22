package com.simtop.billionbeers

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.beerdomain.data.databases.BeersDatabase
import com.simtop.beerdomain.data.localsources.BeersLocalSource
import com.simtop.billionbeers.di.fakeBeerModel2
import com.simtop.billionbeers.di.fakeDbBeerList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class LocalDataSourceTest {

    private lateinit var localSource: BeersLocalSource
    private lateinit var db: BeersDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BeersDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        localSource = BeersLocalSource(db)
    }

    @After
    fun closeDb() {
        db.close()
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
}