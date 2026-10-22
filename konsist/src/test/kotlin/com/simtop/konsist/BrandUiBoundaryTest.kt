package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Product UI consumes the design system; it does not define a second visual system. */
class BrandUiBoundaryTest {
  @TempDir lateinit var fixtures: Path

  @Test
  fun `product controls and visual styles belong to the brand design system`() {
    val root = repoRoot().invariantSeparatorsPath + "/"
    val files =
      Konsist.scopeFromProject().files.filter { isProductSource(it.path.removePrefix(root)) }
    for (owner in
      listOf("app/", "feature/", "shared/", "desktop-app/", "ios-shared/", "web-app/")) {
      assertTrue(
        files.any { it.path.removePrefix(root).startsWith(owner) },
        "No guarded files for $owner",
      )
    }
    val violations = files.flatMap { file -> violations(file).map { "${file.path}: $it" } }
    assertTrue(violations.isEmpty()) {
      "Use the shared/core design-system brand controls and tokens. Layout/state APIs stay local.\n" +
        violations.joinToString("\n")
    }
  }

  @Test
  fun `guard rejects direct aliased wildcard and qualified controls`() {
    for (source in
      listOf(
        "import androidx.compose.material3.Button\nfun ui() { Button(onClick = {}) {} }",
        "import androidx.compose.material3.Button as Action\nfun ui() { Action(onClick = {}) {} }",
        "import androidx.compose.material3.*\nfun ui() { Text(\"Beer\") }",
        "fun ui() { androidx.compose.material3.Text(\"Beer\") }",
        "import androidx.glance.text.Text\nfun ui() { Text(\"Beer\") }",
        "import androidx.compose.foundation.text.BasicText\nfun ui() { BasicText(\"Beer\") }",
      )) assertFalse(checkSource(source).isEmpty(), source)
  }

  @Test
  fun `guard rejects local palettes and typography including aliases`() {
    for (source in
      listOf(
        "import androidx.compose.ui.graphics.Color as Paint\nval tint = Paint(0xFF123456)",
        "val tint = androidx.compose.ui.graphics.Color.Red",
        "import androidx.compose.ui.unit.sp as textSize\nval size = 18.textSize",
        "fun ui() { Text(\"Beer\", fontWeight = weight) }",
        "fun ui() { Text(\"Beer\", style = TextStyle()) }",
        "fun ui() { Theme.typography.bodyLarge.copy(lineHeight = 24.sp) }",
        "import androidx.compose.material3.MaterialTheme as Theme\nval title = Theme.typography.titleLarge",
        "import androidx.compose.material3.TopAppBarDefaults\nval style = TopAppBarDefaults.topAppBarColors()",
      )) assertFalse(checkSource(source).isEmpty(), source)
  }

  @Test
  fun `brand controls and layout state APIs pass without requiring styling on every call`() {
    val source =
      """
      import com.simtop.billionbeers.shared.designsystem.controls.BrandButton
      import com.simtop.billionbeers.shared.designsystem.controls.BrandText
      import androidx.compose.material3.Scaffold
      import androidx.compose.material3.TopAppBarDefaults
      import androidx.compose.ui.unit.dp
      fun ui() {
        val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        BrandButton(onClick = {}) { BrandText("Beer") }
      }
      """
        .trimIndent()
    assertTrue(checkSource(source).isEmpty())
    assertTrue(
      checkSource("// androidx.compose.material3.Text(\"example\")\nval x = \"fontWeight = bold\"")
        .isEmpty()
    )
  }

  @Test
  fun `guard covers production hosts and keeps design system and fixtures separate`() {
    assertTrue(isProductSource("new-feature/src/commonMain/kotlin/Ui.kt"))
    assertTrue(isProductSource("app/src/release/java/Ui.kt"))
    assertTrue(isProductSource("ios-shared/src/iosSimulatorArm64Main/kotlin/Ui.kt"))
    assertTrue(isProductSource("feature/beerslist/src/main/java/Ui.kt"))
    for (path in
      listOf(
        "shared/designsystem/src/commonMain/kotlin/Ui.kt",
        "core/designsystem/src/main/java/Ui.kt",
        "app/src/debug/java/Preview.kt",
        "app/src/androidTest/java/UiTest.kt",
        "catalog/src/main/java/Demo.kt",
        "compose-multiplatform-fixture/src/commonMain/kotlin/Fixture.kt",
        ".claude/worktrees/other/app/src/main/java/Ui.kt",
        "feature/beerslist/build/generated/src/main/Ui.kt",
      )) assertFalse(isProductSource(path), path)
  }

  @Test
  fun `Android color resources belong to the design system`() {
    val root = repoRoot()
    val resourceFiles =
      root
        .walkTopDown()
        .onEnter {
          it.name !in setOf("build", "bin", ".git", ".gradle", ".claude", ".agents")
        }
        .filter { file ->
          val relative = file.relativeTo(root).invariantSeparatorsPath
          file.isFile && file.extension == "xml" && "/res/" in relative && isProductSource(relative)
        }
        .toList()
    assertTrue(resourceFiles.isNotEmpty())
    val violations = resourceFiles.filter {
      Regex("#[0-9a-fA-F]{6,8}\\b").containsMatchIn(it.readText())
    }
    assertTrue(
      violations.isEmpty(),
      "Move literal Android colors to core/designsystem: $violations",
    )
  }

