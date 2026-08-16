# Reproducible results

Each research question has an independent directory. Run outputs contain condition workbooks,
seeded raw result workbooks, logs, `study.tsv`, and `manifest.tsv`.

The final publication workflow is:

1. Execute the question with `make study-run ARGS="--questions RQn ... --output results/RQn/raw"`.
2. Verify that every manifest row has status `COMPLETE`.
3. Generate analysis CSV files, figures, and checksums.
4. Package the raw directory as a versioned GitHub Release asset.
5. Record the release URL, archive SHA-256, software commit, and analysis commit in the question's
   README.

Large generated workbooks and logs should be published as release assets rather than committed
directly to Git. Manifests, checksums, analysis tables, scripts, and figures should remain tracked.
