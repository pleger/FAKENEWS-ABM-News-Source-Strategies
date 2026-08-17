import unittest
import pandas as pd

from analyze_final_study import paired_stats, public_runs


class PairedStatisticsTest(unittest.TestCase):
    def test_pairing_uses_seed_not_row_order(self):
        treatment=pd.DataFrame({"seed":[1,2,3],"metric":[4.0,7.0,9.0]})
        control=pd.DataFrame({"seed":[3,1,2],"metric":[5.0,1.0,3.0]})
        result=paired_stats(treatment,control,"metric")
        self.assertEqual(result["n_pairs"],3)
        self.assertAlmostEqual(result["effect"],11.0/3.0)
        self.assertLess(result["ci95_low"],result["effect"])
        self.assertGreater(result["ci95_high"],result["effect"])

    def test_constant_difference_has_zero_width_interval(self):
        treatment=pd.DataFrame({"seed":[1,2,3],"metric":[2.0,3.0,4.0]})
        control=pd.DataFrame({"seed":[1,2,3],"metric":[1.0,2.0,3.0]})
        result=paired_stats(treatment,control,"metric")
        self.assertAlmostEqual(result["effect"],1.0)
        self.assertAlmostEqual(result["ci95_low"],1.0)
        self.assertAlmostEqual(result["ci95_high"],1.0)

    def test_public_dataset_removes_machine_paths(self):
        runs = pd.DataFrame({
            "research_question": ["RQ1"], "condition": ["control"], "seed": [1001],
            "input_workbook": ["/Users/example/input.xlsx"],
            "result_workbook": ["/Users/example/result.xlsx"], "log": ["/tmp/run.log"],
            "status": ["COMPLETE"],
        })
        published = public_runs(runs)
        self.assertNotIn("input_workbook", published.columns)
        self.assertNotIn("result_workbook", published.columns)
        self.assertNotIn("log", published.columns)
        self.assertEqual(published.loc[0, "seed"], 1001)


if __name__=="__main__": unittest.main()
