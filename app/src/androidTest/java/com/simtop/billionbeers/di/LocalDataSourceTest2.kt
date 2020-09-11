package com.simtop.billionbeers.di


import android.database.sqlite.SQLiteConstraintException
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.billionbeers.data.database.BeersDatabase
import com.simtop.billionbeers.data.localsource.BeersLocalSource
import com.simtop.billionbeers.data.mappers.BeersMapper
import com.simtop.billionbeers.data.models.BeersApiResponseItem
import com.simtop.billionbeers.domain.models.Beer
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4ClassRunner::class)
class LocalDataSourceTest : BaseTest() {

    private lateinit var localSource: BeersLocalSource

    @Inject
    lateinit var db: BeersDatabase

    init {
        injectTest()
    }

    @Before
    fun setUp() {
        localSource = BeersLocalSource(db)
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

    override fun injectTest() {
        (application.appComponent as TestApplicationComponent)
            .inject(this)
    }
}

val fakeBeersApiResponseItem2 = BeersApiResponseItem(
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

val fakeBeerModel2 = Beer(
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

val fakeDbBeerList = listOf(BeersMapper.fromBeerToBeerDbModel(fakeBeerModel2))

