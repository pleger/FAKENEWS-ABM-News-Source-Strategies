import unittest
import os
from pathlib import Path
import tempfile

import pandas as pd

import analyze_major_revision as revision


class MajorRevisionAnalysisTests(unittest.TestCase):
    def test_strategy_and_policy_names_cover_every_condition_family(self):
        self.assertEqual("combined", revision.strategy_of("imperfect_fast_combined_r14_7"))
        self.assertEqual("credibility", revision.strategy_of("m08_credibility"))
        self.assertEqual("control", revision.strategy_of("top1_a0_5_control"))
        self.assertEqual("imperfect_fast", revision.policy_of("imperfect_fast_combined_r14_7"))
        self.assertEqual("scale0_25", revision.policy_of("scale0_25_informational_r14_7"))

    def test_holm_adjustment_is_monotone_in_p_value_order(self):
        frame = pd.DataFrame({"metric": ["x"] * 3, "p_value": [0.03, 0.001, 0.02]})
        result = revision.holm(frame, ["metric"]).sort_values("p_value")
        self.assertTrue((result.p_holm.diff().dropna() >= 0).all())
        self.assertTrue((result.p_holm >= result.p_value).all())

    def test_morris_memory_coordinate_matches_logarithmic_sampling(self):
        low, high = revision.PARAMETERS["memory_half_life"]
        geometric_midpoint = (low * high) ** 0.5
        self.assertAlmostEqual(0.0, revision.normalized_parameter("memory_half_life", low))
        self.assertAlmostEqual(0.5, revision.normalized_parameter(
            "memory_half_life", geometric_midpoint))
        self.assertAlmostEqual(1.0, revision.normalized_parameter("memory_half_life", high))
        self.assertAlmostEqual(0.5, revision.normalized_parameter("target_reach", 7.85))

    def test_structural_effects_pair_on_seed_within_setting(self):
        rows = []
        for seed, control, treatment in [(1, 0.10, 0.13), (2, 0.20, 0.24)]:
            base = {"research_question": "RQ4", "experiment": "E4c-structural-sensitivity",
                    "seed": seed, "network_topology": 1, "user_activity": 0.5}
            for condition, strategy, value in [
                    ("top1_a0_5_control", "NONE", control),
                    ("top1_a0_5_credibility", "CREDIBILITY_CAMOUFLAGE", treatment)]:
                row = {**base, "condition": condition, "strategy": strategy}
                for metric in revision.METRICS:
                    row[metric] = value
                rows.append(row)
        result = revision.structural_effects(pd.DataFrame(rows))
        primary = result[result.metric == "fake_repost_share_all"].iloc[0]
        self.assertEqual(2, primary.n_pairs)
        self.assertAlmostEqual(0.035, primary.effect)
        self.assertAlmostEqual(0.15, primary.control_mean)
        self.assertAlmostEqual(0.185, primary.treatment_mean)
        self.assertAlmostEqual(0.035 / 0.15 * 100, primary.relative_effect_percent)

    def test_execution_metadata_uses_study_and_completion_timestamps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            study = root / "study.tsv"
            marker = root / "results" / "RQ3" / "E3" / "c" / "seed-1" / ".complete"
            prior = root / "results" / "RQ3" / "E3" / "c" / "seed-0" / ".complete"
            marker.parent.mkdir(parents=True)
            prior.parent.mkdir(parents=True, exist_ok=True)
            study.write_text("study_id\ttest\n", encoding="utf-8")
            marker.write_text("", encoding="utf-8")
            prior.write_text("", encoding="utf-8")
            os.utime(study, (1000, 1000)); os.utime(marker, (1123.5, 1123.5)); os.utime(prior, (900, 900))
            result = revision.execution_metadata(root, simulation_jobs=10)
            self.assertEqual(123.5, result["elapsed_seconds_since_resume"])
            self.assertEqual(10, result["simulation_jobs"])
            self.assertEqual(2, result["completed_runs"])
            self.assertEqual(1, result["preexisting_completed_runs"])
            self.assertEqual(1, result["completed_since_resume"])
            self.assertIn("operating_system", result)

    def test_markdown_rows_formats_publication_values(self):
        frame = pd.DataFrame([{"policy": "discovery", "effect": 0.12345}])
        table = revision.markdown_rows(
            frame, ["policy", "effect"], ["Policy", "Effect"], {"effect": "{:.3f}"})
        self.assertIn("| Policy | Effect |", table)
        self.assertIn("| discovery | 0.123 |", table)

    def test_morris_stability_bootstraps_whole_trajectories(self):
        rows = []
        for trajectory in range(1, 5):
            for seed in (1, 2):
                rows.append({"strategy": "combined", "metric": "m", "factor": "strong",
                             "trajectory": trajectory, "elementary_effect": 2.0 + trajectory / 10})
                rows.append({"strategy": "combined", "metric": "m", "factor": "weak",
                             "trajectory": trajectory, "elementary_effect": 0.1})
        result = revision.morris_stability(pd.DataFrame(rows), resamples=200, seed=1)
        strong = result[result.factor == "strong"].iloc[0]
        weak = result[result.factor == "weak"].iloc[0]
        self.assertEqual(1.0, strong.rank1_probability)
        self.assertEqual(1, strong.loo_rank_min)
        self.assertEqual(2, weak.loo_rank_max)

    def test_rq2_source_windows_use_matching_absolute_periods(self):
        periods = []
        for condition, increment in (("control", 0), ("campaign", 5)):
            for seed in (1, 2):
                for period in range(1, 5):
                    row = {"research_question": "RQ2", "condition": condition,
                           "seed": seed, "period": period}
                    for source in revision.core.SOURCES:
                        row[f"selections_{source.lower()}"] = 10
                    if condition == "campaign" and period <= 2:
                        row["selections_fake_news_source"] += increment
                    periods.append(row)
        runs = pd.DataFrame([{"research_question": "RQ2", "condition": "campaign",
                              "strategy": "COMBINED", "start_period": 1, "end_period": 2}])
        old_periods = revision.core.PERIODS
        revision.core.PERIODS = 4
        try:
            result = revision.rq2_source_window_effects(pd.DataFrame(periods), runs)
        finally:
            revision.core.PERIODS = old_periods
        target = result[(result.window == "during") &
                        (result.source == "FAKE_NEWS_SOURCE")].iloc[0]
        self.assertAlmostEqual(5 / revision.core.AGENTS, target.effect)


if __name__ == "__main__":
    unittest.main()
