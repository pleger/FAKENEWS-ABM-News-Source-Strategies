# Factorial experiment runner

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

Use `--input path/to/workbook.xlsx` to select another source workbook. Scenario conditions require
the workbook's `Scenario` sheet to define `FROM` in A1 and `TO` in B1. Columns D onward are preserved,
so an empty attribute list continues to mean “copy all attributes.”
