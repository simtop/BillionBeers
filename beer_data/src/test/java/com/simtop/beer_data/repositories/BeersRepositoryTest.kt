package com.simtop.beer_data.repositories

import app.cash.turbine.test
import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.remotesources.BeersRemoteSourceContract
import com.simtop.core.core.PagingState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class BeersRepositoryTest {

    private val beersRemoteSource = mockk<BeersRemoteSourceContract>(relaxed = true)
    private val beersLocalSource = mockk<BeersLocalSource>(relaxed = true)
    private lateinit var beersRepository: BeersRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val dbFlow = MutableStateFlow<List<BeerDbModel>>(emptyList())
        every { beersLocalSource.getAllBeersFromDB() } returns dbFlow
        beersRepository = BeersRepositoryImpl(beersRemoteSource, beersLocalSource)
    }

    @Test
    fun `getBeersFromSingleSource should load first page when DB is empty`() = runTest(testDispatcher) {
        // Mock DB empty
        coEvery { beersLocalSource.getCountFromDB() } returns 0
        
        // Mock Remote
        val remoteBeer = BeersApiResponseItem(
            id = "1",
            name = "Beer 1",
            abv = 5.0,
            ibu = 20.0,
            imageId = "url",
            translations = emptyList(),
            foodPairing = emptyList()
        )
        coEvery { beersRemoteSource.getListOfBeers(1) } returns listOf(remoteBeer)
        
        // Mock Local Insert
        coEvery { beersLocalSource.insertAllToDB(any()) } returns Unit
        
        // Mock Local Flow
        val dbFlow = MutableStateFlow<List<BeerDbModel>>(emptyList())
        every { beersLocalSource.getAllBeersFromDB() } returns dbFlow

        beersRepository.getBeersFromSingleSource(1).test {
            // Initial empty emission from flow
            assertEquals(emptyList<Any>(), awaitItem())
            
            // Verify loadFirstPage triggered remote fetch
            coVerify(exactly = 1) { beersRemoteSource.getListOfBeers(1) }
            
            // Verify insert
            coVerify(exactly = 1) { beersLocalSource.insertAllToDB(any()) }
        }
    }

    @Test
    fun `loadNextPage should trigger remote fetch and insert`() = runTest(testDispatcher) {
        // Mock DB has items
        coEvery { beersLocalSource.getCountFromDB() } returns 10
        
        // Mock Remote
        val remoteBeer = BeersApiResponseItem(
            id = "1",
            name = "Beer 1",
            abv = 5.0,
            ibu = 20.0,
            imageId = "url",
            translations = emptyList(),
            foodPairing = emptyList()
        )
        // Assuming next page is 1 because initialKey is 1 and we haven't loaded anything yet
        coEvery { beersRemoteSource.getListOfBeers(1) } returns listOf(remoteBeer)
        
        // Mock Local Insert
        coEvery { beersLocalSource.insertAllToDB(any()) } returns Unit
        
        // Mock Local Flow
        val dbFlow = MutableStateFlow<List<BeerDbModel>>(emptyList())
        every { beersLocalSource.getAllBeersFromDB() } returns dbFlow

        // Manually trigger loadNextPage
        // First we need to initialize the mediator state if needed, but loadNextPage should work if we are not loading.
        // However, currentKey starts at 1. loadNextPage uses currentKey.
        // If we haven't loaded first page, currentKey is 1.
        // But wait, if we call loadNextPage directly, it checks if loading.
        
        // We need to simulate that we are ready to load next page.
        // PagingMediator starts with initialKey = 1.
        // If we call loadNextPage, it uses currentKey (1).
        // But we want to test loading page 2?
        // Actually, if we haven't loaded page 1, loadNextPage will load page 1?
        // No, loadNextPage uses currentKey. If currentKey is 1, it loads page 1.
        // If we want to test loading page 2, we must have loaded page 1 first?
        // Or we can just verify it calls getListOfBeers(1) if we assume it's the "next" page from initial state.
        
        // Let's just verify it calls remote source.
        
        beersRepository.loadNextPage()
        testScheduler.advanceUntilIdle()
        
        coVerify(exactly = 1) { beersRemoteSource.getListOfBeers(1) }
        coVerify(exactly = 1) { beersLocalSource.insertAllToDB(any()) }
    }
}
