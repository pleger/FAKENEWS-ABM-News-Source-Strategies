#!/usr/bin/env python3
"""Focused tests for dissemination-specific workbook transformations."""

from __future__ import annotations

import unittest

from openpyxl import load_workbook

from prepare_workbook import (
    CREDIBILITY_ATTRIBUTE,
    NON_CREDIBILITY_ATTRIBUTES,
    set_scenario_attributes,
    set_source_reach,
)


class PrepareWorkbookTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workbook = load_workbook("input/FAKENEWS_BASELINE_2.xlsx")

    def scenario_values(self) -> list[str]:
        return [
            cell.value for cell in self.workbook["Scenario"][1][3:]
            if cell.value is not None and str(cell.value).strip()
        ]

    def test_credibility_scope_copies_only_credibility(self) -> None:
        set_scenario_attributes(self.workbook["Scenario"], "credibility")
        self.assertEqual([CREDIBILITY_ATTRIBUTE], self.scenario_values())

    def test_non_credibility_scope_preserves_fake_news_probability_dimension(self) -> None:
        set_scenario_attributes(self.workbook["Scenario"], "non-credibility")
        self.assertEqual(list(NON_CREDIBILITY_ATTRIBUTES), self.scenario_values())
        self.assertNotIn(CREDIBILITY_ATTRIBUTE, self.scenario_values())

    def test_all_scope_uses_empty_attribute_shortcut(self) -> None:
        set_scenario_attributes(self.workbook["Scenario"], "all")
        self.assertEqual([], self.scenario_values())

    def test_target_source_reach_is_replaced(self) -> None:
        set_source_reach(self.workbook["SourceReach"], "FAKE_NEWS_SOURCE", 46.7)
        values = {row[0].value: row[1].value for row in self.workbook["SourceReach"].iter_rows()}
        self.assertEqual(46.7, values["FAKE_NEWS_SOURCE"])


if __name__ == "__main__":
    unittest.main()
