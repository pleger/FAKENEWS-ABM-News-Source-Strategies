#!/usr/bin/env python3
"""Build reproducible figures and validated effect tables for both reports."""

from __future__ import annotations

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.stats import t


ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / "output/research_questions_20260812_180619/analysis"
BASE = ROOT / "output/dissemination_experiments_20260813_215632/analysis"
NEW = ROOT / "output/dissemination_experiments_20260814_083018/analysis"
REPORT_IMAGES = ROOT / "informe/reporte/images"
PAPER_IMAGES = ROOT / "informe/paper/images"

BLUE = "#2C5F8A"
ORANGE = "#D4872C"
INK = "#243746"
GRAY = "#8A949E"
LIGHT = "#E7EEF4"


def paired_ci(values: pd.Series) -> tuple[float, float, float, float]:
    values = values.astype(float)
    mean = float(values.mean())
    se = float(values.std(ddof=1) / np.sqrt(len(values)))
    critical = float(t.ppf(0.975, len(values) - 1))
    return mean, se, mean - critical * se, mean + critical * se


def scenario_effects(runs: pd.DataFrame) -> pd.DataFrame:
    keys = ["phase", "memory", "wom", "fake_effect", "true_effect", "contacts",
            "friends", "source_reach", "target_reach"]
    rows: list[dict[str, object]] = []
    treatments = runs[runs.timing.astype(str) != "none"]
    for cell, treatment in treatments.groupby("cell", sort=False):
        first = treatment.iloc[0]
        control = runs[runs.timing.astype(str) == "none"]
        for key in keys:
            control = control[control[key].astype(str) == str(first[key])]
        row = {key: first[key] for key in keys}
        row.update({"cell": cell, "timing": int(first.timing), "scope": first.scope})
        for metric, prefix in (("fake_repost_share_final100", "fake"),
                               ("target_share_final100", "target"),
                               ("cumulative_fake_reposts", "cumulative")):
            paired = treatment[["seed", metric]].merge(
                control[["seed", metric]], on="seed", suffixes=("_t", "_c"), validate="one_to_one"
            )
            mean, se, low, high = paired_ci(paired[f"{metric}_t"] - paired[f"{metric}_c"])
            row.update({prefix: mean, f"{prefix}_se": se, f"{prefix}_low": low,
                        f"{prefix}_high": high, "n_pairs": len(paired)})
        rows.append(row)
    return pd.DataFrame(rows)


def style_axis(axis) -> None:
    axis.grid(axis="y", color=LIGHT, linewidth=0.8)
    axis.set_axisbelow(True)
    axis.spines[["top", "right"]].set_visible(False)
    axis.spines[["left", "bottom"]].set_color("#A9B5BF")
    axis.tick_params(colors=INK, labelsize=8.5)


