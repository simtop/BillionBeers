# Accessibility release QA

This checklist is the manual complement to Compose semantics tests and screenshot coverage. It is not
replaced by an instrumented test: release candidates must be exercised with TalkBack and a hardware
keyboard on real devices.

## Matrix

Run the checklist for:

- English (`en`) and French (`fr`) resource builds;
- Arabic (`ar`) with right-to-left layout;
- compact phone width;
- expanded tablet or unfolded-foldable width;
- normal, 1.5x, and 2.0x system font scales;
- light and dark themes.

Use representative catalog, search, browse, and detail states, including loading, error, retry,
long-content, and end-of-list states.

## TalkBack

- [ ] Focus starts on a sensible, visible control and follows reading order from app bar to content.
- [ ] Back, search, browse, clear, tabs, beer rows, retry, and availability controls announce a useful label.
- [ ] Interactive controls announce the correct role: button, tab, or text field.
- [ ] The availability control announces its current state, and the announcement changes after toggling.
- [ ] Selecting the Browse tabs announces the selected tab and does not leave focus stranded.
- [ ] Error messages and retry affordances are announced after an error appears.
- [ ] Pull-to-refresh, load-more failure, and end-of-list feedback are understandable without sight.
- [ ] Long names, descriptions, and translated strings are not truncated in a way that hides meaning.
- [ ] RTL reading order, back navigation, icons, and scrolling feel natural in Arabic.
- [ ] The rotor or controls navigation reaches every actionable element and skips decorative images.
- [ ] Focus remains usable when content changes, when a dialog appears, and after returning from detail.

## Keyboard and IME

- [ ] Tab and Shift-Tab traverse controls in a logical order on an expanded device.
- [ ] Enter or Space activates focused buttons, tabs, rows, retry, and availability actions.
- [ ] The search field receives focus, exposes its label/hint, and accepts text from a physical keyboard.
- [ ] The clear action becomes reachable when text is present and returns focus to a sensible location.
- [ ] The IME action and Back dismiss or navigate as expected without losing the query.
- [ ] The keyboard does not cover the active field, error action, or availability control.
- [ ] Focus and traversal remain logical at 1.5x and 2.0x font scales and in RTL.

## Reduced motion and form factors

- [ ] With system animator duration set to zero, state changes remain understandable and no control is
  stranded by an animation.
- [ ] On a compact phone, content scrolls without horizontal clipping or unreachable controls.
- [ ] On a tablet and an unfolded foldable, expanded content uses the available width without making
  text or actions uncomfortably distant.
- [ ] Rotation or fold/unfold preserves the current destination and does not lose the active query,
  selected tab, or visible state unexpectedly.

Record device, Android version, locale, font scale, theme, and any failed step with the release QA
result. Adaptive multi-pane navigation is a separate implementation follow-up; this checklist is
intended to expose its product-level failures before that work lands.
