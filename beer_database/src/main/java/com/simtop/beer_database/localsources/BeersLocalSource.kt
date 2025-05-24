package com.simtop.beer_database.localsources

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.models.BeerDbModel
import javax.inject.Inject

//TODO check if we can do pagination without db, if we can then inject Daos
class BeersLocalSource @Inject constructor(private val db: BeersDatabase) {

    fun getAllBeersFromDB() = db.beersDao().getAllBeers()

    suspend fun insertAllToDB(beers: List<BeerDbModel>) = db.beersDao().insertAll(beers)

    fun updateBeer(primaryKey: String, availability: Boolean) = db.beersDao()
        .updateBeer(primaryKey, availability)

    fun deleteAllFromDB() = db.beersDao().deleteAll()

    fun getCountFromDB() = db.beersDao().getCount()

}