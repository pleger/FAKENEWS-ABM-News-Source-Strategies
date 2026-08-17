JAVAC ?= javac
JAVA ?= java
JAR ?= jar
VERSION ?= 0.1.0
APP_NAME := FAKENEWS-ABM
CLASSPATH := build/classes:lib/*
STUDY_CLASS ?= experiment.NewsSourceStrategiesStudy
STUDY_BASE ?= input/FAKENEWS_BASELINE_4_STRATEGIES.xlsx
DIST_DIR := dist/$(APP_NAME)-$(VERSION)
DIST_ZIP := dist/$(APP_NAME)-$(VERSION).zip

.PHONY: build test jar dist run study-plan study-run analysis-processed analysis-raw clean

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

run: build
	$(JAVA) -cp "$(CLASSPATH)" Main --input FAKENEWS_BASELINE --no-gui

study-plan: build
	$(JAVA) -cp "$(CLASSPATH)" experiment.StudyMain --study-class "$(STUDY_CLASS)" --base "$(STUDY_BASE)" $(ARGS)

study-run: build
	$(JAVA) -cp "$(CLASSPATH)" experiment.StudyMain --study-class "$(STUDY_CLASS)" --base "$(STUDY_BASE)" $(ARGS) --execute

analysis-processed:
	python3 experiments/analyze_final_study.py --processed analysis/final-study/run-metrics.csv $(ARGS)

analysis-raw:
	python3 experiments/analyze_final_study.py output/final-study $(ARGS)

clean:
	rm -rf build dist output
