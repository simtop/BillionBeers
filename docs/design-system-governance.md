# Design-system governance

This document defines the supported surface and review contract for `:core:designsystem`. It is the
working agreement for this repository today; it is not a promise that the module is a published
binary library.

## Ownership

- `:shared:designsystem` owns portable semantic color/spacing/typography tokens and Material mapping.
  `:core:designsystem` supplies the Android theme facade, components and preview annotations.
- `:catalog` owns the interactive catalog shell. Catalog demos remain owned by the module that
  declares them, while `:catalog-processor` owns discovery and generated providers.
- Changes to either surface should be reviewed as design-system changes, even when the code diff is
  small. CODEOWNERS names the current repository owner explicitly so that ownership remains visible
  if the catch-all rule changes.

## API categories

### Supported runtime API

These declarations are safe for application and feature modules to consume:

- `BillionBeersTheme` and its `colors`, `spacing`, and `typography` accessors;
- semantic token value types and their composition locals when a consumer has a documented reason to
  provide a theme override;
- reusable components and helpers whose KDoc describes their behavior and accessibility contract.

A public declaration is not automatically supported API. Before adding one, decide whether a
consumer should be allowed to depend on it and document the decision in the declaration's KDoc.

### Implementation API

Primitive color values, light/dark token instances, Material color-scheme mapping, and private
layout helpers are implementation details. Keep them `internal` or `private`; consumers should use
semantic roles rather than raw palette values.

### Preview and catalog API

Preview annotations and catalog entry points are tooling APIs. They may be public because generated
KSP code or another module needs to load them, but they are not runtime design-system components.
Keep demo containers and preview-only providers out of the supported runtime API unless a consumer
actually needs them.

## Token governance

- Add semantic roles before adding a raw color or dimension.
- Reuse `BillionBeersTheme.colors`, `.spacing`, and `.typography` in reusable components. A raw value
  is acceptable only when it is intrinsic to a platform/material contract or is explained in review.
- Token changes must include the affected light/dark previews and a screenshot review. A rename or
  removal needs a migration note and a deprecation period when an in-repository consumer exists.
- Token value holders must remain immutable so Compose can reason about stability safely.
- Do not enable strict explicit-API or binary-compatibility tooling solely for this internal module.
  Revisit that decision when the design system gains an external consumer, a published artifact, or a
  separately versioned template.

## Component contract

### Android/Web beer row

[SharedBeerListItem](../shared/presentation/src/commonMain/kotlin/com/simtop/billionbeers/shared/presentation/SharedBeerListItem.kt)
owns the Android/Web card layout: title hierarchy, rounded image tile, spacing, wrapping ABV/IBU
and availability chips, and one button action with availability state semantics. It belongs in
`:shared:presentation` because it consumes a domain beer; generic theme tokens remain in the design
system. Android's existing catalog previews and translations remain in
[ComposeBeersListItem](../presentation_utils/src/main/java/com/simtop/presentation_utils/custom_views/ComposeBeersListItem.kt).
Desktop and iOS still use their host rows.

Hosts supply formatted/localized labels and image content. Android retains Coil loading/shimmer
and error resources; Web retains Fetch/Skia and its deterministic placeholder. Web list images use
`Fit` to preserve the whole bottle; detail's image policy remains host-owned. The shared row caps
the Android title/tagline at one/two lines, with full text in semantics and detail available through the row.
Web retains wrapping full titles through `titleMaxLines` so the shared style does not hide beer names.
Metric/status labels wrap rather than squeezing into a narrow fixed row.

Run `make test MODULE=:shared:presentation` for compact normal/2× text, translated status, light/dark,
RTL, image-slot sizing and row action/state checks. Run `make screenshot-verify` for Android previews
and `make web-verify` for the production Web host and route interactions. Inspect actual host images
alongside these checks; shared JVM renders do not establish browser or spoken screen-reader acceptance.

### Portable Back affordance

