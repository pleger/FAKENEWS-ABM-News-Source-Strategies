# Final-study processed data

This directory is the public analytical record for *Evaluations of News Source Strategies to
Disseminate Fake News in X*. It contains no survey microdata and no machine-specific paths.

## Contents

- `run-metrics.csv`: canonical dataset with one row per seeded simulation run.
- `run-manifest.csv`: condition, seed, configuration, and completion status for every run.
- `condition-summary.csv`: means and standard deviations by experimental condition.
- `paired-effects.csv`: treatment-minus-control contrasts paired by common seed, with Student-t
  95% confidence intervals, Cohen's dz, raw p-values, and Holm-adjusted p-values.
- `validation.json`: expected population, horizon, run counts, and condition counts.
- `data-dictionary.md`: definitions, units, and analytical grain.
- `figures/` and `tables/`: manuscript-ready outputs generated from `run-metrics.csv`.
- `SHA256SUMS`: integrity hashes for the published analytical package.

## Reproduce the paper analysis

From the repository root:

```sh
python3 -m venv /tmp/fakenews-analysis-venv
/tmp/fakenews-analysis-venv/bin/pip install -r experiments/requirements.txt
/tmp/fakenews-analysis-venv/bin/python experiments/analyze_final_study.py \
  --processed analysis/final-study/run-metrics.csv \
  --output /tmp/fakenews-reproduced
```

The resulting `condition-summary.csv` and `paired-effects.csv` must match the tracked files.
The analysis is paired by seed because each treatment and its control use common random numbers.
Intervals describe Monte Carlo variability under the encoded model, not population sampling error.

## Full rerun

The Java study provider defines 79 conditions and 2,010 deterministic seeds. A complete rerun
generates approximately 583 MB of raw workbooks and logs and took about 10 h 29 min on the hardware
reported in the manuscript. Those generated files are intentionally excluded from Git. After a
rerun, pass its directory as the positional argument to `experiments/analyze_final_study.py` to
recreate this processed package.
