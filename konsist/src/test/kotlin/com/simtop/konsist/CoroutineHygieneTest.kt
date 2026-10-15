package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Enforces the flow and cancellation rules identified in QAudit 2.8. */
class CoroutineHygieneTest {

  @Test
  fun `suspend functions do not use runCatching`() {
    val functions = projectFunctions()
    val suspendFunctions = functions.filter { it.hasSuspendModifier }
    assertTrue(suspendFunctions.isNotEmpty()) {
      "No suspend functions were discovered; the coroutine hygiene rule would pass vacuously"
    }

    val violations = suspendFunctions.filter {
      hasRunCatchingInSuspendFunction(it.hasSuspendModifier, it.text)
    }
    assertTrue(violations.isEmpty()) {
      "runCatching inside suspend functions can swallow cancellation: ${violations.describe()}"
    }
  }

  @Test
  fun `observe flows do not use catch`() {
    val functions = projectFunctions()
    val violations = functions.filter { function ->
      hasCatchOnObservedFlow(function.name, function.text)
    }

    assertTrue(violations.isEmpty()) {
      "catch completes a long-lived observe flow; use retryWhen: ${violations.describe()}"
    }
  }

  @Test
  fun `runCatching matcher only flags suspend declarations`() {
    val source = "suspend fun load() = runCatching { repository.load() }"

    assertTrue(hasRunCatchingInSuspendFunction(hasSuspendModifier = true, declarationText = source))
    assertFalse(hasRunCatchingInSuspendFunction(hasSuspendModifier = false, source))
    assertFalse(hasRunCatchingInSuspendFunction(hasSuspendModifier = true, "suspend fun load() {}"))
  }

  @Test
  fun `observe flow matcher finds caught flows in observer and consumer functions`() {
    assertTrue(
      hasCatchOnObservedFlow("observeBeers", "fun observeBeers() = source.observe().catch {}")
    )
    assertTrue(hasCatchOnObservedFlow("collectBeers", "source.observeBeers().catch {}"))
    assertFalse(hasCatchOnObservedFlow("loadBeers", "source.load().catch {}"))
    assertFalse(hasCatchOnObservedFlow("observeBeers", "fun observeBeers() = source.observe()"))
  }

  private fun projectFunctions(): List<KoFunctionDeclaration> =
    Konsist.scopeFromProject()
      .files
      .filterNot { file ->
        val pathSegments = file.path.split('/')
        pathSegments.any { segment -> segment == "test" || segment.endsWith("Test") } ||
          pathSegments.windowed(2).any { (parent, child) ->
            parent == ".claude" && child == "worktrees"
          }
      }
      .flatMap { it.functions() }

  private fun List<KoFunctionDeclaration>.describe(): String = joinToString { function ->
    "${function.containingFile.path}:${function.name}"
  }

  private fun hasRunCatchingInSuspendFunction(
    hasSuspendModifier: Boolean,
    declarationText: String,
  ): Boolean = hasSuspendModifier && RUN_CATCHING_PATTERN.containsMatchIn(declarationText)

  private fun hasCatchOnObservedFlow(
    functionName: String,
    declarationText: String,
  ): Boolean =
    CATCH_OPERATOR_PATTERN.containsMatchIn(declarationText) &&
      (functionName.startsWith("observe") || OBSERVE_CALL_PATTERN.containsMatchIn(declarationText))

  private companion object {
    val RUN_CATCHING_PATTERN = Regex("\\brunCatching\\b")
    val CATCH_OPERATOR_PATTERN = Regex("\\.\\s*catch\\s*(?:\\{|\\()")
    val OBSERVE_CALL_PATTERN = Regex("\\bobserve[A-Z]\\w*\\s*\\(")
  }
}
