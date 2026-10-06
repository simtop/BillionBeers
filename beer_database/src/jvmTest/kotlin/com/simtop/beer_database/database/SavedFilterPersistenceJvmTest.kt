package com.simtop.beer_database.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_storage.api.FilterPresetCapacityReachedException
import com.simtop.beer_storage.api.MAX_STORED_FILTER_PRESETS
import com.simtop.beer_storage.api.StoredFilterPreset
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SavedFilterPersistenceJvmTest {
  private val directory = createTempDirectory("saved-filter-persistence").toFile()
  private val file = File(directory, "presets.db").absolutePath
  private var db = openDatabase()
  private val initial = StoredFilterPreset("initial", "IPAs", "ipa", "style-1", null, 1L)
  private val added = StoredFilterPreset("added", "Lagers", "lager", null, "brewery-1", 11L)

  @After
  fun tearDown() {
    db.close()
    directory.deleteRecursively()
  }

  @Test
  fun capacityRejectionPreservesDurableRowsAndAllowsUpdateAndRetry() = runBlocking {
    val storage = BeersLocalSourceImpl(db)
    val presets = (0 until MAX_STORED_FILTER_PRESETS).map { index ->
      initial.copy(id = "preset-$index", name = "Preset $index", updatedAt = index.toLong())
    }
    presets.forEach { storage.saveFilterPreset(it) }
    val before = storage.observeSavedFilterPresets().first()

    try {
      storage.saveFilterPreset(added)
      fail("an additional preset should be rejected at capacity")
    } catch (_: FilterPresetCapacityReachedException) {
      assertEquals(before, storage.observeSavedFilterPresets().first())
    }
    assertReopenedPresets(before)

    val updated = presets.first().copy(name = "Updated", updatedAt = 20L)
    storage.saveFilterPreset(updated)
    storage.deleteFilterPreset(presets.last().id)
    storage.saveFilterPreset(added)
    val expected = (presets.drop(1).dropLast(1) + updated + added).sortedByDescending { it.updatedAt }
    assertEquals(expected, storage.observeSavedFilterPresets().first())
    assertEquals(expected, reopenStorage().observeSavedFilterPresets().first())
  }

  @Test
  fun abortedSavePreservesDurableRowsAndAllowsRetry() = runBlocking {
    assertAbortRecovery("INSERT", listOf(added, initial)) { it.saveFilterPreset(added) }
  }

  @Test
  fun abortedRenamePreservesDurableRowsAndAllowsRetry() = runBlocking {
    assertAbortRecovery("UPDATE", listOf(initial.copy(name = "Renamed", updatedAt = 3L))) {
      it.renameFilterPreset(initial.id, "Renamed", 3L)
    }
  }

  @Test
  fun abortedDeletePreservesDurableRowsAndAllowsRetry() = runBlocking {
    assertAbortRecovery("DELETE", emptyList()) { it.deleteFilterPreset(initial.id) }
  }

  private suspend fun assertAbortRecovery(
    operation: String,
    expected: List<StoredFilterPreset>,
    mutate: suspend (BeersLocalSourceImpl) -> Unit,
  ) {
    val storage = BeersLocalSourceImpl(db)
    storage.saveFilterPreset(initial)
    // Abort after the statement changes the row, exercising SQLite rollback rather than a fake.
    executeSql(
      "CREATE TRIGGER fail_preset_mutation AFTER $operation ON filter_presets " +
        "BEGIN SELECT RAISE(ABORT, 'preset mutation failure'); END"
    )
    val failure = runCatching { mutate(storage) }.exceptionOrNull()
    assertTrue(
      "the trigger should reject the write: $failure",
      failure?.message.orEmpty().contains("preset mutation failure"),
    )
    assertEquals(listOf(initial), storage.observeSavedFilterPresets().first())

    assertReopenedPresets(listOf(initial))
    executeSql("DROP TRIGGER fail_preset_mutation")
    mutate(storage)
    assertEquals(expected, storage.observeSavedFilterPresets().first())
    assertEquals(expected, reopenStorage().observeSavedFilterPresets().first())
  }

  private suspend fun executeSql(sql: String) {
    db.useConnection(false) { connection ->
      connection.usePrepared(sql) { it.step() }
    }
  }

  private suspend fun assertReopenedPresets(expected: List<StoredFilterPreset>) {
    val reopened = openDatabase()
    try {
      assertEquals(expected, BeersLocalSourceImpl(reopened).observeSavedFilterPresets().first())
    } finally {
      reopened.close()
    }
  }

  private fun reopenStorage(): BeersLocalSourceImpl {
    db.close()
    db = openDatabase()
    return BeersLocalSourceImpl(db)
  }

  private fun openDatabase(): BeersDatabase =
    Room.databaseBuilder(file) { BeersDatabaseConstructor.initialize() }
      .setDriver(BundledSQLiteDriver())
      .build()
}
