package com.simtop.beer_database.localsources

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

interface BeersLocalSource {
  fun getAllBeersFromDB(): Flow<List<BeerDbModel>>

  /**
   * Keyed upsert: new ids are inserted (their `availability` seeded from the fetched row); existing
   * ids get every column updated *except* `availability`, which is treated as local-only and must
   * survive refreshes. The server's `available` field therefore only seeds first inserts. See
   * [com.simtop.beer_database.database.BeersDao.insertAll].
   */
  suspend fun insertAllToDB(beers: List<BeerDbModel>)

  /**
   * Same keyed upsert as [insertAllToDB], but also records the paging bookmark for [surface] in the
   * *same transaction* so resume position can't diverge from the stored rows. See
   * [com.simtop.beer_database.database.BeersDao.insertPage].
   */
  suspend fun insertPageToDB(
    beers: List<BeerDbModel>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  )

  suspend fun getPagingState(surface: String): PagingStateDbModel?

  /**
   * Total bookmarks across all surfaces - zero means a legacy cache written before paging_state.
   */
  suspend fun countPagingStates(): Int

  suspend fun updateBeer(primaryKey: String, availability: Boolean)

  suspend fun deleteAllFromDB()

  suspend fun getCountFromDB(): Int
}

@Inject
class BeersLocalSourceImpl(private val db: BeersDatabase) : BeersLocalSource {

  override fun getAllBeersFromDB(): Flow<List<BeerDbModel>> = db.beersDao().getAllBeers()

  override suspend fun insertAllToDB(beers: List<BeerDbModel>) = db.beersDao().insertAll(beers)

  override suspend fun insertPageToDB(
    beers: List<BeerDbModel>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) = db.beersDao().insertPage(beers, surface, nextKey, totalCount, System.currentTimeMillis())

  override suspend fun getPagingState(surface: String) = db.beersDao().getPagingState(surface)

  override suspend fun countPagingStates() = db.beersDao().countPagingStates()

  override suspend fun updateBeer(primaryKey: String, availability: Boolean) =
    db.beersDao().updateBeer(primaryKey, availability)

  override suspend fun deleteAllFromDB() = db.beersDao().deleteAll()

  override suspend fun getCountFromDB() = db.beersDao().getCount()
}
