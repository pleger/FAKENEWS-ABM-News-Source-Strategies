#!/usr/bin/env python3
"""Validate, compare, and visualize the fake-news dissemination experiment suite."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path
import warnings

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


SOURCES = ["TRADITIONAL_MEDIA", "UNKNOWN_MEDIA", "FAKE_NEWS_SOURCE", "MIXED_SOURCE"]
TARGET = "FAKE_NEWS_SOURCE"
EXPECTED_FAKE_RATES = {
    "TRADITIONAL_MEDIA": 0.092,
    "UNKNOWN_MEDIA": 0.206,
    "FAKE_NEWS_SOURCE": 0.667,
    "MIXED_SOURCE": 0.416,
}
METRICS = [
    "fake_repost_share_final100",
    "target_share_final100",
    "fake_repost_share_all",
    "cumulative_fake_reposts",
    "target_fake_rate_all",
    "target_unique_reposters_p400",
]

warnings.filterwarnings("ignore", message="Workbook contains no default style")


def load_workbook_metrics(workbook: Path, metadata: dict[str, object]) -> dict[str, object]:
    reposts = pd.read_excel(workbook, sheet_name="RepostsPerSource", engine="openpyxl").dropna(axis=1, how="all")
    fake = pd.read_excel(workbook, sheet_name="FakeNewsPerSource", engine="openpyxl").dropna(axis=1, how="all")
    unique = pd.read_excel(workbook, sheet_name="UniqueRepostersPerSource", engine="openpyxl").dropna(axis=1, how="all")
    fake.rename(columns={"Simulation": "SimulationId"}, inplace=True)

    if len(reposts) != 400 or len(fake) != 400 or len(unique) != 400:
        raise ValueError(f"{workbook}: expected exactly 400 rows in each period-level report")
    for label, frame in (("reposts", reposts), ("fake", fake), ("unique", unique)):
        if frame[["SimulationId", "Period"]].duplicated().any():
            raise ValueError(f"{workbook}: duplicate {label} period rows")
    if not set(np.unique(fake[SOURCES].values)).issubset({0, 1}):
        raise ValueError(f"{workbook}: fake-news states must be binary")

    selection_totals = reposts[SOURCES].sum(axis=1)
    reach_enabled = int(metadata["source_reach"]) == 1
    if reach_enabled and not selection_totals.between(0, 400).all():
        raise ValueError(f"{workbook}: selection totals fall outside 0..400")
    if not reach_enabled and not (selection_totals == 400).all():
        raise ValueError(f"{workbook}: SOURCE_REACH=0 requires exactly 400 selections per period")

    merged = reposts.merge(fake, on=["SimulationId", "Period"], suffixes=("_reposts", "_fake"), validate="one_to_one")
    merged["target_share"] = merged[f"{TARGET}_reposts"] / 400.0
    merged["fake_reposts"] = sum(
        merged[f"{source}_reposts"] * merged[f"{source}_fake"] for source in SOURCES
    )
    merged["fake_repost_share"] = merged.fake_reposts / 400.0
    final = merged[merged.Period >= 301]
    p400 = unique[unique.Period == 400]

    result = {
        **metadata,
        "cell": re.sub(r"_s\d+$", "", str(metadata["condition"])),
        "selection_total_min": float(selection_totals.min()),
        "selection_total_max": float(selection_totals.max()),
        "fake_repost_share_final100": float(final.fake_repost_share.mean()),
        "target_share_final100": float(final.target_share.mean()),
        "fake_repost_share_all": float(merged.fake_repost_share.mean()),
        "cumulative_fake_reposts": float(merged.fake_reposts.sum()),
        "target_fake_rate_all": float(merged[f"{TARGET}_fake"].mean()),
        "target_unique_reposters_p400": float(p400[TARGET].iloc[0]),
    }
    for source in SOURCES:
        result[f"{source.lower()}_fake_rate_all"] = float(merged[f"{source}_fake"].mean())
    return result


def summarize(runs: pd.DataFrame) -> pd.DataFrame:
    keys = [
        "phase", "cell", "memory", "wom", "fake_effect", "true_effect", "timing", "scope",
        "contacts", "friends", "source_reach", "target_reach",
    ]
    rows: list[dict[str, object]] = []
    for group_values, group in runs.groupby(keys, dropna=False, sort=False):
        row = dict(zip(keys, group_values))
        row["n"] = len(group)
        for metric in METRICS:
            values = group[metric].astype(float)
            row[f"{metric}_mean"] = values.mean()
            row[f"{metric}_sd"] = values.std(ddof=1)
            row[f"{metric}_se"] = values.std(ddof=1) / math.sqrt(len(values)) if len(values) > 1 else math.nan
        rows.append(row)
    return pd.DataFrame(rows)


def paired_difference(treatment: pd.DataFrame, control: pd.DataFrame, metric: str) -> dict[str, float | int]:
    paired = treatment[["seed", metric]].merge(
        control[["seed", metric]], on="seed", suffixes=("_treatment", "_control"), validate="one_to_one"
    )
    differences = paired[f"{metric}_treatment"] - paired[f"{metric}_control"]
    n = len(differences)
    mean = float(differences.mean())
    se = float(differences.std(ddof=1) / math.sqrt(n)) if n > 1 else math.nan
    return {
        "n_pairs": n,
        f"{metric}_effect": mean,
        f"{metric}_effect_se": se,
        f"{metric}_ci95_low": mean - 1.96 * se if n > 1 else math.nan,
        f"{metric}_ci95_high": mean + 1.96 * se if n > 1 else math.nan,
    }


def baseline_analysis(runs: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    baseline = runs[runs.phase == "baseline"].copy()
    if baseline.empty:
        return (
            pd.DataFrame(columns=["check", "expected", "observed", "tolerance", "passed"]),
            pd.DataFrame(columns=["contrast"]),
        )
    labels = {"baseline_b0": "B0", "baseline_b1": "B1", "baseline_b2": "B2"}
    baseline["baseline"] = baseline.cell.map(labels)
    contrasts = []
    for treatment_label, control_label in (("B1", "B0"), ("B2", "B1")):
        treatment = baseline[baseline.baseline == treatment_label]
        control = baseline[baseline.baseline == control_label]
        row: dict[str, object] = {"contrast": f"{treatment_label}-{control_label}"}
        for metric in ("fake_repost_share_final100", "target_share_final100"):
            row.update(paired_difference(treatment, control, metric))
        contrasts.append(row)

    checks = []
    truth_sensitive = baseline[baseline.baseline == "B2"]
    for source, expected in EXPECTED_FAKE_RATES.items():
        observed = truth_sensitive[f"{source.lower()}_fake_rate_all"].mean()
        checks.append({
            "check": f"{source} fake-news rate",
            "expected": expected,
            "observed": observed,
            "tolerance": 0.02,
            "passed": abs(observed - expected) <= 0.02,
        })
    checks.append({
        "check": "B2-B1 fake-repost effect is negative",
        "expected": "< 0",
        "observed": contrasts[1]["fake_repost_share_final100_effect"],
        "tolerance": "paired 95% CI below 0",
        "passed": contrasts[1]["fake_repost_share_final100_ci95_high"] < 0,
    })
    return pd.DataFrame(checks), pd.DataFrame(contrasts)


def scenario_effects(runs: pd.DataFrame) -> pd.DataFrame:
    experiment = runs[runs.phase.isin(["strategy", "confirmation", "robustness"])].copy()
    match_keys = [
        "phase", "memory", "wom", "fake_effect", "true_effect", "contacts", "friends",
        "source_reach", "target_reach",
    ]
    rows = []
    for _, treatment_cell in experiment[experiment.timing != "none"].groupby("cell", sort=False):
        first = treatment_cell.iloc[0]
        control = experiment[experiment.timing == "none"]
        for key in match_keys:
            control = control[control[key].astype(str) == str(first[key])]
        if control.empty:
            raise ValueError(f"No matched control for {first['cell']}")
        row = {key: first[key] for key in match_keys}
        row.update({"cell": first.cell, "timing": first.timing, "scope": first.scope})
        for metric in ("fake_repost_share_final100", "target_share_final100", "cumulative_fake_reposts"):
            row.update(paired_difference(treatment_cell, control, metric))
        rows.append(row)
    if rows:
        return pd.DataFrame(rows)
    return pd.DataFrame(columns=[
        "phase", "cell", "memory", "wom", "fake_effect", "true_effect", "timing", "scope",
        "contacts", "friends", "source_reach", "target_reach",
        "fake_repost_share_final100_effect", "target_share_final100_effect",
    ])


def save_figures(summary: pd.DataFrame, effects: pd.DataFrame, output: Path) -> None:
    figures = output / "figures"
    figures.mkdir(exist_ok=True)

    baseline = summary[summary.phase == "baseline"].sort_values("cell")
    if not baseline.empty:
        fig, axis = plt.subplots(figsize=(7, 4))
        axis.bar(["B0\nSin WOM", "B1\nDescubrimiento", "B2\nVeracidad"],
                 baseline.fake_repost_share_final100_mean * 100, color=["#8c8c8c", "#4c78a8", "#e45756"])
        axis.set_ylabel("Reposts falsos en períodos 301–400 (%)")
        axis.set_title("Validación del baseline WOM")
        fig.tight_layout()
        fig.savefig(figures / "baseline_wom.png", dpi=180)
        plt.close(fig)

    strategy = effects[effects.phase == "strategy"].sort_values("fake_repost_share_final100_effect", ascending=False).head(20)
    if not strategy.empty:
        labels = strategy.apply(lambda row: f"{row.scope} P{row.timing} M{row.memory} F{row.fake_effect}", axis=1)
        fig, axis = plt.subplots(figsize=(9, 7))
        axis.barh(labels[::-1], strategy.fake_repost_share_final100_effect[::-1] * 100, color="#d95f02")
        axis.axvline(0, color="black", linewidth=0.8)
        axis.set_xlabel("Efecto vs. control (puntos porcentuales de reposts falsos)")
        axis.set_title("Estrategias con mayor diseminación simulada")
        fig.tight_layout()
        fig.savefig(figures / "strategy_ranking.png", dpi=180)
        plt.close(fig)

    sensitivity = effects[(effects.phase == "strategy") & (effects.scope == "non-credibility")
                          & (effects.fake_effect == -1) & (effects.true_effect == 1)].copy()
    if not sensitivity.empty:
        fig, axis = plt.subplots(figsize=(7, 4.5))
        for memory, group in sensitivity.groupby("memory"):
            group = group.assign(timing_number=group.timing.astype(int)).sort_values("timing_number")
            axis.plot(group.timing_number, group.fake_repost_share_final100_effect * 100,
                      marker="o", label=f"Memoria {memory}")
        axis.axhline(0, color="black", linewidth=0.8)
        axis.set_xlabel("Período de activación")
        axis.set_ylabel("Efecto en reposts falsos (puntos porcentuales)")
        axis.set_title("Camuflaje: interacción entre memoria y momento")
        axis.legend()
        fig.tight_layout()
        fig.savefig(figures / "memory_timing_non_credibility.png", dpi=180)
        plt.close(fig)


def write_report(checks: pd.DataFrame, contrasts: pd.DataFrame, effects: pd.DataFrame, output: Path) -> None:
    passed = int(checks.passed.sum()) if not checks.empty else 0
    total = len(checks)
    ranking = effects.sort_values("fake_repost_share_final100_effect", ascending=False).head(10)
    baseline_status = (
        f"Passed checks: **{passed}/{total}**. A failed directional WOM check requires diagnosis; "
        "it must not be silently discarded."
        if total else "Baseline suite not present in this experiment root."
    )
    lines = [
        "# Fake-news dissemination experiment report",
        "",
        "## Baseline validation",
        "",
        baseline_status,
        "",
        checks.to_markdown(index=False) if not checks.empty else "Baseline suite not present.",
        "",
        "### Paired WOM contrasts",
        "",
        contrasts.to_markdown(index=False) if not contrasts.empty else "No baseline contrasts available.",
        "",
        "## Scenario ranking",
        "",
        "The primary outcome is the paired change in actual fake repost share, not source popularity.",
        "",
        ranking[["scope", "timing", "memory", "fake_effect", "true_effect",
                 "fake_repost_share_final100_effect", "fake_repost_share_final100_ci95_low",
                 "fake_repost_share_final100_ci95_high", "target_share_final100_effect"]].to_markdown(index=False)
        if not ranking.empty else "Scenario suites not present.",
        "",
        "## Interpretation constraint",
        "",
        "Copying credibility changes the model's objective fake-news probability. Therefore, credibility-only and full-copy scenarios are mechanistic diagnostics. The non-credibility scenario is the primary dissemination treatment because it retains the target source's fake-news propensity.",
    ]
    if not checks.empty:
        baseline_heading = lines.index("## Scenario ranking")
        lines[baseline_heading:baseline_heading] = ["![Baseline WOM](figures/baseline_wom.png)", ""]
    if not ranking.empty:
        interpretation_heading = lines.index("## Interpretation constraint")
        figure_lines = ["![Strategy ranking](figures/strategy_ranking.png)", ""]
        sensitivity = effects[(effects.phase == "strategy") & (effects.scope == "non-credibility")
                              & (effects.fake_effect == -1) & (effects.true_effect == 1)]
        if not sensitivity.empty:
            figure_lines.extend(["![Memory and timing](figures/memory_timing_non_credibility.png)", ""])
        lines[interpretation_heading:interpretation_heading] = figure_lines
    (output / "dissemination_findings.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("experiment_root", type=Path)
    args = parser.parse_args()
    manifest_path = args.experiment_root / "manifest.tsv"
    manifest = pd.read_csv(manifest_path, sep="\t", dtype={"timing": str, "seed": int, "target_reach": str})
    runs = pd.DataFrame([
        load_workbook_metrics(Path(record.pop("workbook")), record)
        for record in manifest.to_dict("records")
    ])
    output = args.experiment_root / "analysis"
    output.mkdir(exist_ok=True)
    summary = summarize(runs)
    checks, contrasts = baseline_analysis(runs)
    effects = scenario_effects(runs)
    ranking = effects.sort_values("fake_repost_share_final100_effect", ascending=False)

    runs.to_csv(output / "run_metrics.csv", index=False)
    summary.to_csv(output / "condition_summary.csv", index=False)
    checks.to_csv(output / "baseline_checks.csv", index=False)
    contrasts.to_csv(output / "baseline_contrasts.csv", index=False)
    effects.to_csv(output / "scenario_effects.csv", index=False)
    ranking.to_csv(output / "strategy_ranking.csv", index=False)
    save_figures(summary, effects, output)
    write_report(checks, contrasts, effects, output)
    validation = {
        "workbooks": len(manifest),
        "runs": len(runs),
        "baseline_checks_passed": int(checks.passed.sum()) if not checks.empty else 0,
        "baseline_checks_total": len(checks),
    }
    (output / "validation.json").write_text(json.dumps(validation, indent=2), encoding="utf-8")
    print(f"Validated {len(manifest)} workbooks")
    print(f"Analysis written to {output}")


if __name__ == "__main__":
    main()
