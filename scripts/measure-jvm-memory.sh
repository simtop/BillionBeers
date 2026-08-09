#!/usr/bin/env bash
#
# Measure what the Gradle and Kotlin daemons actually need on THIS machine, and print the
# org.gradle.jvmargs / kotlin.daemon.jvmargs values to use.
#
# Why this exists: the right heap size is not a property of the project, it is a property of the
# machine and of what else is running on it. A 16 GB laptop with an emulator and a browser open
# wants a different number from a 64 GB desktop, and the usual "4g and you're fine" advice assumes
# the build is the only thing running. So measure instead of guessing.
#
#   ./scripts/measure-jvm-memory.sh                 # sweep the default candidates
#   ./scripts/measure-jvm-memory.sh 2g 3g 4g 6g     # sweep your own
#   TASK=":app:assembleDebug" ./scripts/measure-jvm-memory.sh
#
# IMPORTANT: run it with your normal working set open - emulator, browser, IDE. The swap column is
# the whole point, and it only means something under realistic memory pressure. Measuring on an
# idle machine will tell you a larger heap is always fine, which is true right up until it isn't.
#
# Three numbers decide the answer:
#
#   live set     Max heap still in use after a garbage collection. This is the real working set,
#                and it is the floor: a heap below it will thrash. Target roughly 1.5-2x.
#   gc overhead  Share of build wall time spent in GC pauses. Above ~5% the heap is too small;
#                near 0% with a large heap means you are over-provisioned and could give the
#                memory back to the emulator.
#   swap delta   Bytes the OS swapped out during the build. Anything above ~0 means the total
#                allocation is too high for this machine no matter how happy the JVM looks. This
#                is the ceiling, and it is the number the usual advice ignores.
#
# The recommendation is the smallest candidate that keeps GC overhead under the threshold without
# causing swap.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

TASK="${TASK:-assembleDebug}"
METASPACE="${METASPACE:-1536m}"
KOTLIN_HEAP="${KOTLIN_HEAP:-1536m}"
GC_OVERHEAD_BUDGET="${GC_OVERHEAD_BUDGET:-5.0}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