  private fun checkSource(source: String): List<String> {
    val directory = Files.createTempDirectory(fixtures, "case")
    Files.writeString(directory.resolve("Case.kt"), source)
    return violations(Konsist.scopeFromExternalDirectory(directory.toString()).files.single())
  }

  private fun violations(file: KoFileDeclaration): List<String> {
    val imports = file.imports.map { it.name }
    val issues = imports.filter(::forbiddenImport).map { "unbranded import $it" }.toMutableList()
    var code = maskCommentsAndStrings(file.text)
    for (import in file.imports) {
      import.alias?.let { alias ->
        code = code.replace(Regex("\\b${Regex.escape(alias.name)}\\b"), import.name)
      }
    }
    val qualified = Regex("\\bandroidx\\s*\\.\\s*(?:compose|glance)(?:\\s*\\.\\s*(?:\\w+|\\*))*")
    qualified
      .findAll(code)
      .map { it.value.replace(Regex("\\s"), "") }
      .filter(::forbiddenImport)
      .forEach { issues += "unbranded qualified API $it" }
    if (
      Regex("\\b(?:fontSize|fontFamily|fontWeight|fontStyle|lineHeight|letterSpacing)\\s*=")
        .containsMatchIn(code)
    ) {
      issues += "local typography override; add/use a brand text role"
    }
    if (Regex("\\bTextStyle\\s*\\(").containsMatchIn(code)) issues += "local TextStyle construction"
    if (Regex("\\b\\d+(?:\\.\\d+)?\\s*\\.\\s*(?:sp|em)\\b").containsMatchIn(code))
      issues += "local text unit"
    if (
      Regex(
          "TopAppBarDefaults\\s*\\.\\s*\\w*(?:Colors|Height|Elevation)\\b",
          RegexOption.IGNORE_CASE,
        )
        .containsMatchIn(code)
    ) {
      issues += "app-bar appearance belongs to BrandTopAppBar; only scroll policy stays local"
    }
    return issues.distinct()
  }

  private fun forbiddenImport(name: String): Boolean {
    val material = "androidx.compose.material3."
    return when {
      name.startsWith(material) ->
        name.removePrefix(material).substringBefore('.').let {
          it !in materialSupport && it != "adaptive"
        }
      name.startsWith("androidx.compose.material.") ->
        !name.startsWith("androidx.compose.material.icons.")
      else ->
        name == "androidx.compose.ui.graphics.Color" ||
          name.startsWith("androidx.compose.ui.graphics.Color.") ||
          name.startsWith("androidx.compose.ui.text.font.") ||
          name in
            setOf(
              "androidx.compose.ui.unit.sp",
              "androidx.compose.ui.unit.em",
              "androidx.compose.ui.unit.*",
              "androidx.compose.ui.graphics.*",
              "androidx.compose.foundation.text.BasicText",
              "androidx.compose.foundation.text.BasicTextField",
              "androidx.glance.text.Text",
              "androidx.glance.text.TextStyle",
              "androidx.glance.Image",
            )
    }
  }

  private fun isProductSource(path: String): Boolean {
    val parts = path.split('/')
    val excluded =
      parts.any { it in setOf("build", "bin", ".claude", ".agents", ".git") } ||
        path.startsWith("core/designsystem/") ||
        path.startsWith("shared/designsystem/") ||
        parts.first() in setOf("catalog", "catalog-generator", "compose-multiplatform-fixture")
    val sourceSet = parts.windowed(2).firstOrNull { it[0] == "src" }?.get(1).orEmpty()
    return !excluded &&
      (sourceSet in setOf("main", "release", "releaseSmoke") || sourceSet.endsWith("Main"))
  }

  private val materialSupport =
    setOf(
      "Scaffold",
      "SnackbarHostState",
      "ExperimentalMaterial3Api",
      "TopAppBarDefaults",
      "TopAppBarScrollBehavior",
      "TopAppBarState",
      "rememberTopAppBarState",
      "minimumInteractiveComponentSize",
      "LocalContentColor",
      "LocalTextStyle",
      "ProvideTextStyle",
    )
}

/** Keep code offsets while excluding comments and literal prose from the source policy. */
private fun maskCommentsAndStrings(source: String): String = SourceMasker(source).code()

private class SourceMasker(private val source: String) {
  private val result = source.toCharArray()
  private var index = 0

  fun code(): String {
    while (index < source.length) {
      val start = index
      when {
        source.startsWith("//", index) -> lineComment()
        source.startsWith("/*", index) -> blockComment()
        source.startsWith("\"\"\"", index) -> tripleQuoted()
        source[index] == '"' || source[index] == '\'' -> quoted()
        else -> {
          index++
          continue
        }
      }
      for (i in start until index) if (result[i] != '\n') result[i] = ' '
    }
    return String(result)
  }

  private fun lineComment() {
    index = source.indexOf('\n', index).let { if (it < 0) source.length else it }
  }

  private fun blockComment() {
    var depth = 1
    index += 2
    while (index < source.length && depth > 0) {
      when {
        source.startsWith("/*", index) -> {
          depth++
          index += 2
        }
        source.startsWith("*/", index) -> {
          depth--
          index += 2
        }
        else -> index++
      }
    }
  }

  private fun tripleQuoted() {
    index = source.indexOf("\"\"\"", index + 3).let { if (it < 0) source.length else it + 3 }
  }

  private fun quoted() {
    val quote = source[index++]
    while (index < source.length) {
      if (source[index] == '\\') index = (index + 2).coerceAtMost(source.length)
      else if (source[index++] == quote) break
    }
  }
}
