#!/usr/bin/env python3
"""Analyse the major-revision experiments and publish compact, reproducible data.

Raw simulation workbooks remain local.  The outputs of this script contain one
row per run or statistical contrast and are suitable for version control.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ProcessPoolExecutor
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import subprocess
from datetime import datetime

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats

import analyze_final_study as core


EXPECTED_RUNS = {"RQ3": 1860, "RQ4": 2820}
EXPECTED_CONDITIONS = {"RQ3": 62, "RQ4": 282}
STRATEGIES = ("control", "credibility", "informational", "combined")
METRICS = core.METRICS
PARAMETERS = {
    "memory_half_life": (0.33, 5.0),
    "contacts": (3.0, 10.0),
    "target_reach": (1.0, 14.7),
    "wom_receiver_scale": (0.25, 1.0),
    "base": (1.10, 1.40),
    "traditional_fake_probability": (0.05, 0.15),
    "unknown_fake_probability": (0.15, 0.30),
    "target_fake_probability": (0.55, 0.80),
    "mixed_fake_probability": (0.30, 0.55),
    "source_attribute_contrast": (0.75, 1.25),
}


def normalized_parameter(name: str, value: float) -> float:
    """Map a realised Morris input back to its unit-cube coordinate.

    Memory half-life is deliberately sampled log-uniformly because its
    plausible range spans qualitatively different decay regimes.  Computing
    its elementary effect on a linear coordinate would therefore change the
    intended Morris step size and distort comparisons with the other factors.
    """
    low, high = PARAMETERS[name]
    if name == "memory_half_life":
        return (math.log(value) - math.log(low)) / (math.log(high) - math.log(low))
    return (value - low) / (high - low)


def strategy_of(condition: str) -> str:
    for strategy in STRATEGIES:
        if re.search(rf"(?:^|_){strategy}(?:_|$)", condition):
            return strategy
    raise ValueError(f"Cannot identify strategy in condition {condition}")


def paired_effect(treatment: pd.DataFrame, control: pd.DataFrame, metric: str,
                  metadata: dict[str, object]) -> dict[str, object]:
    result = dict(metadata)
    result["metric"] = metric
    result.update(core.paired_stats(treatment, control, metric))
    result["control_mean"] = float(control[metric].mean())
    result["treatment_mean"] = float(treatment[metric].mean())
    result["relative_effect_percent"] = (
        result["effect"] / result["control_mean"] * 100
        if result["control_mean"] != 0 else math.nan
    )
    return result


def policy_of(condition: str) -> str:
    if condition.startswith("scale"):
        return condition.split("_control_")[0].split("_credibility_")[0].split("_informational_")[0].split("_combined_")[0]
    for policy in ("imperfect_delayed", "imperfect_fast", "flag_only", "discovery", "disabled", "oracle"):
        if condition.startswith(policy + "_"):
            return policy
    raise ValueError(f"Cannot identify recommendation policy in {condition}")


def recommendation_effects(new: pd.DataFrame, old: pd.DataFrame) -> pd.DataFrame:
    rows: list[dict[str, object]] = []
    q3 = new[new.research_question == "RQ3"]
    for condition, treatment in q3.groupby("condition", sort=False):
        strategy = strategy_of(condition)
        if strategy == "control":
            continue
        policy = policy_of(condition)
        if policy in ("disabled", "oracle"):
            reach = float(treatment.target_reach.iloc[0])
            wom = 0.0 if policy == "disabled" else 1.0
            control = old[(old.research_question == "RQ3") & old.condition.str.startswith("control_") &
                          np.isclose(old.target_reach, reach) & np.isclose(old.wom, wom)]
        else:
            control_name = re.sub(rf"_{strategy}_", "_control_", condition, count=1)
            control = q3[q3.condition == control_name]
        if control.empty:
            raise ValueError(f"No paired control for {condition}")
        metadata = {
            "condition": condition, "policy": policy, "strategy": strategy,
            "target_reach": float(treatment.target_reach.iloc[0]),
            "wom_receiver_scale": float(treatment.wom_receiver_scale.iloc[0]),
        }
        for metric in METRICS:
            rows.append(paired_effect(treatment, control, metric, metadata))

    # Reuse the original seeds for the legacy credibility/informational cells.
    legacy = old[(old.research_question == "RQ3") & ~old.condition.str.startswith("control_")]
    for condition, treatment in legacy.groupby("condition", sort=False):
        strategy = strategy_of(condition)
        reach = float(treatment.target_reach.iloc[0])
        wom = float(treatment.wom.iloc[0])
        if reach not in (14.7, 5.0, 1.0):
            continue
        control = old[(old.research_question == "RQ3") & old.condition.str.startswith("control_") &
                      np.isclose(old.target_reach, reach) & np.isclose(old.wom, wom)]
        metadata = {
            "condition": condition, "policy": "disabled" if wom == 0 else "oracle",
            "strategy": strategy, "target_reach": reach, "wom_receiver_scale": 0.5,
        }
        for metric in METRICS:
            rows.append(paired_effect(treatment, control, metric, metadata))
    return holm(pd.DataFrame(rows), ["metric"])


def global_strategy_effects(runs: pd.DataFrame) -> pd.DataFrame:
    rows: list[dict[str, object]] = []
    data = runs[runs.experiment == "E4b-global-sensitivity"]
    for condition, treatment in data.groupby("condition", sort=False):
        strategy = strategy_of(condition)
        if strategy == "control":
            continue
        point = int(re.match(r"m(\d+)_", condition).group(1))
        control = data[data.condition == f"m{point:02d}_control"]
        metadata = {"condition": condition, "point": point, "strategy": strategy}
        metadata.update({name: treatment[name].iloc[0] for name in PARAMETERS})
        for metric in METRICS:
            rows.append(paired_effect(treatment, control, metric, metadata))
    return holm(pd.DataFrame(rows), ["strategy", "metric"])


def structural_effects(runs: pd.DataFrame) -> pd.DataFrame:
    rows: list[dict[str, object]] = []
    data = runs[runs.experiment == "E4c-structural-sensitivity"]
    for condition, treatment in data.groupby("condition", sort=False):
        strategy = strategy_of(condition)
        if strategy == "control":
            continue
        topology = int(treatment.network_topology.iloc[0])
        activity = float(treatment.user_activity.iloc[0])
        control = data[(data.network_topology == topology) & np.isclose(data.user_activity, activity) &
                       data.condition.str.endswith("_control")]
        metadata = {"condition": condition, "strategy": strategy,
                    "network_topology": topology, "user_activity": activity}
        for metric in METRICS:
            rows.append(paired_effect(treatment, control, metric, metadata))
    return holm(pd.DataFrame(rows), ["strategy", "metric"])


def holm(frame: pd.DataFrame, groups: list[str]) -> pd.DataFrame:
    frame["p_holm"] = np.nan
    for _, index in frame.groupby(groups, dropna=False).groups.items():
        ordered = frame.loc[index, "p_value"].sort_values()
        adjusted = np.maximum.accumulate(ordered.to_numpy() * (len(ordered) - np.arange(len(ordered))))
        frame.loc[ordered.index, "p_holm"] = np.minimum(adjusted, 1.0)
    return frame


def morris_elementary_effects(runs: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    data = runs[runs.experiment == "E4b-global-sensitivity"].copy()
    impacts = []
    for strategy in ("credibility", "combined"):
        treatment = data[data.condition.str.endswith("_" + strategy)]
        control = data[data.condition.str.endswith("_control")]
        for point in range(1, 89):
            t = treatment[treatment.condition == f"m{point:02d}_{strategy}"]
            c = control[control.condition == f"m{point:02d}_control"]
            merged = t[["seed"] + METRICS].merge(c[["seed"] + METRICS], on="seed", suffixes=("_t", "_c"))
            meta = t.iloc[0]
            for _, row in merged.iterrows():
                impacts.append({"strategy": strategy, "point": point, "seed": row.seed,
                                **{metric: row[f"{metric}_t"] - row[f"{metric}_c"] for metric in METRICS},
                                **{name: meta[name] for name in PARAMETERS}})
    impacts = pd.DataFrame(impacts)
    effects = []
    for strategy in ("credibility", "combined"):
        part = impacts[impacts.strategy == strategy]
        for trajectory in range(8):
            first = trajectory * 11 + 1
            for point in range(first, first + 10):
                left = part[part.point == point]
                right = part[part.point == point + 1]
                normalized_changes = {}
                for name in PARAMETERS:
                    normalized_changes[name] = (
                        normalized_parameter(name, float(right[name].iloc[0]))
                        - normalized_parameter(name, float(left[name].iloc[0]))
                    )
                factor = max(normalized_changes, key=lambda name: abs(normalized_changes[name]))
                delta = normalized_changes[factor]
                if abs(delta) < 1e-12:
                    raise ValueError(f"No Morris factor changed from point {point} to {point + 1}")
                paired = left.merge(right, on=["strategy", "seed"], suffixes=("_left", "_right"))
                for metric in METRICS:
                    for _, row in paired.iterrows():
                        effects.append({"strategy": strategy, "trajectory": trajectory + 1,
                                        "step": point - first + 1, "seed": row.seed,
                                        "factor": factor, "metric": metric,
                                        "elementary_effect": (row[f"{metric}_right"] - row[f"{metric}_left"]) / delta})
    effects = pd.DataFrame(effects)
    summary = effects.groupby(["strategy", "factor", "metric"], as_index=False).elementary_effect.agg(
        n="count", mu="mean", mu_star=lambda x: np.mean(np.abs(x)), sigma="std")
    return effects, summary


def morris_stability(effects: pd.DataFrame, resamples: int = 10_000,
                     seed: int = 20260819) -> pd.DataFrame:
    """Cluster-bootstrap Morris ranks by trajectory and report leave-one-trajectory-out ranges."""
    rng = np.random.default_rng(seed)
    rows: list[dict[str, object]] = []
    for (strategy, metric), group in effects.groupby(["strategy", "metric"], sort=False):
        trajectories = np.array(sorted(group.trajectory.unique()))
        factors = sorted(group.factor.unique())
        trajectory_means = (group.assign(abs_effect=group.elementary_effect.abs())
                            .groupby(["factor", "trajectory"]).abs_effect.mean())
        matrix = trajectory_means.unstack().loc[factors, trajectories].to_numpy()
        sampled = rng.choice(len(trajectories), size=(resamples, len(trajectories)), replace=True)
        bootstrap_values = matrix[:, sampled].mean(axis=2)
        bootstrap_ranks = 1 + (bootstrap_values[:, None, :] < bootstrap_values[None, :, :]).sum(axis=1)
        loo_ranks = []
        for omitted in range(len(trajectories)):
            values = np.delete(matrix, omitted, axis=1).mean(axis=1)
            loo_ranks.append(1 + (values[:, None] < values[None, :]).sum(axis=1))
        loo_ranks = np.asarray(loo_ranks).T
        for factor_index, factor in enumerate(factors):
            ranks = bootstrap_ranks[factor_index]
            rows.append({
                "strategy": strategy, "metric": metric, "factor": factor,
                "bootstrap_resamples": resamples,
                "rank1_probability": float(np.mean(ranks == 1)),
                "median_rank": float(np.median(ranks)),
                "rank_95_low": float(np.quantile(ranks, 0.025)),
                "rank_95_high": float(np.quantile(ranks, 0.975)),
                "loo_rank_min": int(loo_ranks[factor_index].min()),
                "loo_rank_max": int(loo_ranks[factor_index].max()),
            })
    return pd.DataFrame(rows)


def condition_summary(runs: pd.DataFrame) -> pd.DataFrame:
    keys = ["research_question", "experiment", "condition"]
    metadata = ["strategy", "target_reach", "wom", "wom_receiver_scale", "wom_label_delay",
                "wom_label_coverage", "wom_label_sensitivity", "wom_label_specificity", "contacts",
                "user_activity", "network_topology", "memory_half_life", "base", "source_attribute_contrast"]
    rows = []
    for values, group in runs.groupby(keys, sort=False):
        row = dict(zip(keys, values)); row["n"] = len(group)
        row.update({name: group[name].iloc[0] for name in metadata})
        for metric in METRICS:
            row[f"{metric}_mean"] = group[metric].mean()
            row[f"{metric}_sd"] = group[metric].std(ddof=1)
        rows.append(row)
    return pd.DataFrame(rows)


def rq2_source_window_effects(periods: pd.DataFrame, runs: pd.DataFrame) -> pd.DataFrame:
    """Decompose RQ2 treatment-control changes into source-specific matched windows."""
    source_columns = [f"selections_{source.lower()}" for source in core.SOURCES]
    controls = periods[(periods.research_question == "RQ2") & (periods.condition == "control")]
    rows: list[dict[str, object]] = []
    treatments = runs[(runs.research_question == "RQ2") & (runs.condition != "control")]
    for condition, group in treatments.groupby("condition", sort=False):
        first = group.iloc[0]
        start, raw_end = int(first.start_period), int(first.end_period)
        end = core.PERIODS if raw_end < 0 else raw_end
        windows = [("during", start, end)]
        if raw_end >= 0:
            windows += [("post_1_25", end + 1, min(end + 25, core.PERIODS)),
                        ("post_26_50", end + 26, min(end + 50, core.PERIODS)),
                        ("post_51_100", end + 51, min(end + 100, core.PERIODS))]
        treatment_periods = periods[(periods.research_question == "RQ2") & (periods.condition == condition)]
        for window, window_start, window_end in windows:
            if window_start > core.PERIODS or window_start > window_end:
                continue
            treatment_window = treatment_periods[treatment_periods.period.between(window_start, window_end)]
            control_window = controls[controls.period.between(window_start, window_end)]
            for source, column in zip(core.SOURCES, source_columns):
                treatment_seed = treatment_window.groupby("seed", as_index=False)[column].mean()
                control_seed = control_window.groupby("seed", as_index=False)[column].mean()
                treatment_seed[column] /= core.AGENTS
                control_seed[column] /= core.AGENTS
                metadata = {"condition": condition, "strategy": first.strategy,
                            "window": window, "window_start": window_start,
                            "window_end": window_end, "source": source}
                rows.append({**metadata, **core.paired_stats(treatment_seed, control_seed, column)})
    return pd.DataFrame(rows)


def rebuild_public_supplements(elementary: pd.DataFrame, existing_periods: pd.DataFrame,
                               existing_runs: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Recreate supporting artifacts using only publication-safe processed data."""
    stability = morris_stability(elementary)
    source_windows = rq2_source_window_effects(existing_periods, existing_runs)
    if stability.empty or source_windows.empty:
        raise ValueError("Processed inputs did not produce the promised supporting artifacts")
    return stability, source_windows