CANDIDATES=("$@")
if [ ${#CANDIDATES[@]} -eq 0 ]; then
  CANDIDATES=(2g 3g 4g 6g)
fi

command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }

# macOS reports swap through sysctl; Linux through /proc/vmstat. Returns bytes swapped out.
swap_out_bytes() {
  if [[ "$(uname)" == "Darwin" ]]; then
    # vm.swapusage: "total = 2048.00M  used = 1234.50M  free = 813.50M  (encrypted)"
    sysctl -n vm.swapusage 2>/dev/null \
      | awk '{for(i=1;i<=NF;i++) if($i=="used"){gsub(/M/,"",$(i+2)); printf "%.0f", $(i+2)*1048576}}'
  else
    awk '/^pswpout/{printf "%.0f", $2*4096}' /proc/vmstat 2>/dev/null || echo 0
  fi
}

human_mb() { python3 -c "import sys; print(f'{int(sys.argv[1])/1048576:,.0f} MB')" "$1"; }

echo "Measuring on $(uname -s), $(sysctl -n hw.ncpu 2>/dev/null || nproc) cores, \
$(python3 -c "import sys;print(f'{int(sys.argv[1])/1073741824:.0f} GB RAM')" "$(sysctl -n hw.memsize 2>/dev/null || echo 0)")"
echo "Task: $TASK   Candidates: ${CANDIDATES[*]}"
echo "Keep your normal apps open - the swap column depends on it."
echo

RESULTS="$WORK_DIR/results.tsv"
: > "$RESULTS"

for heap in "${CANDIDATES[@]}"; do
  gc_log="$WORK_DIR/gc-$heap.log"
  jvmargs="-Xmx$heap -XX:MaxMetaspaceSize=$METASPACE -Dfile.encoding=UTF-8 -Xlog:gc:file=$gc_log"

  # A new daemon per candidate, or we would measure the previous heap setting.
  ./gradlew --stop >/dev/null 2>&1 || true

  # Warm-up: fills the build cache and the daemon's JIT so the measured run is steady-state.
  ./gradlew "$TASK" -Dorg.gradle.jvmargs="$jvmargs" \
    -Dkotlin.daemon.jvmargs="-Xmx$KOTLIN_HEAP" --console=plain >/dev/null 2>&1 || true

  # Note where the warm-up left off instead of deleting the log. The measured run reuses the same
  # daemon (same jvmargs, by design - we want it JIT-warm), and that daemon holds the log file
  # open: deleting it here would leave the daemon writing to an unlinked inode and every GC number
  # below would come back as zero. Parsing from this offset isolates the measured window instead.
  gc_offset=$(wc -c < "$gc_log" 2>/dev/null | tr -d ' ' || echo 0)
  swap_before="$(swap_out_bytes)"
  start_ns=$(python3 -c "import time;print(time.time_ns())")

  ./gradlew "$TASK" --rerun-tasks -Dorg.gradle.jvmargs="$jvmargs" \
    -Dkotlin.daemon.jvmargs="-Xmx$KOTLIN_HEAP" --console=plain >"$WORK_DIR/build-$heap.log" 2>&1 \
    && status="ok" || status="FAILED"

  end_ns=$(python3 -c "import time;print(time.time_ns())")
  swap_after="$(swap_out_bytes)"
  wall_ms=$(( (end_ns - start_ns) / 1000000 ))
  swap_delta=$(( ${swap_after:-0} - ${swap_before:-0} ))
  [ "$swap_delta" -lt 0 ] && swap_delta=0

  # JDK 9+ unified GC logging:
  #   [12.345s][info][gc] GC(7) Pause Young (Normal) (G1 Evacuation Pause) 812M->233M(3072M) 9.8ms
  # The value after "->" is heap still live once the collection finished; the max of those across
  # the build is the live set. The trailing duration summed is total pause time.
  read -r live_set_mb gc_ms gc_count <<<"$(python3 - "$gc_log" "$gc_offset" <<'PY'
import re, sys
try:
    with open(sys.argv[1], errors="replace") as handle:
        handle.seek(int(sys.argv[2]))     # skip everything the warm-up build logged
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
    "$heap" "$wall_ms" "$live_set_mb" "$gc_ms" "$gc_pct" "$swap_delta" "$status" >> "$RESULTS"

  printf '  %-4s  wall %6sms   live set %5s MB   gc %5sms (%s%%)   swapped %s   %s\n' \
    "$heap" "$wall_ms" "$live_set_mb" "$gc_ms" "$gc_pct" "$(human_mb "$swap_delta")" "$status"
done

echo
python3 - "$RESULTS" "$GC_OVERHEAD_BUDGET" "$METASPACE" "$KOTLIN_HEAP" <<'PY'
import sys

rows = []
for line in open(sys.argv[1]):
    heap, wall, live, gc_ms, gc_pct, swap, status = line.rstrip("\n").split("\t")
    rows.append(dict(heap=heap, wall=int(wall), live=int(live), gc_pct=float(gc_pct),
                     swap=int(swap), status=status))

budget, metaspace, kotlin_heap = float(sys.argv[2]), sys.argv[3], sys.argv[4]
ok = [r for r in rows if r["status"] == "ok"]
if not ok:
    print("Every candidate failed - fix the build before tuning memory."); raise SystemExit(1)

no_swap = [r for r in ok if r["swap"] == 0]
if not no_swap:
    print("WARNING: every candidate caused swapping. This machine is short on RAM for this")
    print("workload - close something, or accept that the build competes with the emulator.")
    no_swap = ok

healthy = [r for r in no_swap if r["gc_pct"] <= budget] or no_swap
pick = min(healthy, key=lambda r: (r["wall"], r["heap"]))
live_max = max(r["live"] for r in ok)

print(f"Live set peaked at {live_max} MB, so anything at or below that will thrash.")
print(f"Rule of thumb puts the floor around {int(live_max*1.5)}-{live_max*2} MB.\n")
print("Recommended, for this machine under this workload:\n")
print(f"  org.gradle.jvmargs=-Xmx{pick['heap']} -XX:MaxMetaspaceSize={metaspace} "
      f"-XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8")
print(f"  kotlin.daemon.jvmargs=-Xmx{kotlin_heap} -XX:MaxMetaspaceSize=768m\n")
if all(r["swap"] > 0 for r in ok):
    print("Chosen as the fastest candidate overall - none of them avoided swapping, so the")
    print("swap criterion could not discriminate and wall time decided it.")
else:
    print(f"Chosen as the fastest candidate that neither swapped nor spent more than "
          f"{budget}% in GC.")
print()
print("Note on GC %: the measured build uses --rerun-tasks, i.e. a full recompile, which is the")
print("worst case rather than the typical one. Percentages well above the budget are expected")
print("here; what matters is how they compare BETWEEN candidates, and where swap starts.")
print("Re-run KOTLIN_HEAP=<value> to sweep the Kotlin daemon the same way once the Gradle")
print("daemon is settled - it is a separate JVM and every module compiles through it.")
PY
