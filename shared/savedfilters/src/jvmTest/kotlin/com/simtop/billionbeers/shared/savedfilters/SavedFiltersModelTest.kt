package com.simtop.billionbeers.shared.savedfilters

import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.Either
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SavedFiltersModelTest {
  @Test
  fun `presets reflect repository changes and rename and delete delegate to repository`() =
    runTest {
      val repository = FakeBeersRepository()
      val model = SavedFiltersModel(repository)
      val preset = SavedFilterPreset("preset-1", "IPA", BeersQuery(search = "ipa"), 1L)
      assertIs<Either.Right<Unit>>(repository.saveFilterPreset(preset))

      assertEquals(listOf(preset), model.presets.first())
      assertIs<Either.Right<Unit>>(model.rename(preset.id, " Pale Ale "))
      assertEquals("Pale Ale", model.presets.first().single().name)
      assertIs<Either.Right<Unit>>(model.delete(preset.id))
      assertEquals(emptyList(), model.presets.first())
    }
}