def validate(runs: pd.DataFrame) -> dict[str, object]:
    actual_runs = runs.groupby("research_question").size().to_dict()
    actual_conditions = runs.groupby("research_question").condition.nunique().to_dict()
    if actual_runs != EXPECTED_RUNS or actual_conditions != EXPECTED_CONDITIONS:
        raise ValueError(f"Unexpected design: runs={actual_runs}, conditions={actual_conditions}")
    if runs.duplicated(["research_question", "condition", "seed"]).any():
        raise ValueError("Duplicate condition/seed runs")
    if set(runs.status) != {"COMPLETE"}:
        raise ValueError("Every run must be COMPLETE")
    for metric in ("fake_repost_share_all", "target_share_all", "participation_rate",
                   "fake_repost_share_decisions", "target_share_decisions"):
        if not runs[metric].between(0, 1).all():
            raise ValueError(f"{metric} is outside [0,1]")
    return {"status": "PASS", "workbooks": len(runs), "runs_by_rq": actual_runs,
            "conditions_by_rq": actual_conditions, "paired_seeds": True,
            "global_sensitivity": "Morris elementary effects, 8 trajectories, 10 factors"}


def figures(recommendation: pd.DataFrame, structural: pd.DataFrame,
            morris: pd.DataFrame, output: Path) -> None:
    directory = output / "figures"; directory.mkdir(parents=True, exist_ok=True)
    metric = "fake_repost_share_decisions"
    policies = ["disabled", "discovery", "flag_only", "oracle", "imperfect_fast", "imperfect_delayed"]
    policy_labels = {
        "disabled": "Disabled",
        "discovery": "Discovery only",
        "flag_only": "False-only flag",
        "oracle": "Perfect immediate (oracle)",
        "imperfect_fast": "Fast imperfect",
        "imperfect_delayed": "Delayed imperfect",
    }
    plot = recommendation[(recommendation.metric == metric) & recommendation.policy.isin(policies)]
    fig, axes = plt.subplots(2, 3, figsize=(10.5, 6.6), sharex=True, sharey=True)
    colors = {"credibility": "#2b6f92", "informational": "#d07a2d", "combined": "#6a4c93"}
    for ax, policy in zip(axes.flat, policies):
        group = plot[plot.policy == policy]
        for strategy in ("credibility", "informational", "combined"):
            part = group[group.strategy == strategy].sort_values("target_reach")
            effect = part.effect.to_numpy() * 100
            low = part.ci95_low.to_numpy() * 100
            high = part.ci95_high.to_numpy() * 100
            ax.errorbar(part.target_reach, effect, yerr=[effect-low, high-effect], marker="o",
                        linewidth=1.1, capsize=2, color=colors[strategy], label=strategy.title())
        ax.axhline(0, color="black", linewidth=.7); ax.set_title(policy_labels[policy])
        ax.set_xlabel("Direct reach (%)"); ax.set_ylabel("Effect (percentage points)")
    axes[0, 2].legend(fontsize=8)
    fig.suptitle("Recommendation realism: strategy effects on false share among decisions")
    fig.tight_layout(); fig.savefig(directory / "recommendation-realism.svg")
    fig.savefig(directory / "recommendation-realism.png", dpi=300); plt.close(fig)

    fig, axes = plt.subplots(2, 2, figsize=(9.0, 7.0), sharex=True, sharey="row")
    for row_index, (row_metric, row_title) in enumerate((
            (metric, "False share among decisions"),
            ("participation_rate", "Participation rate"))):
        plot = structural[structural.metric == row_metric].copy()
        # Activity is imposed symmetrically in each matched pair.  Floating-point
        # subtraction leaves residuals around 1e-17 even though the estimand is
        # exactly zero; plotting those residuals would falsely suggest variation.
        if row_metric == "participation_rate":
            for column in ("effect", "ci95_low", "ci95_high"):
                plot.loc[plot[column].abs() < 1e-12, column] = 0.0
        for column_index, topology in enumerate((0, 1)):
            ax = axes[row_index, column_index]
            group = plot[plot.network_topology == topology]
            for strategy in ("credibility", "combined"):
                part = group[group.strategy == strategy].sort_values("user_activity")
                effect = part.effect.to_numpy() * 100
                low = part.ci95_low.to_numpy() * 100
                high = part.ci95_high.to_numpy() * 100
                ax.errorbar(part.user_activity, effect, yerr=[effect-low, high-effect], marker="o",
                            capsize=2, color=colors[strategy], label=strategy.title())
            ax.axhline(0, color="black", linewidth=.7)
            ax.set_title(("Random fixed-outdegree" if topology == 0 else "Directed small-world")
                         if row_index == 0 else row_title)
            ax.set_xlabel("User activity probability")
            ax.set_ylabel(row_title + " effect (pp)")
            if row_metric == "participation_rate":
                ax.set_ylim(-0.05, 0.05)
                ax.ticklabel_format(axis="y", style="plain", useOffset=False)
    axes[0, 1].legend(fontsize=8)
    fig.suptitle("Structural sensitivity of strategy and participation effects")
    fig.tight_layout(); fig.savefig(directory / "structural-sensitivity.svg")
    fig.savefig(directory / "structural-sensitivity.png", dpi=300); plt.close(fig)

    plot = morris[morris.metric == metric].copy()
    fig, axes = plt.subplots(1, 2, figsize=(9, 4.8), sharex=True, sharey=True)
    label_offsets = {
        ("combined", "memory_half_life"): (-5, 4),
        ("credibility", "base"): (4, 4),
        ("credibility", "wom_receiver_scale"): (4, -2),
        ("credibility", "memory_half_life"): (10, 16),
        ("credibility", "target_reach"): (10, -18),
    }
    for ax, (strategy, group) in zip(axes, plot.groupby("strategy")):
        ax.scatter(group.mu_star * 100, group.sigma * 100, color=colors[strategy], s=28)
        for _, row in group.iterrows():
            offset = label_offsets.get((strategy, row.factor), (3, 3))
            alignment = "right" if (strategy, row.factor) == ("combined", "memory_half_life") else "left"
            ax.annotate(row.factor.replace("_fake_probability", " p(false)").replace("_", " "),
                        (row.mu_star * 100, row.sigma * 100), xytext=offset,
                        textcoords="offset points", fontsize=7, ha=alignment)
        ax.set_title(strategy.title()); ax.set_xlabel(r"Overall influence $\mu^*$ (pp)")
        ax.set_ylabel(r"Non-linearity/interaction $\sigma$ (pp)")
        ax.grid(alpha=.18)
    fig.suptitle("Morris global sensitivity of false-decision-share effects")
    fig.tight_layout(); fig.savefig(directory / "global-sensitivity.svg")
    fig.savefig(directory / "global-sensitivity.png", dpi=300); plt.close(fig)


