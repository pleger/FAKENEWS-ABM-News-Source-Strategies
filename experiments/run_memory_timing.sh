#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INPUT_WORKBOOK="$PROJECT_ROOT/input/FAKENEWS_BASELINE_2.xlsx"
MEMORY=-1
WOM=1
PYTHON_BIN="${PYTHON_BIN:-python3}"
JAVA_BIN="${JAVA_BIN:-java}"
KEEP_WORKBOOKS=0

usage() {
  printf 'Usage: %s [--input workbook.xlsx] [--memory -1|N] [--wom 0|1] [--keep-workbooks]\n' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input|--memory|--wom)
      if [[ $# -lt 2 ]]; then printf 'Missing value for %s\n' "$1" >&2; exit 2; fi
      option="$1"; value="$2"; shift 2
      case "$option" in
        --input) INPUT_WORKBOOK="$value" ;;
        --memory) MEMORY="$value" ;;
        --wom) WOM="$value" ;;
      esac
      ;;
    --keep-workbooks) KEEP_WORKBOOKS=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ ! -f "$INPUT_WORKBOOK" ]]; then
  printf 'Input workbook not found: %s\n' "$INPUT_WORKBOOK" >&2
  exit 1
fi
if [[ "$MEMORY" != "-1" && ! "$MEMORY" =~ ^[0-9]+$ ]]; then
  printf 'Memory must be -1 or a nonnegative integer.\n' >&2
  exit 2
fi
if [[ "$WOM" != "0" && "$WOM" != "1" ]]; then
  printf 'WOM must be 0 or 1.\n' >&2
  exit 2
fi
if ! "$PYTHON_BIN" -c 'import openpyxl' 2>/dev/null; then
  printf 'Python package openpyxl is required. Install experiments/requirements.txt.\n' >&2
  exit 1
fi

cd "$PROJECT_ROOT"
make build

EXPERIMENT_TMP="$(mktemp -d "${TMPDIR:-/tmp}/fakenews-experiment.XXXXXX")"
cleanup_workbooks() {
  if [[ "$KEEP_WORKBOOKS" == "1" ]]; then
    printf 'Temporary workbooks retained at %s\n' "$EXPERIMENT_TMP"
    return
  fi
  find "$EXPERIMENT_TMP" -type f -delete
  rmdir "$EXPERIMENT_TMP" 2>/dev/null || true
}
trap cleanup_workbooks EXIT

run_condition() {
  local label="$1"
  local scenario_period="${2:-}"
  local workbook="$EXPERIMENT_TMP/FAKENEWS_M${MEMORY}_WOM${WOM}_${label}.xlsx"
  local prepare=("$PYTHON_BIN" "$SCRIPT_DIR/prepare_workbook.py" "$INPUT_WORKBOOK" "$workbook" --memory "$MEMORY" --wom "$WOM")
  if [[ -n "$scenario_period" ]]; then
    prepare+=(--scenario-period "$scenario_period")
  fi
  "${prepare[@]}"

  printf '\nRunning condition %s (MEMORY=%s, WOM=%s)\n' "$label" "$MEMORY" "$WOM"
  "$JAVA_BIN" -cp "build/classes:lib/*" Main \
    --input "$workbook" \
    --periods 400 \
    --agents 400 \
    --repetitions 10 \
    --learning-periods 0 \
    --no-gui
}

run_condition no_scenario
for scenario_period in 1 25 50 100; do
  run_condition "scenario_${scenario_period}" "$scenario_period"
done

printf '\nCompleted five conditions. Current repetition semantics produce 11 runs per condition (55 total).\n'
printf 'Results are in %s/output/.\n' "$PROJECT_ROOT"
