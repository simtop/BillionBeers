package com.simtop.benchmark.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beerdomain.domain.models.Beer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeersMapperBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val beer = Beer(
        id = "1",
        name = "Buzz",
        tagline = "A Real Bitter Experience.",
        description = "A light, golden classic beer brewed with quite a large amount of late hops.",
        imageUrl = "https://images.punkapi.com/v2/keg.png",
        abv = 4.5,
        ibu = 60.0,
        foodPairing = listOf("Spicy chicken wings", "Grilled steak", "Lemon drizzle cake")
    )

    @Test
    fun benchmarkFromBeerToBeerDbModel() {
        benchmarkRule.measureRepeated {
            BeersMapper.fromBeerToBeerDbModel(beer)
        }
    }
}
