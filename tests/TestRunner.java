import agent.SNSUserFactory;
import agent.NewsSource;
import agent.NewsSourceFactory;
import agent.NewsSourceSelectionStrategies;
import agent.SNSUser;
import endorsement.WomRecommendationEffect;
import endorsement.AttributesNewsSource;
import endorsement.Endorsement;
import endorsement.EndorsementEvalStrategies;
import endorsement.Endorsements;
import gui.Chart;
import gui.DataRepostChart;
import inputManager.Configuration;
import inputManager.Loader;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import reporter.Reporter;
import reporter.EndorsementData;
import reporter.FakeNewsPerSourceData;
import reporter.RepostsPerSourceData;
import scenarios.Scenario;
import scenarios.ScenarioFactory;
import simulation.Simulation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class TestRunner {
    private static final String PROBE_UNPROCESSED_FAKE_NEWS = "--probe-unprocessed-fake-news";
    private static final String PROBE_MISSING_LAST_FAKE_NEWS = "--probe-missing-last-fake-news";
    private static final String PROBE_INCOMPATIBLE_COPY_ALL_SCENARIO = "--probe-incompatible-copy-all-scenario";
    private static int passed = 0;

    public static void main(String[] args) {
        if (args.length == 1 && PROBE_UNPROCESSED_FAKE_NEWS.equals(args[0])) {
            probeUnprocessedFakeNewsPeriod();
            return;
        }
        if (args.length == 1 && PROBE_MISSING_LAST_FAKE_NEWS.equals(args[0])) {
            probeMissingLastFakeNewsState();
            return;
        }
        if (args.length == 1 && PROBE_INCOMPATIBLE_COPY_ALL_SCENARIO.equals(args[0])) {
            probeIncompatibleCopyAllScenario();
            return;
        }

        testEndorsementFormulaForHighBinaryLevel();
        testConfigurationAppliesPluralSavedEndorsementsKey();
        testConfigurationSupportsSavedFakeNews();
        testConfigurationSupportsWomRecommendationEffects();
        testConfigurationSupportsMemoryDecayAndWomScale();
        testConfigurationOutputOrderIsStable();
        testConfigurationRejectsInvalidValues();
        testConfigurationAcceptsExcelDisabledScenario();
        testConfigurationAcceptsInfiniteMemoryConstant();
        testConfigurationRejectsInvalidMemoryValues();
        testLargeConfigurationWithDetailedSavingIsAcceptedWithWarning();
        testLoaderReadsFakeNewsBaseline();
        testCustomizedScenarioCopiesSelectedAttributes();
        testCustomizedScenarioWithoutAttributesCopiesAll();
        testCopyAllScenarioRejectsIncompatibleTargetSchema();
        testScenarioReportPreviewDoesNotMutateSource();
        testProbabilitySelectionHandlesNonPositiveEvaluations();
        testRepeatedLoaderClearsScenarioCache();
        testReporterClearRemovesAccumulatedRows();
        testFakeNewsReportIncludesAllPeriodsAndSources();
        testReporterSplitsLargeEndorsementSheets();
        testEmptyWordOfMouthRecommendationsAreIgnored();
        testNewsSourceRejectsUnprocessedFakeNewsPeriod();
        testNewsSourceRejectsMissingLastFakeNewsState();
        testNewsSourceUsesGreatestProcessedPeriodAsLast();
        testRecommendationsUseStrongestEvaluationAndRememberSourceOnce();
        testWomRecommendationOutcomePolicies();
        testZeroEndorsementHasNeutralEvaluation();
        testExponentialMemoryDecay();
        testUserWithNoKnownSourcesCanStep();
        testMainWritesReporterWorkbookWithExpectedSheets();
        testAttributeReplacementLeavesOriginalUntouched();
        testEndorsementsFilterByMemoryAndSelectedSource();
        testEndorsementsInfiniteMemoryKeepsAllPeriods();
        testNewsSourceSelectionByMax();
        testFactoriesResetIdsAcrossCreations();
        testSimulationResetRestoresSourcesBeforeInitialEndorsements();
        testAggregateRepostSeriesCalculatesSampleDeviation();
        testRepostChartsUseStableOutputNames();
        testRepostsDataClonesInputArray();
        System.out.println("Tests passed: " + passed);
    }

    private static void testEndorsementFormulaForHighBinaryLevel() {
        Configuration.LEVELS = 2;
        double value = EndorsementEvalStrategies.BY_MAX(new Double[]{0.1, 0.9}, 4.0);
        assertEquals("binary high endorsement should equal snsUser weight", 4.0, value, 0.0001);
        passed++;
    }

    private static void testConfigurationAppliesPluralSavedEndorsementsKey() {
        HashMap<String, Double> conf = validConfiguration();
        conf.put("SAVED_ENDORSEMENTS", 1.0);

        Configuration.set(conf);

        assertTrue("plural SAVED_ENDORSEMENTS key should enable endorsement reporting",
                Configuration.SAVED_ENDORSEMENTS);
        passed++;
    }

    private static void testConfigurationSupportsSavedFakeNews() {
        HashMap<String, Double> absent = validConfiguration();
        Configuration.set(absent);
        assertTrue("SAVED_FAKENEWS should default to false", !Configuration.SAVED_FAKENEWS);

        HashMap<String, Double> enabled = validConfiguration();
        enabled.put("SAVED_FAKENEWS", 1.0);
        Configuration.set(enabled);
        assertTrue("SAVED_FAKENEWS=1 should enable fake-news reporting", Configuration.SAVED_FAKENEWS);
        assertEquals("configuration output should include SAVED_FAKENEWS",
                1.0, Configuration.toMap().get("SAVED_FAKENEWS"), 0.0001);

        assertThrows("SAVED_FAKENEWS should reject values other than 0 or 1", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("SAVED_FAKENEWS", 2.0);
            Configuration.set(invalid);
        });
        passed++;
    }

    private static void testConfigurationSupportsWomRecommendationEffects() {
        Configuration.set(validConfiguration());
        assertTrue("old workbooks should penalize fake recommendations by default",
                Configuration.WOM_FAKE_NEWS_EFFECT == WomRecommendationEffect.PENALIZE);
        assertTrue("old workbooks should reward true recommendations by default",
                Configuration.WOM_TRUE_NEWS_EFFECT == WomRecommendationEffect.REWARD);

        HashMap<String, Double> configured = validConfiguration();
        configured.put("WOM_FAKE_NEWS_EFFECT", 0.0);
        configured.put("WOM_TRUE_NEWS_EFFECT", -1.0);
        Configuration.set(configured);
        assertTrue("fake-news WOM policy should be configurable",
                Configuration.WOM_FAKE_NEWS_EFFECT == WomRecommendationEffect.IGNORE);
        assertTrue("true-news WOM policy should be configurable",
                Configuration.WOM_TRUE_NEWS_EFFECT == WomRecommendationEffect.PENALIZE);

        assertThrows("WOM effects should reject values outside -1, 0, and 1", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("WOM_FAKE_NEWS_EFFECT", 2.0);
            Configuration.set(invalid);
        });
        assertThrows("WOM effects should reject fractional values", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("WOM_TRUE_NEWS_EFFECT", 0.5);
            Configuration.set(invalid);
        });
        passed++;
    }

    private static void testConfigurationSupportsMemoryDecayAndWomScale() {
        HashMap<String, Double> legacy = validConfiguration();
        legacy.remove("MEMORY");
        Configuration.set(legacy);
        assertEquals("missing MEMORY should use the new 25-period default", 25, Configuration.MEMORY);
        assertEquals("old workbooks should disable exponential decay by default",
                Configuration.MEMORY_HALF_LIFE_DISABLED, Configuration.MEMORY_HALF_LIFE, 0.0001);
        assertEquals("old workbooks should retain the legacy WOM receiver scale",
                0.5, Configuration.WOM_RECEIVER_SCALE, 0.0001);

        HashMap<String, Double> configured = validConfiguration();
        configured.put("MEMORY_HALF_LIFE", 5.5);
        configured.put("WOM_RECEIVER_SCALE", 0.25);
        Configuration.set(configured);
        assertEquals("memory half-life should accept positive decimal periods",
                5.5, Configuration.MEMORY_HALF_LIFE, 0.0001);
        assertEquals("WOM receiver scale should be configurable",
                0.25, Configuration.WOM_RECEIVER_SCALE, 0.0001);
        assertEquals("configuration output should preserve memory half-life",
                5.5, Configuration.toMap().get("MEMORY_HALF_LIFE"), 0.0001);
        assertEquals("configuration output should preserve WOM receiver scale",
                0.25, Configuration.toMap().get("WOM_RECEIVER_SCALE"), 0.0001);

        assertThrows("zero memory half-life should be rejected", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("MEMORY_HALF_LIFE", 0.0);
            Configuration.set(invalid);
        });
        assertThrows("negative memory half-lives other than -1 should be rejected", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("MEMORY_HALF_LIFE", -2.0);
            Configuration.set(invalid);
        });
        assertThrows("negative WOM receiver scales should be rejected", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("WOM_RECEIVER_SCALE", -0.1);
            Configuration.set(invalid);
        });
        assertThrows("non-finite configuration values should be rejected", () -> {
            HashMap<String, Double> invalid = validConfiguration();
            invalid.put("WOM_RECEIVER_SCALE", Double.NaN);
            Configuration.set(invalid);
        });
        passed++;
    }

    private static void testConfigurationOutputOrderIsStable() {
        Configuration.set(validConfiguration());
        List<String> keys = new ArrayList<>(Configuration.toMap().keySet());

        assertEquals("first configuration key", "PERIODS", keys.get(0));
        assertEquals("second configuration key", "AGENTS", keys.get(1));
        assertEquals("last configuration key", "SAVED_FAKENEWS", keys.get(keys.size() - 1));
        passed++;
    }

    private static void testConfigurationRejectsInvalidValues() {
        assertThrows("LEVELS should only accept supported endorsement levels", () -> {
            HashMap<String, Double> conf = validConfiguration();
            conf.put("LEVELS", 4.0);
            Configuration.set(conf);
        });

        assertThrows("integer fields should reject decimals", () -> {
            HashMap<String, Double> conf = validConfiguration();
            conf.put("AGENTS", 2.5);
            Configuration.set(conf);
        });

        assertThrows("boolean fields should only accept 0 or 1", () -> {
            HashMap<String, Double> conf = validConfiguration();
            conf.put("GUI", 2.0);
            Configuration.set(conf);
        });

        assertThrows("FRIENDS should stay within probability range", () -> {
            HashMap<String, Double> conf = validConfiguration();
            conf.put("FRIENDS", 1.5);
            Configuration.set(conf);
        });

        passed++;
    }

    private static void testConfigurationAcceptsExcelDisabledScenario() {
        HashMap<String, Double> conf = validConfiguration();
        conf.put("SCENARIO", 0.0);

        Configuration.set(conf);

        assertEquals("Excel SCENARIO=0 should map to internal disabled scenario",
                Configuration.DISABLED, Configuration.SCENARIO);
        assertEquals("configuration dump should keep internal disabled scenario",
                Configuration.DISABLED, Configuration.toMap().get("SCENARIO"), 0.0001);
        passed++;
    }

    private static void testConfigurationAcceptsInfiniteMemoryConstant() {
        HashMap<String, Double> conf = validConfiguration();
        conf.put("MEMORY", (double) Configuration.MEMORY_INFINITE);

        Configuration.set(conf);

        assertEquals("MEMORY_INFINITE should be accepted as configured memory",
                Configuration.MEMORY_INFINITE, Configuration.MEMORY);
        assertEquals("configuration dump should preserve MEMORY_INFINITE",
                Configuration.MEMORY_INFINITE, Configuration.toMap().get("MEMORY"), 0.0001);
        passed++;
    }

    private static void testConfigurationRejectsInvalidMemoryValues() {
        assertThrows("MEMORY should reject negative values other than MEMORY_INFINITE", () -> {
            HashMap<String, Double> conf = validConfiguration();
            conf.put("MEMORY", (double) Configuration.MEMORY_INFINITE - 1);
            Configuration.set(conf);
        });

        assertThrows("MEMORY should reject decimal values", () -> {
            HashMap<String, Double> conf = validConfiguration();
            conf.put("MEMORY", 1.5);
            Configuration.set(conf);
        });

        passed++;
    }

    private static void testLargeConfigurationWithDetailedSavingIsAcceptedWithWarning() {
        HashMap<String, Double> conf = validConfiguration();
        conf.put("PERIODS", 1_000.0);
        conf.put("AGENTS", 1_000.0);
        conf.put("REPETITIONS", 1.0);
        conf.put("SAVED_ENDORSEMENTS", 1.0);

        Configuration.set(conf);

        assertEquals("large warning path should preserve configured periods", 1_000, Configuration.PERIODS);
        assertEquals("large warning path should preserve configured agents", 1_000, Configuration.AGENTS);
        assertEquals("large warning path should preserve configured repetitions", 1, Configuration.REPETITIONS);
        assertTrue("large warning path should preserve detailed saving flag", Configuration.SAVED_ENDORSEMENTS);
        passed++;
    }

    private static void testLoaderReadsFakeNewsBaseline() {
        Loader.load("FAKENEWS_BASELINE");
        assertEquals("configured agent count", 120, Configuration.AGENTS);
        Configuration.AGENTS = 3;
        assertEquals("source count", 4, NewsSourceFactory.createFromInput().size());
        assertEquals("agent factory override", 3, SNSUserFactory.createFromInput().size());
        assertEquals("attribute count", 13, Configuration.ATTRIBUTES_SOURCE);
        passed++;
    }

    private static void testCustomizedScenarioCopiesSelectedAttributes() {
        Loader.load("FAKENEWS_COORDINATED_PUSH");
        NewsSourceFactory.createFromInput();
        NewsSource fake = NewsSourceFactory.getNewsSource("FAKE_NEWS_SOURCE");
        NewsSource unknown = NewsSourceFactory.getNewsSource("UNKNOWN_MEDIA");
        Double[] fakeSensationalism = fake.getAttributes().getValues("SENSACIONALISMO DE LA NOTICIA");
        Double[] oldUnknownSensationalism = unknown.getAttributes().getValues("SENSACIONALISMO DE LA NOTICIA");

        Scenario scenario = ScenarioFactory.get(Configuration.SCENARIO);
        assertEquals("scenario should expose its chart marker period", 15, scenario.getStartPeriod());
        scenario.apply(15);
        Double[] newUnknownSensationalism = unknown.getAttributes().getValues("SENSACIONALISMO DE LA NOTICIA");

        assertTrue("scenario should change unknown-media sensationalism",
                oldUnknownSensationalism[0].doubleValue() != newUnknownSensationalism[0].doubleValue());
        assertEquals("scenario should copy fake-news low probability",
                fakeSensationalism[0], newUnknownSensationalism[0], 0.0001);
        assertEquals("scenario should copy fake-news high probability",
                fakeSensationalism[1], newUnknownSensationalism[1], 0.0001);
        passed++;
    }

    private static void testCustomizedScenarioWithoutAttributesCopiesAll() {
        File workbookFile = createCopyAllScenarioWorkbook();
        try {
            Loader.load(workbookFile.getAbsolutePath());
            NewsSourceFactory.createFromInput();
            NewsSource from = NewsSourceFactory.getNewsSource("FAKE_NEWS_SOURCE");
            NewsSource to = NewsSourceFactory.getNewsSource("UNKNOWN_MEDIA");
            Scenario scenario = ScenarioFactory.get(Configuration.SCENARIO);

            assertEquals("empty scenario attributes should be reported as ALL", "ALL",
                    scenario.getAttributeSelectionDescription());

            AttributesNewsSource preview = scenario.attributesAfterApplyingTo(to);
            for (String attribute : from.getAttributes().getNames()) {
                assertArrayEquals("copy-all preview should copy " + attribute,
                        from.getAttributes().getValues(attribute), preview.getValues(attribute), 0.0001);
            }

            scenario.apply(15);
            for (String attribute : from.getAttributes().getNames()) {
                assertArrayEquals("copy-all scenario should copy " + attribute,
                        from.getAttributes().getValues(attribute), to.getAttributes().getValues(attribute), 0.0001);
            }

            Reporter.clear();
            Reporter.write();
            File reportFile = newestWorkbookInOutputDirectory(new File(Configuration.OUTPUT_DIRECTORY));
            try (Workbook report = WorkbookFactory.create(reportFile)) {
                assertEquals("reported Scenario sheet should contain the configured period", 15,
                        (int) report.getSheet("Scenario").getRow(0).getCell(2).getNumericCellValue());
                Sheet changes = report.getSheet("ScenarioChanges");
                int markerColumn = 3 + from.getAttributes().getNames().length;
                assertEquals("scenario report should label its attribute-selection column",
                        "SCENARIO_ATTRIBUTES", changes.getRow(0).getCell(markerColumn).getStringCellValue());
                assertEquals("scenario report should register the copy-all shortcut",
                        "ALL", changes.getRow(1).getCell(markerColumn).getStringCellValue());
            }
        } catch (Exception exception) {
            throw new AssertionError("copy-all scenario should load, apply, and report successfully", exception);
        } finally {
            Loader.close();
            if (!workbookFile.delete()) {
                workbookFile.deleteOnExit();
            }
        }
        passed++;
    }

    private static File createCopyAllScenarioWorkbook() {
        try {
            File result = File.createTempFile("fakenews-copy-all-scenario-", ".xlsx");
            try (FileInputStream input = new FileInputStream("input/FAKENEWS_COORDINATED_PUSH.xlsx");
                 Workbook workbook = WorkbookFactory.create(input);
                 FileOutputStream output = new FileOutputStream(result)) {
                org.apache.poi.ss.usermodel.Row row = workbook.getSheet("Scenario").getRow(0);
                for (int column = row.getLastCellNum() - 1; column >= 3; --column) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(column);
                    if (cell != null) {
                        row.removeCell(cell);
                    }
                }
                row.createCell(3).setCellValue("   ");
                workbook.write(output);
            }
            return result;
        } catch (Exception exception) {
            throw new AssertionError("copy-all scenario test workbook should be created", exception);
        }
    }

    private static void testCopyAllScenarioRejectsIncompatibleTargetSchema() {
        assertFatalExit("copy-all should reject an incompatible target schema",
                PROBE_INCOMPATIBLE_COPY_ALL_SCENARIO, "attributes were not found in the target");
        passed++;
    }

    private static void probeIncompatibleCopyAllScenario() {
        Loader.load("FAKENEWS_BASELINE");
        NewsSourceFactory.createFromInput();
        NewsSource from = NewsSourceFactory.getNewsSource("FAKE_NEWS_SOURCE");
        NewsSource to = NewsSourceFactory.getNewsSource("UNKNOWN_MEDIA");

        ArrayList<String> targetNames = new ArrayList<>();
        ArrayList<Double[]> targetValues = new ArrayList<>();
        String[] currentNames = to.getAttributes().getNames();
        for (int i = 1; i < currentNames.length; ++i) {
            targetNames.add(currentNames[i]);
            targetValues.add(to.getAttributes().getValues(currentNames[i]));
        }
        to.setAttributes(new AttributesNewsSource(targetNames, targetValues));

        new Scenario(ScenarioFactory.CUSTOMIZED, 1, from.getName(), to.getName(), new ArrayList<>()).apply(1);
    }

    private static void testScenarioReportPreviewDoesNotMutateSource() {
        Loader.load("FAKENEWS_COORDINATED_PUSH");
        NewsSourceFactory.createFromInput();
        NewsSource fake = NewsSourceFactory.getNewsSource("FAKE_NEWS_SOURCE");
        NewsSource unknown = NewsSourceFactory.getNewsSource("UNKNOWN_MEDIA");
        String attribute = "SENSACIONALISMO DE LA NOTICIA";

        Double[] originalUnknown = unknown.getAttributes().getValues(attribute);
        Double[] fakeValues = fake.getAttributes().getValues(attribute);
        Scenario scenario = ScenarioFactory.get(Configuration.SCENARIO);
        AttributesNewsSource preview = scenario.attributesAfterApplyingTo(unknown);

        assertEquals("preview should copy fake-news low probability",
                fakeValues[0], preview.getValues(attribute)[0], 0.0001);
        assertEquals("preview should copy fake-news high probability",
                fakeValues[1], preview.getValues(attribute)[1], 0.0001);
        assertEquals("scenario preview should not mutate unknown-media low probability",
                originalUnknown[0], unknown.getAttributes().getValues(attribute)[0], 0.0001);
        assertEquals("scenario preview should not mutate unknown-media high probability",
                originalUnknown[1], unknown.getAttributes().getValues(attribute)[1], 0.0001);
        passed++;
    }

    private static void testProbabilitySelectionHandlesNonPositiveEvaluations() {
        LinkedHashMap<Integer, Double> evaluations = new LinkedHashMap<>();
        evaluations.put(10, -2.0);
        evaluations.put(11, 0.0);
        evaluations.put(12, 2.0);

        int selected = NewsSourceSelectionStrategies.BY_PROBABILITY(evaluations);

        assertTrue("probability selection should return one of the evaluated sources",
                evaluations.containsKey(selected));
        passed++;
    }

    private static void testRepeatedLoaderClearsScenarioCache() {
        Loader.load("FAKENEWS_COORDINATED_PUSH");
        Scenario first = ScenarioFactory.get(Configuration.SCENARIO);

        Loader.load("FAKENEWS_BASELINE");
        Loader.load("FAKENEWS_COORDINATED_PUSH");
        Scenario second = ScenarioFactory.get(Configuration.SCENARIO);

        assertTrue("loader should rebuild scenario cache for each workbook load", first != second);
        passed++;
    }

    private static void testReporterClearRemovesAccumulatedRows() {
        Configuration.SAVED_REPOSTS_PER_SOURCE = true;
        Reporter.clear();
        Reporter.addRepostsUniqueByNewsSourceData(1, 1, new int[]{1, 2});
        assertEquals("reporter should contain one unique-repost row", 1,
                Reporter.getUniqueRepostersPerSourceData().size());

        Reporter.clear();

        assertEquals("reporter clear should remove unique-repost rows", 0,
                Reporter.getUniqueRepostersPerSourceData().size());
        Configuration.SAVED_REPOSTS_PER_SOURCE = false;
        passed++;
    }

    private static void testFakeNewsReportIncludesAllPeriodsAndSources() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 1;
        Configuration.PERIODS = 2;
        Configuration.LEARNING_PERIODS = 100;
        Configuration.SAVED_FAKENEWS = true;
        Configuration.SAVED_REPOSTS_PER_SOURCE = false;
        Configuration.SAVED_ENDORSEMENTS = false;
        Configuration.SAVED_AGENT_DECISIONS = false;
        Configuration.SAVED_DETAILED_AGENT_DECISIONS = false;
        Configuration.SCENARIO = Configuration.DISABLED;
        Reporter.clear();

        List<NewsSource> sources = NewsSourceFactory.createFromInput();
        for (NewsSource source : sources) {
            boolean alwaysFake = source.getID() % 2 == 0;
            source.setAttributes(source.getAttributes().replace("CREDIBILIDAD DE LA FUENTE",
                    alwaysFake ? new Double[]{1.0, 0.0} : new Double[]{0.0, 1.0}));
        }

        Simulation simulation = new Simulation(SNSUserFactory.createFromInput(), sources, Configuration.PERIODS);
        for (NewsSource source : sources) {
            boolean alwaysFake = source.getID() % 2 == 0;
            source.setAttributes(source.getAttributes().replace("CREDIBILIDAD DE LA FUENTE",
                    alwaysFake ? new Double[]{1.0, 0.0} : new Double[]{0.0, 1.0}));
        }
        simulation.run();
        for (NewsSource source : sources) {
            boolean alwaysFake = source.getID() % 2 == 0;
            source.setAttributes(source.getAttributes().replace("CREDIBILIDAD DE LA FUENTE",
                    alwaysFake ? new Double[]{1.0, 0.0} : new Double[]{0.0, 1.0}));
        }
        simulation.run();

        List<FakeNewsPerSourceData> rows = Reporter.getFakeNewsPerSourceData();
        assertEquals("fake-news report should include all periods in both repetitions", 4, rows.size());
        assertEquals("first fake-news row period", 1, rows.get(0).period);
        assertEquals("second fake-news row period", 2, rows.get(1).period);
        assertEquals("next simulation should restart at period 1", 1, rows.get(2).period);
        assertTrue("repetitions should retain distinct simulation identifiers",
                rows.get(0).simulationId != rows.get(2).simulationId);
        assertEquals("fake-news row should include every source", sources.size(), rows.get(0).fakeNews.length);
        for (NewsSource source : sources) {
            assertTrue("fake-news state should preserve source-ID order",
                    rows.get(0).fakeNews[source.getID()] == (source.getID() % 2 == 0));
        }

        boolean original = rows.get(0).fakeNews[0];
        boolean[] input = new boolean[]{!original};
        FakeNewsPerSourceData cloned = new FakeNewsPerSourceData(1, 1, input);
        input[0] = original;
        assertTrue("fake-news report rows should clone their arrays", cloned.fakeNews[0] != input[0]);

        Reporter.write();
        File workbookFile = newestWorkbookInOutputDirectory(new File(Configuration.OUTPUT_DIRECTORY));
        try (Workbook workbook = WorkbookFactory.create(workbookFile)) {
            Sheet sheet = workbook.getSheet("FakeNewsPerSource");
            assertTrue("enabled fake-news report should create its worksheet", sheet != null);
            assertEquals("fake-news sheet should contain four period rows", 4, sheet.getLastRowNum());
            assertEquals("fake-news sheet first header", "Simulation", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("fake-news sheet second header", "Period", sheet.getRow(0).getCell(1).getStringCellValue());
            for (NewsSource source : sources) {
                assertEquals("fake-news sheet source header", source.getName(),
                        sheet.getRow(0).getCell(2 + source.getID()).getStringCellValue());
                assertEquals("fake-news sheet should use numeric 1/0 values",
                        source.getID() % 2 == 0 ? 1.0 : 0.0,
                        sheet.getRow(1).getCell(2 + source.getID()).getNumericCellValue(), 0.0001);
            }
        } catch (Exception exception) {
            throw new AssertionError("fake-news report workbook should be readable", exception);
        }

        Reporter.clear();
        assertEquals("reporter clear should remove fake-news rows", 0, Reporter.getFakeNewsPerSourceData().size());
        Configuration.SAVED_FAKENEWS = false;
        passed++;
    }

    private static void testReporterSplitsLargeEndorsementSheets() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.SAVED_ENDORSEMENTS = true;
        Configuration.SAVED_AGENT_DECISIONS = false;
        Configuration.SAVED_DETAILED_AGENT_DECISIONS = false;
        Configuration.SAVED_REPOSTS_PER_SOURCE = false;
        Configuration.SCENARIO = Configuration.DISABLED;
        Reporter.clear();

        ArrayList<reporter.EndorsementData> data = new ArrayList<>();
        data.add(new reporter.EndorsementData(1, 1, 1, "SOURCE", "A", 1.0));
        data.add(new reporter.EndorsementData(1, 1, 1, "SOURCE", "B", 2.0));
        data.add(new reporter.EndorsementData(1, 1, 1, "SOURCE", "C", 3.0));
        Reporter.addEndorsementData(data);

        System.setProperty("reporter.maxRowsPerSheet", "3");
        try {
            Reporter.write();
        } finally {
            System.clearProperty("reporter.maxRowsPerSheet");
        }

        File workbookFile = newestWorkbookInOutputDirectory(new File(Configuration.OUTPUT_DIRECTORY));
        try (Workbook workbook = WorkbookFactory.create(workbookFile)) {
            assertTrue("first endorsement sheet should exist", workbook.getSheet("Endorsements") != null);
            assertTrue("second endorsement sheet should exist after row rollover", workbook.getSheet("Endorsements_2") != null);
            assertEquals("first endorsement sheet should contain header plus two rows",
                    2, workbook.getSheet("Endorsements").getLastRowNum());
            assertEquals("second endorsement sheet should contain header plus one row",
                    1, workbook.getSheet("Endorsements_2").getLastRowNum());
        } catch (Exception ex) {
            throw new AssertionError("split endorsement workbook should be readable: " + ex);
        } finally {
            Reporter.clear();
        }

        passed++;
    }

    private static void testEmptyWordOfMouthRecommendationsAreIgnored() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 3;
        Configuration.CONTACTS = 0;
        Configuration.FRIENDS = 0;
        Configuration.WOM = true;
        Configuration.PERIODS = 1;
        Configuration.LEARNING_PERIODS = 100;
        Reporter.clear();

        Simulation simulation = new Simulation(SNSUserFactory.createFromInput(), NewsSourceFactory.createFromInput(),
                Configuration.PERIODS);
        simulation.run();

        passed++;
    }

    private static void testRecommendationsUseStrongestEvaluationAndRememberSourceOnce() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 4;
        Configuration.CONTACTS = 3;
        Configuration.FRIENDS = 1.0;

        List<SNSUser> users = SNSUserFactory.createFromInput();
        List<NewsSource> sources = NewsSourceFactory.createFromInput();
        SNSUser target = users.get(0);
        target.setFriends(users);
        target.setKnowNewsSources(new ArrayList<>());

        assertTrue("friends without current decisions should not create a recommendation",
                !target.receiveRecommendation(1));
        assertEquals("empty recommendations should not add endorsements", 0, target.getEndorsements().size());

        NewsSource traditional = NewsSourceFactory.getNewsSource(sources, "TRADITIONAL_MEDIA");
        NewsSource fake = NewsSourceFactory.getNewsSource(sources, "FAKE_NEWS_SOURCE");
        traditional.setAttributes(traditional.getAttributes().replace(
                "CREDIBILIDAD DE LA FUENTE", new Double[]{0.0, 1.0}));
        traditional.doStep(1);
        users.get(1).getEndorsements().add(new Endorsement(1, traditional, "QUALITY", 1.0));
        users.get(1).setCurrentEvaluation(10.0);
        users.get(2).getEndorsements().add(new Endorsement(1, traditional, "QUALITY", 1.0));
        users.get(2).setCurrentEvaluation(1.0);
        users.get(3).getEndorsements().add(new Endorsement(1, fake, "QUALITY", 1.0));
        users.get(3).setCurrentEvaluation(5.0);

        assertTrue("a current friend decision should create a recommendation", target.receiveRecommendation(1));
        ArrayList<EndorsementData> recommendationData = target.getEndorsementData(2);
        assertEquals("one WOM endorsement should be scheduled", 1, recommendationData.size());
        assertEquals("duplicate source recommendations should retain their strongest evaluation",
                traditional.getName(), recommendationData.get(0).newsSourceName);
        assertEquals("recommendation should use the WOM attribute", "WORD OF MOUTH",
                recommendationData.get(0).attribute);
        Configuration.WOM_RECEIVER_SCALE = 0.25;
        target.receiveRecommendation(1);
        ArrayList<EndorsementData> scaledRecommendationData = target.getEndorsementData(2);
        EndorsementData scaledRecommendation = scaledRecommendationData.get(scaledRecommendationData.size() - 1);
        assertEquals("recommendation should use the configured receiver scale",
                target.getAttribute().getValue("WORD OF MOUTH") * Configuration.WOM_RECEIVER_SCALE,
                Math.abs(scaledRecommendation.value), 0.0001);
        assertEquals("default recommendation should retain half of the user's WOM weight",
                target.getAttribute().getValue("WORD OF MOUTH") / 2.0,
                recommendationData.get(0).value, 0.0001);
        assertEquals("a newly recommended source should be remembered", 1, target.getKnownNewsSourceCount());

        assertTrue("the same source can be recommended again", target.receiveRecommendation(1));
        assertEquals("an already known source should not be added twice", 1, target.getKnownNewsSourceCount());
        passed++;
    }

    private static void testWomRecommendationOutcomePolicies() {
        assertRecommendationEffect(true, WomRecommendationEffect.PENALIZE, 1, -1.0);
        assertRecommendationEffect(true, WomRecommendationEffect.IGNORE, 0, 0.0);
        assertRecommendationEffect(false, WomRecommendationEffect.REWARD, 1, 1.0);
        assertRecommendationEffect(false, WomRecommendationEffect.IGNORE, 0, 0.0);
        passed++;
    }

    private static void assertRecommendationEffect(boolean fakeNews, WomRecommendationEffect effect,
                                                   int expectedEndorsements, double expectedSign) {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 2;
        Configuration.CONTACTS = 1;
        Configuration.FRIENDS = 1.0;
        List<SNSUser> users = SNSUserFactory.createFromInput();
        List<NewsSource> sources = NewsSourceFactory.createFromInput();
        SNSUser target = users.get(0);
        SNSUser friend = users.get(1);
        target.setFriends(users);
        target.setKnowNewsSources(new ArrayList<>());

        NewsSource source = sources.get(0);
        Double[] credibility = fakeNews ? new Double[]{1.0, 0.0} : new Double[]{0.0, 1.0};
        source.setAttributes(source.getAttributes().replace("CREDIBILIDAD DE LA FUENTE", credibility));
        source.doStep(1);
        friend.getEndorsements().add(new Endorsement(1, source, "QUALITY", 1.0));
        friend.setCurrentEvaluation(1.0);
        if (fakeNews) {
            Configuration.WOM_FAKE_NEWS_EFFECT = effect;
        } else {
            Configuration.WOM_TRUE_NEWS_EFFECT = effect;
        }

        assertTrue("recommendation should be processed even when its valuation is ignored",
                target.receiveRecommendation(1));
        ArrayList<EndorsementData> recommendations = target.getEndorsementData(2);
        assertEquals("WOM policy should control endorsement creation", expectedEndorsements,
                recommendations.size());
        assertEquals("ignored recommendation should still reveal its source", 1,
                target.getKnownNewsSourceCount());
        if (!recommendations.isEmpty()) {
            assertTrue("WOM policy should control endorsement direction",
                    Math.signum(recommendations.get(0).value) == expectedSign);
        }
    }

    private static void testZeroEndorsementHasNeutralEvaluation() {
        try {
            Method evaluate = agent.Interaction.class.getDeclaredMethod("evaluateNewsSource", double[].class);
            evaluate.setAccessible(true);
            double result = (double) evaluate.invoke(null, (Object) new double[]{0.0});
            assertEquals("zero endorsement should add neither reward nor penalty", 0.0, result, 0.0001);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("zero endorsement evaluation should be testable: " + exception);
        }
        passed++;
    }

    private static void testExponentialMemoryDecay() {
        try {
            Method evaluate = agent.Interaction.class.getDeclaredMethod(
                    "evaluateNewsSource", Endorsements.class, int.class);
            evaluate.setAccessible(true);
            Endorsements endorsements = new Endorsements();
            endorsements.add(new Endorsement(1, null, "QUALITY", 1.0));
            endorsements.add(new Endorsement(3, null, "QUALITY", 1.0));

            Configuration.BASE = 1.2;
            Configuration.MEMORY_HALF_LIFE = 2.0;
            double decayed = (double) evaluate.invoke(null, endorsements, 3);
            assertEquals("one-half-life-old evidence should retain half its transformed contribution",
                    1.8, decayed, 0.0001);

            Configuration.MEMORY_HALF_LIFE = Configuration.MEMORY_HALF_LIFE_DISABLED;
            double disabled = (double) evaluate.invoke(null, endorsements, 3);
            assertEquals("disabled decay should preserve full contributions",
                    2.4, disabled, 0.0001);

            Endorsements initial = new Endorsements();
            initial.add(new Endorsement(-1, null, "QUALITY", 1.0));
            Configuration.MEMORY_HALF_LIFE = 2.0;
            double firstPeriod = (double) evaluate.invoke(null, initial, 1);
            assertEquals("initial evidence should have full weight at the first decision",
                    1.2, firstPeriod, 0.0001);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("exponential memory evaluation should be testable: " + exception);
        } finally {
            Configuration.MEMORY_HALF_LIFE = Configuration.MEMORY_HALF_LIFE_DISABLED;
        }
        passed++;
    }

    private static void testNewsSourceRejectsUnprocessedFakeNewsPeriod() {
        assertFatalExit("unprocessed fake-news periods should stop the simulation",
                PROBE_UNPROCESSED_FAKE_NEWS, "has no fake-news state for period 1");
        passed++;
    }

    private static void testNewsSourceRejectsMissingLastFakeNewsState() {
        assertFatalExit("missing last-publication state should stop the simulation",
                PROBE_MISSING_LAST_FAKE_NEWS, "has no fake-news state; doStep(period) must run");
        passed++;
    }

    private static void probeUnprocessedFakeNewsPeriod() {
        Loader.load("FAKENEWS_BASELINE");
        NewsSourceFactory.createFromInput().get(0).isFakeNews(1);
    }

    private static void probeMissingLastFakeNewsState() {
        Loader.load("FAKENEWS_BASELINE");
        NewsSourceFactory.createFromInput().get(0).wasLastFakeNews();
    }

    private static void testNewsSourceUsesGreatestProcessedPeriodAsLast() {
        Loader.load("FAKENEWS_BASELINE");
        NewsSource source = NewsSourceFactory.createFromInput().get(0);

        source.setAttributes(source.getAttributes().replace(
                "CREDIBILIDAD DE LA FUENTE", new Double[]{0.0, 1.0}));
        source.doStep(2);
        source.setAttributes(source.getAttributes().replace(
                "CREDIBILIDAD DE LA FUENTE", new Double[]{1.0, 0.0}));
        source.doStep(5);

        assertTrue("last publication should come from the greatest processed period",
                source.wasLastFakeNews());
        passed++;
    }

    private static void testUserWithNoKnownSourcesCanStep() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 1;
        SNSUser user = SNSUserFactory.createFromInput().get(0);
        user.setKnowNewsSources(new ArrayList<>());
        user.doStep(1);

        assertTrue("user with no known sources should not select a source", user.getLastSelectMarked(1) == null);
        passed++;
    }

    private static void testMainWritesReporterWorkbookWithExpectedSheets() {
        long startedAt = System.currentTimeMillis();
        String output = runMain("--input", "FAKENEWS_BASELINE", "--periods", "2", "--agents", "3",
                "--repetitions", "0", "--learning-periods", "0", "--no-gui");
        assertTrue("main process should finish and write report", output.contains("Reporter: File saved."));

        File workbookFile = newestWorkbookInNewestOutputDirectory("FAKENEWS_BASELINE", startedAt);
        try (Workbook workbook = WorkbookFactory.create(workbookFile)) {
            assertTrue("output workbook should contain Configuration", workbook.getSheet("Configuration") != null);
            assertTrue("output workbook should contain copied NewsSources", workbook.getSheet("NewsSources") != null);
            assertTrue("output workbook should contain Results", workbook.getSheet("Results") != null);
            assertTrue("output workbook should contain DetailedResult", workbook.getSheet("DetailedResult") != null);
            assertTrue("output workbook should contain Endorsements", workbook.getSheet("Endorsements") != null);
            Sheet reposts = workbook.getSheet("RepostsPerSource");
            assertTrue("output workbook should contain RepostsPerSource", reposts != null);
            assertTrue("repost sheet should include data rows", reposts.getLastRowNum() > 0);
        } catch (Exception ex) {
            throw new AssertionError("output workbook should be readable: " + ex);
        }

        passed++;
    }

    private static void testAttributeReplacementLeavesOriginalUntouched() {
        ArrayList<String> names = new ArrayList<>();
        names.add("A");
        names.add("B");
        ArrayList<Double[]> values = new ArrayList<>();
        values.add(new Double[]{0.2, 0.8});
        values.add(new Double[]{0.4, 0.6});
        AttributesNewsSource attributes = new AttributesNewsSource(names, values);

        AttributesNewsSource replaced = attributes.replace("A", new Double[]{0.9, 0.1});

        assertEquals("replacement should update copied low value", 0.9, replaced.getValues("A")[0], 0.0001);
        assertEquals("original low value should remain unchanged", 0.2, attributes.getValues("A")[0], 0.0001);
        assertEquals("unreplaced value should be preserved", 0.4, replaced.getValues("B")[0], 0.0001);
        passed++;
    }

    private static void testEndorsementsFilterByMemoryAndSelectedSource() {
        Loader.load("FAKENEWS_BASELINE");
        NewsSourceFactory.createFromInput();
        NewsSource traditional = NewsSourceFactory.getNewsSource("TRADITIONAL_MEDIA");
        NewsSource fake = NewsSourceFactory.getNewsSource("FAKE_NEWS_SOURCE");
        Endorsements endorsements = new Endorsements();
        endorsements.add(new Endorsement(1, traditional, "QUALITY", 1.0));
        endorsements.add(new Endorsement(3, fake, "QUALITY", 2.0));
        endorsements.add(new Endorsement(3, fake, "WORD OF MOUTH", 3.0));

        Configuration.MEMORY = 1;
        Endorsements recent = endorsements.filterByMemory(3);

        assertEquals("memory filter should keep period 3 endorsements only", 2, recent.size());
        assertEquals("selected source should ignore WOM endorsement", "FAKE_NEWS_SOURCE",
                endorsements.getSelectedNewsSource(3).getName());
        passed++;
    }

    private static void testEndorsementsInfiniteMemoryKeepsAllPeriods() {
        Endorsements endorsements = new Endorsements();
        endorsements.add(new Endorsement(1, null, "QUALITY", 1.0));
        endorsements.add(new Endorsement(2, null, "QUALITY", 2.0));
        endorsements.add(new Endorsement(3, null, "QUALITY", 3.0));

        Configuration.MEMORY = Configuration.MEMORY_INFINITE;
        Endorsements all = endorsements.filterByMemory(3);

        assertEquals("infinite memory should keep all endorsement periods", 3, all.size());
        passed++;
    }

    private static void testNewsSourceSelectionByMax() {
        LinkedHashMap<Integer, Double> evaluations = new LinkedHashMap<>();
        evaluations.put(1, -3.0);
        evaluations.put(2, 5.0);
        evaluations.put(3, 4.0);

        assertEquals("BY_MAX should select highest evaluation", 2,
                NewsSourceSelectionStrategies.BY_MAX(evaluations));
        passed++;
    }

    private static void testFactoriesResetIdsAcrossCreations() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 2;
        List<SNSUser> firstUsers = SNSUserFactory.createFromInput();
        List<SNSUser> secondUsers = SNSUserFactory.createFromInput();
        List<NewsSource> firstSources = NewsSourceFactory.createFromInput();
        List<NewsSource> secondSources = NewsSourceFactory.createFromInput();

        assertEquals("first user factory run should start at id 0", 0, firstUsers.get(0).getID());
        assertEquals("second user factory run should reset to id 0", 0, secondUsers.get(0).getID());
        assertEquals("first source factory run should start at id 0", 0, firstSources.get(0).getID());
        assertEquals("second source factory run should reset to id 0", 0, secondSources.get(0).getID());
        passed++;
    }

    private static void testSimulationResetRestoresSourcesBeforeInitialEndorsements() {
        Loader.load("FAKENEWS_BASELINE");
        Configuration.AGENTS = 1;
        Configuration.CONTACTS = 0;
        Configuration.FRIENDS = 0.0;
        Configuration.SOURCE_REACH = false;
        Configuration.SCENARIO = Configuration.DISABLED;

        List<NewsSource> sources = NewsSourceFactory.createFromInput();
        List<SNSUser> users = SNSUserFactory.createFromInput();
        Simulation simulation = new Simulation(users, sources, 1);
        NewsSource target = NewsSourceFactory.getNewsSource("FAKE_NEWS_SOURCE");
        String credibility = "CREDIBILIDAD DE LA FUENTE";
        Double[] originalCredibility = target.getAttributes().getValues(credibility).clone();
        double originalInitialEndorsement = findEndorsementValue(
                users.get(0).getEndorsementData(-1), target.getName(), credibility);

        target.setAttributes(target.getAttributes().replace(credibility, new Double[]{0.0, 1.0}));
        target.addSNSUsers(users.get(0).getID());
        target.doStep(1);
        simulation.reinit();

        assertArrayEquals("reset should restore source input attributes", originalCredibility,
                target.getAttributes().getValues(credibility), 0.0001);
        assertEquals("initial endorsements should use restored source attributes",
                originalInitialEndorsement,
                findEndorsementValue(users.get(0).getEndorsementData(-1), target.getName(), credibility),
                0.0001);
        assertEquals("reset should clear cumulative unique reposters", 0, target.getUniqueReposters());
        assertTrue("reset should clear fake-news publication history", target.toString().contains("fakenews=''"));
        passed++;
    }

    private static void testAggregateRepostSeriesCalculatesSampleDeviation() {
        Loader.load("FAKENEWS_BASELINE");
        List<NewsSource> sources = NewsSourceFactory.createFromInput();
        List<RepostsPerSourceData> rows = new ArrayList<>();
        rows.add(new RepostsPerSourceData(1, 10, new int[]{1, 2, 3, 4}));
        rows.add(new RepostsPerSourceData(2, 10, new int[]{3, 4, 5, 6}));
        rows.add(new RepostsPerSourceData(1, 11, new int[]{2, 4, 6, 8}));
        rows.add(new RepostsPerSourceData(2, 11, new int[]{4, 6, 8, 10}));

        DataRepostChart[] aggregate = DataRepostChart.createAggregate(sources, rows);
        assertEquals("aggregate should contain one series per source", sources.size(), aggregate.length);
        assertEquals("aggregate should preserve periods", 10, aggregate[0].getXData().get(0));
        assertEquals("aggregate should calculate the arithmetic mean", 2.0,
                aggregate[0].getYData().get(0), 0.0001);
        assertEquals("aggregate should calculate sample standard deviation", Math.sqrt(2.0),
                aggregate[0].getDeviationData().get(0), 0.0001);

        List<RepostsPerSourceData> oneRun = new ArrayList<>();
        oneRun.add(new RepostsPerSourceData(1, 10, new int[]{1, 2, 3, 4}));
        assertEquals("one-run aggregate deviation should be zero", 0.0,
                DataRepostChart.createAggregate(sources, oneRun)[0].getDeviationData().get(0), 0.0001);

        List<RepostsPerSourceData> incomplete = new ArrayList<>(rows);
        incomplete.remove(incomplete.size() - 1);
        assertIllegalState("aggregate should reject periods missing a simulation",
                () -> DataRepostChart.createAggregate(sources, incomplete));
        passed++;
    }

    private static void testRepostChartsUseStableOutputNames() {
        Loader.load("FAKENEWS_BASELINE");
        List<NewsSource> sources = NewsSourceFactory.createFromInput();
        Configuration.SAVED_REPOSTS_PER_SOURCE = true;
        Configuration.REPETITIONS = 1;
        Configuration.SCENARIO = Configuration.DISABLED;
        Reporter.clear();
        Reporter.addRepostsByNewsSourceData(101, 1, new int[]{1, 2, 3, 4});
        Reporter.addRepostsByNewsSourceData(102, 1, new int[]{3, 4, 5, 6});
        Reporter.addRepostsUniqueByNewsSourceData(101, 1, new int[]{2, 3, 4, 5});
        Reporter.addRepostsUniqueByNewsSourceData(102, 1, new int[]{4, 5, 6, 7});
        Simulation.ID = 101;

        try {
            Path output = Files.createTempDirectory("fakenews-chart-test-");
            Configuration.OUTPUT_DIRECTORY = output.toString();
            Chart.displayReposts(sources);
            Chart.displayAggregateReposts(sources);

            assertNonemptyFile(output.resolve("Simulation_101_RepostsPerPeriod.png"));
            assertNonemptyFile(output.resolve("Simulation_101_UniqueReposters.png"));
            assertNonemptyFile(output.resolve("Aggregate_RepostsPerPeriod_MeanSD.png"));
            assertNonemptyFile(output.resolve("Aggregate_UniqueReposters_MeanSD.png"));
        } catch (Exception exception) {
            throw new AssertionError("repost charts should be generated with stable filenames", exception);
        } finally {
            Reporter.clear();
            Configuration.SAVED_REPOSTS_PER_SOURCE = false;
        }
        passed++;
    }

    private static void testRepostsDataClonesInputArray() {
        int[] reposts = new int[]{1, 2, 3};
        RepostsPerSourceData row = new RepostsPerSourceData(1, 2, reposts);
        reposts[0] = 99;

        assertEquals("repost row should clone input array", 1, row.reposts[0]);
        passed++;
    }

    private static void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(String message, Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message + ": expected IllegalArgumentException");
    }

    private static void assertIllegalState(String message, Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message + ": expected IllegalStateException");
    }

    private static double findEndorsementValue(List<EndorsementData> rows, String source, String attribute) {
        for (EndorsementData row : rows) {
            if (row.newsSourceName.equals(source) && row.attribute.equals(attribute)) {
                return row.value;
            }
        }
        throw new AssertionError("endorsement not found for " + source + " / " + attribute);
    }

    private static void assertNonemptyFile(Path path) {
        try {
            assertTrue("expected generated file " + path, Files.isRegularFile(path) && Files.size(path) > 0);
        } catch (Exception exception) {
            throw new AssertionError("could not inspect generated file " + path, exception);
        }
    }

    private static void assertFatalExit(String message, String probeArgument, String expectedOutput) {
        try {
            Process process = new ProcessBuilder(
                    "java", "-cp", "build/classes:lib/*", "TestRunner", probeArgument)
                    .directory(new File("."))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit == 0 || !output.contains(expectedOutput)) {
                throw new AssertionError(message + ": expected nonzero exit containing '"
                        + expectedOutput + "' but got exit " + exit + "\n" + output);
            }
        } catch (AssertionError error) {
            throw error;
        } catch (Exception exception) {
            throw new AssertionError(message + ": could not run fatal-invariant probe", exception);
        }
    }

    private static void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(String message, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(String message, double expected, double actual, double epsilon) {
        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertArrayEquals(String message, Double[] expected, Double[] actual, double epsilon) {
        assertEquals(message + " length", expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) {
            assertEquals(message + " at index " + i, expected[i], actual[i], epsilon);
        }
    }

    private static String runMain(String... args) {
        ArrayList<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add("build/classes:lib/*");
        command.add("Main");
        for (String arg : args) {
            command.add(arg);
        }

        try {
            Process process = new ProcessBuilder(command)
                    .directory(new File("."))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new AssertionError("Main process failed with exit " + exit + "\n" + output);
            }
            return output;
        } catch (Exception ex) {
            throw new AssertionError("Main process should run successfully: " + ex);
        }
    }

    private static File newestWorkbookInNewestOutputDirectory(String prefix, long startedAt) {
        File outputRoot = new File("output");
        File[] directories = outputRoot.listFiles((dir, name) -> name.startsWith(prefix + "_"));
        assertTrue("output root should contain matching report directory", directories != null && directories.length > 0);

        File newestDirectory = null;
        for (File directory : directories) {
            if (directory.isDirectory() && directory.lastModified() >= startedAt &&
                    (newestDirectory == null || directory.lastModified() > newestDirectory.lastModified())) {
                newestDirectory = directory;
            }
        }
        assertTrue("main process should create a fresh matching report directory", newestDirectory != null);
        return newestWorkbookInOutputDirectory(newestDirectory);
    }

    private static File newestWorkbookInOutputDirectory(File outputDirectory) {
        File[] files = outputDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
        assertTrue("output directory should contain an xlsx report", files != null && files.length > 0);

        File newest = files[0];
        for (File file : files) {
            if (file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return newest;
    }

    private static HashMap<String, Double> validConfiguration() {
        HashMap<String, Double> conf = new HashMap<>();
        conf.put("PERIODS", 30.0);
        conf.put("AGENTS", 10.0);
        conf.put("CONTACTS", 17.0);
        conf.put("FRIENDS", 0.7);
        conf.put("LEVELS", 2.0);
        conf.put("REPETITIONS", 0.0);
        conf.put("GUI", 0.0);
        conf.put("BASE", 1.2);
        conf.put("MEMORY", (double) Configuration.MEMORY_INFINITE);
        conf.put("SOURCE_REACH", 0.0);
        conf.put("WOM", 0.0);
        conf.put("SCENARIO", -1.0);
        conf.put("LEARNING_PERIODS", 100.0);
        conf.put("SAVED_ENDORSEMENTS", 0.0);
        conf.put("SAVED_REPOSTS_PER_SOURCE", 0.0);
        conf.put("SAVED_DETAILED_AGENT_DECISIONS", 0.0);
        conf.put("SAVED_AGENT_DECISIONS", 0.0);
        conf.put("COMPRESSED_RESULTS", 0.0);
        return conf;
    }
}
