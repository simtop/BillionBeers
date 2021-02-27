package com.simtop.beerdomain.data.localsources

import com.simtop.beerdomain.data.databases.BeersDatabase
import com.simtop.beerdomain.data.models.BeerDbModel
import javax.inject.Inject

//TODO check if we can do pagination without db, if we can then inject Daos
class BeersLocalSource @Inject constructor(private val db: BeersDatabase) {

    fun getAllBeersFromDB() = db.beersDao().getAllBeers()

    suspend fun insertAllToDB(beers: List<BeerDbModel>) = db.beersDao().insertAll(beers)

    fun updateBeer(primaryKey: Int, availability: Boolean) = db.beersDao()
        .updateBeer(primaryKey, availability)

    fun deleteAllFromDB() = db.beersDao().deleteAll()

    fun getCountFromDB() = db.beersDao().getCount()

}