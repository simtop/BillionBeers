#!/usr/bin/env bash
#
# Measure what the Gradle and Kotlin daemons actually need on THIS machine, and print the
# org.gradle.jvmargs / kotlin.daemon.jvmargs values to use.
#
# Why this exists: the right heap size is not a property of the project, it is a property of the
# machine and of what else is running on it. A 16 GB laptop with an emulator and a browser open
# wants a different number from a 64 GB desktop, and the usual "4g and you're fine" advice assumes
# the build is the only thing running. Measured here, 4g was *slower* than 3g for exactly that
# reason. So measure instead of guessing.
#
#   ./scripts/measure-jvm-memory.sh                          # sweep the Gradle daemon heap
#   ./scripts/measure-jvm-memory.sh 2g 3g 4g 6g              # ... with your own candidates
#   MODE=kotlin ./scripts/measure-jvm-memory.sh 1g 2g 3g     # sweep the KOTLIN daemon heap
#   TASK=":app:assembleDebug" ./scripts/measure-jvm-memory.sh
#
# The two daemons are separate JVMs and must be swept separately: Gradle runs the build, and a
# Kotlin compile daemon does the actual Kotlin compilation for every module. Settle the Gradle
# heap first, put it in gradle.properties, then sweep Kotlin with MODE=kotlin.
#
# IMPORTANT: run it with your normal working set open - emulator, browser, IDE. The swap column is
# the whole point, and it only means something under realistic memory pressure. Measuring on an
# idle machine will tell you a larger heap is always fine, which is true right up until it isn't.
#
# Three numbers decide the answer:
#
#   live set     Max heap still in use after a garbage collection. This is the real working set,
#                and it is the floor: a heap below it will thrash. Target roughly 1.5-2x.
#   gc overhead  Share of build wall time spent in GC pauses. Compare candidates against each
#                other rather than against an absolute - the measured build is a full recompile,
#                so the percentages are a worst case, not a typical one.
#   swap delta   Bytes the OS swapped out during the build. Anything above ~0 means the total
#                allocation is too high for this machine no matter how happy the JVM looks. This
#                is the ceiling, and it is the number the usual advice ignores.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

MODE="${MODE:-gradle}"
TASK="${TASK:-assembleDebug}"
METASPACE="${METASPACE:-1536m}"
GRADLE_HEAP="${GRADLE_HEAP:-3g}"          # held fixed while sweeping Kotlin
KOTLIN_HEAP="${KOTLIN_HEAP:-1536m}"       # held fixed while sweeping Gradle
KOTLIN_METASPACE="${KOTLIN_METASPACE:-768m}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

case "$MODE" in
  gradle|kotlin) ;;
  *) echo "MODE must be 'gradle' or 'kotlin', got '$MODE'" >&2; exit 1 ;;
esac

