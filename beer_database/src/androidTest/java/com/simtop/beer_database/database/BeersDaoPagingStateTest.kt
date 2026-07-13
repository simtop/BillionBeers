package com.simtop.beer_database.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real [BeersDao.insertPage] against Room (the JVM tests only cover the fake mirror):
 * the page rows and the bookmark are written together, and [PagingStateDbModel.nextKey] advances
 * but never rewinds.
 */
@RunWith(AndroidJUnit4::class)
class BeersDaoPagingStateTest {

  private lateinit var db: BeersDatabase
  private lateinit var dao: BeersDao

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          BeersDatabase::class.java,
        )
        .build()
    dao = db.beersDao()
  }

  @After fun tearDown() = db.close()

  private fun beer(id: String) =
    BeerDbModel(id, "Beer $id", "", "", "", 0.0, 0.0, "[]", availability = true)

  @Test
  fun insertPage_writesRowsAndBookmarkTogether() = runBlocking {
    dao.insertPage(listOf(beer("1")), surface = "catalog:en", nextKey = 2, totalCount = 206, 0L)

    assertEquals(1, dao.getCount())
    val state = dao.getPagingState("catalog:en")
    assertEquals(2, state?.nextKey)
    assertEquals(206, state?.totalCount)
  }

  @Test
  fun insertPage_advancesBookmarkButNeverRewinds() = runBlocking {
    dao.insertPage(listOf(beer("1")), surface = "catalog:en", nextKey = 2, totalCount = 206, 0L)
    // A later page advances the bookmark...
    dao.insertPage(listOf(beer("2")), surface = "catalog:en", nextKey = 5, totalCount = 206, 1L)
    assertEquals(5, dao.getPagingState("catalog:en")?.nextKey)

    // ...but a refresh re-writing an earlier page must not rewind it (monotonic merge).
    dao.insertPage(listOf(beer("1")), surface = "catalog:en", nextKey = 2, totalCount = 206, 2L)
    assertEquals(5, dao.getPagingState("catalog:en")?.nextKey)
  }

  @Test
  fun insertPage_scopesBookmarksBySurface() = runBlocking {
    dao.insertPage(listOf(beer("1")), surface = "catalog:en", nextKey = 2, totalCount = 206, 0L)
    dao.insertPage(listOf(beer("2")), surface = "catalog:fr", nextKey = 9, totalCount = 300, 0L)

    assertEquals(2, dao.getPagingState("catalog:en")?.nextKey)
    assertEquals(9, dao.getPagingState("catalog:fr")?.nextKey)
  }
}
