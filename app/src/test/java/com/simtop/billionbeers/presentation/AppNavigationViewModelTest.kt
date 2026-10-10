package com.simtop.billionbeers.presentation

import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `cached deep link beer resolves from local repository`() =
    runTest(testDispatcher) {
      val cachedBeer = Beer.empty.copy(id = "42", name = "Cached")
      val viewModel = AppNavigationViewModel(FakeBeersRepository(listOf(cachedBeer)))

      expectThat(viewModel.resolveBeer("42")).isEqualTo(cachedBeer)
    }

  @Test
  fun `uncached deep link beer does not trigger remote lookup`() =
    runTest(testDispatcher) {
      val viewModel = AppNavigationViewModel(FakeBeersRepository())

      expectThat(viewModel.resolveBeer("42")).isEqualTo(null)
    }

  @Test
  fun `saved preset trims its name and retains the complete query`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = AppNavigationViewModel(repository)
      val query = BeersQuery(search = "ipa", styleId = "style-1", breweryId = "brewery-1")

      viewModel.savePreset("  My IPA\n", query)
      testScheduler.runCurrent()

      val saved = repository.observeSavedFilterPresets().first().single()
      expectThat(saved.name).isEqualTo("My IPA")
      expectThat(saved.query).isEqualTo(query)
      expectThat(saved.id.isNotBlank()).isTrue()
    }

  @Test
  fun `blank and overlong names do not save a preset`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = AppNavigationViewModel(repository)

      listOf("", " \t\n", "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH + 1)).forEach { name ->
        expectThat(viewModel.savePreset(name, BeersQuery()))
          .isEqualTo(Either.Left(SaveFilterPresetError.InvalidName))
      }
      testScheduler.runCurrent()

      expectThat(repository.observeSavedFilterPresets().first()).isEqualTo(emptyList())
    }

  @Test
  fun `maximum length is checked after trimming`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val name = "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH)

      AppNavigationViewModel(repository).savePreset(" $name ", BeersQuery())
      testScheduler.runCurrent()

      expectThat(repository.observeSavedFilterPresets().first().single().name).isEqualTo(name)
    }

  @Test
  fun `saving the same normalized name and query updates rather than duplicates the preset`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = AppNavigationViewModel(repository)
      val query = BeersQuery(search = "ipa")

      viewModel.savePreset("IPA", query)
      testScheduler.runCurrent()
      val original = repository.observeSavedFilterPresets().first().single()
      viewModel.savePreset(" IPA ", query)
      testScheduler.runCurrent()

      val saved = repository.observeSavedFilterPresets().first().single()
      expectThat(saved.id).isEqualTo(original.id)
      expectThat(saved.query).isEqualTo(query)
      expectThat(saved.name).isEqualTo("IPA")
    }

  @Test
  fun `presets with the same name retain different queries`() =
    runTest(testDispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = AppNavigationViewModel(repository)
      val queries = listOf(BeersQuery(styleId = "style-1"), BeersQuery(breweryId = "brewery-1"))

      queries.forEach { viewModel.savePreset("IPA", it) }
      testScheduler.runCurrent()

      expectThat(repository.observeSavedFilterPresets().first().map { it.query }.toSet())
        .isEqualTo(queries.toSet())
    }
}
