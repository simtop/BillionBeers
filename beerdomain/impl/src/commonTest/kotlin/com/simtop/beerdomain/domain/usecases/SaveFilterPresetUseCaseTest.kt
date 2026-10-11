package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.errors.SaveFilterPresetError
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SaveFilterPresetUseCaseTest {
  @Test
  fun `normalizes the name and retains the complete query`() = runTest {
    val repository = FakeBeersRepository()
    val clock = RecordingClock(42L)
    val idProvider = RecordingIdProvider()
    val query = BeersQuery(search = "ipa", styleId = "style-1", breweryId = "brewery-1")
    val useCase =
      SaveFilterPresetUseCase(
        repository = repository,
        epochTimeProvider = clock,
        idProvider = idProvider,
      )

    val result = useCase("  My IPA\n", query)

    assertEquals(Either.Right(Unit), result)
    assertEquals(
      SavedFilterPreset("id-1", "My IPA", query, 42L),
      repository.observeSavedFilterPresets().first().single(),
    )
    assertEquals(1, clock.calls.size)
  }

  @Test
  fun `invalid names return InvalidName without consulting dependencies`() = runTest {
    val repository = FakeBeersRepository()
    val clock = RecordingClock(42L)
    val idProvider = RecordingIdProvider()
    val useCase =
      SaveFilterPresetUseCase(
        repository = repository,
        epochTimeProvider = clock,
        idProvider = idProvider,
      )

    listOf("", " \t\n", "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH + 1)).forEach {
      assertEquals(Either.Left(SaveFilterPresetError.InvalidName), useCase(it, BeersQuery()))
    }

    assertTrue(repository.saveFilterPresetRequests.isEmpty())
    assertTrue(clock.calls.isEmpty())
    assertEquals(0, idProvider.calls)
  }

  @Test
  fun `maximum normalized length succeeds`() = runTest {
    val repository = FakeBeersRepository()
    val name = "x".repeat(SavedFilterPreset.MAX_NAME_LENGTH)

    val result = SaveFilterPresetUseCase(repository, RecordingClock(1L))(" $name ", BeersQuery())

    assertEquals(Either.Right(Unit), result)
    assertEquals(name, repository.observeSavedFilterPresets().first().single().name)
  }

  @Test
  fun `exact name and query update while a different query creates a preset`() = runTest {
    val repository = FakeBeersRepository()
    val idProvider = RecordingIdProvider()
    val useCase =
      SaveFilterPresetUseCase(repository, RecordingClock(1L), idProvider)
    val firstQuery = BeersQuery(styleId = "style-1")
    val secondQuery = BeersQuery(breweryId = "brewery-1")

    useCase("IPA", firstQuery)
    useCase(" IPA ", firstQuery)
    useCase("IPA", secondQuery)

    val presets = repository.observeSavedFilterPresets().first()
    assertEquals(2, presets.size)
    assertEquals(setOf(firstQuery, secondQuery), presets.map { it.query }.toSet())
    assertEquals(2, idProvider.calls)
  }

  @Test
  fun `hash-colliding names remain separate presets`() = runTest {
    val repository = FakeBeersRepository()
    val query = BeersQuery(search = "ipa")
    val useCase = SaveFilterPresetUseCase(repository, RecordingClock(1L), RecordingIdProvider())

    assertEquals("Aa".hashCode(), "BB".hashCode())
    useCase("Aa", query)
    useCase("BB", query)

    val presets = repository.observeSavedFilterPresets().first()
    assertEquals(setOf("Aa", "BB"), presets.map { it.name }.toSet())
    assertEquals(2, presets.map { it.id }.toSet().size)
  }

  @Test
  fun `saving the old tuple after rename does not overwrite renamed preset`() = runTest {
    val repository = FakeBeersRepository()
    val idProvider = RecordingIdProvider()
    val query = BeersQuery(search = "ipa")
    val save = SaveFilterPresetUseCase(repository, RecordingClock(1L), idProvider)

    save("A", query)
    val originalId = repository.observeSavedFilterPresets().first().single().id
    RenameFilterPresetUseCase(repository, RecordingClock(2L))(originalId, "B")
    save("A", query)

    val presets = repository.observeSavedFilterPresets().first()
    assertEquals(setOf("A", "B"), presets.map { it.name }.toSet())
    assertEquals(2, presets.map { it.id }.toSet().size)
  }

  @Test
  fun `repository failures are propagated unchanged`() = runTest {
    val repository = FakeBeersRepository()
    val unknown = SaveFilterPresetError.Unknown(IllegalStateException("storage"))
    val useCase = SaveFilterPresetUseCase(repository, RecordingClock(1L))

    repository.saveFilterPresetError = SaveFilterPresetError.CapacityReached
    assertEquals(
      Either.Left(SaveFilterPresetError.CapacityReached),
      useCase("IPA", BeersQuery()),
    )

    repository.saveFilterPresetError = unknown
    assertEquals(Either.Left(unknown), useCase("IPA", BeersQuery()))
  }

  @Test
  fun `cancellation propagates`() = runTest {
    val repository = FakeBeersRepository()
    repository.saveFilterPresetException = CancellationException("cancel")

    assertFailsWith<CancellationException> {
      SaveFilterPresetUseCase(repository, RecordingClock(1L))("IPA", BeersQuery())
    }
  }

  private class RecordingClock(private val timestamp: Long) : EpochTimeProvider {
    val calls = mutableListOf<Unit>()

    override fun epochMillis(): Long {
      calls += Unit
      return timestamp
    }
  }

  private class RecordingIdProvider : SavedFilterPresetIdProvider {
    var calls = 0
      private set

    override fun newId(): String {
      calls += 1
      return "id-$calls"
    }
  }
}
