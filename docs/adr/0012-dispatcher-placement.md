# 0012: Dispatchers are chosen where the work is, not where the coroutine starts

## Status

Accepted.

## Context

Every ViewModel in this project launched its coroutines on IO:

```kotlin
viewModelScope.launch(coroutineDispatcher.io) { … }
```

while `:beer_data`, `:beer_database` and `:beer_network` contained no dispatcher calls at all — no
`withContext`, no `Dispatchers.`. The threading decision was made as far from the work as it is
possible to get.

That inversion is invisible in review, because the code looks *more* careful than the alternative.
It was raised as a deliberate choice with two arguments behind it, both reasonable, and this ADR
exists because answering them properly changed the shape of the fix.

## The two arguments, and what happened to them

### "Injecting the dispatcher is how we control coroutines in ViewModel tests"

Half true, and the half that is false is the load-bearing one.

`viewModelScope` is `SupervisorJob() + Dispatchers.Main.immediate`. **No injection can reach it** —
`Dispatchers.setMain` is the only override. So a test needs `setMain` whether or not a dispatcher is
injected, and every ViewModel test here already called it. The injected provider was a *second*
control surface for something the first one already covered.

Settled empirically rather than by argument: `BeerDetailViewModel` was converted to a bare
`viewModelScope.launch { }`, its `mockk` dispatcher stubs deleted, and its test still passed with
only `setMain` — then mutation-probed (an assertion deliberately broken) to confirm the test really
executes and asserts rather than being silently filtered out by the screenshot plugin's test filter.

There is also a hazard in the injected form. If the provider ever yields a *different*
`TestDispatcher` instance than `runTest` uses, the two get separate schedulers, virtual time stops
advancing together, and the test hangs or asserts on a stale value. The tests here avoided it by
passing the same instance, but the pattern invites it.

### "From a call site you can't know whether a callee blocks, until it crashes"

The premise is correct and the remedy was backwards.

On Android that failure is already loud: Room throws on a main-thread query unless
`allowMainThreadQueries()` is set (it is not, outside one instrumented test), and the platform throws
`NetworkOnMainThreadException` for blocking sockets. `launch(io)` does not prevent that crash — it
**suppresses the detector**. The day a repository starts doing real blocking work, running it on IO
produces no exception, no report, and no signal: just a quietly slower app and an occupied IO
thread. A loud, immediate, one-line-fix failure has been traded for a silent regression that
surfaces months later as jank.

The argument is answerable only with a detector, so this ADR ships one — see *StrictMode* below.

## Decision

**Whoever does the work chooses the dispatcher. Whoever starts the coroutine does not.**

| Situation | Where the dispatcher is chosen | Mechanism | Inject a provider? |
|---|---|---|---|
| ViewModel orchestrating main-safe suspend calls | nowhere | `viewModelScope.launch { }` | **No** |
| Flow operator doing CPU work before collection | the ViewModel | `flowOn(default)` | **Yes** |
| Repository calling a suspend Room DAO or Retrofit service | nowhere | plain `suspend fun` | **No** |
| Repository wrapping a **blocking** library or vendor SDK | that class | `withContext(io)` | **Yes** |
| CPU-heavy transform in the data layer (parsing, crypto, large sorts) | that class | `withContext(default)` | **Yes** |
| Direct disk, file or `SharedPreferences` access | that class | `withContext(io)` | **Yes** |
| Testing a ViewModel | the test | `Dispatchers.setMain` | **N/A** — `viewModelScope` is not injectable |
| Testing a class that selects a dispatcher | the test | pass `runTest`'s dispatcher | **Yes** — same scheduler |

Applied here: three ViewModels lost the parameter entirely. Two kept it, because they genuinely
select a dispatcher — `combine(pager.data, pager.pagingState, reducer::reduce)` folds pages into a
growing list on every emission, real CPU work that would otherwise run on Main under
`stateIn(viewModelScope)`.

Those two moved from `io` to `default`. `Dispatchers.IO` is a 64-thread pool sized for threads
parked on blocking waits; using it for computation oversubscribes the CPU. `Default` is sized to the
core count, which is what CPU-bound work wants.

**`CoroutineDispatcherProvider` stays.** It is the right seam for every "Yes" row above. The change
is where it is injected, not whether it exists.

## This is not a departure from Google's guidance

Both of the relevant rules are Google's, and they are usually quoted separately:

- **"Inject Dispatchers"** — *"Don't hardcode `Dispatchers` when creating new coroutines **or calling
  `withContext`**."* The worked example is a `NewsRepository`.
- **"Suspend functions should be safe to call from the main thread"** — *"If a class is doing
  long-running blocking operations in a coroutine, it's in charge of moving the execution off the
  main thread using `withContext`… classes calling suspend functions don't have to worry about which
  Dispatcher to use. That responsibility lies in the class that does the work."*

The injection rule is scoped to the place that *selects* a dispatcher. It was never a mandate to
hand one to every `viewModelScope.launch`. Google's own note that a ViewModel coroutine "almost
always updates the UI on the main thread, so starting coroutines on the main thread saves you extra
thread switches" points the same way.

## StrictMode — the detector that makes this safe

