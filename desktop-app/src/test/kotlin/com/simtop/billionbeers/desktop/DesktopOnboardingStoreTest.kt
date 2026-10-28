package com.simtop.billionbeers.desktop

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopOnboardingStoreTest {
  @Test
  fun completionSurvivesReopenAndResetWithoutTouchingCatalogFiles() = runTest {
    val directory = Files.createTempDirectory("onboarding-test").toFile()
    try {
      val catalog = directory.resolve("beers_database.db").apply { writeText("catalog sentinel") }
      val file = directory.resolve("onboarding.txt")
      assertEquals(emptySet<String>(), DesktopOnboardingStore(file).read())
      DesktopOnboardingStore(file).write(setOf("welcome", "favorites", "future-introduction"))
      assertEquals(
        setOf("welcome", "favorites", "future-introduction"),
        DesktopOnboardingStore(file).read(),
      )
      DesktopOnboardingStore(file).write(emptySet())
      assertEquals(emptySet<String>(), DesktopOnboardingStore(file).read())
      assertEquals("catalog sentinel", catalog.readText())
      assertEquals(
        setOf("beers_database.db", "onboarding.txt"),
        directory.listFiles()!!.map { it.name }.toSet(),
      )
    } finally {
      directory.deleteRecursively()
    }
  }
}