def write_checksums(output: Path) -> None:
    lines = []
    for path in sorted(p for p in output.rglob("*") if p.is_file() and p.name != "SHA256SUMS"):
        lines.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(output)}")
    (output / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_data_dictionary(output: Path) -> None:
    text = """# Major-revision processed data

`run-metrics.csv.gz` contains one row per condition/seed run. Design columns reproduce the
configuration in `manifest.tsv`. Outcome definitions are:

- `fake_repost_share_all`: false reposts per agent-period;
- `fake_repost_share_decisions`: false reposts divided by actual decisions;
- `target_share_all`: experimental-source selections per agent-period;
- `target_share_decisions`: experimental-source selections divided by actual decisions;
- `participation_rate`: actual decisions divided by possible agent-period decisions;
- `target_unique_reposters_p*`: cumulative unique experimental-source reposters at the named horizon;
- `selections_<source>_all`: total selections displaced to or from each source object.

`condition-summary.csv` contains condition means and standard deviations.
`recommendation-policy-effects.csv` contains paired RQ3 strategy effects across recommendation
policies, reach and receiver scale. `global-strategy-effects.csv` contains treatment-control effects
at each Morris point. `morris-elementary-effects.csv.gz` and `morris-summary.csv` contain seed-level
and aggregated elementary effects. `morris-stability.csv` reports trajectory-cluster bootstrap and
leave-one-trajectory-out rank stability and can be rebuilt from elementary effects.
`structural-effects.csv` contains activity/topology contrasts.
Intervals are unadjusted 95% Student-t Monte Carlo intervals; `p_holm` is a Holm-adjusted p value.
`validation.json` and `SHA256SUMS` record design checks and file integrity.
`execution-metadata.json` records the measured execution interval, concurrency,
software environment, source revision and generated raw-data size without any
device serial number or other machine identifier.
`rq2-source-window-effects.csv` decomposes matched campaign and post-removal effects by source and
can be rebuilt from original processed run and period metrics.
"""
    (output / "DATA_DICTIONARY.md").write_text(text, encoding="utf-8")


def markdown_rows(frame: pd.DataFrame, columns: list[str], labels: list[str],
                  formats: dict[str, str] | None = None) -> str:
    formats = formats or {}
    lines = ["| " + " | ".join(labels) + " |",
             "| " + " | ".join(["---"] * len(labels)) + " |"]
    for _, row in frame.iterrows():
        values = []
        for column in columns:
            value = row[column]
            values.append(formats[column].format(value) if column in formats else str(value))
        lines.append("| " + " | ".join(values) + " |")
    return "\n".join(lines)


def write_results_summary(recommendation: pd.DataFrame, structural: pd.DataFrame,
                          morris: pd.DataFrame, output: Path) -> None:
    primary_metrics = ["fake_repost_share_decisions", "target_share_decisions"]
    policy_order = ["disabled", "discovery", "flag_only", "oracle",
                    "imperfect_fast", "imperfect_delayed"]
    main = recommendation[
        recommendation.metric.isin(primary_metrics)
        & recommendation.policy.isin(policy_order)
        & np.isclose(recommendation.wom_receiver_scale, 0.5)
        & np.isclose(recommendation.target_reach, 14.7)
    ].copy()
    main["policy"] = pd.Categorical(main.policy, policy_order, ordered=True)
    main = main.sort_values(["metric", "policy", "strategy"])
    for column in ("control_mean", "treatment_mean", "effect", "ci95_low", "ci95_high"):
        main[column] *= 100

    scale = recommendation[
        recommendation.metric.isin(primary_metrics)
        & recommendation.policy.str.startswith("scale")
    ].copy().sort_values(["metric", "policy", "strategy"])
    for column in ("control_mean", "treatment_mean", "effect", "ci95_low", "ci95_high"):
        scale[column] *= 100

    top = morris[morris.metric.isin(primary_metrics)].copy()
    top["mu_star"] *= 100; top["sigma"] *= 100
    top = top.sort_values(["metric", "strategy", "mu_star"], ascending=[True, True, False])
    top = top.groupby(["metric", "strategy"], as_index=False, group_keys=False).head(5)

    structure = structural[structural.metric.isin(primary_metrics)].copy()
    structure["topology"] = structure.network_topology.map(
        {0: "random fixed-outdegree", 1: "directed small-world"})
    for column in ("control_mean", "treatment_mean", "effect", "ci95_low", "ci95_high"):
        structure[column] *= 100
    structure = structure.sort_values(["metric", "topology", "user_activity", "strategy"])

    effect_columns = ["metric", "policy", "strategy", "control_mean", "effect",
                      "ci95_low", "ci95_high", "relative_effect_percent"]
    effect_labels = ["Outcome", "Policy", "Strategy", "Control (%)", "Effect (pp)",
                     "CI low", "CI high", "Relative (%)"]
    effect_formats = {name: "{:.3f}" for name in effect_columns[3:]}
    text = ["# Major-revision result digest", "",
            "All effects are paired treatment-minus-control estimates. Confidence intervals are",
            "unadjusted 95% Monte Carlo intervals; relative effects use the matched control mean.", "",
            "## RQ3: recommendation policies at 14.7% direct reach", "",
            markdown_rows(main, effect_columns, effect_labels, effect_formats), "",
            "## RQ3: receiver-scale sensitivity under the imperfect-fast policy", "",
            markdown_rows(scale, effect_columns, effect_labels, effect_formats), "",
            "## RQ4: five highest Morris influences within each outcome and strategy", "",
            markdown_rows(top, ["metric", "strategy", "factor", "mu_star", "sigma", "n"],
                          ["Outcome", "Strategy", "Factor", "mu-star (pp)", "sigma (pp)", "n"],
                          {"mu_star": "{:.3f}", "sigma": "{:.3f}", "n": "{:.0f}"}), "",
            "## RQ4: structural sensitivity", "",
            markdown_rows(structure,
                          ["metric", "topology", "user_activity", "strategy", "control_mean",
                           "effect", "ci95_low", "ci95_high", "relative_effect_percent"],
                          ["Outcome", "Topology", "Activity", "Strategy", "Control (%)",
                           "Effect (pp)", "CI low", "CI high", "Relative (%)"],
                          {"user_activity": "{:.2f}", "control_mean": "{:.3f}",
                           "effect": "{:.3f}", "ci95_low": "{:.3f}", "ci95_high": "{:.3f}",
                           "relative_effect_percent": "{:.3f}"}), ""]
    (output / "RESULTS_SUMMARY.md").write_text("\n".join(text), encoding="utf-8")


def command_output(*command: str) -> str | None:
    try:
        return subprocess.run(command, check=True, capture_output=True, text=True).stdout.strip()
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None


def execution_metadata(root: Path, simulation_jobs: int) -> dict[str, object]:
    markers = list(root.glob("results/**/.complete"))
    if not markers:
        raise ValueError("Execution metadata requires at least one completed run")
    started = (root / "study.tsv").stat().st_mtime
    finished = max(path.stat().st_mtime for path in markers)
    preexisting = sum(path.stat().st_mtime < started for path in markers)
    raw_bytes = sum(path.stat().st_size for path in root.rglob("*") if path.is_file())
    repository = Path(__file__).resolve().parents[1]
    git_head = command_output("git", "-C", str(repository), "rev-parse", "HEAD")
    git_status = command_output("git", "-C", str(repository), "status", "--porcelain")
    java_version = command_output("java", "-version")
    # java -version normally writes to stderr, so use a shell-free fallback.
    if not java_version:
        try:
            process = subprocess.run(["java", "-version"], check=True, capture_output=True, text=True)
            java_version = (process.stderr or process.stdout).splitlines()[0]
        except (FileNotFoundError, subprocess.CalledProcessError):
            java_version = None
    return {
        "resumed_at": datetime.fromtimestamp(started).astimezone().isoformat(timespec="seconds"),
        "finished_at": datetime.fromtimestamp(finished).astimezone().isoformat(timespec="seconds"),
        "elapsed_seconds_since_resume": round(finished - started, 3),
        "simulation_jobs": simulation_jobs,
        "completed_runs": len(markers),
        "preexisting_completed_runs": preexisting,
        "completed_since_resume": len(markers) - preexisting,
        "raw_generated_bytes": raw_bytes,
        "operating_system": platform.platform(),
        "architecture": platform.machine(),
        "logical_cpu_count": os.cpu_count(),
        "java_version": java_version,
        "git_head_at_analysis": git_head,
        "git_worktree_clean_at_analysis": git_status == "",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--existing", type=Path, required=True,
                        help="Processed run-metrics.csv for the original 2,010 runs")
    parser.add_argument("--output", type=Path, default=Path("analysis/major-revision"))
    parser.add_argument("--jobs", type=int, default=6)
    parser.add_argument("--simulation-jobs", type=int, default=10,
                        help="Concurrency used to generate the simulation workbooks")
    parser.add_argument("--structural-root", type=Path,
                        help="Optional corrected 180-run E4c output root replacing the original structural cells")
    parser.add_argument("--existing-periods", type=Path,
                        help="Optional processed period-metrics file for source-level RQ2 decomposition")
    args = parser.parse_args()
    manifest = pd.read_csv(args.root / "manifest.tsv", sep="\t")
    complete = manifest[manifest.status == "COMPLETE"].copy()
    if len(complete) != sum(EXPECTED_RUNS.values()):
        raise ValueError(f"Expected 4,680 completed runs; found {len(complete)}")
    with ProcessPoolExecutor(max_workers=args.jobs) as pool:
        rows = list(pool.map(core._read_run, complete.to_dict("records"), chunksize=4))
    for row in rows:
        row.pop("_period_metrics", None)
    runs = core.public_runs(pd.DataFrame(rows))
    if args.structural_root is not None:
        structural_manifest = pd.read_csv(args.structural_root / "manifest.tsv", sep="\t")
        structural_complete = structural_manifest[structural_manifest.status == "COMPLETE"].copy()
        if len(structural_complete) != 180 or structural_complete.condition.nunique() != 18:
            raise ValueError("Corrected structural root must contain 180 complete runs across 18 conditions")
        with ProcessPoolExecutor(max_workers=args.jobs) as pool:
            structural_rows = list(pool.map(core._read_run, structural_complete.to_dict("records"), chunksize=4))
        for row in structural_rows:
            row.pop("_period_metrics", None)
        corrected = core.public_runs(pd.DataFrame(structural_rows))
        runs = pd.concat([runs[runs.experiment != "E4c-structural-sensitivity"], corrected],
                         ignore_index=True)
    audit = validate(runs)
    audit["structural_correction_runs"] = 180 if args.structural_root is not None else 0
    old = pd.read_csv(args.existing)
    summary = condition_summary(runs)
    recommendation = recommendation_effects(runs, old)
    global_effects = global_strategy_effects(runs)
    structural = structural_effects(runs)
    elementary, morris = morris_elementary_effects(runs)
    stability = morris_stability(elementary)
    rq2_sources = (rq2_source_window_effects(pd.read_csv(args.existing_periods), old)
                   if args.existing_periods is not None else pd.DataFrame())

    args.output.mkdir(parents=True, exist_ok=True)
    runs.to_csv(args.output / "run-metrics.csv.gz", index=False, compression="gzip")
    summary.to_csv(args.output / "condition-summary.csv", index=False)
    recommendation.to_csv(args.output / "recommendation-policy-effects.csv", index=False)
    global_effects.to_csv(args.output / "global-strategy-effects.csv", index=False)
    structural.to_csv(args.output / "structural-effects.csv", index=False)
    elementary.to_csv(args.output / "morris-elementary-effects.csv.gz", index=False, compression="gzip")
    morris.to_csv(args.output / "morris-summary.csv", index=False)
    stability.to_csv(args.output / "morris-stability.csv", index=False)
    if not rq2_sources.empty:
        rq2_sources.to_csv(args.output / "rq2-source-window-effects.csv", index=False)
    figures(recommendation, structural, morris, args.output)
    (args.output / "validation.json").write_text(json.dumps(audit, indent=2), encoding="utf-8")
    metadata = execution_metadata(args.root, args.simulation_jobs)
    (args.output / "execution-metadata.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    write_data_dictionary(args.output)
    write_results_summary(recommendation, structural, morris, args.output)
    write_checksums(args.output)
    print(json.dumps(audit, indent=2))


if __name__ == "__main__":
    main()
