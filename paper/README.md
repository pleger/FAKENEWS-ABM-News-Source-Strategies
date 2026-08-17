# Manuscript build

Compile the author and anonymous versions from this directory:

```sh
latexmk -pdf main.tex
latexmk -pdf main-anonymous.tex
```

All BibTeX entries must contain a DOI. Items without a DOI are cited as URLs in the text rather than added to `reference.bib`.
