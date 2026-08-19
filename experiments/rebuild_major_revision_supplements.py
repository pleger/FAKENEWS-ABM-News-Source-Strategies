#!/usr/bin/env python3
"""Rebuild promised major-revision supporting artifacts from public processed data."""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

import analyze_major_revision as revision


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--elementary", type=Path,
                        default=Path("analysis/major-revision/morris-elementary-effects.csv.gz"))
    parser.add_argument("--existing", type=Path,
                        default=Path("analysis/major-revision-existing/run-metrics.csv"))
    parser.add_argument("--existing-periods", type=Path,
                        default=Path("analysis/major-revision-existing/period-metrics.csv.gz"))
    parser.add_argument("--output", type=Path, default=Path("analysis/major-revision"))
    args = parser.parse_args()

    stability, source_windows = revision.rebuild_public_supplements(
        pd.read_csv(args.elementary), pd.read_csv(args.existing_periods), pd.read_csv(args.existing))
    args.output.mkdir(parents=True, exist_ok=True)
    stability.to_csv(args.output / "morris-stability.csv", index=False)
    source_windows.to_csv(args.output / "rq2-source-window-effects.csv", index=False)
    revision.write_data_dictionary(args.output)
    revision.write_checksums(args.output)
    print(f"Wrote {len(stability)} Morris-stability rows and {len(source_windows)} RQ2 source-window rows")


if __name__ == "__main__":
    main()
