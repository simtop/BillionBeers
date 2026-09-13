package com.simtop.billionbeers.presentation

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class AppNavigationViewModelTest {

  @Test
  fun `cached deep link beer resolves from local repository`() = runTest {
    val cachedBeer = Beer.empty.copy(id = "42", name = "Cached")
    val viewModel = AppNavigationViewModel(FakeBeersRepository(listOf(cachedBeer)))

    expectThat(viewModel.resolveBeer("42")).isEqualTo(cachedBeer)
  }

  @Test
  fun `uncached deep link beer does not trigger remote lookup`() = runTest {
    val viewModel = AppNavigationViewModel(FakeBeersRepository())

    expectThat(viewModel.resolveBeer("42")).isEqualTo(null)
  }
}