`app/src/debug/.../StrictModeConfig.kt`, with a no-op `src/release` twin, mirroring `DebugDrawerHost`.
It detects disk reads, disk writes, network and custom slow calls on the main thread, and reports
with `penaltyLog` rather than `penaltyDeath` — a violation should interrupt whoever caused it, not
make the debug build unusable on an unrelated screen.

This is the direct answer to "you can't tell from outside": you can, on the first debug run, with a
stack trace pointing at the exact call. Room and the platform already cover the two largest cases;
StrictMode covers the remainder.

**It earned its keep on the first launch**, and not in a repository. `NetworkingModule` built its
OkHttp cache with `context.cacheDir`, which routes through `ContextImpl.getDataDir()` and calls
`File.exists()` - a main-thread disk read of ~490-790 ms on every launch, before the first frame,
because the first composition resolves a ViewModel that pulls the repository that builds the client.
No amount of `launch(io)` in a ViewModel would ever have revealed it: the work was in the DI graph,
upstream of any coroutine. Fixed by resolving the path from `applicationInfo.dataDir` (a String field,
no syscall) and letting OkHttp create the directory lazily on its own dispatcher; verified on an
emulator as two violations per launch before and zero after, with the cache still written.

Its sibling, a `newSSLContext` violation from building the OkHttp client, was removed by warming the
networking branch of the graph on the injected IO dispatcher in `BillionBeersApplication`.

**Measured on a Pixel 8, not an emulator**, 10 cold starts per side of the minified benchmark
variant: median 209.5 ms to 206.2 ms, mean 213.9 ms to 206.4 ms. A small real win of 3-7 ms, against
a same-code run-to-run swing of 2.5 ms. Debug-build StrictMode reported the violation at 270-294 ms
on the main thread every launch, and zero after.

Two lessons worth more than the change itself:

- **A StrictMode duration is not a cost estimate.** 270-294 ms in a debug build bought ~5 ms in the
  shipped variant, which is minified and AOT-compiled from a baseline profile. Read StrictMode as
  "this is on the wrong thread", never as "this costs the user that much".
- **The emulator could not answer this and quietly said it could.** The same comparison there swung
  160 ms between two runs of *identical* code - 30x the effect - and a first single-run comparison
  showed a convincing -182 ms (-15.9%) that was pure noise. The Pixel reproduces to 2.5 ms. This is
  ADR 0009's measurement warning in a second setting, and why `androidx.benchmark.suppressErrors`
  lists `EMULATOR`.

## Worked example: a vendor SDK that does not handle threading

The case the guidance has to survive. A third-party SDK exposes a blocking call and cannot be
changed:

```kotlin
class VendorTokenSource(
  private val sdk: VendorSdk,
  private val dispatchers: CoroutineDispatcherProvider,
) : TokenSource {

  // The SDK blocks and we do not own it, so main-safety becomes our job - here, once, in the class
  // that knows. Not every caller's job, forever, in a language none of them can check.
  override suspend fun token(): String = withContext(dispatchers.io) { sdk.getToken() }
}
```

Why the boundary and not the ViewModel:

- **It is the only place that knows.** The boundary class holds the SDK; a ViewModel three layers up
  is guessing.
- **It is fixed once.** Wrap it here and `token()` is main-safe for every present and future caller.
  Wrap it at each call site and correctness depends on everyone remembering, forever.
- **`io` is right *here* and wrong at the ViewModel** for the same reason — a thread parked in a
  blocking SDK call belongs in the pool built for parked threads.
- **Getting it wrong is now detectable.** Forget the wrapper and StrictMode reports it on the next
  debug run, rather than the app silently absorbing it.

## Cost accepted

**Three ViewModels no longer have a dispatcher seam.** Adding blocking work to one would need the
parameter back. That is the correct amount of friction: the alternative is five constructor
parameters kept alive for work nobody is doing.

**StrictMode is debug-only, so it cannot catch a violation that only occurs in a release-only code
path.** There are none today, and `penaltyLog` means CI cannot fail on it either — this is a
developer-facing detector, not a gate.

## Consequences

- `MainDispatcherExtension` in `:testing-utils` replaces `MainCoroutineScopeRule`, which was dead
  code that *could not* have been used: a JUnit 4 `TestWatcher` in a JUnit 5 project, unreferenced,
  and the only file in the module. The convention plugins now supply `:testing-utils` to every
  library and dynamic-feature module.
- The extension exposes a **real fake** provider, not a `mockk`. This is not cosmetic: the mocks
  stubbed only `.io`, so changing production code to `.default` failed seven unrelated tests on an
  unstubbed member instead of on an assertion.
- It defaults to `StandardTestDispatcher`. `UnconfinedTestDispatcher` runs coroutines eagerly at the
  launch point and hides ordering bugs; the two search/browse tests pass one explicitly because
  their debounce assertions were written against eager execution.

## When to revisit

- **If a repository ever needs `withContext`**, inject the provider *there* and add a test that
  passes `runTest`'s dispatcher. That is the first row of the matrix earning its keep, not an
  exception to it.
- **If StrictMode violations stop being rare**, move from `penaltyLog` to `penaltyDeath` in debug.
  The reason it is not `penaltyDeath` today is tolerance for pre-existing noise, not principle.
- **If a Compose or Paging change makes `reducer::reduce` cheap**, re-measure before keeping
  `flowOn(default)` — it is justified by the cost of the fold, and that is an assumption, not a
  measurement.
