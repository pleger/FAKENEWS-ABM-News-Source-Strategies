#!/usr/bin/env python3
"""Validate and summarize the workbook-driven research-question experiments."""

from __future__ import annotations

import argparse
import math
from pathlib import Path
import warnings

import numpy as np
import pandas as pd
from scipy import stats


SOURCE_COLUMNS = ["TRADITIONAL_MEDIA", "UNKNOWN_MEDIA", "FAKE_NEWS_SOURCE", "MIXED_SOURCE"]
TARGET = "FAKE_NEWS_SOURCE"

warnings.filterwarnings("ignore", message="Workbook contains no default style")


def workbook_metrics(workbook: Path, metadata: dict[str, object]) -> list[dict[str, object]]:
    reposts = pd.read_excel(workbook, sheet_name="RepostsPerSource", engine="openpyxl").dropna(axis=1, how="all")
    fake = pd.read_excel(workbook, sheet_name="FakeNewsPerSource", engine="openpyxl").dropna(axis=1, how="all")
    unique = pd.read_excel(workbook, sheet_name="UniqueRepostersPerSource", engine="openpyxl").dropna(axis=1, how="all")
    fake.rename(columns={"Simulation": "SimulationId"}, inplace=True)
    expected = 400 * reposts.SimulationId.nunique()
    if len(reposts) != expected or len(fake) != expected or len(unique) != expected:
        raise ValueError(f"{workbook}: report row counts do not match 400 periods per run")
    for name, frame in (("reposts", reposts), ("fake", fake), ("unique", unique)):
        if frame[["SimulationId", "Period"]].duplicated().any():
            raise ValueError(f"{workbook}: duplicate {name} run-period rows")
    selection_totals = reposts[SOURCE_COLUMNS].sum(axis=1)
    reach_enabled = str(metadata.get("source_reach", "0")) in {"1", "1.0", "True", "true"}
    if reach_enabled:
        if not selection_totals.between(0, 400).all():
            raise ValueError(f"{workbook}: repost counts fall outside the 0..400 agent population")
    elif not (selection_totals == 400).all():
        raise ValueError(f"{workbook}: repost counts do not sum to 400 while source reach is disabled")
    if not set(np.unique(fake[SOURCE_COLUMNS].values)).issubset({0, 1}):
        raise ValueError(f"{workbook}: fake-news states are not binary")

    merged = reposts.merge(fake, on=["SimulationId", "Period"], suffixes=("_reposts", "_fake"), validate="one_to_one")
    merged["target_share"] = merged[f"{TARGET}_reposts"] / 400.0
    merged["fake_repost_share"] = sum(
        merged[f"{source}_reposts"] * merged[f"{source}_fake"] for source in SOURCE_COLUMNS
    ) / 400.0
    rows = []
    for simulation_id, run in merged.groupby("SimulationId"):
        final = run[run.Period >= 301]
        final_unique = unique[(unique.SimulationId == simulation_id) & (unique.Period == 400)]
        rows.append({
            **metadata,
            "simulation_id": int(simulation_id),
            "target_share_final100": final.target_share.mean(),
            "fake_repost_share_final100": final.fake_repost_share.mean(),
            "target_fake_rate_final100": final[f"{TARGET}_fake"].mean(),
            "target_unique_reposters_p400": float(final_unique[TARGET].iloc[0]),
        })
    return rows


def summarize(runs: pd.DataFrame) -> pd.DataFrame:
    metrics = [c for c in runs if c.endswith("final100") or c.endswith("p400")]
    keys = ["question", "condition", "memory", "wom", "timing", "scope", "contacts", "friends", "source_reach"]
    records = []
    for group_keys, group in runs.groupby(keys, dropna=False, sort=False):
        record = dict(zip(keys, group_keys))
        record["n"] = len(group)
        for metric in metrics:
            values = group[metric].astype(float)
            record[f"{metric}_mean"] = values.mean()
            record[f"{metric}_sd"] = values.std(ddof=1)
            record[f"{metric}_se"] = values.std(ddof=1) / math.sqrt(len(values)) if len(values) > 1 else math.nan
        records.append(record)
    return pd.DataFrame(records)


def q1_table(summary: pd.DataFrame) -> pd.DataFrame:
    return summary[summary.question == "Q1"].sort_values(["memory", "wom", "scope"])


