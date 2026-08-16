# Journal of Simulation manuscript

Working title: *Camouflage, memory, and truth-sensitive word of mouth in fake-news dissemination: a seeded agent-based simulation study*.

## Build

From this directory:

```sh
latexmk -pdf -interaction=nonstopmode -halt-on-error main.tex
```

Clean auxiliary files with `latexmk -c` if required. The manuscript uses the Taylor & Francis `interact` class and author-year citations.

## Editorial limits

The Taylor & Francis instructions page for *Journal of Simulation* was protected by an automated-access challenge when this draft was prepared. Following the approved fallback, the manuscript is capped at 8,000 words. Run:

```sh
texcount -inc -sum main.tex
```

The current source is below that fallback cap. Before submission, the corresponding author should check the live journal portal and apply any lower current limit, including its rules about whether the abstract, references, tables, and captions count. The abstract is kept below 200 words.

## Metadata still requiring author confirmation

- ORCID identifiers and email addresses for authors other than Paul Leger.
- Specific academic units for Agustín Olivares, Francis Espinoza, and Carolina Rodríguez.
- Final spelling/diacritics preferred by every author.
- CRediT roles, competing-interest confirmation, and formal funding wording.
- Persistent archive/DOI and exact Git commit for the submitted data and code package.

The verified corresponding-author metadata is Paul Leger, `pleger@ucn.cl`, ORCID `0000-0003-0969-5139`.
