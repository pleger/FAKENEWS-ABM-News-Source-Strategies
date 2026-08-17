# Processed-data dictionary

## Grain and identifiers

`run-metrics.csv` has one row per research question, condition, and seed. The composite key is
`research_question`, `condition`, `seed`; all 2,010 rows have `status=COMPLETE`.

| Column | Definition |
|---|---|
| `study` | Stable study identifier (`news-source-strategies`). |
| `research_question` | RQ1, RQ2, RQ3, or RQ4. |
| `experiment` | Java experiment identifier. |
| `condition` | Stable treatment or matched-control identifier. |
| `seed` | Pseudorandom seed; treatments and controls share seeds. |
| `strategy` | Comma-separated named strategies, or `NONE`. |
| `from`, `to` | Source whose attributes are copied and malicious source receiving them. |
| `start_period`, `end_period` | Inclusive strategy interval; `-1` means through period 400. |
| `target_reach` | Direct reach probability assigned to the malicious source, when varied. |
| `memory` | Hard memory window in periods; `-1` disables the hard cutoff. |
| `memory_half_life` | Exponential-memory half-life in periods; `-1` disables decay. |
| `wom` | `1` enables social recommendations and `0` disables them. |
| `contacts` | Number of contacts sampled for social recommendation. |
| `friends` | Fraction of sampled contacts whose recommendations are received. |
| `status` | Execution status; the public dataset includes complete runs only. |

## Outcome metrics

All shares are proportions in `[0,1]`; multiply by 100 for percentage points.

| Column | Definition |
|---|---|
| `fake_repost_share_all` | Mean per-period false repost count divided by 500 agents over periods 1–400. |
| `fake_repost_share_final100` | Same share over periods 301–400. |
| `target_share_all` | Mean share of agents reposting the malicious source over periods 1–400. |
| `target_share_final100` | Malicious-source repost share over periods 301–400. |
| `cumulative_fake_reposts` | Total false repost decisions over 400 periods. |
| `target_fake_rate_all` | Fraction of periods in which the malicious source published false news. |
| `target_unique_reposters_p400` | Cumulative distinct agents that reposted the malicious source by period 400. |
| `fake_repost_share_during` | False-repost share during the configured strategy interval. |
| `fake_repost_share_post100` | False-repost share in up to 100 periods after a finite strategy ends; blank for permanent strategies. |

`condition-summary.csv` reports condition-level means and sample standard deviations. In
`paired-effects.csv`, `effect` is treatment minus matched control, `ci95_low` and `ci95_high` are
the paired Student-t interval, `cohens_dz` is the standardized paired effect, and `p_holm` controls
multiple comparisons within each research-question/outcome family.
