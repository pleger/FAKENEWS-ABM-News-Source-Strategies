# Manuscript build and submission package

Compile the author and anonymous versions from this directory:

```sh
latexmk -pdf main.tex
latexmk -pdf main-anonymous.tex
```

`main.tex` preserves the author and affiliation block approved by the authors, with Paul Leger listed first and as corresponding author.
`main-anonymous.tex` defines the anonymous-review switch and then includes the
same modular manuscript. Section files live in `sections/`, tables in `tables/`,
and figures in `images/`.

Journal articles and proceedings entries in `reference.bib` include a DOI.
The software package is cited by public release `v0.2.2`, its exact
source revision and checksums. A repository DOI may additionally be minted by
archiving that release in Zenodo or an equivalent service.
Outstanding non-computational submission items are recorded in
`AUTHOR_ACTIONS_BEFORE_SUBMISSION.md`.

The manuscript's numerical results are rebuilt from the compact processed data
described in the repository root. Generated raw workbooks are intentionally not
part of the paper directory.
