# FAKENEWS-ABM

FAKENEWS-ABM is an agent-based model for simulating the dissemination of fake news on Social Network Sites (SNSs) such as X or Instagram.

## Project Context

This software supports the PLURALISMO project `PLU230018`, **Evaluacion de Estrategias para Diseminar Fake News Usando Inteligencia Artificial**.

- Responsible researcher: Paul Leger, Associate Professor, Universidad Catolica del Norte (UCN).
- Research team: Paul Leger, Agustin Olivares, Oswaldo Teran, Manuela Lopez, Francis Espinoza, and Carolina Rodriguez.

The project studies how fake news spreads through social network sites and how computational simulation can help evaluate dissemination and mitigation strategies. FAKENEWS-ABM contributes an agent-based simulation environment based on Endorsement theory, using SNS users, news sources, source credibility, message features, social sharing, and scenario interventions as the core modeling elements.

The model uses Endorsement theory to represent how SNS users evaluate news sources and decide whether to repost news:

- `SNSUser` agents represent SNS users;
- `NewsSource` objects represent source types, such as traditional media, unknown online media, fake-news sources, and mixed sources;
- each period, a user's selected source represents a repost decision;
- source selection share becomes repost share;
- word of mouth becomes contact-based social sharing;
- endorsement attributes come from `datos para simulacion.xlsx`.

In this context, the ABM provides a computational laboratory for comparing how source credibility, content framing, source reach, user contacts, and scenario interventions affect fake-news diffusion.

## Model

Each simulation has a population of SNS users. At every period, each user evaluates the sources they know through accumulated endorsement values. A selected source represents the source whose news the user reposts in that period.

The source attributes are two-level probability distributions (`Low`, `High`) for variables such as:

- positive and negative evoked emotions;
- simple language;
- political, geographic, and social proximity;
- sensationalism;
- information quality;
- information entertainment value;
- credibility of the source;
- audiovisual content, hashtags, and links.

User attributes are the corresponding mean weights on a 1-7 scale. The optional `WORD OF MOUTH` weight controls how strongly users incorporate recommendations from contacts. The endorsement formula supports negative weights too, which can be useful when an experiment should model aversion to a high level of an attribute, such as sensationalism.

## Inputs

The simulator reads Excel workbooks from `input/` by default. The loader also accepts direct workbook paths and checks `inputs/` for compatibility with the plural folder name.

Generated fake-news inputs:

- `FAKENEWS_BASELINE.xlsx`: plural media ecosystem with WOM enabled.
- `FAKENEWS_BASELINE_3.xlsx`: decay-oriented baseline with infinite hard memory, half-life `0.33`, five contacts, full contact listening, and fake-news-state reporting.
- `FAKENEWS_NO_WOM.xlsx`: same source ecology with social sharing disabled.
- `FAKENEWS_COORDINATED_PUSH.xlsx`: after period 15, unknown media adopts fake-news-source values for simple language, sensationalism, and entertainment.
- `FAKENEWS_MEDIA_LITERACY.xlsx`: countermeasure-style input with higher quality weighting and lower sensitivity to sensationalism/negative emotion.

Workbook sheets:

- `Configuration`: simulation controls such as `PERIODS`, `AGENTS`, `REPETITIONS`, `WOM`, and report flags. `MEMORY` is the legacy hard cutoff (default `25`); `MEMORY_HALF_LIFE` optionally applies exponential decay (`-1` disables it, positive decimals are allowed). For a pure decay experiment, use `MEMORY=-1`. `WOM_RECEIVER_SCALE` controls recommendation magnitude and defaults to the legacy value `0.5`. `WOM_FAKE_NEWS_EFFECT` and `WOM_TRUE_NEWS_EFFECT` independently set the effect of a recommendation to `-1` (penalize), `0` (ignore), or `1` (reward). Optional keys preserve their documented defaults when omitted.
- `NewsSources`: source-type endorsement distributions.
- `SNSUsers`: SNS-user endorsement weights.
- `SourceReach`: source reach or visibility probability.
- `SourceBehavior` (new schema): one `FAKE_NEWS_PROBABILITY` in `[0,1]` per `SOURCE`. This objective publication probability is independent of perceived credibility and is never copied by a scenario. When the sheet is absent, the loader preserves the legacy rule based on the source's current low-credibility probability and logs a warning.
- `Strategies` (new schema): normalized `STRATEGY`/`ATTRIBUTE` rows defining reusable, named attribute groups.
- `Scenario`: optional custom intervention. The legacy one-row form (`FROM`, `TO`, `PERIOD`, then attributes) remains supported and permanent. The new header-based form uses `FROM`, `TO`, `START_PERIOD`, `END_PERIOD`, `STRATEGIES`, then optional additional attributes on the next row. Strategy names may be comma-separated; their attributes are unioned with explicit attributes. `END_PERIOD=-1` means permanent, otherwise the inclusive campaign interval is `[START_PERIOD, END_PERIOD]`. If neither strategies nor attributes are supplied, all attributes are copied.

