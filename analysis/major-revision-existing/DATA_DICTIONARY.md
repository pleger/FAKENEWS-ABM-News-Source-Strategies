# Processed data dictionary

These files reanalyse the original 2,010 completed runs. Raw workbooks are generated artefacts and are not tracked.

## `run-metrics.csv`

One row is one research-question/condition/seed run.

| Field or prefix | Meaning |
|---|---|
| `study`, `research_question`, `experiment`, `condition`, `seed` | Stable design identifiers |
| `strategy`, `from`, `to`, `start_period`, `end_period` | Scenario metadata; `end_period=-1` means through period 400 |
| `target_reach`, `memory`, `memory_half_life`, `wom`, `contacts`, `friends` | Model settings |
| `fake_repost_share_all` | False reposts divided by 500 users and 400 periods (false-repost rate per agent-period) |
| `fake_repost_share_decisions` | False reposts divided by actual repost decisions |
| `target_share_all` | Experimental-source selections divided by 500 users and 400 periods |
| `target_share_decisions` | Experimental-source selections divided by actual repost decisions |
| `participation_rate` | Actual decisions divided by 500 users and 400 periods |
| `*_final100` | Corresponding metric restricted to periods 301--400 |
| `total_decisions` | Actual decisions across all 400 periods |
| `cumulative_fake_reposts` | False decisions across all 400 periods |
| `target_unique_reposters_p25`, `_p50`, `_p100`, `_p200`, `_p400` | Cumulative distinct users selecting the experimental source by the named horizon |
| `target_first_repost_period` | First period with an experimental-source selection |
| `target_time_to_25pct_final`, `_50pct_`, `_75pct_` | Period at which the run reaches the named fraction of its period-400 unique reach |
| `selections_<source>_all` | Total selections of each of the four source objects |

## `period-metrics.csv.gz`

One row is one run-period (804,000 rows). It contains `total_decisions`, `false_reposts`, `target_selections`, cumulative `target_unique_reposters`, and selection counts for every source. It permits alternative denominators and windows to be reconstructed.

## Derived files

- `condition-summary.csv`: condition means and standard deviations.
- `paired-effects.csv`: seed-paired treatment-minus-control effects, unadjusted 95% t intervals, paired `d_z`, raw p values, and Holm-adjusted p values.
- `rq2-window-effects.csv`: matched during-campaign and fixed post-removal effects.
- `run-manifest.csv`: publication-safe run metadata without local file paths.
- `validation.json`: expected counts and validation outcome.
- `SHA256SUMS`: integrity digests for every published artefact.
