# 0010: Non-goals — what this project deliberately does not do

## Status

Accepted.

## Context

The nine existing ADRs all settle the same *kind* of question: given something we are going to
build, which implementation wins. Several of them decline an option outright and say so — Paging3
(0002), a use-case layer (0003), Renovate (0005), keeping every instrumented test in `:app` (0009).
That habit is healthy and this ADR does not disturb it.

What none of them covers is the other kind of decision: **a capability the product does not have at
all.** A reader — an interviewer, a future contributor, an agent — sees a production-shaped app with
ten mechanically-enforced invariants and a supply-chain ledger, and then notices there is no
authentication, no certificate pinning, no encrypted storage, no push, no background sync. Every one
of those absences is correct. None of them is written down anywhere, so all of them read as
oversights. "There is no credential to protect" is a good answer that currently lives only in one
person's head, which is exactly the failure mode the ADR habit exists to prevent.

The root fact almost all of these follow from: **`brewbuddy.dev` is not ours, it is read-only, and
it is unauthenticated.** Repeating that reasoning per capability is how it gets forgotten; recording
it once is how it survives.

## The deciding rule

**A capability is a non-goal when the premise it serves is absent — not when it is merely unbuilt.**

Auth is a non-goal because there is no credential anywhere in the system. An offline write queue is
*deferred*, not declined, because its premise already exists: `available` is a real local write with
optimistic UI, and the queue simply hasn't been built. The distinction matters because it makes the
revisit trigger fall out for free — for a non-goal, the trigger is always "the premise arrives."

The two lists below are therefore kept separate on purpose. Mistaking a deferral for a decline is
how a plan quietly loses items.

## Decision — non-goals

| Not doing | Why — the missing premise | Reopens when |
|---|---|---|
| **Auth, sessions, token refresh**, OkHttp `Authenticator` | The API takes no credential. There is no identity in the system to establish, store, or refresh | Any endpoint we call requires a token |
| **Certificate pinning** | Nothing confidential is in flight — public catalog data, no credential. Pinning carries a real operational cost (rotation bricks old clients; it needs a backup pin *and* a remote kill switch, which is itself deferred below) | Auth lands — but only *after* remote config, never before |
| **Encrypted storage** (SQLCipher, `EncryptedSharedPreferences`) | Room holds the public catalog plus one local boolean. There is no secret at rest | Any user-supplied or personal data is persisted |
| **Play Integrity / SafetyNet, root or tamper detection** | No entitlement, no revenue, no server trust decision. Nothing is gated on the client being honest | A paid or licensed artifact ships |
| **Push notifications (FCM)** | No server we control, so nothing to send | A backend of ours exists |
| **Server-side sync of `available`** | The backend isn't ours, so a sync could only ever one-way-overwrite the user's edit. The server value seeds a row on first insert and is local-only thereafter | We own the write path |
| **A shipped analytics or crash SDK** | The *seam* is built and deliberate — `AnalyticsTracker` / `CrashReporter` / `Logger` with no-op bindings in `ObservabilityModule`, swappable one binding at a time. Declining the *dependency* is the separate call: no `google-services.json` exists anywhere, so this repo clones and builds with zero external account setup. That property is worth more to a public template than a wired-up dashboard | A real distribution needs the data, or one integration lands behind a flag to prove the seam |
| **Consent, GDPR, and ad-ID flows** | Falls out of the row above: no SDK ships, no personal data is collected, no advertising ID is read. Play's Data Safety declaration is trivially satisfied because the honest answer is "nothing" | The row above changes |
| **Automatic network retry and backoff** | Failure is surfaced to the user and retried explicitly (`PagedListFooter.Retry`, `LoadMoreRetryFooter`) rather than silently re-attempted. On a read-only catalog a silent retry buys little and hides the failure the paging tests are written against. OkHttp's connection-level `retryOnConnectionFailure` default is left on; nothing sits above it | A write path exists, where transparent retry is load-bearing rather than cosmetic |
| **Multi-process** | Nothing declares `android:process`. No component needs isolation, and the second process would cost a second graph | A component needs its own lifetime or crash domain |

## Deferred, not declined

Planned for the future and simply not built yet. Recorded here only so nothing in this file is
mistaken for a closed question — none of these is waiting on a missing premise, so none of them
belongs in the table above.

| Deferred | Why it is a deferral, not a decline |
|---|---|
| Offline write queue (WorkManager, conflict resolution, idempotency) | The local write it would queue already exists — `available`, with optimistic UI |
| Remote config, kill switch, staged rollout, force-update | The premise is present, unlike push: `FeatureFlagProvider` already exists with a local implementation, and a remote source needs no backend of ours. Only the remote half is unbuilt — and the certificate-pinning row above is blocked behind it |
| Release-build health in CI, APK size budget, diffuse-on-PR | `make bundle-release` already runs R8 and regenerates the baseline profile locally; only the CI gate is missing |
| Adaptive two-pane layout; favorites + Glance widget | Ordinary feature work. The `adaptive` and `navigation-3` skills are already vendored |
| Kotlin Multiplatform | The domain and `:core-common` are already pure-JVM and Metro is KMP-capable, so the groundwork is in place |

The implementation choices named in the Context above — plus dev-apps for dynamic features (0004) —
keep their own ADRs and are not reopened here.

## Cost accepted

**This project cannot be read as a demonstration of any capability in the first table.** Someone
evaluating it for auth, sync, or push fluency finds nothing, and that is a real loss for a portfolio
artifact.

Accepted, because the alternative is worse. Building auth against a backend we don't own means
inventing a fake server to authenticate against, and a fake premise produces fake architecture —
code shaped by a scenario nobody has to live with. The value of this repository is that its shape is
honest: every structure in it answers a constraint that actually exists. One well-argued page of
declines is a better signal than a token-refresh interceptor with no token behind it.

## Consequences

- **`ACCESS_NETWORK_STATE` is declared and unread by our code.** `app/src/main/AndroidManifest.xml:4`
  requests it; no file under any `src/` touches `ConnectivityManager` or `NetworkCapabilities`. It is
  a leftover from the connectivity-aware retry this ADR declines. Drop the explicit declaration —
  if Play Core still needs it, its own manifest merges it in and the line was redundant; if not, the
  app was asking for a permission it does not use, which is exactly the drift this repo otherwise
  refuses to tolerate.
- **No `google-services.json` is a property to protect, not an accident.** Adding Firebase would
  make a public template require a private file to build. Any future integration must survive its
  absence.
- **Adding one of these means deleting its row, in the same PR.** A non-goal list that outlives its
  premise is worse than no list — it becomes a reason not to look.

## When to revisit

Concrete triggers, in the order the rows actually unlock:

1. **A write path to a server we own.** This does not reopen one row, it reopens five — auth,
   encrypted storage, server-side `available` sync, automatic retry, and (only afterwards)
   certificate pinning. Pinning is last on purpose: shipping a pin without a remote kill switch is
   how a fleet gets bricked by a certificate rotation, so it depends on the remote-config work in
   the deferred table.
2. **A paid or licensed artifact ships** — planned, but not scheduled. Integrity/anti-tamper and a
   real observability backend both acquire a premise the day one does.
3. **The app collects anything about a person** — consent and Data Safety stop being trivial the
   day the honest answer stops being "nothing."
