# Preserve behavior across a visual change

Use this reference when a review changes shared UI or adopts an option. A screenshot establishes a rendered state; callbacks, recovery and navigation need runtime evidence.

## Establish the affected flow

Trace the reviewed control through its source owner and callers. Include related entry routes when they render the same action. Record a compact inventory: route, user task, state, source owner, capture case and relevant runtime check. Start with the affected flow rather than an exhaustive app matrix.

For disclosure or search, identify the current query/selection, opening and closing actions, save eligibility, callback criteria, failure feedback, and Back/focus behavior. Include asynchronous completion and restoration when the action already supports them. For scrolling, record expanded, partial, compact and actual scrolled-body states when relevant; a seeded compact header does not establish body scrolling or gesture behavior.

Choose what stays invariant before coding. Treat a changed callback, search scope or collapse policy as an interaction decision with its own reason and verification, rather than an incidental rebrand. Check shared consumers before concluding that a correction on the primary screen is complete.

## Capture and test what the proposal changes

Keep data, theme, locale, effective viewport, font scale and state comparable. Verify the renderer's actual configuration and inspect its output. Requested preview dimensions or an RTL label can differ from what the harness renders. Distinguish feature content from a full host screen with navigation.

Keep new data/art fixtures in debug/test sources, separate from the production renderer being varied. Declare their providers and default-model dependencies as fixtures. A wrapper hash alone cannot freeze data imported from an editable renderer. When fixture/case coverage changes, freeze a fresh common baseline before comparing options.

Use existing native tests or replay the real flow for changed behavior. Useful assertions include the criteria delivered by a Save action, draft retention after failure, focus after delayed completion, Back dismissal order, and body space released by a collapsing header. Adapt these examples to the task; do not require every check for a color-only change.

Inspect new or changed PNGs before accepting intentional goldens. Verify unchanged states against their existing goldens. If previews share production packaging or add dependencies/assets, check the applicable packaged-build gates; a passing debug preview does not establish release behavior.

## Adopt with recoverable evidence

Preserve every option and revision with its named stash, patch and capture manifest before switching or cleaning experiments. Use a stable stash OID. Recover on an isolated adoption branch and inspect selected files/hunks when composing a mix; keep the originals available.

Apply an already-authorized selection, run checks that can falsify its visual and behavioral claims, and capture the implemented result under `design/`. Update the recommendation with observed outcomes, costs and remaining coverage limits. Keep model critique and reports local. Existing deterministic CI gates remain the project's responsibility.
