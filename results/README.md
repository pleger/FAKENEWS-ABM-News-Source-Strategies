# Reproducible results

Status: **complete**. All 2,010 seeded runs passed the final-study validation.

Each research question has an independent documentation directory. The publication-safe,
run-level results are tracked in [`../analysis/final-study`](../analysis/final-study); generated
condition workbooks and execution logs remain ignored because they can be recreated from the
published study definition and seeds.

The publication workflow is:

1. Inspect or rerun a question with the Java study runner.
2. Verify that every manifest row has status `COMPLETE`.
3. Extract one publication-safe metrics row per seed.
4. Recompute paired contrasts, confidence intervals, tables, and figures.
5. Verify the tracked `SHA256SUMS` file.

Large generated workbooks and logs are not committed. Processed data, a path-free manifest,
checksums, analysis tables, scripts, and figures are tracked directly in Git.