def save_bilingual(fig, spanish: str, english: str, spanish_setup=None) -> None:
    REPORT_IMAGES.mkdir(parents=True, exist_ok=True)
    PAPER_IMAGES.mkdir(parents=True, exist_ok=True)
    fig.savefig(PAPER_IMAGES / english, dpi=240, bbox_inches="tight", facecolor="white")
    if spanish_setup:
        spanish_setup()
    fig.savefig(REPORT_IMAGES / spanish, dpi=240, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def baseline_figure(base: pd.DataFrame) -> None:
    means = base[base.phase == "baseline"].groupby("cell").fake_repost_share_final100.mean()
    values = 100 * means.loc[["baseline_b0", "baseline_b1", "baseline_b2"]].to_numpy()
    fig, axis = plt.subplots(figsize=(7.1, 3.0))
    bars = axis.bar(["No WOM", "Discovery-only WOM", "Truth-sensitive WOM"], values,
                    color=[GRAY, BLUE, ORANGE], edgecolor=INK, linewidth=0.7)
    for bar, value in zip(bars, values):
        axis.text(bar.get_x() + bar.get_width()/2, value + 0.35, f"{value:.2f}%",
                  ha="center", va="bottom", fontsize=9, color=INK)
    axis.set_ylabel("Fake repost share, periods 301-400 (%)")
    axis.set_ylim(0, max(values) * 1.18)
    style_axis(axis)
    def spanish():
        axis.set_xticks(range(3), ["Sin WOM", "WOM solo descubrimiento", "WOM sensible a veracidad"])
        axis.set_ylabel("Reposts falsos, períodos 301-400 (%)")
    save_bilingual(fig, "01_baseline_wom.png", "01_baseline_wom.png", spanish)


def memory_figure() -> None:
    effects = pd.read_csv(OLD / "q2_memory_timing_effects.csv")
    memories = [5, 10, 25, 50, 100, -1]
    timings = [1, 25, 50, 100]
    matrix = (100 * effects.pivot(index="memory", columns="timing_period",
                                  values="target_share_effect_vs_control").loc[memories, timings]).to_numpy()
    fig, axis = plt.subplots(figsize=(7.1, 3.45))
    image = axis.imshow(matrix, cmap="Blues", vmin=0, vmax=max(8.5, float(matrix.max())), aspect="auto")
    for i in range(len(memories)):
        for j in range(len(timings)):
            axis.text(j, i, f"{matrix[i, j]:.2f}", ha="center", va="center",
                      color="white" if matrix[i, j] > 4.5 else INK, fontsize=8.5)
    axis.set_xticks(range(4), timings)
    axis.set_yticks(range(6), ["Infinite" if value == -1 else value for value in memories])
    axis.set_xlabel("Scenario activation period")
    axis.set_ylabel("Memory horizon (periods)")
    bar = fig.colorbar(image, ax=axis, fraction=0.035, pad=0.03)
    bar.set_label("Target-source selection effect (percentage points)", fontsize=8)
    fig.tight_layout()
    def spanish():
        axis.set_yticklabels(["Infinita" if value == -1 else value for value in memories])
        axis.set_xlabel("Período de activación del escenario")
        axis.set_ylabel("Horizonte de memoria (períodos)")
        bar.set_label("Efecto en selección de fuente objetivo (puntos porcentuales)", fontsize=8)
    save_bilingual(fig, "02_memoria_momento.png", "02_memory_timing.png", spanish)


def scope_figure() -> None:
    q1 = pd.read_csv(OLD / "q1_copy_scope.csv")
    q1 = q1[(q1.memory == -1) & (q1.wom == 1)].copy()
    labels = {"none": "No scenario", "engagement": "Engagement only", "all": "All attributes"}
    colors = {"none": GRAY, "engagement": ORANGE, "all": BLUE}
    fig, axis = plt.subplots(figsize=(7.1, 3.25))
    for row in q1.itertuples():
        label = labels[row.scope]
        axis.scatter(100 * row.target_share_final100_mean, 100 * row.target_fake_rate_final100_mean,
                     s=110, color=colors[row.scope], edgecolor=INK, linewidth=0.8, label=label)
        axis.annotate(label, (100 * row.target_share_final100_mean, 100 * row.target_fake_rate_final100_mean),
                      xytext=(5, 6), textcoords="offset points", fontsize=8.5)
    axis.set_xlabel("Target-source selection share, periods 301-400 (%)")
    axis.set_ylabel("Target-source fake-news publication rate (%)")
    axis.set_xlim(left=-0.3)
    axis.set_ylim(0, 75)
    style_axis(axis)
    axis.get_legend().remove() if axis.get_legend() else None
    def spanish():
        axis.set_xlabel("Selección de la fuente objetivo, períodos 301-400 (%)")
        axis.set_ylabel("Tasa de publicación falsa de la fuente objetivo (%)")
        translated = {"No scenario": "Sin escenario", "Engagement only": "Solo engagement",
                      "All attributes": "Todos los atributos"}
        for annotation in axis.texts:
            if annotation.get_text() in translated:
                annotation.set_text(translated[annotation.get_text()])
    save_bilingual(fig, "03_alcance_copia.png", "03_copy_scope.png", spanish)


def strategy_figure(effects: pd.DataFrame) -> None:
    data = effects[effects.phase == "strategy"].copy()
    fig, axis = plt.subplots(figsize=(7.1, 3.65))
    color_map = {"all": BLUE, "credibility": GRAY, "non-credibility": ORANGE}
    for scope, group in data.groupby("scope"):
        axis.scatter(100 * group.target, 100 * group.fake, s=32, alpha=0.72,
                     color=color_map[scope], edgecolor="white", linewidth=0.4,
                     label=scope)
    axis.axhline(0, color=INK, linewidth=0.9)
    axis.axvline(0, color=INK, linewidth=0.9)
    axis.set_xlabel("Effect on target-source selection (percentage points)")
    axis.set_ylabel("Effect on actual fake reposts (percentage points)")
    axis.legend(frameon=False, fontsize=8, ncol=3, loc="lower right")
    style_axis(axis)
    def spanish():
        axis.set_xlabel("Efecto en selección de la fuente objetivo (puntos porcentuales)")
        axis.set_ylabel("Efecto en reposts falsos reales (puntos porcentuales)")
        handles, labels = axis.get_legend_handles_labels()
        translated = {"all": "todos", "credibility": "credibilidad", "non-credibility": "sin credibilidad"}
        axis.legend(handles, [translated[x] for x in labels], frameon=False, fontsize=8, ncol=3, loc="lower right")
    save_bilingual(fig, "04_popularidad_vs_desinformacion.png", "04_popularity_vs_misinformation.png", spanish)


def policy_figure(effects: pd.DataFrame) -> None:
    data = effects[effects.phase == "confirmation"].sort_values(["true_effect", "fake_effect"])
    labels = [f"False {int(r.fake_effect):+d} / True {int(r.true_effect):+d}" for r in data.itertuples()]
    values = 100 * data.fake.to_numpy()
    lows = 100 * data.fake_low.to_numpy()
    highs = 100 * data.fake_high.to_numpy()
    fig, axis = plt.subplots(figsize=(7.1, 3.2))
    positions = np.arange(len(data))
    axis.bar(positions, values, color=[ORANGE if r.true_effect == 0 else BLUE for r in data.itertuples()],
             edgecolor=INK, linewidth=0.6)
    axis.errorbar(positions, values, yerr=np.vstack([values-lows, highs-values]), fmt="none",
                  ecolor=INK, capsize=3, linewidth=0.9)
    axis.axhline(0, color=INK, linewidth=0.8)
    axis.set_xticks(positions, labels, rotation=25, ha="right")
    axis.set_ylabel("Effect on actual fake reposts (percentage points)")
    style_axis(axis)
    fig.tight_layout()
    def spanish():
        axis.set_xticklabels([f"Falsa {int(r.fake_effect):+d} / Verdadera {int(r.true_effect):+d}" for r in data.itertuples()], rotation=25, ha="right")
        axis.set_ylabel("Efecto en reposts falsos reales (puntos porcentuales)")
    save_bilingual(fig, "05_politicas_wom.png", "05_wom_policies.png", spanish)


def robustness_figure(effects: pd.DataFrame) -> None:
    data = effects[effects.phase == "robustness"].copy()
    reach_order = ["default", "14.7", "46.7", "81.2"]
    fig, axis = plt.subplots(figsize=(7.1, 3.25))
    for contacts, marker in ((6, "o"), (15, "s"), (27, "^")):
        group = data[data.contacts == contacts].copy()
        group["order"] = group.target_reach.astype(str).map({x:i for i,x in enumerate(reach_order)})
        group = group.sort_values("order")
        axis.plot(group.order, 100 * group.fake, marker=marker, linewidth=1.6,
                  label=f"{contacts} contacts")
    axis.axhline(0, color=INK, linewidth=0.8)
    axis.set_xticks(range(4), ["Reach off", "14.7%", "46.7%", "81.2%"])
    axis.set_ylabel("Camouflage effect on fake reposts (percentage points)")
    axis.legend(frameon=False, fontsize=8, ncol=3)
    style_axis(axis)
    fig.tight_layout()
    def spanish():
        axis.set_xticklabels(["Alcance desactivado", "14,7%", "46,7%", "81,2%"])
        axis.set_ylabel("Efecto del camuflaje en reposts falsos (puntos porcentuales)")
        handles, _ = axis.get_legend_handles_labels()
        axis.legend(handles, ["6 contactos", "15 contactos", "27 contactos"], frameon=False, fontsize=8, ncol=3)
    save_bilingual(fig, "06_robustez_red.png", "06_network_robustness.png", spanish)


def seeded_figure() -> None:
    precision = pd.read_csv(OLD / "q5_paired_precision.csv")
    values = precision.se_ratio_paired_to_unpaired.to_numpy()
    labels = [f"M{r.memory}, P{r.timing}" for r in precision.itertuples()]
    fig, axis = plt.subplots(figsize=(7.1, 3.15))
    positions = np.arange(len(values))
    axis.bar(positions, values, color=[BLUE if value < 1 else ORANGE for value in values],
             edgecolor=INK, linewidth=0.5)
    axis.axhline(1, color=INK, linewidth=1, linestyle="--")
    axis.set_xticks(positions, labels, rotation=35, ha="right")
    axis.set_ylabel("Paired SE / unpaired SE")
    axis.set_ylim(0, 2.25)
    style_axis(axis)
    fig.tight_layout()
    def spanish():
        axis.set_ylabel("Error estándar emparejado / no emparejado")
    save_bilingual(fig, "07_precision_semillas.png", "07_seeded_precision.png", spanish)


def main() -> None:
    base = pd.read_csv(BASE / "run_metrics.csv")
    new = pd.read_csv(NEW / "run_metrics.csv")
    if len(base) != 178 or len(new) != 1911:
        raise ValueError("Unexpected run count in dissemination suites")
    effects = scenario_effects(new)
    baseline_figure(base)
    memory_figure()
    scope_figure()
    strategy_figure(effects)
    policy_figure(effects)
    robustness_figure(effects)
    seeded_figure()
    effects.to_csv(ROOT / "informe/paper/tablas/validated_scenario_effects.csv", index=False)


if __name__ == "__main__":
    main()