CANDIDATES=("$@")
if [ ${#CANDIDATES[@]} -eq 0 ]; then
  if [ "$MODE" = "kotlin" ]; then CANDIDATES=(1g 1536m 2g 3g); else CANDIDATES=(2g 3g 4g 6g); fi
fi

command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }

swap_out_bytes() {
  if [[ "$(uname)" == "Darwin" ]]; then
    sysctl -n vm.swapusage 2>/dev/null \
      | awk '{for(i=1;i<=NF;i++) if($i=="used"){gsub(/M/,"",$(i+2)); printf "%.0f", $(i+2)*1048576}}'
  else
    awk '/^pswpout/{printf "%.0f", $2*4096}' /proc/vmstat 2>/dev/null || echo 0
  fi
}

human_mb() { python3 -c "import sys; print(f'{int(sys.argv[1])/1048576:,.0f} MB')" "$1"; }

# `./gradlew --stop` stops Gradle daemons only. Kotlin compile daemons are separate JVMs that
# outlive it, so sweeping Kotlin heaps without this would keep measuring the first one started.
stop_all_daemons() {
  ./gradlew --stop >/dev/null 2>&1 || true
  pkill -f "org.jetbrains.kotlin.daemon.KotlinCompileDaemon" >/dev/null 2>&1 || true
  sleep 1
}

# Guards against the whole run being meaningless. `kotlin.daemon.jvmargs` is read as a Gradle
# property, so it is passed with -P; if a future plugin version stops honouring that, the daemon
# would quietly keep its old heap and every row below would be identical. Read the requested -Xmx
# back off the running process instead of trusting that it applied.
# Guards against the whole run being meaningless. Two earlier versions of this check were wrong in
# instructive ways, so it now verifies the ARTIFACT rather than the process:
#
#   1. `pgrep -f PATTERN -l` matches nothing on macOS - options must precede the pattern - so it
#      reported "no daemon" while one was running with exactly the requested heap.
#   2. Even corrected, polling after the build is the wrong moment: the Kotlin daemon exits once
#      compilation finishes, so a correct pgrep still finds nothing.
#
# The thing we actually depend on is the daemon having accepted `-Xlog:gc:file=`. If that file
# exists and has content, the args were honoured and the numbers are real. If it does not, the row
# is meaningless and must be labelled so rather than quietly reported as zero.
kotlin_measurement_is_valid() {
  local log="$1"
  [ -s "$log" ]
}

echo "Mode: $MODE   Task: $TASK   Candidates: ${CANDIDATES[*]}"
if [ "$MODE" = "kotlin" ]; then
  echo "Gradle daemon held fixed at -Xmx$GRADLE_HEAP"
else
  echo "Kotlin daemon held fixed at -Xmx$KOTLIN_HEAP"
fi
echo "Keep your normal apps open - the swap column depends on it."
echo

RESULTS="$WORK_DIR/results.tsv"
: > "$RESULTS"

for candidate in "${CANDIDATES[@]}"; do
  gradle_gc_log="$WORK_DIR/gradle-gc-$candidate.log"
  kotlin_gc_log="$WORK_DIR/kotlin-gc-$candidate.log"

  if [ "$MODE" = "gradle" ]; then
    gradle_heap="$candidate"; kotlin_heap="$KOTLIN_HEAP"; gc_log="$gradle_gc_log"
  else
    gradle_heap="$GRADLE_HEAP"; kotlin_heap="$candidate"; gc_log="$kotlin_gc_log"
  fi

  gradle_args="-Xmx$gradle_heap -XX:MaxMetaspaceSize=$METASPACE -Dfile.encoding=UTF-8"
  gradle_args="$gradle_args -Xlog:gc:file=$gradle_gc_log"
  kotlin_args="-Xmx$kotlin_heap -XX:MaxMetaspaceSize=$KOTLIN_METASPACE"
  kotlin_args="$kotlin_args -Xlog:gc:file=$kotlin_gc_log"

  stop_all_daemons

  # Warm-up: fills the build cache and lets both daemons JIT, so the measured run is steady state.
  ./gradlew "$TASK" -Dorg.gradle.jvmargs="$gradle_args" -Pkotlin.daemon.jvmargs="$kotlin_args" \
    --console=plain >/dev/null 2>&1 || true

  # Read from where the warm-up stopped rather than deleting the log: the daemon holds the file
  # open, so deleting it leaves it writing to an unlinked inode and every GC figure reads as zero.
  # In kotlin mode the log legitimately may not exist yet, so a missing file is offset 0, not an
  # error worth printing.
  gc_offset=0
  [ -f "$gc_log" ] && gc_offset=$(wc -c < "$gc_log" | tr -d ' ')
  swap_before="$(swap_out_bytes)"
  start_ns=$(python3 -c "import time;print(time.time_ns())")

  ./gradlew "$TASK" --rerun-tasks -Dorg.gradle.jvmargs="$gradle_args" \
    -Pkotlin.daemon.jvmargs="$kotlin_args" --console=plain \
    >"$WORK_DIR/build-$candidate.log" 2>&1 && status="ok" || status="FAILED"

  # In kotlin mode the GC log is the proof the daemon honoured our args. No log, no measurement -
  # say so instead of reporting a confident zero.
  if [ "$status" = "ok" ] && [ "$MODE" = "kotlin" ] && ! kotlin_measurement_is_valid "$gc_log"; then
    status="NO-GC-LOG"
  fi

  end_ns=$(python3 -c "import time;print(time.time_ns())")
  swap_after="$(swap_out_bytes)"
  wall_ms=$(( (end_ns - start_ns) / 1000000 ))
  swap_delta=$(( ${swap_after:-0} - ${swap_before:-0} ))
  [ "$swap_delta" -lt 0 ] && swap_delta=0

  # JDK 9+ unified GC logging:
  #   [12.345s][info][gc] GC(7) Pause Young (G1 Evacuation Pause) 812M->233M(3072M) 9.8ms
  # The value after "->" is heap still live once the collection finished; the max across the build
  # is the live set. The trailing durations summed are total pause time.
  read -r live_set_mb gc_ms gc_count <<<"$(python3 - "$gc_log" "$gc_offset" <<'PY'
import re, sys
try:
    with open(sys.argv[1], errors="replace") as handle:
        handle.seek(int(sys.argv[2]))
        text = handle.read()
except OSError:
    print("0 0 0"); raise SystemExit
after = [int(m) for m in re.findall(r'->(\d+)M\(', text)]
pauses = [float(m) for m in re.findall(r'\s([\d.]+)ms\s*$', text, re.M)]
print(max(after) if after else 0, round(sum(pauses)) if pauses else 0, len(pauses))
PY
)"

  gc_pct=$(python3 -c \
    "import sys; print(f'{(int(sys.argv[1])/max(int(sys.argv[2]),1))*100:.1f}')" "$gc_ms" "$wall_ms")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$candidate" "$wall_ms" "$live_set_mb" "$gc_ms" "$gc_pct" "$swap_delta" "$status" >> "$RESULTS"

  printf '  %-6s  wall %6sms   live set %5s MB   gc %5sms (%s%%)   swapped %s   %s\n' \
    "$candidate" "$wall_ms" "$live_set_mb" "$gc_ms" "$gc_pct" "$(human_mb "$swap_delta")" "$status"
