package com.simtop.billionbeers.data.mappers

import com.simtop.billionbeers.data.models.BeersApiResponseItem
import com.simtop.billionbeers.domain.models.Beer
import retrofit2.HttpException

object DataErrorsMapper {
    //TODO: Create different Exceptions for each kind of error
    fun processErrors(exception: Exception, defaultMessage: String = "Default Error "): Exception {
        return when (exception) {
            is HttpException -> {
                when (exception.code()) {
                    404 -> {
                        Exception("No Connection $exception")
                    }
                    //TODO: More cases
                    else -> {
                        Exception("$defaultMessage ${exception.message}")
                    }
                }
            }
            else -> {
                Exception("$defaultMessage ${exception.message}")
            }
        }
    }
}