# CLI experiment runners

## Java study orchestrator

Typed Java study providers do not require Python to generate workbooks or execute simulations:

```sh
make study-plan STUDY_CLASS=your.package.YourStudyProvider
java -cp "build/classes:lib/*" experiment.StudyMain \
  --study-class your.package.YourStudyProvider --base input/your-study.xlsx \
  --questions RQ1,RQ2 --jobs 2 --output output/studies/main --execute
```

Planning is the safe default. Execution requires `--execute`; supplying the same output directory
resumes the study and skips runs marked complete. Python remains useful for statistical analysis,
figures, and the legacy batch scripts documented below.

This directory keeps batch-experiment concerns outside the Java simulation core. The runner creates
temporary copies of an input workbook, changes configuration and scenario cells in those copies,
and invokes the existing generic Java CLI.

Install the workbook dependency in an isolated environment, then run:

```sh
python3 -m venv /tmp/fakenews-experiment-venv
/tmp/fakenews-experiment-venv/bin/pip install -r experiments/requirements.txt
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/run_memory_timing.sh --memory -1 --wom 1
```

The five conditions are no scenario and scenario activation at periods 1, 25, 50, and 100. Each
uses 400 agents, 400 periods, saved fake-news state, and the current `REPETITIONS=10` behavior,
which executes simulations 1 through 11. Generated results remain under `output/`. Add
`--keep-workbooks` when the generated input variants should also be retained for auditing.

Use `--input path/to/workbook.xlsx` to select another source workbook. Legacy scenario conditions
require `FROM` in A1 and `TO` in B1; columns D onward remain explicit attributes and an empty list
means “copy all attributes.” Header-based workbooks can additionally define reusable groups in
`Strategies`, reference them from `Scenario`, and set an inclusive `END_PERIOD`. Workbooks with
`SourceBehavior` keep objective fake-news probability independent from copied credibility.

## Follow-up research questions

Run the copy-scope, memory/timing, network/WOM, and seeded-pairing matrices without changing the
Java simulation core:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/run_research_questions.sh --questions 1,2,3,5 --jobs 6
```

The command prints an experiment root under `output/research_questions_*`. Analyze it with:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/analyze_research_questions.py output/research_questions_YYYYMMDD_HHMMSS
```

After validation, create the technical findings report and static figures:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/build_research_markdown.py output/research_questions_YYYYMMDD_HHMMSS
```

Question 1 defines “engagement attributes” as every source attribute except `Credibilidad de la
fuente`; this preserves the target source's original fake-news probability. Question 2 tests memory
5, 10, 25, 50, 100, and infinite (`-1`) at scenario periods 1, 25, 50, and 100. Question 3 varies
realized contact degree and the model's binary source-reach switch. The current simulation has no
homophily parameter, so homophily cannot be estimated by the CLI harness alone. Question 5 uses
the model's explicit reproducible random seed support. The legacy `SeededMain` launcher remains as
a compatibility wrapper around `Main --seed`.

Empirical calibration (question 4) additionally requires observed target metrics and uncertainty
or weights. No empirical repost, exposure, or credibility dataset is included in this repository,
so the simulation matrices must not be described as empirically calibrated.

Copy `experiments/empirical_targets_template.csv`, fill its observed values and defensible scales,
then rank a simulated parameter grid with:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/calibrate_empirical.py empirical_targets.csv simulated_metrics.csv \
  --output calibration_scores.csv
```

The simulated metrics file must contain `memory`, `base`, `wom_weight`, `metric`, and `value`.

## WOM outcome policies

`prepare_workbook.py` can independently control how the receiver values a recommendation after
learning whether the publication was false or true:

```sh
python3 experiments/prepare_workbook.py input/FAKENEWS_BASELINE.xlsx /tmp/wom-policy.xlsx \
  --memory 25 --wom 1 \
  --wom-fake-news-effect -1 \
  --wom-true-news-effect 1
```

Both effect options accept `-1` (penalize), `0` (ignore without creating an endorsement), and `1`
(reward). Omitting them preserves the legacy/current policy: false publications are penalized and
true publications are rewarded. The same overrides are available directly in `Main` as
`--wom-fake-news-effect` and `--wom-true-news-effect`.

Recommendation magnitude can be varied independently with `--wom-receiver-scale`. The default
model value is `0.5`; this preserves legacy behavior and is a sensitivity parameter rather than an
empirically calibrated constant. Exponential memory can be enabled with `--memory-half-life H`,
where `H` is a positive number of periods and `-1` disables decay. Use `--memory -1` together with
a positive half-life for a pure decay experiment; otherwise decay is followed by the hard cutoff.

## Baseline and dissemination strategies

The dissemination suite separates model validation from hypothetical interventions. Run the
baseline first:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/run_dissemination_experiments.sh --suites baseline --jobs 2
```

It compares no WOM, discovery-only WOM (`0/0`), and truth-sensitive WOM (`-1/+1`) with 30 common
seeds, plus memory sensitivity with 11 seeds. After reviewing the baseline, run the intervention
and robustness suites:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/run_dissemination_experiments.sh \
  --suites strategies,confirmation,robustness --jobs 2
```

Analyze any completed suite with:

```sh
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/analyze_dissemination_experiments.py \
  output/dissemination_experiments_YYYYMMDD_HHMMSS
```

The analyzer validates period rows and source fake-news states, calculates paired effects against
matched controls, and creates CSV summaries, a Markdown report, and figures under `analysis/`.
The primary outcome is actual fake-repost share. In legacy workbooks, `non-credibility` remains the
principal camouflage scenario because credibility also controls fake-news generation. In new
workbooks with `SourceBehavior`, credibility is only perceived credibility, so credibility and
all-attribute strategies no longer alter the source's objective fake-news probability.

Expected run counts are 178 for `baseline` (including memory sensitivity), 1,287 for `strategies`,
360 for `confirmation`, and 264 for `robustness`. Run the suites separately when results should be
reviewed between stages. The default concurrency is two Java processes; increase `--jobs` only
after checking CPU and memory usage on the execution machine.
Lower calibration scores indicate a closer weighted normalized fit; held-out empirical targets are
still required to assess generalization.