done

stop_all_daemons
echo

python3 - "$RESULTS" "$MODE" "$METASPACE" "$KOTLIN_METASPACE" <<'PY'
import sys

rows = []
for line in open(sys.argv[1]):
    heap, wall, live, gc_ms, gc_pct, swap, status = line.rstrip("\n").split("\t")
    rows.append(dict(heap=heap, wall=int(wall), live=int(live), gc_pct=float(gc_pct),
                     swap=int(swap), status=status))

mode, metaspace, kotlin_metaspace = sys.argv[2], sys.argv[3], sys.argv[4]
ok = [r for r in rows if r["status"] == "ok"]
if not ok:
    print("Every candidate failed - fix the build before tuning memory."); raise SystemExit(1)

no_swap = [r for r in ok if r["swap"] == 0]
all_swapped = not no_swap
if all_swapped:
    print("WARNING: every candidate caused swapping. This machine is short on RAM for this")
    print("workload - close something, or accept that the build competes with the emulator.")
    no_swap = ok

pick = min(no_swap, key=lambda r: (r["wall"], r["heap"]))
live_max = max(r["live"] for r in ok)

if live_max == 0:
    print("Live set read as 0 MB for every candidate - the GC log was not captured, so these")
    print("numbers mean nothing. Check that the daemon accepted -Xlog:gc.")
    raise SystemExit(1)

print(f"Live set peaked at {live_max} MB, so anything at or below that will thrash.")
print(f"Rule of thumb puts the floor around {int(live_max*1.5)}-{live_max*2} MB.\n")
print("Recommended, for this machine under this workload:\n")
if mode == "gradle":
    print(f"  org.gradle.jvmargs=-Xmx{pick['heap']} -XX:MaxMetaspaceSize={metaspace} "
          f"-XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8")
    print("\nNow settle the Kotlin daemon separately:")
    print(f"  MODE=kotlin GRADLE_HEAP={pick['heap']} ./scripts/measure-jvm-memory.sh")
else:
    print(f"  kotlin.daemon.jvmargs=-Xmx{pick['heap']} -XX:MaxMetaspaceSize={kotlin_metaspace}")

print()
if all_swapped:
    print("Chosen as the fastest candidate overall - none avoided swapping, so that criterion")
    print("could not discriminate and wall time decided it.")
else:
    print("Chosen as the fastest candidate that did not swap.")
print()
print("Note on GC %: the measured build uses --rerun-tasks, i.e. a full recompile, which is the")
print("worst case rather than the typical one. Percentages far above a few percent are expected")
print("here; what matters is how they compare BETWEEN candidates, and where swap starts.")
print()
walls = [r["wall"] for r in ok]
if len(walls) > 2 and (walls == sorted(walls) or walls == sorted(walls, reverse=True)):
    print("CAUTION: wall time moved monotonically across the candidates in the order they were")
    print("run. That is what progressive warming (OS file cache, JIT) looks like as well as a real")
    print("heap effect, and the two are indistinguishable from one pass. Re-run with the candidate")
    print("order REVERSED: if the same heap wins again it is real, if the winner tracks position")
    print("in the list it was warming and these numbers should be discarded.")
PY
