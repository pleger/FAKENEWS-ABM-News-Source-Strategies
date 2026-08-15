#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
JOBS="${JOBS:-2}"
SUITES="baseline,strategies,confirmation,robustness"

usage() {
  printf 'Usage: %s [--suites baseline,strategies,confirmation,robustness] [--jobs N]\n' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --suites|--jobs)
      if [[ $# -lt 2 ]]; then printf 'Missing value for %s\n' "$1" >&2; exit 2; fi
      option="$1"; value="$2"; shift 2
      if [[ "$option" == "--suites" ]]; then SUITES="$value"; else JOBS="$value"; fi
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
RUN_ROOT="$PROJECT_ROOT/output/dissemination_experiments_$STAMP"
mkdir -p "$RUN_ROOT"/{workbooks,logs,results,fragments,analysis}
SPECS="$RUN_ROOT/specs.tsv"
: > "$SPECS"

selected() { [[ ",$SUITES," == *",$1,"* ]]; }
add_spec() { printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$@" >> "$SPECS"; }

if selected baseline; then
  for seed in $(seq 1001 1030); do
    add_spec baseline "baseline_b0_s${seed}" 25 0 -1 1 none none 15 0.33 0 default "$seed"
    add_spec baseline "baseline_b1_s${seed}" 25 1 0 0 none none 15 0.33 0 default "$seed"
    add_spec baseline "baseline_b2_s${seed}" 25 1 -1 1 none none 15 0.33 0 default "$seed"
  done
  for memory in 10 50 100 -1; do
    for seed in $(seq 1001 1011); do
      add_spec baseline_sensitivity "baseline_m${memory}_discovery_s${seed}" "$memory" 1 0 0 none none 15 0.33 0 default "$seed"
      add_spec baseline_sensitivity "baseline_m${memory}_truth_s${seed}" "$memory" 1 -1 1 none none 15 0.33 0 default "$seed"
    done
  done
fi

if selected strategies; then
  for memory in 25 100 -1; do
    for fake_effect in -1 0 1; do
      for seed in $(seq 1001 1011); do
        add_spec strategy "strategy_m${memory}_f${fake_effect}_control_s${seed}" "$memory" 1 "$fake_effect" 1 none none 15 0.33 0 default "$seed"
        for scope in credibility non-credibility all; do
          for timing in 1 25 50 100; do
            add_spec strategy "strategy_m${memory}_f${fake_effect}_${scope}_p${timing}_s${seed}" \
              "$memory" 1 "$fake_effect" 1 "$timing" "$scope" 15 0.33 0 default "$seed"
          done
        done
      done
    done
  done
fi

if selected confirmation; then
  for fake_effect in -1 0 1; do
    for true_effect in 0 1; do
      for seed in $(seq 1001 1030); do
        add_spec confirmation "confirm_f${fake_effect}_t${true_effect}_control_s${seed}" \
          25 1 "$fake_effect" "$true_effect" none none 15 0.33 0 default "$seed"
        add_spec confirmation "confirm_f${fake_effect}_t${true_effect}_noncred_p1_s${seed}" \
          25 1 "$fake_effect" "$true_effect" 1 non-credibility 15 0.33 0 default "$seed"
      done
    done
  done
fi

if selected robustness; then
  for contacts in 6 15 27; do
    for reach_spec in off:0:default original:1:14.7 medium:1:46.7 traditional:1:81.2; do
      IFS=: read -r reach_label source_reach target_reach <<< "$reach_spec"
      for seed in $(seq 1001 1011); do
        add_spec robustness "robust_c${contacts}_${reach_label}_control_s${seed}" \
          25 1 -1 1 none none "$contacts" 0.33 "$source_reach" "$target_reach" "$seed"
        add_spec robustness "robust_c${contacts}_${reach_label}_noncred_p1_s${seed}" \
          25 1 -1 1 1 non-credibility "$contacts" 0.33 "$source_reach" "$target_reach" "$seed"
      done
    done
  done
fi

if [[ ! -s "$SPECS" ]]; then printf 'No experiment suites selected.\n' >&2; exit 2; fi

cd "$PROJECT_ROOT"
make build
javac -cp "build/classes:lib/*" -d build/classes experiments/SeededMain.java
export PYTHON_BIN
export SOURCE_WORKBOOK="${SOURCE_WORKBOOK:-$PROJECT_ROOT/input/FAKENEWS_BASELINE_2.xlsx}"
export JAVA_BIN="${JAVA_BIN:-java}"

printf 'Experiment root: %s\n' "$RUN_ROOT"
printf 'Conditions: %s; parallel jobs: %s\n' "$(wc -l < "$SPECS" | tr -d ' ')" "$JOBS"
while IFS= read -r spec; do printf '%s\0' "$spec"; done < "$SPECS" \
  | xargs -0 -n 1 -P "$JOBS" "$SCRIPT_DIR/run_dissemination_condition.sh" "$RUN_ROOT"

MANIFEST="$RUN_ROOT/manifest.tsv"
printf 'phase\tcondition\tmemory\twom\tfake_effect\ttrue_effect\ttiming\tscope\tcontacts\tfriends\tsource_reach\ttarget_reach\tseed\tworkbook\n' > "$MANIFEST"
cat "$RUN_ROOT"/fragments/*.tsv | sort >> "$MANIFEST"
printf 'Completed experiment suite: %s\n' "$RUN_ROOT"
printf 'Analyze with: %s %s\n' "$SCRIPT_DIR/analyze_dissemination_experiments.py" "$RUN_ROOT"
