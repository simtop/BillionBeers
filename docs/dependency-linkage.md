# Dependency updates and Android binary linkage

The `Dependency API Compatibility` CI job checks whether a dependency update removes or changes
a binary symbol referenced by the Android release graph. The graph includes `:app`, both on-demand
features, generated classes such as serializers, and precompiled runtime dependencies.
The base app and each feature retain separate before and after runtime classpaths. Their resolved
versions can differ, so the checker evaluates every context instead of combining conflicting versions.

Japicmp compares the complete before and after library APIs. Its report includes internal and
experimental APIs. The comparison has no library-specific exclusions and receives Android's boot
classpath, so unresolved Android framework classes do not require a global ignore flag.

`CheckDependencyLinkage.java` reads the candidate consumers' bytecode without executing it. For
references into a changed library or its subclasses, the checker compares resolution against the
base and candidate runtime graphs. A symbol that resolved in the base graph and fails in the
candidate graph blocks CI. An unused removal stays in the Japicmp report without blocking CI.
An internal API reference receives the same check as a stable API reference.
The checker also compares references introduced by an updated dependency with that dependency's
old bytecode. A newly introduced unresolved reference blocks CI even when its target library did
not change, such as an update that requires a method absent from a pinned transitive dependency.

## Findings and reports

CI archives `dependency-api-and-linkage` with these reports for each module context:

- Library Markdown and XML reports list the binary API changes found by Japicmp.
- `referenced-apis.tsv` identifies the consuming class, referenced symbol, and resolution result
  for references into changed class hierarchies and references made by updated dependencies.
  External consumers include their dependency coordinates.
- `linkage-risks.tsv` lists regressions and incomplete hierarchy comparisons.
- `linkage-inputs.tsv` records the resolved artifacts, compiled consumers, and platform classpath.

The checker detects removed classes, methods, constructors, and fields. It also checks changes to
static linkage, visibility, class or interface kind, final superclasses and overridden methods,
and new abstract requirements on concrete consumers. Method matching includes the JVM descriptor,
so a retained overload does not conceal a removed overload. Inherited members are resolved through
the class hierarchy. References in descriptors and method handles are included.

The checker flags a class that becomes abstract when consumer bytecode instantiates it through
`new` or a constructor method handle. A type in a signature, an array element type, or a call to
a superclass constructor can still refer to an abstract class.

Exit code `0` means no observed linkage regression. Exit code `1` means a consumer has a linkage
risk. Exit code `2` means analysis could not complete, including missing inputs or an unresolved
hierarchy needed to decide a regression. Missing compiled consumers never produce a green result.

## Scope and limits

A bytecode reference establishes a possible dependency, not whether a user executes that path.
The checker scans all classes in the release inputs. It can therefore report a reference that R8
would remove, or a dependency's optional integration that the app does not use.

The comparison does not follow class names or method names assembled through reflection, native
code, downloaded code, or service configuration. It does not establish compatibility of JSON data,
network behavior, persisted data, or Kotlin metadata. Android Lint and behavior tests retain those
responsibilities where applicable. Apple and Wasm tests cover their separate targets.

The baseline comparison reports newly introduced linkage problems. An unchanged reference unresolved
in both graphs is recorded without being attributed to the update. This check is
not a complete JVM verifier or a proof that every runtime path is crash-free.

## Local commands

The pinned Japicmp archive and checksum are in `.github/workflows/ci.yml`. With that archive
available locally, run:

```sh
make dependency-api-test JAPICMP=/absolute/path/to/japicmp.jar
make dependency-api-graphs BASE_SHA=origin/master
make dependency-api-inputs
make dependency-api-check JAPICMP=/absolute/path/to/japicmp.jar
```

`DEPENDENCY_API_DIR` selects the output directory. Its default is `build/dependency-api`.
Graph export resolves the base revision in a temporary source archive and the candidate in the
current checkout. Each Android module resolves its own release configuration. Consumer export uses
AGP's scoped classes API before dexing and shrinking, so it requires compilation but no signing key.
