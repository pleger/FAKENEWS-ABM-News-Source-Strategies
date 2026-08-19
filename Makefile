JAVAC ?= javac
JAVA ?= java
JAR ?= jar
PYTHON ?= python3
VERSION ?= 0.1.0
APP_NAME := FAKENEWS-ABM
CLASSPATH := build/classes:lib/*
STUDY_CLASS ?= experiment.NewsSourceStrategiesStudy
STUDY_BASE ?= input/FAKENEWS_BASELINE_4_STRATEGIES.xlsx
DIST_DIR := dist/$(APP_NAME)-$(VERSION)
DIST_ZIP := dist/$(APP_NAME)-$(VERSION).zip

.PHONY: build test analysis-test jar dist run study-plan study-run study-progress study-diagnostics study-structural-correction analysis-processed analysis-raw analysis-major-revision analysis-diagnostics clean

build:
	mkdir -p build/classes
	$(JAVAC) -cp "lib/*" -d build/classes $$(find src -name "*.java")

jar: build
	mkdir -p build/package
	$(JAR) cfe build/package/$(APP_NAME).jar Main -C build/classes .

dist: jar
	rm -rf "$(DIST_DIR)" "$(DIST_ZIP)"
	mkdir -p "$(DIST_DIR)"
	cp -R build/package/$(APP_NAME).jar lib input bin README.md LICENSE "$(DIST_DIR)/"
	cd dist && zip -qr "$(APP_NAME)-$(VERSION).zip" "$(APP_NAME)-$(VERSION)"
	@echo "$(DIST_ZIP)"

test: build
	$(JAVAC) -cp "$(CLASSPATH)" -d build/classes $$(find tests -name "*.java")
	$(JAVA) -cp "$(CLASSPATH)" TestRunner

analysis-test:
	PYTHONPATH=experiments $(PYTHON) -m unittest experiments.test_analyze_major_revision experiments.test_analyze_recommendation_diagnostics

run: build
	$(JAVA) -cp "$(CLASSPATH)" Main --input FAKENEWS_BASELINE --no-gui

study-plan: build
	$(JAVA) -cp "$(CLASSPATH)" experiment.StudyMain --study-class "$(STUDY_CLASS)" --base "$(STUDY_BASE)" $(ARGS)

study-run: build
	$(JAVA) -cp "$(CLASSPATH)" experiment.StudyMain --study-class "$(STUDY_CLASS)" --base "$(STUDY_BASE)" $(ARGS) --execute

study-progress:
	$(PYTHON) experiments/study_progress.py output/major-revision --total 4680

study-diagnostics: build
	$(JAVA) -cp "$(CLASSPATH)" experiment.StudyMain --study-class experiment.RecommendationDiagnosticsStudy \
		--base "$(STUDY_BASE)" $(ARGS) --execute

study-structural-correction: build
	$(JAVA) -cp "$(CLASSPATH)" experiment.StudyMain --study-class experiment.StructuralCorrectionStudy \
		--base "$(STUDY_BASE)" $(ARGS) --execute

analysis-processed:
	python3 experiments/analyze_final_study.py --processed analysis/final-study/run-metrics.csv $(ARGS)

analysis-raw:
	python3 experiments/analyze_final_study.py output/final-study $(ARGS)

analysis-major-revision:
	$(PYTHON) experiments/analyze_major_revision.py output/major-revision \
		--existing analysis/major-revision-existing/run-metrics.csv \
		--existing-periods analysis/major-revision-existing/period-metrics.csv.gz \
		--structural-root output/structural-correction \
		--output analysis/major-revision $(ARGS)

analysis-diagnostics:
	$(PYTHON) experiments/analyze_recommendation_diagnostics.py output/rq3-diagnostics \
		--existing analysis/major-revision-existing/run-metrics.csv \
		--revision analysis/major-revision/run-metrics.csv.gz \
		--output analysis/major-revision $(ARGS)

clean:
	rm -rf build dist output