[SharedBackIcon](../shared/presentation/src/commonMain/kotlin/com/simtop/billionbeers/shared/presentation/SharedBackIcon.kt)
provides a 24dp vector Back arrow for Web's host slot, tinted with the local content color and
automatically mirrored in RTL. Its localized description remains in semantics. It avoids the
font-fallback dependency of a text arrow; Desktop and Android retain their platform icon providers.
`BackIconRenderTest` checks rendered arrowhead/shaft pixels, description, fixed size and absence of
glyph text at normal/2× text in LTR/RTL. Browser smoke still checks real Back/history actions;
inspect early-frame Web captures to verify the reported transient symptom.

### Reusable component states

[SharedFilterPresetForm](../shared/presentation/src/commonMain/kotlin/com/simtop/billionbeers/shared/presentation/SharedFilterPresetForm.kt)
owns the saved-filter editor/action layout on Android and in the shared shell. A full-width,
single-line name field sits above Save so large text and translated actions cannot consume its
width. Hosts provide labels, the draft, query eligibility and save callbacks. The form enforces the
64-character limit and blank-name disablement; hosts clear the draft after success and retain it
after failure. Android's `ComposeFilterPresetForm` remains its resource adapter.

The shared shell reserves feedback space above navigation, or at the bottom when navigation is
absent. Feedback may reduce the visible list area temporarily; it must not cover the retry action.
The shared renderer's `FilterPresetFormLayoutTest` covers Catalog, Search and Browse at compact
width, 2× text, long French fixture labels and RTL, plus query/draft preservation and non-overlap.
The Web production smoke checks all three forms at 320px and exercises saving/persistence/history.
Injected French labels do not establish localization of the English Web shell or spoken accessibility.

A component is governed when it is intended for reuse outside its defining source file. Its API and
previews should make the following states explicit where the component supports them:

- loading/progress;
- error or retry;
- disabled;
- selected/unselected;
- long or translated text.

Not every component supports every state. The preview or KDoc should say which states do not apply
rather than inventing fake variants. Screen-level state previews are valuable but do not replace a
contract for a reusable design-system component.

Interactive components must:

- expose a meaningful label, role, and state through Compose semantics;
- keep the complete target at least 48dp when the design system owns the interaction;
- preserve keyboard and screen-reader activation; and
- avoid hiding an interaction behind an unlabeled generic click helper.

Screenshots prove layout and visual states. Semantics tests prove behavior. Use both when a governed
component owns interaction; do not treat a passing screenshot as accessibility evidence.

## Preview and visual review

- Use the shared preview annotations in `DesignAnnotations.kt` rather than one-off copies.
- Apply `AccessibilityMatrixPreview` to a small representative set: it expands to light/dark,
  1.0/1.5/2.0 font scale, English/long-text locale/RTL, and compact/expanded widths.
- Keep ordinary state previews focused. Add loading, error, disabled, selected, and long-text cases
  only where the component supports them.
- `make screenshot-record MODULE=:core:designsystem` updates goldens through the normal review PR.
  `make screenshot-verify MODULE=:core:designsystem` must pass before merging visual changes.
- The Paparazzi HTML report at
  `core/designsystem/build/reports/paparazzi/debug/index.html` is the local/CI visual review
  surface. CI already archives `**/build/reports/paparazzi/`; a custom gallery site is not required
  for this module.

## Deprecation and review checklist

For a design-system change, reviewers should be able to answer yes to these questions:

1. Is the declaration intentionally supported runtime API, implementation API, or tooling API?
2. Does the change use semantic tokens and preserve light/dark behavior?
3. Are the applicable component states represented by previews?
4. Do interactive semantics, labels, roles, and minimum target sizes remain correct?
5. Were screenshot goldens verified and the Paparazzi report inspected when visuals changed?
6. If an API is removed or renamed, is there a migration path and a deprecation period for current
   consumers?

When the module becomes independently published or consumed by another repository, add an API dump
or binary-compatibility check in the same change that establishes that external boundary.
