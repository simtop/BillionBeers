package com.simtop.testing_utils_android

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry

open class BaseTestRobot(private val composeTestRule: ComposeTestRule) {

  /**
   * Fails if any node a user can activate reaches a screen reader unlabelled - no
   * `contentDescription`, no text, no editable text. That is the single most common Compose
   * accessibility defect, and it is invisible to every other rung of the ladder: an
   * `Icon(contentDescription = null)` inside an `IconButton` renders identically and passes
   * screenshot verification.
   *
   * Read against the *merged* semantics tree on purpose. A clickable `Row` holding a `Text` owns no
   * label of its own but merges its children's, which is exactly what TalkBack announces - checking
   * the unmerged tree would flag every such row as a false positive.
   *
   * Why this and not the Accessibility Test Framework, which is the usual recipe (Espresso's
   * `AccessibilityChecks.enable()`, or ATF behind Compose's `composeAccessibilityValidator`):
   * **measured, ATF is blind to Compose here.** It builds its hierarchy by walking `View` children,
   * and a Compose screen is one `AndroidComposeView` whose content lives in virtual
   * `AccessibilityNodeInfo` nodes it never descends into. Wired up against espresso-accessibility
   * 3.7.0 (ATF 3.1.2) it returned exactly one finding on every screen - `SpeakableTextPresentCheck`
   * against `AndroidComposeView` itself, id -1 - and a deliberately unlabelled back button added to
   * the search screen did **not** change that result. One guaranteed false positive, zero true
   * positives. Revisit only with an ATF that accepts an `AccessibilityNodeInfo` root.
   */
  fun assertEveryClickableIsLabelled() {
    val candidates =
      composeTestRule.onAllNodes(
        SemanticsMatcher("clickable node with no screen-reader label") { node ->
          node.config.contains(SemanticsActions.OnClick) &&
            node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
            node.config.getOrNull(SemanticsProperties.EditableText) == null
        }
      )

    // Displayed only. The semantics tree also holds what is composed but off-screen - a closed
    // drawer, a lazy list's prefetched rows - and a screen reader cannot reach those either, so
    // flagging them would report defects no user can hit. Filtered here rather than in the matcher
    // because isDisplayed() is defined on the interaction, not on the node.
    val unlabelled =
      (0 until candidates.fetchSemanticsNodes().size)
        .map { candidates[it] }
        .filter {
          it.isDisplayed()
        }

    if (unlabelled.isNotEmpty()) {
      val detail =
        unlabelled.joinToString("\n") { interaction ->
          val node = interaction.fetchSemanticsNode()
          "  - node ${node.id}, testTag=" +
            "${node.config.getOrNull(SemanticsProperties.TestTag) ?: "(none)"}, " +
            "role=${node.config.getOrNull(SemanticsProperties.Role)}, " +
            "bounds=${node.boundsInRoot}"
        }
      throw AssertionError(
        "${unlabelled.size} clickable node(s) reach a screen reader with no label:\n$detail"
      )
    }
  }

  fun clickOnNodeWithTag(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).performClick()
  }

  fun clickOnNodeWithText(text: String) {
    composeTestRule.onNodeWithText(text).performClick()
  }

  fun clickOnNodeWithContentDescription(label: String) {
    composeTestRule.onNodeWithContentDescription(label).performClick()
  }

  fun assertTextIsDisplayed(text: String) {
    composeTestRule.onNodeWithText(text).assertIsDisplayed()
  }

  fun assertTextIsSelected(text: String) {
    composeTestRule.onNodeWithText(text).assertIsSelected()
  }

  fun assertNodeWithTagIsDisplayed(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).assertIsDisplayed()
  }

  fun assertNodeWithTextsIsDisplayed(vararg texts: String) {
    composeTestRule.onNode(texts.map(::hasText).reduce(SemanticsMatcher::and)).assertIsDisplayed()
  }

  /**
   * Types through the real input connection, one commit per call - not a state assignment. That is
   * the point of doing it on a device: it exercises the IME path a user actually goes through.
   */
  fun typeIntoNodeWithTag(testTag: String, text: String) {
    composeTestRule.onNodeWithTag(testTag).performTextInput(text)
  }

  fun assertNodeWithTagIsFocused(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).assertIsFocused()
  }

  fun assertNodeWithTagHasRole(testTag: String, role: Role) {
    composeTestRule
      .onNodeWithTag(testTag)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, role))
  }

  fun assertNodeWithTagHasStateDescription(testTag: String, description: String) {
    composeTestRule
      .onNodeWithTag(testTag)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
  }

  fun assertTextHasLiveRegion(text: String, mode: LiveRegionMode) {
    composeTestRule
      .onNodeWithText(text)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode))
  }

  /**
   * Scrolls a lazy list by index rather than by pixel offset, so a test states the item it wants
   * rather than a distance that depends on the device's density and item heights.
   */
  fun scrollToIndexInNodeWithTag(testTag: String, index: Int) {
    composeTestRule.onNodeWithTag(testTag).performScrollToIndex(index)
  }

  fun waitForIdle() {
    composeTestRule.waitForIdle()
  }

  fun waitUntil(timeoutMillis: Long = 5000, condition: () -> Boolean) {
    composeTestRule.waitUntil(timeoutMillis, condition)
  }

  fun assertNodeWithContentDescriptionIsDisplayed(label: String) {
    composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
  }

  fun assertNodeWithContentDescriptionDoesNotExist(label: String) {
    composeTestRule.onNodeWithContentDescription(label).assertDoesNotExist()
  }

  fun string(@StringRes resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilNodeWithTextIsDisplayed(text: String, timeoutMillis: Long = 5000) {
    composeTestRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis)
  }

  fun pressBack() {
    Espresso.pressBack()
    composeTestRule.waitForIdle()
  }
}
