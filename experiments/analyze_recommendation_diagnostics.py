#!/usr/bin/env python3
"""Validate RQ3 diagnostic re-executions and publish mechanism-level aggregates."""

from __future__ import annotations

import argparse
from concurrent.futures import ProcessPoolExecutor
import json
import math
from pathlib import Path

from openpyxl import load_workbook
import numpy as np
import pandas as pd

import analyze_final_study as core
import analyze_major_revision as revision


EXPECTED_RUNS = 360
EXPECTED_CONDITIONS = 12
COUNT_COLUMNS = [
    "receivers_with_recommendation", "contact_recommendations",
    "duplicate_source_recommendations", "exact_maximum_ties", "new_source_discoveries",
    "labels_covered", "labels_uncovered", "true_positive_labels", "false_negative_labels",
    "true_negative_labels", "false_positive_labels", "rewarded_recommendations",
    "penalized_recommendations", "ignored_recommendations", "endorsements_scheduled",
    "endorsements_delivered",
]
RATE_COLUMNS = [
    "receiver_period_rate", "mean_contacts_per_receiver", "duplicate_rate", "tie_rate",
    "discovery_rate", "realised_coverage", "realised_sensitivity", "realised_specificity",
    "reward_rate", "penalty_rate", "ignore_rate", "delivery_completion",
]


def snake(name: str) -> str:
    result = []
    for index, character in enumerate(name):
        if character.isupper() and index and (not name[index - 1].isupper()):
            result.append("_")
        result.append(character.lower())
    return "".join(result)


def ratio(numerator: float, denominator: float) -> float:
    return numerator / denominator if denominator else math.nan


def read_diagnostic(record: dict[str, object]) -> dict[str, object]:
    run = core._read_run(record)
    run.pop("_period_metrics", None)
    path = Path(str(record["result_workbook"]))
    workbook = load_workbook(path, read_only=True, data_only=True)
    try:
        headers, rows = core._sheet_rows(workbook, "WomDiagnostics")
    finally:
        workbook.close()
    if len(rows) != core.PERIODS:
        raise ValueError(f"{path}: expected {core.PERIODS} WOM diagnostic rows; found {len(rows)}")
    index = {snake(header): column for column, header in enumerate(headers)}
    periods = [int(row[index["period"]]) for row in rows]
    if periods != list(range(1, core.PERIODS + 1)):
        raise ValueError(f"{path}: WOM diagnostic periods are incomplete or unordered")
    counts = {column: int(sum(int(row[index[column]] or 0) for row in rows)) for column in COUNT_COLUMNS}
    receivers = counts["receivers_with_recommendation"]
    covered = counts["labels_covered"]
    actual_false = counts["true_positive_labels"] + counts["false_negative_labels"]
    actual_true = counts["true_negative_labels"] + counts["false_positive_labels"]
    run.update(counts)
    run.update({
        "policy": revision.policy_of(str(record["condition"])),
        "diagnostic_strategy": revision.strategy_of(str(record["condition"])),
        "receiver_period_rate": receivers / (core.AGENTS * core.PERIODS),
        "mean_contacts_per_receiver": ratio(counts["contact_recommendations"], receivers),
        "duplicate_rate": ratio(counts["duplicate_source_recommendations"], counts["contact_recommendations"]),
        "tie_rate": ratio(counts["exact_maximum_ties"], receivers),
        "discovery_rate": ratio(counts["new_source_discoveries"], receivers),
        "realised_coverage": ratio(covered, covered + counts["labels_uncovered"]),
        "realised_sensitivity": ratio(counts["true_positive_labels"], actual_false),
        "realised_specificity": ratio(counts["true_negative_labels"], actual_true),
        "reward_rate": ratio(counts["rewarded_recommendations"], covered),
        "penalty_rate": ratio(counts["penalized_recommendations"], covered),
        "ignore_rate": ratio(counts["ignored_recommendations"], covered),
        "delivery_completion": ratio(counts["endorsements_delivered"], counts["endorsements_scheduled"]),
    })
    if covered + counts["labels_uncovered"] != receivers:
        raise ValueError(f"{path}: label accounting does not equal processed recommendations")
    if (counts["true_positive_labels"] + counts["false_negative_labels"]
            + counts["true_negative_labels"] + counts["false_positive_labels"] != covered):
        raise ValueError(f"{path}: classification accounting does not equal covered labels")
    if (counts["rewarded_recommendations"] + counts["penalized_recommendations"]
            + counts["ignored_recommendations"] != covered):
        raise ValueError(f"{path}: policy-effect accounting does not equal covered labels")
    if counts["endorsements_scheduled"] != (counts["rewarded_recommendations"]
                                             + counts["penalized_recommendations"]):
        raise ValueError(f"{path}: scheduled endorsements do not equal non-ignored labels")
    return run


def reference_condition(condition: str) -> tuple[str, str]:
    if condition == "disabled_control_r14_7":
        return "existing", "control_reach14_7_wom0"
    if condition == "oracle_control_r14_7":
        return "existing", "control_reach14_7_wom1"
    return "revision", condition


