package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.errors.MutateFilterPresetError
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.Either
import com.simtop.core.core.EpochTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class RenameFilterPresetUseCaseTest {
  @Test
  fun `normalizes the name and delegates the injected time`() = runTest {
    val repository = FakeBeersRepository()
    val clock = RecordingClock(42L)
    val useCase = RenameFilterPresetUseCase(repository, clock)

    val result = useCase("preset-id", "  India Pale Ale\n")

    assertEquals(Either.Right(Unit), result)
    assertEquals(listOf(Triple("preset-id", "India Pale Ale", 42L)), repository.renameFilterPresetRequests)
    assertEquals(1, clock.calls.size)
  }

  @Test
  fun `invalid names return InvalidName without consulting dependencies`() = runTest {
    val repository = FakeBeersRepository()
    val clock = RecordingClock(42L)
    val useCase = RenameFilterPresetUseCase(repository, clock)

    listOf("", " \t\n", "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH + 1)).forEach {
      assertEquals(Either.Left(MutateFilterPresetError.InvalidName), useCase("preset-id", it))
    }

    assertTrue(repository.renameFilterPresetRequests.isEmpty())
    assertTrue(clock.calls.isEmpty())
  }

  @Test
  fun `maximum normalized length succeeds`() = runTest {
    val repository = FakeBeersRepository()
    val name = "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH)

    val result = RenameFilterPresetUseCase(repository, RecordingClock(1L))("preset-id", " $name ")

    assertEquals(Either.Right(Unit), result)
    assertEquals(listOf(Triple("preset-id", name, 1L)), repository.renameFilterPresetRequests)
  }

  @Test
  fun `repository failure is propagated unchanged`() = runTest {
    val repository = FakeBeersRepository()
    val error = MutateFilterPresetError.Unknown(IllegalStateException("storage"))
    repository.mutateFilterPresetError = error

    val result = RenameFilterPresetUseCase(repository, RecordingClock(1L))("preset-id", "IPA")

    assertEquals(Either.Left(error), result)
  }

  @Test
  fun `cancellation propagates`() = runTest {
    val repository = FakeBeersRepository()
    repository.mutateFilterPresetException = CancellationException("cancel")

    assertFailsWith<CancellationException> {
      RenameFilterPresetUseCase(repository, RecordingClock(1L))("preset-id", "IPA")
    }
  }

  private class RecordingClock(private val timestamp: Long) : EpochTimeProvider {
    val calls = mutableListOf<Unit>()

    override fun epochMillis(): Long {
      calls += Unit
      return timestamp
    }
  }
}
