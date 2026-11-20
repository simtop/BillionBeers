package com.simtop.billionbeers.buildlogic

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UnusedDependenciesArtifactTest {
  @TempDir lateinit var temporary: Path

  @Test
  fun `reads class packages from a resolved JAR`() {
    val jar = temporary.resolve("dependency.jar")
    jar.writeBytes(
      archive(
        "com/example/Widget.class" to byteArrayOf(),
        "META-INF/internal/Metadata.class" to byteArrayOf(),
        "module-info.class" to byteArrayOf(),
      )
    )

    assertEquals(setOf("com.example"), dependencyPackages(jar.toFile()))
  }

  @Test
  fun `reads an AAR's classes and bundled libraries`() {
    val aar = temporary.resolve("dependency.aar")
    aar.writeBytes(
      archive(
        "classes.jar" to archive("org/example/Widget.class" to byteArrayOf()),
        "libs/bundled.jar" to archive("org/example/embedded/Helper.class" to byteArrayOf()),
        "res/drawable/icon.xml" to byteArrayOf(),
      )
    )

    assertEquals(setOf("org.example", "org.example.embedded"), dependencyPackages(aar.toFile()))
  }

  @Test
  fun `package matching respects package segment boundaries`() {
    assertTrue(packageMatchesImport("com.example", "com.example.Widget"))
    assertTrue(packageMatchesImport("com.example", "com.example"))
    assertFalse(packageMatchesImport("com.example", "com.examples.Widget"))
  }

  private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      entries.forEach { (name, content) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
      }
    }
    return bytes.toByteArray()
  }
}