def q2_effects(summary: pd.DataFrame) -> pd.DataFrame:
    data = summary[summary.question == "Q2"].copy()
    rows = []
    for memory, group in data.groupby("memory"):
        control = group[group.timing == "none"].iloc[0]
        for _, row in group[group.timing != "none"].iterrows():
            target_se = math.sqrt(row.target_share_final100_se ** 2 + control.target_share_final100_se ** 2)
            fake_se = math.sqrt(row.fake_repost_share_final100_se ** 2 + control.fake_repost_share_final100_se ** 2)
            rows.append({
                "memory": memory,
                "timing": row.timing,
                "timing_period": int(row.timing),
                "target_share_mean": row.target_share_final100_mean,
                "target_share_effect_vs_control": row.target_share_final100_mean - control.target_share_final100_mean,
                "target_share_effect_se": target_se,
                "target_share_effect_ci95_low": row.target_share_final100_mean - control.target_share_final100_mean - 1.96 * target_se,
                "target_share_effect_ci95_high": row.target_share_final100_mean - control.target_share_final100_mean + 1.96 * target_se,
                "fake_repost_share_mean": row.fake_repost_share_final100_mean,
                "fake_repost_effect_vs_control": row.fake_repost_share_final100_mean - control.fake_repost_share_final100_mean,
                "fake_repost_effect_se": fake_se,
            })
    return pd.DataFrame(rows).sort_values(["memory", "timing_period"])


def q3_effects(summary: pd.DataFrame) -> pd.DataFrame:
    data = summary[summary.question == "Q3"].copy()
    rows = []
    for (contacts, friends, reach), group in data.groupby(["contacts", "friends", "source_reach"]):
        on = group[group.wom == 1].iloc[0]
        off = group[group.wom == 0].iloc[0]
        target_se = math.sqrt(on.target_share_final100_se ** 2 + off.target_share_final100_se ** 2)
        fake_se = math.sqrt(on.fake_repost_share_final100_se ** 2 + off.fake_repost_share_final100_se ** 2)
        rows.append({
            "contacts": contacts,
            "friends": friends,
            "realized_degree": int(float(contacts) * float(friends)),
            "source_reach": reach,
            "target_share_wom_off": off.target_share_final100_mean,
            "target_share_wom_on": on.target_share_final100_mean,
            "target_share_wom_effect": on.target_share_final100_mean - off.target_share_final100_mean,
            "target_share_wom_effect_se": target_se,
            "target_share_wom_effect_ci95_low": on.target_share_final100_mean - off.target_share_final100_mean - 1.96 * target_se,
            "target_share_wom_effect_ci95_high": on.target_share_final100_mean - off.target_share_final100_mean + 1.96 * target_se,
            "fake_repost_wom_effect": on.fake_repost_share_final100_mean - off.fake_repost_share_final100_mean,
            "fake_repost_wom_effect_se": fake_se,
        })
    return pd.DataFrame(rows).sort_values(["source_reach", "realized_degree"])


def prior_factorial(root: Path) -> pd.DataFrame:
    candidates: dict[tuple[int, int, str], tuple[float, Path]] = {}
    for workbook in root.glob("output/FAKENEWS_BASELINE_2_*/*.xlsx"):
        if workbook.name.startswith("~$"):
            continue
        try:
            configuration = pd.read_excel(workbook, sheet_name="Configuration", header=None, engine="openpyxl")
            conf = dict(zip(configuration.iloc[:, 0].astype(str), configuration.iloc[:, 1]))
            if int(conf.get("PERIODS", 0)) != 400 or int(conf.get("AGENTS", 0)) != 400:
                continue
            memory, wom = int(conf["MEMORY"]), int(conf["WOM"])
            if memory not in {-1, 25} or wom not in {0, 1}:
                continue
            if int(conf["SCENARIO"]) == -1:
                timing = "none"
            else:
                scenario = pd.read_excel(workbook, sheet_name="Scenario", header=None, engine="openpyxl")
                timing = str(int(scenario.iloc[0, 2]))
            if timing not in {"none", "1", "25", "50", "100"}:
                continue
            key = (memory, wom, timing)
            candidates[key] = max(candidates.get(key, (0.0, workbook)), (workbook.stat().st_mtime, workbook))
        except Exception:
            continue
    if len(candidates) != 20:
        return pd.DataFrame()
    rows = []
    for (memory, wom, timing), (_, workbook) in candidates.items():
        metadata = {"memory": memory, "wom": wom, "timing": timing}
        rows.extend(workbook_metrics(workbook, metadata))
    return pd.DataFrame(rows)


