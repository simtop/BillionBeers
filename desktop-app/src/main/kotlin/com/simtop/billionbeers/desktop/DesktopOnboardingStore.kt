package com.simtop.billionbeers.desktop

import com.simtop.billionbeers.shared.presentation.OnboardingStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small host preference file beside the selected catalog database, including --data-dir. */
internal class DesktopOnboardingStore(
  private val file: File,
  private val io: CoroutineDispatcher = Dispatchers.IO,
) : OnboardingStore {
  override suspend fun read(): Set<String> =
    withContext(io) {
      if (file.exists()) file.readLines().filter(String::isNotEmpty).toSet() else emptySet()
    }

  override suspend fun write(completedIds: Set<String>) =
    withContext(io) {
      val directory = file.absoluteFile.parentFile.toPath()
      Files.createDirectories(directory)
      val temporary = Files.createTempFile(directory, "onboarding-", ".tmp")
      try {
        Files.write(temporary, completedIds.sorted())
        Files.move(
          temporary,
          file.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
        )
        Unit
      } finally {
        Files.deleteIfExists(temporary)
      }
    }
}
