package com.simtop.billionbeers.domain.repository

import com.simtop.billionbeers.domain.models.Beer

interface BeersRepository {
    suspend fun getListOfBeerModels(page : Int): List<Beer>
}