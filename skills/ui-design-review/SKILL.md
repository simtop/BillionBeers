---
name: ui-design-review
description: Critique an existing app screen and render comparable code changes as visual alternatives, then produce a report with before-and-after screenshots, tradeoffs, and a recommendation. Use for screenshot-backed UI design reviews and exploring design options. Keep ordinary bug reproduction and fixes in the project's QA workflow.
---

# UI design review

Produce a critique the user can evaluate visually. Show the current screen beside each rendered alternative, explain the changes, and recommend an option or keeping the current design.

If the user asks for a text-only critique, use available screenshots as references and stop after the recommendations. Render code changes when the request includes visual alternatives.

## Establish the baseline

Identify the screen, primary task, platform, source owners, and existing screenshot mechanism. Prefer deterministic screen previews or screenshot tests over launching the app. Include the host's navigation when reviewing the whole screen. A component capture cannot establish whole-screen quality.

Read the actual screenshots and relevant production code before judging. Record what already works. Separate observed defects, design preferences, and behavior that a still image cannot establish. Do not infer usability improvements from a prettier image or a passing screenshot test.

If `qa/design-review.json` exists, use its screen cases and commands. Otherwise find the project's existing capture commands. Read [capture-and-compare.md](references/capture-and-compare.md) for the helper commands and report format. For Android in BillionBeers, read [billionbeers.md](references/billionbeers.md).

Freeze baseline images before editing. Keep theme, font scale, viewport, locale, content, image state, and scroll position equal within every comparison. Loaded, offline image fixtures are preferable for visual composition. Identify synthetic artwork and data in the report. Keep loading previews as separate state coverage.

## Choose a visual direction

Treat visual identity as part of the brief. Establish the user's desired character, references, and explicit preferences before proposing changes. State what should remain recognizable and which conventions are functional rather than stylistic. Native components provide interaction foundations; their default appearance is not the design goal.

When earlier critiques or experiments exist, read their images and findings. Extract useful decisions and failures, including density costs, loss of semantic color, and generic retrieval results. Carry forward the user's reactions. Do not repeat a conservative option merely because a previous report recommended it.

For original or app-inspired work, read [visual-directions.md](references/visual-directions.md). It explains reference selection, distinct hypotheses, and a final synthesis. Use outside skills as explicit contributions to a direction, not as independent proof of quality. A skill's generic palette or web template is not a product identity.

## Propose and render alternatives

Match the breadth of alternatives to the request. A focused correction can start with the smallest effective change. Exploration should include genuinely different visual directions, rather than three levels of restraint. Usually three or four options suffice; use fewer when they repeat each other. When the user requests a fusion, render it last and explain which decisions it takes from each preceding proposal and which it rejects. A valid outcome is that the current screen wins.

Judge native conventions, task clarity, information hierarchy, typography, grouping, density, action discovery, state feedback, and accessibility. Preserve meaningful color distinctions and useful content density. A platform guideline supports a choice; it does not establish that every existing card, badge, or icon is wrong. Read an additional design skill only when its guidance answers a concrete question, and identify that contribution.

Check a direction on a second representative screen when the request includes one. Carry a coherent identity across the flow while letting each screen serve its task; a catalog needs scanning, while detail can give the product more space.

For alternatives, change the actual rendering code in an isolated checkout. Start every option from the same source baseline. Preserve existing behavior and callbacks. A newly proposed interaction needs an explicit event/state contract. Show expanded and collapsed states when relevant, and disclose unwired behavior. Do not present a locally filtered page as full-catalog search. Do not replace working screens with a parallel mock implementation to make screenshots easier. Do not change the fixtures to make an option look better. Save the source patch and a named Git stash for every option, including revisions. Read [capture-and-compare.md](references/capture-and-compare.md) for the preservation command and recovery workflow. A patch alone does not satisfy experiment preservation.

An existing clean checkout can be copied or put in a temporary Git worktree. If the user's checkout has changes, carry the relevant tracked diff and required new files into the isolated copy. Never reset or overwrite the user's working files. Keep reports, captures, source patches and stash manifests under the project’s `design/reviews/<run>/` directory, ignored locally. The HTML report belongs in that directory too. Honor a user-chosen destination when supplied. Do not update the user's accepted goldens with proposed designs.

Use the existing screenshot record command in the isolated checkout, then capture the resulting PNGs and their source provenance. The helper rejects ambiguous or missing cases and records fixture hashes. Compare every configured state, including dark theme and larger text when available. An Android-rendered shared Compose screen is Android evidence, not an iOS, browser, or Desktop screenshot.

Inspect every final image. Batch one correction pass for broken captures, unintended drift, or obvious implementation mistakes. Stop rather than polishing endlessly. Record build failures and unavailable platforms honestly. Do not create AI-painted images and label them as screenshot-test results.

## Deliver the critique

Write the report using the helper's format. Put the skill or skills used in each option's title. For each option, show baseline and output, the original finding, changes made, observed result, and tradeoffs. Include corresponding state pairs so a light-theme improvement cannot conceal a dark-theme regression. Record the design brief, lessons from previous experiments, reference links and the specific patterns transferred. Explain the synthesis decision by decision; combining all proposals indiscriminately is not a recommendation.

Lead the conclusion with your recommendation and the evidence that supports it. A smaller change or the unchanged screen can win. Do not invent taste scores, user-study results, or interaction measurements. Distinguish visual judgment, screenshot verification, and runtime testing. Mention shared model context if comparing design skills without isolated runs.

Deliver an HTML report with embedded images and a Markdown report, plus the source patches and capture manifests. Link the report and show at least one before-and-after pair in chat. State which platforms were rendered and which checks ran. Leave the product design unchanged unless the user also asked to adopt an option. Before cleaning a temporary checkout, verify every option has a saved stash OID, source patch and capture manifest. Keep those stashes until the user requests removal. Recover with `git stash apply <OID>` so the original remains available; selectively restore files when composing a mix. Clean up only temporary files and checkouts created for this review.