Set the optional `SAVED_FAKENEWS` configuration value to `1` to save every period's source
publication classification, or omit it/use `0` to disable that report.

## CLI

The Java implementation is the reference simulation core. Its source code lives in `src/`, tests in `tests/`, Java libraries in `lib/`, and Java CLI scripts in `bin/`.

Build:

```sh
make build
```

List available inputs:

```sh
java -cp "build/classes:lib/*" Main --list-inputs
```

Run a scenario:

```sh
java -cp "build/classes:lib/*" Main --input FAKENEWS_BASELINE --no-gui
```

Build a distributable package:

```sh
make dist
```

The package is written to `dist/FAKENEWS-ABM-0.1.0.zip`. After unzipping it, run:

```sh
bin/fakenews-abm --input FAKENEWS_BASELINE --no-gui
```

Useful overrides:

```sh
java -cp "build/classes:lib/*" Main \
  --input FAKENEWS_COORDINATED_PUSH \
  --periods 60 \
  --agents 500 \
  --repetitions 20 \
  --no-gui
```

## Reproducible batch experiments

Studies can now be planned and executed entirely from Java while ordinary one-workbook executions
through `Main` remain unchanged. A study lives in a separate class implementing `StudyProvider`,
so research questions and paper-specific conditions do not become part of the generic model.
Preview a provider's complete typed design first:

```sh
make study-plan STUDY_CLASS=your.package.YourStudyProvider
```

Run selected questions with isolated JVMs and an explicit resumable output directory:

```sh
java -cp "build/classes:lib/*" experiment.StudyMain \
  --study-class your.package.YourStudyProvider \
  --base input/your-study.xlsx \
  --questions RQ1 \
  --jobs 2 \
  --output output/studies/rq1 \
  --execute
```

Reusing the same `--output` skips completed runs and retries incomplete ones. `--seeds N` limits
the common seed set and `--max-runs N` supports smoke tests. Each study writes `study.tsv`, a
condition workbook for every treatment, isolated logs/results, and a status-aware `manifest.tsv`.
The standalone model also accepts `--seed` and `--output-directory`, so one particular simulation
can still be run without the study orchestrator.

The generic Java study hierarchy is `StudySpecification` → `ResearchQuestionSpecification` →
`ExperimentSpecification` → `ConditionSpecification` → `SimulationRunSpecification`. It belongs
to the optional `experiment` package; the simulation domain does not depend on it. Concrete study
providers and their inputs can therefore be published in separate reproducibility repositories.

Batch-specific workbook changes are isolated in `experiments/`; the Java production entry point
does not contain memory, report-flag, or scenario-timing controls created solely for one experiment.
To execute the five memory/timing conditions using temporary workbook copies:

```sh
python3 -m venv /tmp/fakenews-experiment-venv
/tmp/fakenews-experiment-venv/bin/pip install -r experiments/requirements.txt
PYTHON_BIN=/tmp/fakenews-experiment-venv/bin/python \
  experiments/run_memory_timing.sh --memory -1 --wom 1
```

See [`experiments/README.md`](experiments/README.md) for configuration and output details.

For the truth-sensitive baseline and the hypothetical fake-news dissemination matrix, use
`experiments/run_dissemination_experiments.sh`. It separates no-WOM, discovery-only WOM, and
truth-sensitive WOM before testing credibility-only, non-credibility camouflage, and full-copy
scenarios with paired seeds.

## Example Scenario Results

The following example compares four 40-period runs with 200 SNS users. Each line shows reposts per period for one source type: traditional media, unknown media, fake-news source, and mixed source.

