#!/usr/bin/env python3
"""Rank simulated parameter cells against user-supplied empirical target metrics."""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


PARAMETERS = ["memory", "base", "wom_weight"]
REQUIRED_TARGETS = {"metric", "value", "scale", "weight"}
REQUIRED_SIMULATIONS = {*PARAMETERS, "metric", "value"}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compute a weighted normalized-error score for each MEMORY/BASE/WOM-weight cell."
    )
    parser.add_argument("empirical_targets", type=Path)
    parser.add_argument("simulated_metrics", type=Path)
    parser.add_argument("--output", type=Path, default=Path("calibration_scores.csv"))
    args = parser.parse_args()

    targets = pd.read_csv(args.empirical_targets)
    simulated = pd.read_csv(args.simulated_metrics)
    missing_targets = REQUIRED_TARGETS - set(targets.columns)
    missing_simulations = REQUIRED_SIMULATIONS - set(simulated.columns)
    if missing_targets or missing_simulations:
        parser.error(
            f"missing target columns {sorted(missing_targets)}; "
            f"missing simulation columns {sorted(missing_simulations)}"
        )
    if targets.metric.duplicated().any():
        parser.error("empirical target metrics must be unique")
    if targets[list(REQUIRED_TARGETS)].isna().any().any():
        parser.error("empirical metric, value, scale, and weight fields must not be blank")
    if simulated[list(REQUIRED_SIMULATIONS)].isna().any().any():
        parser.error("simulated parameter, metric, and value fields must not be blank")
    if (targets["scale"] <= 0).any() or (targets["weight"] < 0).any():
        parser.error("scale must be positive and weight must be nonnegative")

    joined = simulated.merge(targets, on="metric", suffixes=("_simulated", "_empirical"), validate="many_to_one")
    if set(targets.metric) - set(joined.metric):
        parser.error("at least one empirical target has no matching simulated metric")
    matched_per_cell = joined.groupby(PARAMETERS).metric.nunique()
    if not matched_per_cell.eq(len(targets)).all():
        parser.error("every parameter cell must contain every empirical target metric exactly once")
    joined["normalized_squared_error"] = (
        (joined.value_simulated - joined.value_empirical) / joined.scale
    ) ** 2
    joined["weighted_error"] = joined.normalized_squared_error * joined.weight
    scores = joined.groupby(PARAMETERS, as_index=False).agg(
        calibration_score=("weighted_error", "sum"),
        matched_metrics=("metric", "nunique"),
    )
    scores.sort_values("calibration_score", inplace=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    scores.to_csv(args.output, index=False)
    print(f"Ranked {len(scores)} parameter cells; best score={scores.calibration_score.iloc[0]:.6g}")


if __name__ == "__main__":
    main()