def q5_analysis(runs: pd.DataFrame, prior: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    seeded = runs[runs.question == "Q5"].copy()
    condition_means = seeded.groupby(["memory", "wom", "timing"], as_index=False)[
        ["target_share_final100", "fake_repost_share_final100"]
    ].mean()
    rank_rows = []
    if not prior.empty:
        prior_means = prior.groupby(["memory", "wom", "timing"], as_index=False)[
            ["target_share_final100", "fake_repost_share_final100"]
        ].mean()
        joined = condition_means.merge(prior_means, on=["memory", "wom", "timing"], suffixes=("_seeded", "_prior"))
        for metric in ("target_share_final100", "fake_repost_share_final100"):
            rho, pvalue = stats.spearmanr(joined[f"{metric}_seeded"], joined[f"{metric}_prior"])
            rank_rows.append({"metric": metric, "spearman_rho": rho, "p_value": pvalue, "conditions": len(joined)})
    rank = pd.DataFrame(rank_rows)

    precision_rows = []
    for (memory, timing), group in seeded.groupby(["memory", "timing"]):
        paired = group.pivot(index="seed", columns="wom", values="target_share_final100").dropna()
        differences = paired[1] - paired[0]
        paired_se = differences.std(ddof=1) / math.sqrt(len(differences))
        if prior.empty:
            unpaired_se = math.nan
        else:
            reference = prior[(prior.memory == memory) & (prior.timing.astype(str) == str(timing))]
            on, off = reference[reference.wom == 1].target_share_final100, reference[reference.wom == 0].target_share_final100
            unpaired_se = math.sqrt(on.var(ddof=1) / len(on) + off.var(ddof=1) / len(off))
        precision_rows.append({
            "memory": memory,
            "timing": timing,
            "paired_wom_effect": differences.mean(),
            "paired_se": paired_se,
            "paired_ci95_low": differences.mean() - 1.96 * paired_se,
            "paired_ci95_high": differences.mean() + 1.96 * paired_se,
            "positive_pairs": int((differences > 0).sum()),
            "negative_pairs": int((differences < 0).sum()),
            "prior_unpaired_se": unpaired_se,
            "se_ratio_paired_to_unpaired": paired_se / unpaired_se if unpaired_se else math.nan,
        })
    return rank, pd.DataFrame(precision_rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("experiment_root", type=Path)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    manifest = pd.read_csv(args.experiment_root / "manifest.tsv", sep="\t", dtype={"timing": str, "seed": str})
    rows = []
    for record in manifest.to_dict("records"):
        metadata = {key: record[key] for key in manifest.columns if key != "workbook"}
        rows.extend(workbook_metrics(Path(record["workbook"]), metadata))
    runs = pd.DataFrame(rows)
    output = args.experiment_root / "analysis"
    output.mkdir(exist_ok=True)
    summary = summarize(runs)
    prior = prior_factorial(args.project_root)
    q5_rank, q5_precision = q5_analysis(runs, prior)
    runs.to_csv(output / "run_metrics.csv", index=False)
    summary.to_csv(output / "condition_summary.csv", index=False)
    q1_table(summary).to_csv(output / "q1_copy_scope.csv", index=False)
    q2_effects(summary).to_csv(output / "q2_memory_timing_effects.csv", index=False)
    q3_effects(summary).to_csv(output / "q3_wom_network_effects.csv", index=False)
    q5_rank.to_csv(output / "q5_rank_reproduction.csv", index=False)
    q5_precision.to_csv(output / "q5_paired_precision.csv", index=False)
    print(f"Validated {len(manifest)} workbooks and {len(runs)} simulation runs")
    print(f"Prior factorial reference: {len(prior)} runs across {prior.groupby(['memory','wom','timing']).ngroups if not prior.empty else 0} cells")


if __name__ == "__main__":
    main()