def verify_non_interference(runs: pd.DataFrame, existing: pd.DataFrame,
                            revised: pd.DataFrame) -> list[dict[str, object]]:
    metrics = ["fake_repost_share_all", "fake_repost_share_decisions", "target_share_all",
               "target_share_decisions", "participation_rate", "total_decisions",
               "cumulative_fake_reposts", "selections_traditional_media_all",
               "selections_unknown_media_all", "selections_fake_news_source_all",
               "selections_mixed_source_all"]
    mismatches = []
    for _, row in runs.iterrows():
        source, condition = reference_condition(row.condition)
        reference = existing if source == "existing" else revised
        match = reference[(reference.condition == condition) & (reference.seed == row.seed)]
        if len(match) != 1:
            raise ValueError(f"No unique reference run for {row.condition}/{row.seed}")
        reference_row = match.iloc[0]
        for metric in metrics:
            if not np.isclose(float(row[metric]), float(reference_row[metric]), rtol=0, atol=1e-15):
                mismatches.append({"condition": row.condition, "seed": row.seed,
                                   "metric": metric, "diagnostic": row[metric],
                                   "reference": reference_row[metric]})
    return mismatches


def paired_mechanism_effects(runs: pd.DataFrame) -> pd.DataFrame:
    metrics = RATE_COLUMNS + ["fake_repost_share_decisions", "target_share_decisions",
                              "participation_rate", "selections_traditional_media_all",
                              "selections_unknown_media_all", "selections_fake_news_source_all",
                              "selections_mixed_source_all"]
    rows = []
    for policy, group in runs.groupby("policy", sort=False):
        treatment = group[group.diagnostic_strategy == "combined"]
        control = group[group.diagnostic_strategy == "control"]
        for metric in metrics:
            values = treatment[["seed", metric]].merge(control[["seed", metric]], on="seed",
                                                        suffixes=("_t", "_c"))
            values = values.dropna()
            if len(values) < 2:
                continue
            treatment_values = values[["seed", f"{metric}_t"]].rename(columns={f"{metric}_t": metric})
            control_values = values[["seed", f"{metric}_c"]].rename(columns={f"{metric}_c": metric})
            stats = core.paired_stats(treatment_values, control_values, metric)
            rows.append({"policy": policy, "metric": metric, **stats,
                         "control_mean": float(values[f"{metric}_c"].mean()),
                         "treatment_mean": float(values[f"{metric}_t"].mean())})
    return pd.DataFrame(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--existing", type=Path, required=True)
    parser.add_argument("--revision", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("analysis/major-revision"))
    parser.add_argument("--jobs", type=int, default=6)
    args = parser.parse_args()
    manifest = pd.read_csv(args.root / "manifest.tsv", sep="\t")
    complete = manifest[manifest.status == "COMPLETE"].copy()
    if len(complete) != EXPECTED_RUNS or complete.condition.nunique() != EXPECTED_CONDITIONS:
        raise ValueError("Expected 360 complete diagnostics across 12 conditions")
    with ProcessPoolExecutor(max_workers=args.jobs) as pool:
        rows = list(pool.map(read_diagnostic, complete.to_dict("records"), chunksize=2))
    runs = core.public_runs(pd.DataFrame(rows))
    existing = pd.read_csv(args.existing)
    revised = pd.read_csv(args.revision)
    mismatches = verify_non_interference(runs, existing, revised)
    if mismatches:
        raise ValueError(f"Diagnostic instrumentation changed {len(mismatches)} archived outcomes")
    disabled = runs[runs.policy == "disabled"]
    if disabled[COUNT_COLUMNS].to_numpy().sum() != 0:
        raise ValueError("WOM-disabled diagnostics must contain zero recommendation events")

    summary = runs.groupby(["policy", "diagnostic_strategy"], as_index=False)[COUNT_COLUMNS + RATE_COLUMNS].agg(
        ["mean", "std"])
    summary.columns = ["_".join(str(value) for value in column if value).rstrip("_")
                       for column in summary.columns.to_flat_index()]
    effects = paired_mechanism_effects(runs)
    configurations = runs[["policy", "wom", "wom_fake_effect", "wom_true_effect",
                           "wom_label_delay", "wom_label_coverage", "wom_label_sensitivity",
                           "wom_label_specificity", "wom_receiver_scale"]].drop_duplicates().sort_values("policy")

    args.output.mkdir(parents=True, exist_ok=True)
    runs.to_csv(args.output / "rq3-mechanism-diagnostics.csv.gz", index=False, compression="gzip")
    summary.to_csv(args.output / "rq3-mechanism-summary.csv", index=False)
    effects.to_csv(args.output / "rq3-mechanism-effects.csv", index=False)
    configurations.to_csv(args.output / "rq3-policy-configurations.csv", index=False)
    audit = {"status": "PASS", "diagnostic_reexecutions": len(runs),
             "conditions": int(runs.condition.nunique()), "periods_per_run": core.PERIODS,
             "outcome_mismatches": 0, "accounting_checks": "PASS"}
    (args.output / "diagnostic-validation.json").write_text(json.dumps(audit, indent=2) + "\n",
                                                              encoding="utf-8")
    revision.write_checksums(args.output)
    print(json.dumps(audit, indent=2))


if __name__ == "__main__":
    main()
