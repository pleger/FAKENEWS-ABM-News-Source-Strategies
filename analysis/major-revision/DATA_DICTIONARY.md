# Major-revision processed data

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
