#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
JOBS="${JOBS:-4}"
QUESTIONS="1,2,3,5"

usage() {
  printf 'Usage: %s [--questions 1,2,3,5] [--jobs N]\n' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --questions|--jobs)
      if [[ $# -lt 2 ]]; then printf 'Missing value for %s\n' "$1" >&2; exit 2; fi
      option="$1"; value="$2"; shift 2
      if [[ "$option" == "--questions" ]]; then QUESTIONS="$value"; else JOBS="$value"; fi
      ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done
if [[ ! "$JOBS" =~ ^[1-9][0-9]*$ ]]; then printf 'Jobs must be a positive integer.\n' >&2; exit 2; fi
if ! "$PYTHON_BIN" -c 'import openpyxl' 2>/dev/null; then
  printf 'Python package openpyxl is required. Install experiments/requirements.txt.\n' >&2
  exit 1
fi

STAMP="$(date '+%Y%m%d_%H%M%S')"
RUN_ROOT="$PROJECT_ROOT/output/research_questions_$STAMP"
mkdir -p "$RUN_ROOT"/{workbooks,logs,results,fragments,analysis}
SPECS="$RUN_ROOT/specs.tsv"
: > "$SPECS"

selected() { [[ ",$QUESTIONS," == *",$1,"* ]]; }
add_spec() { printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$@" >> "$SPECS"; }

if selected 1; then
  for memory in 25 -1; do
    for wom in 0 1; do
      add_spec Q1 "q1_m${memory}_w${wom}_none" "$memory" "$wom" none none default default 0 none
      add_spec Q1 "q1_m${memory}_w${wom}_all_p1" "$memory" "$wom" 1 all default default 0 none
      add_spec Q1 "q1_m${memory}_w${wom}_engagement_p1" "$memory" "$wom" 1 engagement default default 0 none
    done
  done
fi

if selected 2; then
  for memory in 5 10 25 50 100 -1; do
    add_spec Q2 "q2_m${memory}_none" "$memory" 1 none none default default 0 none
    for timing in 1 25 50 100; do
      add_spec Q2 "q2_m${memory}_p${timing}" "$memory" 1 "$timing" all default default 0 none
    done
  done
fi

if selected 3; then
  for contacts in 6 15 27; do
    for reach in 0 1; do
      for wom in 0 1; do
        add_spec Q3 "q3_c${contacts}_r${reach}_w${wom}" 25 "$wom" 1 engagement "$contacts" 0.33 "$reach" none
      done
    done
  done
fi

if selected 5; then
  for seed in $(seq 1001 1011); do
    for memory in 25 -1; do
      for wom in 0 1; do
        add_spec Q5 "q5_s${seed}_m${memory}_w${wom}_none" "$memory" "$wom" none none default default 0 "$seed"
        for timing in 1 25 50 100; do
          add_spec Q5 "q5_s${seed}_m${memory}_w${wom}_p${timing}" "$memory" "$wom" "$timing" all default default 0 "$seed"
        done
      done
    done
  done
fi

if [[ ! -s "$SPECS" ]]; then printf 'No executable questions selected.\n' >&2; exit 2; fi

cd "$PROJECT_ROOT"
make build
javac -cp "build/classes:lib/*" -d build/classes experiments/SeededMain.java
export PYTHON_BIN
export SOURCE_WORKBOOK="$PROJECT_ROOT/input/FAKENEWS_BASELINE_2.xlsx"
export JAVA_BIN="${JAVA_BIN:-java}"

printf 'Experiment root: %s\n' "$RUN_ROOT"
printf 'Conditions: %s; parallel jobs: %s\n' "$(wc -l < "$SPECS" | tr -d ' ')" "$JOBS"
while IFS= read -r spec; do printf '%s\0' "$spec"; done < "$SPECS" \
  | xargs -0 -n 1 -P "$JOBS" "$SCRIPT_DIR/run_condition.sh" "$RUN_ROOT"

MANIFEST="$RUN_ROOT/manifest.tsv"
printf 'question\tcondition\tmemory\twom\ttiming\tscope\tcontacts\tfriends\tsource_reach\tseed\tworkbook\n' > "$MANIFEST"
cat "$RUN_ROOT"/fragments/*.tsv | sort >> "$MANIFEST"
printf 'Completed experiment suite: %s\n' "$RUN_ROOT"
printf 'Manifest: %s\n' "$MANIFEST"
