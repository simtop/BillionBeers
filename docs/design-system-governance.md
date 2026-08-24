# Design-system governance

This document defines the supported surface and review contract for `:core:designsystem`. It is the
working agreement for this repository today; it is not a promise that the module is a published
binary library.

## Ownership

- `:core:designsystem` owns the theme wrapper, semantic color/spacing/typography tokens, reusable
  components, and preview annotations.
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
