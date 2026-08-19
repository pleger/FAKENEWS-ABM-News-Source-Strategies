import unittest

import pandas as pd

import analyze_recommendation_diagnostics as diagnostics


class RecommendationDiagnosticAnalysisTests(unittest.TestCase):
    def test_header_names_are_normalized(self):
        self.assertEqual("receivers_with_recommendation",
                         diagnostics.snake("ReceiversWithRecommendation"))
        self.assertEqual("true_positive_labels", diagnostics.snake("TruePositiveLabels"))

    def test_reference_mapping_reuses_archived_cells(self):
        self.assertEqual(("existing", "control_reach14_7_wom0"),
                         diagnostics.reference_condition("disabled_control_r14_7"))
        self.assertEqual(("existing", "control_reach14_7_wom1"),
                         diagnostics.reference_condition("oracle_control_r14_7"))
        self.assertEqual(("revision", "imperfect_fast_combined_r14_7"),
                         diagnostics.reference_condition("imperfect_fast_combined_r14_7"))

    def test_non_interference_requires_exact_archived_outcomes(self):
        metrics = {"fake_repost_share_all": .1, "fake_repost_share_decisions": .1,
                   "target_share_all": .2, "target_share_decisions": .2,
                   "participation_rate": 1.0, "total_decisions": 200000,
                   "cumulative_fake_reposts": 20000,
                   "selections_traditional_media_all": 1,
                   "selections_unknown_media_all": 2,
                   "selections_fake_news_source_all": 3,
                   "selections_mixed_source_all": 4}
        runs = pd.DataFrame([{"condition": "disabled_control_r14_7", "seed": 1001, **metrics}])
        existing = pd.DataFrame([{"condition": "control_reach14_7_wom0", "seed": 1001, **metrics}])
        revised = pd.DataFrame(columns=existing.columns)
        self.assertEqual([], diagnostics.verify_non_interference(runs, existing, revised))
        self.assertEqual(1e-15, diagnostics.NON_INTERFERENCE_ATOL)


if __name__ == "__main__":
    unittest.main()
