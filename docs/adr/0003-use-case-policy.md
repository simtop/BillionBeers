# 0003: Use cases exist iff they add behaviour; the layer boundary is enforced by Konsist

## Status

Accepted

## Context

After the typed-errors work, every use case in `beerdomain/impl/.../usecases/` is a one-line
pass-through to `BeersRepository`:

```kotlin
class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) {
    suspend operator fun invoke(beer: Beer) = beersRepository.updateAvailability(beer)
}
```

`LoadNextPageUseCase`, `RefreshBeersUseCase`, and `ObservePagingStateUseCase` have the same shape.
None orchestrates multiple repositories, applies a threading policy, encodes a business rule, or
transforms anything - each mirrors exactly one repository method under a class-shaped name.

Two defensible policies exist, and both are used successfully in industry:

1. **Mandatory use-case layer** ("use case always"): every ViewModel dependency goes through a use
   case, even a pass-through. Its real value is *social enforceability*: a reviewer can police the
   layer boundary with zero judgment - no use case, reject the PR. This matters in large multi-team
   codebases reviewed by eyeball, where the observed failure mode of the looser rule is layer
   erosion: "inject what you need" has no lower bound, and under deadline pressure ViewModels end
   up injecting remote sources or raw API services. That erosion is expensive, compounding, and
   hard to reverse. (This is not hypothetical - it is direct prior experience from a team where
   "inject the thing you need" degraded exactly this way, and mandating use cases everywhere was
   the successful fix.)
2. **Use case iff it adds behaviour**: a use case exists only when it does something a repository
   call doesn't (combine repositories, enforce a domain rule, own a threading/retry policy). Pure
   pass-throughs are deleted and the ViewModel injects the repository interface directly.

Key observations that frame the choice:

- The pass-through class does no architectural work by itself. Nothing stops a developer from
  injecting `ApiService` into a use case, or into a ViewModel *alongside* one. In policy 1, the
  *review rule* provides the safety; the class is only the rule's visible token.
- DI is neutral between the policies. With Metro, injecting `BeersRepository` into a ViewModel is
  mechanically identical to injecting a use case - same constructor injection, same graph. If
  anything, mirror use cases multiply bindings 1:1 per repository method. The DI pain associated
  with policy 2 in practice comes from injecting the *wrong layer*, which is a boundary violation,
  not a DI limitation.
- Policy 1 has a signal cost at scale: when every repository method has a mirror use case, the
  presence of a use case stops meaning "business logic lives here". Most of the domain layer
  becomes name-shadowing, and every repository signature change touches three layers and their
  tests.
- Both policies are consistent. "A use case exists iff it does something a repository call
  doesn't" is as binary and bikeshed-proof in review as "always".

The asymmetry that decides it: policy 2's failure mode (layer erosion) is severe but only occurs
**without mechanical enforcement**; policy 1's failure mode (boilerplate, diluted signal) is mild
but unconditional. So the correct policy is a function of tooling, not taste.

## Decision

Adopt policy 2, **coupled to mechanical boundary enforcement**:

- A use case exists iff it does something a repository call doesn't. Pass-throughs are deleted:
  `LoadNextPageUseCase`, `RefreshBeersUseCase`, `ObservePagingStateUseCase`,
  `UpdateAvailabilityUseCase`. ViewModels inject `BeersRepository` directly.
- The layer boundary those classes were socially guarding becomes a Konsist rule that fails the
  build: **ViewModels may depend only on domain-layer types** (repository interfaces from
  `:beerdomain:api`, surviving use cases, domain models) - never on data-layer sources, DTOs,
  Retrofit services, or DAOs.

The Konsist rule is not optional hardening; it is the load-bearing half of this decision. Deleting
the pass-throughs is only safe because the invariant they represented is now compiler-enforced
instead of convention-enforced.

## Why

- The invariant worth protecting is "ViewModels see only the domain layer", not "ViewModels see
  only use cases". Konsist enforces the former directly and cannot be bypassed by a tired
  reviewer; a mandatory use-case layer enforces it indirectly and only as long as review culture
  holds.
- With the boundary mechanically enforced, a pass-through buys nothing and costs real things: an
  extra class and test per repository method, three-layer churn on every signature change, and -
  the subtle one - loss of signal, because a domain layer full of mirrors no longer tells a reader
  where the business rules are. In this codebase, a use case's existence should be information.
- Retrofitting a use case later is cheap here. When `UpdateAvailability` grows real behaviour
  (validating availability transitions, emitting an analytics event), introducing the class then
  is a small mechanical refactor confined to one feature, and the plain-fakes test style
  (`FakeBeersRepository` + Turbine, per ADR 0002) means no test infrastructure changes.

## Cost accepted

- **Retrofit churn:** when a plain repository call grows behaviour, the ViewModel's constructor
  and its tests change to swap the repository for a new use case. Accepted: it is localized,
  mechanical, and rare compared to the per-method mirror tax.
- **No universal seam:** policy 1 gives every operation a ready-made hook for cross-cutting
  behaviour (analytics, logging). Accepted: cross-cutting concerns belong in the observability
  facade (the `Logger`/`AnalyticsTracker` seam in `:core-common`), not smeared across per-call
  wrappers.
- **Onboarding nuance:** "always" is easier to teach than "iff". Accepted: the rule is one
  sentence, and Konsist turns violations of the underlying boundary into build failures rather
  than tribal knowledge.

## Consequences

- `LoadNextPageUseCase`, `RefreshBeersUseCase`, `ObservePagingStateUseCase`, and
  `UpdateAvailabilityUseCase` are deleted; `BeersListViewModel` / `BeerDetailViewModel` inject
  `BeersRepository`. `GetAllBeersUseCase` survives only if it grows behaviour (per §2.4 of the
  audit).
- The Konsist suite (`konsist/`) must gain the ViewModel-boundary rule **in the same change** that
  deletes the pass-throughs. A PR that does the deletion without the rule reintroduces the
  unenforced-floor situation this ADR exists to prevent.
- **Revisit trigger:** if this codebase is ever worked on by multiple teams *without* Konsist (or
  equivalent) in CI, flip to policy 1. "Use case always" is the correct rule for
  convention-enforced codebases; this ADR's choice is strictly conditional on mechanical
  enforcement.
