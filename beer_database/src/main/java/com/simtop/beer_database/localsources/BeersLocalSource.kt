package com.simtop.beer_database.localsources

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.models.BeerDbModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

interface BeersLocalSource {
  fun getAllBeersFromDB(): Flow<List<BeerDbModel>>

  suspend fun insertAllToDB(beers: List<BeerDbModel>)

  suspend fun updateBeer(primaryKey: String, availability: Boolean)

  fun deleteAllFromDB()

  suspend fun getCountFromDB(): Int
}

@Inject
class BeersLocalSourceImpl(private val db: BeersDatabase) : BeersLocalSource {

  override fun getAllBeersFromDB(): Flow<List<BeerDbModel>> = db.beersDao().getAllBeers()

  override suspend fun insertAllToDB(beers: List<BeerDbModel>) = db.beersDao().insertAll(beers)

  override suspend fun updateBeer(primaryKey: String, availability: Boolean) =
    db.beersDao().updateBeer(primaryKey, availability)

  override fun deleteAllFromDB() = db.beersDao().deleteAll()

  override suspend fun getCountFromDB() = db.beersDao().getCount()
}