![Scenario comparison](imgs/scenario-comparison.png)

The `Baseline` panel represents the default media ecosystem with contact-based sharing enabled. Traditional media receives the highest repost volume, while fake-news sources remain comparatively low because they combine lower credibility and lower reach.

The `No WOM` panel disables contact-based sharing. This isolates direct source evaluation from social recommendations, reducing the social reinforcement effect that can amplify source visibility through user contacts.

The `Coordinated Push` panel activates the intervention configured in `FAKENEWS_COORDINATED_PUSH.xlsx`: after period 15, `UNKNOWN_MEDIA` adopts selected fake-news-source values for simple language, sensationalism, and entertainment. This scenario is useful for studying how a source that is not explicitly labeled as fake can become more attractive by adopting high-engagement content features.

The `Media Literacy` panel represents a countermeasure-style population: users give more weight to information quality and less weight to sensationalism and negative emotion. In this run, reposts concentrate more strongly around traditional media and reduce the relative presence of mixed and fake-news sources.

## Browser TypeScript App

The backend-free TypeScript implementation lives entirely in `web/`. It runs the ABM directly in the browser, reads Excel workbooks, validates the same configuration fields used by `Configuration.java`, displays simulation progress and logs, renders generated chart images, and produces downloadable workbook/ZIP artifacts.

Install and run:

```sh
cd web
npm install
npm run dev
```

Then open the local URL printed by Vite. The app includes two bundled examples:

- `FAKENEWS_BASELINE.xlsx`
- `FAKENEWS_COORDINATED_PUSH.xlsx`

You can also load any compatible `.xlsx` workbook through the file picker.

Build the static web app:

```sh
cd web
npm run build
```

The build output is written to `web/dist/` and is deployed to GitHub Pages.

## Project Layout

- `src/`: Java reference implementation and simulation core.
- `tests/`: Java tests for the reference implementation.
- `input/`: Excel workbooks consumed by the Java CLI.
- `experiments/`: isolated workbook preparation and batch-execution utilities.
- `web/`: TypeScript browser implementation and GitHub Pages app.
- `web/src/`: TypeScript port of the model, loader, reporter, charts, and UI.
- `web/public/examples/`: Excel examples bundled into the web app.
- `imgs/`: README and report images.
- `output/`, `build/`, `dist/`, `web/dist/`, and `node_modules/`: generated artifacts.

When changing model behavior in Java, treat `src/` as the source of truth and then mirror the same behavioral change in `web/src/main.ts`. This keeps the browser version aligned with the Java CLI while keeping the two implementations easy to distinguish.

## Tests

Run:

```sh
make test
```

The test suite currently checks:

- endorsement scoring for binary high/low levels;
- loading the generated fake-news workbook;
- applying a custom scenario that copies selected attributes from one source type to another;
- reset behavior for source/user factories across repeated loads.

## Outputs

Each run writes a timestamped folder under `output/`. The output workbook includes the configuration and input sheets, plus:

- `RepostsPerSource`;
- `UniqueRepostersPerSource`;
- `Results`;
- `DetailedResult`;
- `Endorsements`;
- `ScenarioChanges`;
- `FakeNewsPerSource` when `SAVED_FAKENEWS=1`; values are `1` for fake news and `0` otherwise.

The source code now uses PLURALISMO/SNS vocabulary for the main domain types: `SNSUser`, `NewsSource`, `NewsSourceFactory`, `SNSUserFactory`, `RepostsPerSourceData`, and related input/report classes.

### Generated charts

When `GUI=1` and repost reporting is enabled, every simulation saves two charts:

- `Simulation_<ID>_RepostsPerPeriod.png` plots the number of source selections in each period;
- `Simulation_<ID>_UniqueReposters.png` plots the cumulative number of distinct agents that have
  selected each source.

For experiments with repetitions, the simulator also saves `Aggregate_RepostsPerPeriod_MeanSD.png`
and `Aggregate_UniqueReposters_MeanSD.png`. These show the across-run arithmetic mean with sample
standard-deviation error bars. A configured scenario is marked by a dashed vertical line when its
start period falls inside the plotted reporting range.

At the beginning of every repetition, source attributes are restored from the input workbook before
initial user endorsements are generated. Consequently, scenario changes do not leak into the next
simulation's initial conditions.

## License

MIT.
