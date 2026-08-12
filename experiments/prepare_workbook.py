#!/usr/bin/env python3
"""Create one isolated experiment workbook without changing the source workbook."""

from __future__ import annotations

import argparse
from pathlib import Path

from openpyxl import load_workbook


CONFIGURATION = {
    "PERIODS": 400,
    "AGENTS": 400,
    "REPETITIONS": 10,
    "GUI": 0,
    "LEARNING_PERIODS": 0,
    "SAVED_ENDORSEMENTS": 0,
    "SAVED_REPOSTS_PER_SOURCE": 1,
    "SAVED_DETAILED_AGENT_DECISIONS": 0,
    "SAVED_AGENT_DECISIONS": 0,
    "SAVED_FAKENEWS": 1,
    "COMPRESSED_RESULTS": 1,
}


def set_configuration(worksheet, key: str, value: int) -> None:
    for row in worksheet.iter_rows():
        if str(row[0].value).strip().upper() == key:
            row[1].value = value
            return
    worksheet.append([key, value])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--memory", type=int, required=True)
    parser.add_argument("--wom", type=int, choices=(0, 1), required=True)
    parser.add_argument("--scenario-period", type=int)
    args = parser.parse_args()

    if args.memory < 0 and args.memory != -1:
        parser.error("--memory must be -1 or a nonnegative integer")
    if args.scenario_period is not None and not 1 <= args.scenario_period <= 400:
        parser.error("--scenario-period must be within 1..400")
    if not args.source.is_file():
        parser.error(f"source workbook does not exist: {args.source}")

    workbook = load_workbook(args.source)
    configuration = workbook["Configuration"]
    values = dict(CONFIGURATION)
    values.update({
        "MEMORY": args.memory,
        "WOM": args.wom,
        "SCENARIO": 0 if args.scenario_period is None else -2,
    })
    for key, value in values.items():
        set_configuration(configuration, key, value)

    if args.scenario_period is not None:
        scenario = workbook["Scenario"]
        if not scenario.cell(1, 1).value or not scenario.cell(1, 2).value:
            parser.error("Scenario!A1 and Scenario!B1 must define source FROM and TO")
        scenario.cell(1, 3).value = args.scenario_period

    args.destination.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(args.destination)


if __name__ == "__main__":
    main()
