#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s RUN_ROOT TAB_SEPARATED_SPEC\n' "$0" >&2
  exit 2
fi

RUN_ROOT="$1"
IFS=$'\t' read -r QUESTION CONDITION MEMORY WOM TIMING SCOPE CONTACTS FRIENDS SOURCE_REACH SEED <<< "$2"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
JAVA_BIN="${JAVA_BIN:-java}"
SOURCE_WORKBOOK="${SOURCE_WORKBOOK:-$PROJECT_ROOT/input/FAKENEWS_BASELINE_2.xlsx}"

INPUT_COPY="$RUN_ROOT/workbooks/$CONDITION.xlsx"
LOG_FILE="$RUN_ROOT/logs/$CONDITION.log"
RESULT_DIR="$RUN_ROOT/results/$CONDITION"
PREPARE=("$PYTHON_BIN" "$PROJECT_ROOT/experiments/prepare_workbook.py" "$SOURCE_WORKBOOK" "$INPUT_COPY"
  --memory "$MEMORY" --wom "$WOM" --periods 400 --agents 400)

if [[ "$TIMING" == "none" ]]; then
  PREPARE+=(--repetitions "$([[ "$SEED" == "none" ]] && printf 10 || printf 0)")
else
  PREPARE+=(--scenario-period "$TIMING" --scenario-attributes "$SCOPE"
    --repetitions "$([[ "$SEED" == "none" ]] && printf 10 || printf 0)")
fi
if [[ "$CONTACTS" != "default" ]]; then PREPARE+=(--contacts "$CONTACTS"); fi
if [[ "$FRIENDS" != "default" ]]; then PREPARE+=(--friends "$FRIENDS"); fi
if [[ "$SOURCE_REACH" != "default" ]]; then PREPARE+=(--source-reach "$SOURCE_REACH"); fi
"${PREPARE[@]}"

WOM_OPTION=--no-wom
if [[ "$WOM" == "1" ]]; then WOM_OPTION=--wom; fi
MAIN_OPTIONS=(--input "$INPUT_COPY" --periods 400 --agents 400 --learning-periods 0 "$WOM_OPTION" --no-gui)
if [[ "$SEED" == "none" ]]; then
  COMMAND=("$JAVA_BIN" -cp "build/classes:lib/*" Main --repetitions 10 "${MAIN_OPTIONS[@]}")
else
  COMMAND=("$JAVA_BIN" -cp "build/classes:lib/*" SeededMain
    --seed "$SEED" --repetitions 0 "${MAIN_OPTIONS[@]}")
fi

printf 'Starting %s\n' "$CONDITION"
(cd "$PROJECT_ROOT" && "${COMMAND[@]}") 2>&1 | tee "$LOG_FILE"
GENERATED_DIR="$(sed -n 's/.*Saving results in: //p' "$LOG_FILE" | tail -1)"
if [[ -z "$GENERATED_DIR" || ! -d "$GENERATED_DIR" ]]; then
  printf 'Could not resolve generated output directory for %s\n' "$CONDITION" >&2
  exit 1
fi
/bin/mv "$GENERATED_DIR" "$RESULT_DIR"
WORKBOOK="$(find "$RESULT_DIR" -maxdepth 1 -type f -name '*.xlsx' ! -name '~$*' -print | head -1)"
if [[ -z "$WORKBOOK" ]]; then
  printf 'No result workbook found for %s\n' "$CONDITION" >&2
  exit 1
fi

printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
  "$QUESTION" "$CONDITION" "$MEMORY" "$WOM" "$TIMING" "$SCOPE" "$CONTACTS" "$FRIENDS" \
  "$SOURCE_REACH" "$SEED" "$WORKBOOK" > "$RUN_ROOT/fragments/$CONDITION.tsv"
printf 'Completed %s\n' "$CONDITION"
