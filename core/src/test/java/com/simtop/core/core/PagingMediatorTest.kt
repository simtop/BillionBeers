package com.simtop.core.core

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@ExperimentalCoroutinesApi
class PagingMediatorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `loadNextPage should emit LoadingNextPage then Success`() = runTest(testDispatcher) {
        val fetchRemote: suspend (Int) -> List<String> = { 
            delay(100) // Simulate network delay
            listOf("Item 1") 
        }
        
        val mediator = PagingMediator<Int, String>(
            initialKey = 1,
            nextKey = { current, _ -> current + 1 },
            fetchRemote = fetchRemote,
            saveLocal = { },
            fetchLocal = { flowOf(emptyList()) }
        )

        mediator.pagingState.test {
            assertEquals(PagingState.Idle, awaitItem())

            mediator.loadNextPage()
            
            assertEquals(PagingState.LoadingNextPage, awaitItem())
            assertEquals(PagingState.Success, awaitItem())
        }
    }
    
    @Test
    fun `loadNextPage should emit Error when fetchRemote fails`() = runTest(testDispatcher) {
        val fetchRemote: suspend (Int) -> List<String> = { 
            throw RuntimeException("Network error")
        }
        
        val mediator = PagingMediator<Int, String>(
            initialKey = 1,
            nextKey = { current, _ -> current + 1 },
            fetchRemote = fetchRemote,
            saveLocal = { },
            fetchLocal = { flowOf(emptyList()) }
        )

        mediator.pagingState.test {
            assertEquals(PagingState.Idle, awaitItem())

            mediator.loadNextPage()
            
            assertEquals(PagingState.LoadingNextPage, awaitItem())
            val error = awaitItem() as PagingState.Error
            assertEquals("Network error", error.message)
        }
    }
}
