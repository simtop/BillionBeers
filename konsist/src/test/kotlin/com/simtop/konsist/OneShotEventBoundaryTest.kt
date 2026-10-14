package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * One-shot UI events go over `Channel(BUFFERED).receiveAsFlow()`, never `MutableSharedFlow`.
 *
 * The reason is a real failure mode, not a style preference: a `SharedFlow` with no active
 * collector drops what it emits. A toast or a navigation command sent while the UI is
 * backgrounded - a config change, a process pause - is simply lost, and the bug it produces is
 * intermittent and blamed on everything else first. A `Channel` buffers instead, so the event is
 * delivered when collection resumes.
 *
 * `BeersListViewModel` and the beerdetail ViewModels already do this; the rule stops the next one
 * from reaching for the more familiar API. State is a different problem - `StateFlow` for screen
 * state is correct and untouched by this rule.
 */
class OneShotEventBoundaryTest {

  @Test
  fun `ViewModels do not use MutableSharedFlow for events`() {
    val viewModels = Konsist.scopeFromProject().classes().withNameEndingWith("ViewModel")
    assertTrue(viewModels.isNotEmpty()) {
      "No ViewModel classes were discovered; this boundary would pass vacuously"
    }

    viewModels.assertFalse { viewModel ->
      viewModel.containingFile.hasImport { import ->
        import.name == "kotlinx.coroutines.flow.MutableSharedFlow"
      }
    }
  }
}
