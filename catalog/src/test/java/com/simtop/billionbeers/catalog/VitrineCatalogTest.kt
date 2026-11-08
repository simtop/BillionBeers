package com.simtop.billionbeers.catalog

import io.github.simtop.vitrine.loadVitrineItems
import org.junit.Assert.assertTrue
import org.junit.Test

class VitrineCatalogTest {
  @Test
  fun discoversDemosFromTheDesignSystemAndPresentationModules() {
    val entries = loadVitrineItems().map { it.tab to it.name }.toSet()

    assertTrue("Missing design system demos: $entries", ("Design System" to "Colors") in entries)
    assertTrue("Missing presentation demos: $entries", ("Utilities" to "ComposeTitle") in entries)
    assertTrue("Missing dialog demo: $entries", ("Utilities" to "Progress Dialog") in entries)
  }
}
