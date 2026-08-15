#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s RUN_ROOT TAB_SEPARATED_SPEC\n' "$0" >&2
  exit 2
fi

RUN_ROOT="$1"
IFS=$'\t' read -r PHASE CONDITION MEMORY WOM FAKE_EFFECT TRUE_EFFECT TIMING SCOPE \
  CONTACTS FRIENDS SOURCE_REACH TARGET_REACH SEED <<< "$2"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
JAVA_BIN="${JAVA_BIN:-java}"
SOURCE_WORKBOOK="${SOURCE_WORKBOOK:-$PROJECT_ROOT/input/FAKENEWS_BASELINE_2.xlsx}"

INPUT_COPY="$RUN_ROOT/workbooks/$CONDITION.xlsx"
LOG_FILE="$RUN_ROOT/logs/$CONDITION.log"
RESULT_DIR="$RUN_ROOT/results/$CONDITION"
PREPARE=("$PYTHON_BIN" "$PROJECT_ROOT/experiments/prepare_workbook.py" "$SOURCE_WORKBOOK" "$INPUT_COPY"
  --memory "$MEMORY" --wom "$WOM" --periods 400 --agents 400 --repetitions 0
  --wom-fake-news-effect "$FAKE_EFFECT" --wom-true-news-effect "$TRUE_EFFECT"
  --source-reach "$SOURCE_REACH")

if [[ "$TIMING" != "none" ]]; then
  PREPARE+=(--scenario-period "$TIMING" --scenario-attributes "$SCOPE")
fi
if [[ "$CONTACTS" != "default" ]]; then PREPARE+=(--contacts "$CONTACTS"); fi
if [[ "$FRIENDS" != "default" ]]; then PREPARE+=(--friends "$FRIENDS"); fi
if [[ "$TARGET_REACH" != "default" ]]; then
  PREPARE+=(--target-source FAKE_NEWS_SOURCE --target-source-reach "$TARGET_REACH")
fi
"${PREPARE[@]}"

WOM_OPTION=--no-wom
if [[ "$WOM" == "1" ]]; then WOM_OPTION=--wom; fi
COMMAND=("$JAVA_BIN" --add-opens java.base/java.lang=ALL-UNNAMED
  -cp "build/classes:lib/*" SeededMain --seed "$SEED"
  --input "$INPUT_COPY" --periods 400 --agents 400 --repetitions 0 --learning-periods 0
  "$WOM_OPTION" --wom-fake-news-effect "$FAKE_EFFECT" --wom-true-news-effect "$TRUE_EFFECT" --no-gui)

printf 'Starting %s\n' "$CONDITION"
(cd "$PROJECT_ROOT" && "${COMMAND[@]}") > "$LOG_FILE" 2>&1
GENERATED_DIR="$(sed -n 's/.*Saving results in: //p' "$LOG_FILE" | tail -1)"
if [[ -z "$GENERATED_DIR" || ! -d "$GENERATED_DIR" ]]; then
  printf 'Could not resolve generated output directory for %s; see %s\n' "$CONDITION" "$LOG_FILE" >&2
  exit 1
fi
/bin/mv "$GENERATED_DIR" "$RESULT_DIR"
WORKBOOK="$(find "$RESULT_DIR" -maxdepth 1 -type f -name '*.xlsx' ! -name '~$*' -print | head -1)"
if [[ -z "$WORKBOOK" ]]; then
  printf 'No result workbook found for %s\n' "$CONDITION" >&2
  exit 1
fi

printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
  "$PHASE" "$CONDITION" "$MEMORY" "$WOM" "$FAKE_EFFECT" "$TRUE_EFFECT" "$TIMING" "$SCOPE" \
  "$CONTACTS" "$FRIENDS" "$SOURCE_REACH" "$TARGET_REACH" "$SEED" "$WORKBOOK" \
  > "$RUN_ROOT/fragments/$CONDITION.tsv"
printf 'Completed %s\n' "$CONDITION"
