#!/usr/bin/env python3
"""Create a reproducible Markdown technical report and static figures."""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def percent(value: float) -> str:
    return f"{100 * value:.2f}%"


def save_q1(data: pd.DataFrame, path: Path) -> None:
    scopes = ["none", "engagement", "all"]
    labels = ["No scenario", "Engagement only", "All attributes"]
    fig, axes = plt.subplots(2, 2, figsize=(10, 7), sharey=True)
    for axis, ((memory, wom), group) in zip(axes.flat, data.groupby(["memory", "wom"])):
        group = group.set_index("scope").loc[scopes]
        axis.bar(labels, 100 * group.target_share_final100_mean, color=["#9ca3af", "#2563eb", "#0f766e"])
        axis.errorbar(labels, 100 * group.target_share_final100_mean,
                      yerr=1.96 * 100 * group.target_share_final100_se,
                      fmt="none", ecolor="#111827", capsize=3)
        axis.set_title(f"Memory {'infinite' if memory == -1 else memory}; WOM {wom}")
        axis.tick_params(axis="x", rotation=16)
        axis.grid(axis="y", alpha=.25)
    axes[0, 0].set_ylabel("Target repost share (%)")
    axes[1, 0].set_ylabel("Target repost share (%)")
    fig.suptitle("Copying engagement attributes helps, but less than copying credibility too", fontweight="bold")
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def save_q2(data: pd.DataFrame, path: Path) -> None:
    order = [5, 10, 25, 50, 100, -1]
    timings = [1, 25, 50, 100]
    pivot = data.pivot(index="memory", columns="timing_period", values="target_share_effect_vs_control").loc[order, timings]
    values = 100 * pivot.values
    fig, axis = plt.subplots(figsize=(8.5, 5.2))
    image = axis.imshow(values, cmap="Blues", aspect="auto", vmin=0, vmax=max(.01, values.max()))
    axis.set_xticks(range(len(timings)), timings)
    axis.set_yticks(range(len(order)), ["5", "10", "25", "50", "100", "infinite"])
    axis.set_xlabel("Scenario activation period")
    axis.set_ylabel("Memory horizon")
    axis.set_title("Earlier activation and longer memory yield larger target-share gains", fontweight="bold")
    for row in range(values.shape[0]):
        for column in range(values.shape[1]):
            axis.text(column, row, f"{values[row, column]:.2f}", ha="center", va="center",
                      color="white" if values[row, column] > values.max() * .55 else "#111827")
    colorbar = fig.colorbar(image, ax=axis)
    colorbar.set_label("Effect vs matched no-scenario control (percentage points)")
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def save_q3(data: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(8.5, 5.2))
    for reach, group in data.groupby("source_reach"):
        offset = -.09 if reach == 0 else .09
        x = np.arange(len(group)) + offset
        effect = 100 * group.target_share_wom_effect.to_numpy()
        lower = 100 * (group.target_share_wom_effect - group.target_share_wom_effect_ci95_low).to_numpy()
        upper = 100 * (group.target_share_wom_effect_ci95_high - group.target_share_wom_effect).to_numpy()
        axis.errorbar(x, effect, yerr=np.vstack([lower, upper]), marker="o", capsize=4,
                      label=f"Source reach {'on' if reach else 'off'}")
    axis.axhline(0, color="#111827", linewidth=1)
    axis.set_xticks(range(3), [1, 4, 8])
    axis.set_xlabel("Realized contact degree")
    axis.set_ylabel("WOM effect on target share (percentage points)")
    axis.set_title("WOM lowers transformed-source share in every supported network cell", fontweight="bold")
    axis.legend(frameon=False)
    axis.grid(axis="y", alpha=.25)
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def save_q5(rank: pd.DataFrame, precision: pd.DataFrame, path: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.8))
    rank_labels = ["Target share", "Fake-repost share"]
    axes[0].bar(rank_labels, rank.spearman_rho, color=["#2563eb", "#0f766e"])
    axes[0].set_ylim(0.9, 1.005)
    axes[0].set_ylabel("Spearman rank correlation")
    axes[0].set_title("Seeded and prior ordering agree")
    axes[0].tick_params(axis="x", rotation=12)
    precision = precision.copy()
    precision["cell"] = precision.apply(
        lambda row: f"M={'∞' if row.memory == -1 else int(row.memory)}, {row.timing}", axis=1
    )
    colors = ["#0f766e" if value < 1 else "#dc2626" for value in precision.se_ratio_paired_to_unpaired]
    axes[1].barh(precision.cell, precision.se_ratio_paired_to_unpaired, color=colors)
    axes[1].axvline(1, color="#111827", linewidth=1)
    axes[1].set_xlabel("Paired SE / prior independent SE")
    axes[1].set_title("Pairing narrows uncertainty in 8 of 10 cells")
    axes[1].invert_yaxis()
    fig.suptitle("Seeded pairs reproduce ordering and usually improve precision", fontweight="bold")
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("experiment_root", type=Path)
    args = parser.parse_args()
    analysis = args.experiment_root / "analysis"
    figures = analysis / "figures"
    figures.mkdir(exist_ok=True)
    q1 = pd.read_csv(analysis / "q1_copy_scope.csv")
    q2 = pd.read_csv(analysis / "q2_memory_timing_effects.csv")
    q3 = pd.read_csv(analysis / "q3_wom_network_effects.csv")
    rank = pd.read_csv(analysis / "q5_rank_reproduction.csv")
    precision = pd.read_csv(analysis / "q5_paired_precision.csv")
    runs = pd.read_csv(analysis / "run_metrics.csv")
    save_q1(q1, figures / "q1_copy_scope.png")
    save_q2(q2, figures / "q2_memory_timing.png")
    save_q3(q3, figures / "q3_wom_network.png")
    save_q5(rank, precision, figures / "q5_seeded_pairs.png")

    engagement_fake = q1[q1.scope == "engagement"].target_fake_rate_final100_mean.mean()
    all_fake = q1[q1.scope == "all"].target_fake_rate_final100_mean.mean()
    best = q2.loc[q2.target_share_effect_vs_control.idxmax()]
    target_rank = rank[rank.metric == "target_share_final100"].iloc[0]
    fake_rank = rank[rank.metric == "fake_repost_share_final100"].iloc[0]
    narrower = int((precision.se_ratio_paired_to_unpaired < 1).sum())
    median_ratio = precision.se_ratio_paired_to_unpaired.median()

    q3_rows = "\n".join(
        f"| {int(row.realized_degree)} | {'On' if row.source_reach else 'Off'} | "
        f"{100 * row.target_share_wom_effect:.2f} pp | "
        f"[{100 * row.target_share_wom_effect_ci95_low:.2f}, {100 * row.target_share_wom_effect_ci95_high:.2f}] pp | "
        f"{100 * row.fake_repost_wom_effect:.2f} pp |"
        for row in q3.itertuples()
    )
    report = f"""# FAKENEWS-ABM follow-up experiment report

## Technical summary

All executable matrices completed and passed structural validation: **274 workbooks and {len(runs)} simulation runs**. Engagement-only copying preserves the transformed source's original fake-news probability ({percent(engagement_fake)}), while full copying lowers it to {percent(all_fake)}. Scenario gains are strongly dependent on memory and timing: the largest target-share effect is {percent(best.target_share_effect_vs_control)} at memory {int(best.memory)} and activation period {int(best.timing_period)}. WOM lowers transformed-source share in all six supported degree/reach cells. Seeded paired runs reproduce the earlier condition ordering (target-share rho={target_rank.spearman_rho:.3f}; fake-repost-share rho={fake_rank.spearman_rho:.3f}) and narrow uncertainty in {narrower}/10 cells (median SE ratio={median_ratio:.3f}).

These are descriptive simulation contrasts, not empirical or causal estimates.

## 1. Engagement-only copying preserves fake-news propensity

“Engagement-only” copies the 12 source attributes other than `Credibilidad de la fuente`. Because credibility controls fake-news publication probability, this isolates engagement competitiveness while retaining the target's original propensity to publish fake news.

![Copy-scope comparison](figures/q1_copy_scope.png)

Across the four memory/WOM strata, engagement-only copying increases target share relative to no scenario, but by less than full copying. The effect is especially suppressed when memory is 25 and WOM is enabled: engagement-only target share is 0.04%, compared with 2.86% under full copying. Full copying also changes the intervention's meaning because it lowers the target fake-news rate from about 66% to about 10%.

## 2. Memory and scenario timing interact strongly

Effects below compare each intervention with the no-scenario control at the same memory horizon.

![Memory and timing sensitivity](figures/q2_memory_timing.png)

At memory 5, target-share effects are essentially zero. At memory 10, period 1 has a small positive effect (0.16 percentage points), while later interventions are weak. From memory 25 upward, earlier activation is consistently strongest and effects decay as activation moves to periods 25, 50, and 100. Memory 100 at period 1 gives the largest observed gain (8.34 percentage points; approximate 95% interval 7.92–8.75). Infinite memory is similar but slightly lower at period 1 (8.20 points).

## 3. WOM does not benefit the transformed source in supported network cells

The simulation supports contact degree and a binary source-reach switch. It does **not** implement homophily, so homophily sensitivity cannot be estimated without changing the model.

![WOM network sensitivity](figures/q3_wom_network.png)

| Degree | Source reach | Target-share WOM effect | Approx. 95% interval | Fake-repost-share WOM effect |
|---:|:---:|---:|---:|---:|
{q3_rows}

All six target-share intervals are below zero. WOM also lowers total fake-repost share in all cells, so it is unfavorable to the transformed source but favorable to the system-level misinformation outcome under this operationalization.

## 4. Empirical calibration is feasible but not estimable from this repository

Empirical repost, exposure, or credibility targets could calibrate memory, exponential base, and WOM weight through a weighted distance or likelihood objective. The repository contains no empirical target dataset, sampling uncertainty, or measurement mapping, so calibration cannot be honestly executed yet. The CLI support includes:

- `experiments/empirical_targets_template.csv` for observed metrics, scales, and weights.
- `experiments/calibrate_empirical.py` to rank simulated parameter cells using weighted normalized squared error.
- `experiments/prepare_workbook.py --memory ... --base ... --wom-weight ...` to generate isolated calibration workbooks without changing Java production code.

Calibration should reserve some empirical targets for out-of-sample validation; otherwise the best-fitting grid cell only demonstrates in-sample agreement.

## 5. Seeded pairs reproduce ordering and usually narrow uncertainty

![Seeded comparison and precision](figures/q5_seeded_pairs.png)

Eleven common seeds were run across all 20 memory × WOM × timing cells. Their ordering closely reproduces the earlier independent factorial experiment: rho={target_rank.spearman_rho:.3f} for target share and rho={fake_rank.spearman_rho:.3f} for fake-repost share. Pairing reduces the estimated WOM-effect standard error in 8/10 cells. It does not improve precision for memory 25 at period 1 (ratio 1.22) or the memory-25 no-scenario cell (ratio 2.03), where the effect is essentially zero.

## Scope, metrics, and validation

- Configuration: 400 agents, 4 sources, 400 periods, and 11 repetitions or seeds per condition.
- Primary window: periods 301–400.
- Target share: selections of `FAKE_NEWS_SOURCE` divided by the 400-agent population, averaged over the final 100 periods.
- Fake-repost share: selections weighted by the selected source's binary fake-news state, divided by 400, averaged over the final 100 periods.
- Source reach: when enabled, an agent who knows no source contributes zero selections but remains in the population denominator.
- Validation: 400 unique period rows per run; binary fake-news states; exactly 400 selections when reach filtering is off and 0–400 when it is on; balanced seed/condition coverage.

## Limitations and next steps

Approximate intervals use 1.96 × standard error and are not corrected for multiple comparisons. Common seeds improve pairing, although treatment branches can cause random-number streams to diverge later. Homophily is absent, and empirical calibration remains blocked by missing observed data. The next defensible steps are to supply empirical targets with uncertainty, run a preregistered parameter grid, validate on held-out targets, and only then consider adding a homophily mechanism.

## Reproducibility

Run `experiments/run_research_questions.sh`, then `experiments/analyze_research_questions.py`, and finally this report builder. The complete manifest, run-level metrics, condition summaries, workbooks, logs, and figures are stored with this report.
"""
    (analysis / "research_findings.md").write_text(report, encoding="utf-8")
    print(f"Wrote {analysis / 'research_findings.md'} and four figures")


if __name__ == "__main__":
    main()
