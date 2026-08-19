package reporter;

import agent.NewsSource;
import agent.NewsSourceFactory;
import inputManager.Configuration;
import inputManager.Loader;
import utils.Console;
import utils.Error;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import endorsement.AttributesNewsSource;
import endorsement.WomRecommendationEffect;
import scenarios.Scenario;
import scenarios.ScenarioFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Collects optional in-memory simulation results and writes a consolidated Excel workbook containing
 * normalized configuration, copied inputs, decisions, endorsements, reposts, and scenario previews.
 */
public class Reporter {
    private static final int EXCEL_MAX_ROWS = 1_048_576;
    private static final String MAX_ROWS_PROPERTY = "reporter.maxRowsPerSheet";

    private static final List<AgentDecisionData> agentDecisionData = new ArrayList<>();
    private static final List<DetailedAgentDecisionData> detailedAgentDecisionData = new ArrayList<>();
    private static final List<EndorsementData> endorsData = new ArrayList<>();
    private static final List<RepostsPerSourceData> repostsPerNewsSourceData = new ArrayList<>();
    private static final List<UniqueRepostersPerSourceData> repostsUniquePerNewsSourceData = new ArrayList<>();
    private static final List<FakeNewsPerSourceData> fakeNewsPerSourceData = new ArrayList<>();
    private static final Map<String, WomDiagnosticData> womDiagnosticData = new LinkedHashMap<>();

    /**
     * Builds the final workbook from loaded inputs and accumulated records, writes it to the current
     * output directory, and optionally compresses that directory.
     */
    public static void write() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Console.info("Reporter: Adding sheets");

            writeConfiguration(workbook.createSheet("Configuration"));
            addSheet(workbook, Loader.getNewsSources());
            addSheet(workbook, Loader.getSNSUsers());
            addSheet(workbook, Loader.getSourceReach());
            if (Loader.getSourceBehavior() != null) addSheet(workbook, Loader.getSourceBehavior());
            if (Loader.getStrategies() != null) addSheet(workbook, Loader.getStrategies());
            if (Configuration.SCENARIO != Configuration.DISABLED) addScenarioSheet(workbook);


            writeRepostsPerNewsSource(workbook, "RepostsPerSource", repostsPerNewsSourceData);
            writeRepostsPerNewsSource(workbook, "UniqueRepostersPerSource", repostsUniquePerNewsSourceData);
            if (Configuration.SAVED_FAKENEWS) writeFakeNewsPerSource(workbook);
            if (Configuration.SAVED_WOM_DIAGNOSTICS) writeWomDiagnostics(workbook);
            writeAgentDecision(workbook);
            writeDetailedAgentDecision(workbook);
            writeEndorsements(workbook);
            writeScenarioChanges(workbook.createSheet("ScenarioChanges"));

            Console.info("Reporter: Writing to the disk");
            writeDisk(workbook);
        } catch (IOException ex) {
            Error.trigger("Reporter.write: output workbook could not be closed\n.ERROR: " + ex, ex);
        }
    }

    /**
     * Copies the input scenario definition and replaces its scheduled period with the effective
     * runtime value, including any command-line override, so the report remains self-describing.
     *
     * @param workbook report workbook receiving the Scenario sheet
     */
    private static void addScenarioSheet(XSSFWorkbook workbook) {
        addSheet(workbook, Loader.getScenario());
        Sheet outputScenario = workbook.getSheet("Scenario");
        Scenario scenario = ScenarioFactory.get(Configuration.SCENARIO);
        boolean headerBased = "FROM".equalsIgnoreCase(
                outputScenario.getRow(0).getCell(0).toString().trim());
        int definitionRow = headerBased ? 1 : 0;
        outputScenario.getRow(definitionRow).getCell(2).setCellValue(scenario.getStartPeriod());
        if (headerBased) {
            outputScenario.getRow(definitionRow).getCell(3).setCellValue(scenario.getEndPeriod());
        }
    }

    /** Clears all accumulated rows before a new top-level CLI execution begins. */
    public static void clear() {
        agentDecisionData.clear();
        detailedAgentDecisionData.clear();
        endorsData.clear();
        repostsPerNewsSourceData.clear();
        repostsUniquePerNewsSourceData.clear();
        fakeNewsPerSourceData.clear();
        womDiagnosticData.clear();
    }

    private static WomDiagnosticData womDiagnostic(int simulationId, int period) {
        String key = simulationId + ":" + period;
        return womDiagnosticData.computeIfAbsent(key, ignored -> new WomDiagnosticData(simulationId, period));
    }

    public static void ensureWomDiagnosticPeriod(int simulationId, int period) {
        if (Configuration.SAVED_WOM_DIAGNOSTICS) womDiagnostic(simulationId, period);
    }

    public static void recordWomSelection(int simulationId, int period, int contactRecommendations,
                                          int duplicates, boolean exactMaximumTie, boolean newlyDiscovered) {
        if (!Configuration.SAVED_WOM_DIAGNOSTICS) return;
        WomDiagnosticData row = womDiagnostic(simulationId, period);
        ++row.receiversWithRecommendation;
        row.contactRecommendations += contactRecommendations;
        row.duplicateSourceRecommendations += duplicates;
        if (exactMaximumTie) ++row.exactMaximumTies;
        if (newlyDiscovered) ++row.newSourceDiscoveries;
    }

    public static void recordWomUncovered(int simulationId, int period) {
        if (Configuration.SAVED_WOM_DIAGNOSTICS) ++womDiagnostic(simulationId, period).labelsUncovered;
    }

    public static void recordWomLabel(int simulationId, int period, boolean actuallyFalse,
                                      boolean observedFalse, WomRecommendationEffect effect) {
        if (!Configuration.SAVED_WOM_DIAGNOSTICS) return;
        WomDiagnosticData row = womDiagnostic(simulationId, period);
        ++row.labelsCovered;
        if (actuallyFalse && observedFalse) ++row.truePositiveLabels;
        else if (actuallyFalse) ++row.falseNegativeLabels;
        else if (observedFalse) ++row.falsePositiveLabels;
        else ++row.trueNegativeLabels;
        switch (effect) {
            case REWARD: ++row.rewardedRecommendations; break;
            case PENALIZE: ++row.penalizedRecommendations; break;
            case IGNORE: ++row.ignoredRecommendations; break;
            default: throw new IllegalStateException("Unknown WOM recommendation effect: " + effect);
        }
    }

    public static void recordWomScheduled(int simulationId, int period) {
        if (Configuration.SAVED_WOM_DIAGNOSTICS) ++womDiagnostic(simulationId, period).endorsementsScheduled;
    }

    public static void recordWomDelivered(int simulationId, int period) {
        if (Configuration.SAVED_WOM_DIAGNOSTICS) ++womDiagnostic(simulationId, period).endorsementsDelivered;
    }

    public static List<WomDiagnosticData> getWomDiagnosticData() {
        ArrayList<WomDiagnosticData> rows = new ArrayList<>(womDiagnosticData.values());
        rows.sort((left, right) -> {
            int simulation = Integer.compare(left.simulationId, right.simulationId);
            return simulation != 0 ? simulation : Integer.compare(left.period, right.period);
        });
        return rows;
    }

    /**
     * Writes a non-mutating preview of each source after the configured intervention.
     *
     * @param scenarios output ScenarioChanges sheet
     */
    private static void writeScenarioChanges(XSSFSheet scenarios) {
        boolean enabled = Configuration.SCENARIO != Configuration.DISABLED;
        Console.info("Reporter: Information of Scenario Changes: " + enabled);

        if (enabled) {
            Scenario scenario = ScenarioFactory.get(Configuration.SCENARIO);
            ArrayList<NewsSource> newsSources = NewsSourceFactory.getNewsSources();

            Row headRow = scenarios.createRow(0);
            headRow.createCell(0).setCellValue("SOURCE_NAME");
            headRow.createCell(1).setCellValue("SOURCE_ID");
            headRow.createCell(2).setCellValue("SOURCE_REACH");

            int column = 3;
            for (String attribute : newsSources.get(0).getAttributes().getNames()) {
                headRow.createCell(column).setCellValue(attribute);
                ++column;
            }
            headRow.createCell(column).setCellValue("SCENARIO_ATTRIBUTES");
            headRow.createCell(column + 1).setCellValue("SCENARIO_STRATEGIES");
            headRow.createCell(column + 2).setCellValue("START_PERIOD");
            headRow.createCell(column + 3).setCellValue("END_PERIOD");

            int rowIndex = 1;
            for (NewsSource mk : newsSources) {
                Row dataRow = scenarios.createRow(rowIndex);
                dataRow.createCell(0).setCellValue(mk.getName());
                dataRow.createCell(1).setCellValue(mk.getID());
                dataRow.createCell(2).setCellValue(mk.getReach());

                column = 3;
                AttributesNewsSource attributes = scenario.attributesAfterApplyingTo(mk);
                for (String attributeName : attributes.getNames()) {
                    Double[] vals = attributes.getValues(attributeName);
                    dataRow.createCell(column).setCellValue(Arrays.toString(vals));
                    ++column;
                }
                dataRow.createCell(column).setCellValue(scenario.getAttributeSelectionDescription());
                dataRow.createCell(column + 1).setCellValue(scenario.getStrategySelectionDescription());
                dataRow.createCell(column + 2).setCellValue(scenario.getStartPeriod());
                dataRow.createCell(column + 3).setCellValue(scenario.getEndPeriod());
                ++rowIndex;
            }

            setReadableColumnWidths(scenarios, 7 + newsSources.get(0).getAttributes().getNames().length);
        }
    }

    /**
     * Accumulates endorsement rows only when their detailed-output option is enabled.
     *
     * @param endors current-period rows to register
     */
    public static void addEndorsementData(ArrayList<EndorsementData> endors) {
        if (Configuration.SAVED_ENDORSEMENTS) endorsData.addAll(endors);
    }

    /**
     * Registers a user's selected source when agent-decision output is enabled.
     *
     * @param simulationId run identifier
     * @param period decision period
     * @param snsUserId selecting user identifier
     * @param newsSourceName selected source
     * @param evaluation selected source score
     */
    public static void addAgentDecisionData(int simulationId, int period, int snsUserId, String newsSourceName, double evaluation) {
        if (Configuration.SAVED_AGENT_DECISIONS)
            agentDecisionData.add(new AgentDecisionData(simulationId, period, snsUserId, newsSourceName, evaluation));
    }

    /**
     * Registers one candidate score when detailed decision output is enabled.
     *
     * @param simulationId run identifier
     * @param period evaluation period
     * @param snsUserId evaluating user identifier
     * @param newsSourceName candidate source
     * @param evaluation candidate score
     */
    public static void addDetailedAgentDecisionData(int simulationId, int period, int snsUserId, String newsSourceName, double evaluation) {
        if (Configuration.SAVED_DETAILED_AGENT_DECISIONS)
            detailedAgentDecisionData.add(new DetailedAgentDecisionData(simulationId, period, snsUserId, newsSourceName, evaluation));
    }

    /**
     * Registers total source selections when aggregate repost output is enabled.
     *
     * @param simulationId run identifier
     * @param period aggregation period
     * @param reposts counts indexed by source identifier
     */
    public static void addRepostsByNewsSourceData(int simulationId, int period, int[] reposts) {
        if (Configuration.SAVED_REPOSTS_PER_SOURCE)
            repostsPerNewsSourceData.add(new RepostsPerSourceData(simulationId, period, reposts));
    }

    /**
     * Registers cumulative unique-reposter counts when aggregate output is enabled.
     *
     * @param simulationId run identifier
     * @param period aggregation period
     * @param reposts distinct-user counts indexed by source identifier
     */
    public static void addRepostsUniqueByNewsSourceData(int simulationId, int period, int[] reposts) {
        if (Configuration.SAVED_REPOSTS_PER_SOURCE)
            repostsUniquePerNewsSourceData.add(new UniqueRepostersPerSourceData(simulationId,period,reposts));
    }

    /**
     * Registers all source publication classifications for one simulation period when enabled.
     *
     * @param simulationId run identifier
     * @param period publication period
     * @param fakeNews source-ID-indexed fake-news statuses
     */
    public static void addFakeNewsPerSourceData(int simulationId, int period, boolean[] fakeNews) {
        if (Configuration.SAVED_FAKENEWS) {
            fakeNewsPerSourceData.add(new FakeNewsPerSourceData(simulationId, period, fakeNews));
        }
    }

    /**
     * Exposes accumulated fake-news rows for report verification and other read-only consumers.
     *
     * @return current fake-news report rows
     */
    public static List<FakeNewsPerSourceData> getFakeNewsPerSourceData() {
        return fakeNewsPerSourceData;
    }

    /** Writes source publication classifications using numeric {@code 1}/{@code 0} cells. */
    private static void writeFakeNewsPerSource(XSSFWorkbook workbook) {
        Console.info("Reporter: Adding Fake News Per Source: " + fakeNewsPerSourceData.size());
        writePagedRows(workbook, "FakeNewsPerSource", FakeNewsPerSourceData.getHeader(),
                fakeNewsPerSourceData, (dataRow, oneRow) -> {
                    dataRow.createCell(0).setCellValue(oneRow.simulationId);
                    dataRow.createCell(1).setCellValue(oneRow.period);
                    for (int i = 0; i < oneRow.fakeNews.length; ++i) {
                        dataRow.createCell(2 + i).setCellValue(oneRow.fakeNews[i] ? 1 : 0);
                    }
                });
    }

    private static void writeWomDiagnostics(XSSFWorkbook workbook) {
        List<WomDiagnosticData> rows = getWomDiagnosticData();
        Console.info("Reporter: Adding WOM diagnostics: " + rows.size());
        writePagedRows(workbook, "WomDiagnostics", WomDiagnosticData.getHeader(), rows, (dataRow, row) -> {
            int[] values = {row.simulationId, row.period, row.receiversWithRecommendation,
                    row.contactRecommendations, row.duplicateSourceRecommendations, row.exactMaximumTies,
                    row.newSourceDiscoveries, row.labelsCovered, row.labelsUncovered,
                    row.truePositiveLabels, row.falseNegativeLabels, row.trueNegativeLabels,
                    row.falsePositiveLabels, row.rewardedRecommendations, row.penalizedRecommendations,
                    row.ignoredRecommendations, row.endorsementsScheduled, row.endorsementsDelivered};
            for (int column = 0; column < values.length; ++column) {
                dataRow.createCell(column).setCellValue(values[column]);
            }
        });
    }

    /**
     * Writes either total-repost or unique-reposter rows using their shared schema and paging logic.
     *
     * @param workbook output workbook
     * @param sheetName base worksheet name
     * @param reposts aggregate rows to write
     */
    private static void writeRepostsPerNewsSource(XSSFWorkbook workbook, String sheetName, List<? extends RepostsPerSourceData> reposts) {
        Console.info("Reporter: Adding Reposts Per Source: " + reposts.size());
        writePagedRows(workbook, sheetName, RepostsPerSourceData.getHeader(), reposts, (dataRow, oneRow) -> {
            dataRow.createCell(0).setCellValue(oneRow.simulationId);
            dataRow.createCell(1).setCellValue(oneRow.period);

            for (int i = 0; i < oneRow.reposts.length; ++i) {
                dataRow.createCell(2 + i).setCellValue(oneRow.reposts[i]);
            }
        });
    }

    /**
     * Writes all source-candidate evaluations to paged DetailedResult sheets.
     *
     * @param workbook output workbook
     */
    private static void writeDetailedAgentDecision(XSSFWorkbook workbook) {
        Console.info("Reporter: Adding Detailed Agent Decisions: " + detailedAgentDecisionData.size());
        writePagedRows(workbook, "DetailedResult", DetailedAgentDecisionData.getHeader(), detailedAgentDecisionData, (dataRow, oneRow) -> {
            dataRow.createCell(0).setCellValue(oneRow.simulationId);
            dataRow.createCell(1).setCellValue(oneRow.period);
            dataRow.createCell(2).setCellValue(oneRow.snsUserId);
            dataRow.createCell(3).setCellValue(oneRow.newsSourceName);
            dataRow.createCell(4).setCellValue(oneRow.evaluation);
        });
    }

    /**
     * Copies supported cell values from an input worksheet into the result workbook.
     *
     * @param workbook output workbook
     * @param sheet loaded input sheet to reproduce
     */
    private static void addSheet(XSSFWorkbook workbook, Sheet sheet) {
        Sheet newSheet = workbook.createSheet(sheet.getSheetName());

        for (int i = 0; i <= sheet.getLastRowNum(); ++i) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Row newRow = newSheet.createRow(i);
            for (int j = 0; j < row.getLastCellNum(); ++j) {
                Cell cell = row.getCell(j);
                if (cell == null) continue;
                String cellType = cell.getCellTypeEnum().name();
                Cell newCell = newRow.createCell(j);
                if (cellType.equalsIgnoreCase("STRING")) {
                    newCell.setCellValue(cell.getRichStringCellValue());
                }
                if (cellType.equalsIgnoreCase("NUMERIC") || cellType.equalsIgnoreCase("FORMULA")) {
                    newCell.setCellValue(cell.getNumericCellValue());
                }
                if (cellType.equalsIgnoreCase("BOOLEAN")) {
                    newCell.setCellValue(cell.getBooleanCellValue());
                }
            }
        }
    }

    /**
     * Exposes per-period source-selection totals used by repost charts and aggregate analysis.
     *
     * @return accumulated total-repost rows
     */
    public static List<? extends RepostsPerSourceData> getTotalRepostsPerSourceData() {
        return repostsPerNewsSourceData;
    }

    /**
     * Exposes cumulative unique-reposter aggregates used by repost charts and aggregate analysis.
     *
     * @return accumulated unique-reposter rows
     */
    public static List<? extends RepostsPerSourceData> getUniqueRepostersPerSourceData() {
        return repostsUniquePerNewsSourceData;
    }

    /**
     * Compatibility alias for the original ambiguous chart-data accessor.
     *
     * @return accumulated cumulative unique-reposter rows
     * @deprecated use {@link #getUniqueRepostersPerSourceData()}
     */
    @Deprecated
    public static List<? extends RepostsPerSourceData> getRepostsPerSourceData() {
        return getUniqueRepostersPerSourceData();
    }

    /**
     * Writes attribute-level events to one or more Endorsements sheets.
     *
     * @param workbook output workbook
     */
    private static void writeEndorsements(XSSFWorkbook workbook) {
        Console.info("Reporter: Adding endorsements: " + endorsData.size());
        writePagedRows(workbook, "Endorsements", EndorsementData.getHeader(), endorsData, (dataRow, oneRow) -> {
            dataRow.createCell(0).setCellValue(oneRow.simulationId);
            dataRow.createCell(1).setCellValue(oneRow.period);
            dataRow.createCell(2).setCellValue(oneRow.snsUserId);
            dataRow.createCell(3).setCellValue(oneRow.newsSourceName);
            dataRow.createCell(4).setCellValue(oneRow.attribute);
            dataRow.createCell(5).setCellValue(oneRow.value);
        });
    }

    /**
     * Writes selected-source decision rows to one or more Results sheets.
     *
     * @param workbook output workbook
     */
    private static void writeAgentDecision(XSSFWorkbook workbook) {
        Console.info("Reporter: Adding Agent Decisions: " + agentDecisionData.size());
        writePagedRows(workbook, "Results", AgentDecisionData.getHeader(), agentDecisionData, (dataRow, oneRow) -> {
            dataRow.createCell(0).setCellValue(oneRow.simulationId);
            dataRow.createCell(1).setCellValue(oneRow.period);
            dataRow.createCell(2).setCellValue(oneRow.snsUserId);
            dataRow.createCell(3).setCellValue(oneRow.newsSourceName);
            dataRow.createCell(4).setCellValue(oneRow.evaluation);
        });
    }

    /**
     * Writes typed rows across sequentially suffixed sheets without exceeding the configured row cap.
     *
     * @param workbook output workbook
     * @param baseSheetName first sheet name and suffix base
     * @param headers ordered column labels repeated on every page
     * @param rows records to write
     * @param writer record-specific cell writer
     * @param <T> report row type
     */
    private static <T> void writePagedRows(XSSFWorkbook workbook, String baseSheetName, List<String> headers,
                                          List<T> rows, BiConsumer<Row, T> writer) {
        int maxRows = maxRowsPerSheet();
        XSSFSheet sheet = createPagedSheet(workbook, baseSheetName, 1, headers);
        int rowIndex = 1;
        int sheetNumber = 1;

        for (T oneRow : rows) {
            if (rowIndex >= maxRows) {
                ++sheetNumber;
                sheet = createPagedSheet(workbook, baseSheetName, sheetNumber, headers);
                rowIndex = 1;
            }

            Row dataRow = sheet.createRow(rowIndex);
            writer.accept(dataRow, oneRow);
            ++rowIndex;
        }
    }

    /**
     * Creates one report page and installs its header row.
     *
     * @param workbook output workbook
     * @param baseSheetName unsuffixed report name
     * @param sheetNumber one-based page number
     * @param headers ordered column labels
     * @return newly created sheet
     */
    private static XSSFSheet createPagedSheet(XSSFWorkbook workbook, String baseSheetName, int sheetNumber,
                                              List<String> headers) {
        String sheetName = sheetNumber == 1 ? baseSheetName : baseSheetName + "_" + sheetNumber;
        XSSFSheet sheet = workbook.createSheet(sheetName);
        Row headRow = sheet.createRow(0);

        int column = 0;
        for (String head : headers) {
            Cell cell = headRow.createCell(column);
            cell.setCellValue(head);
            ++column;
        }

        return sheet;
    }

    /**
     * Resolves the optional test/runtime paging override while enforcing Excel's row limit.
     *
     * @return valid maximum rows per sheet, including its header row
     */
    private static int maxRowsPerSheet() {
        String configuredValue = System.getProperty(MAX_ROWS_PROPERTY);
        if (configuredValue == null || configuredValue.trim().isEmpty()) {
            return EXCEL_MAX_ROWS;
        }

        try {
            int maxRows = Integer.parseInt(configuredValue);
            if (maxRows >= 2 && maxRows <= EXCEL_MAX_ROWS) {
                return maxRows;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the production default.
        }

        Console.warn("Reporter: ignoring invalid " + MAX_ROWS_PROPERTY + "=" + configuredValue);
        return EXCEL_MAX_ROWS;
    }

    /**
     * Writes normalized effective configuration rather than copying potentially incomplete input.
     *
     * @param conf output Configuration sheet
     */
    private static void writeConfiguration(Sheet conf) {
        Console.info("Reporter: Adding Configuration");
        Map<String, Double> dump = Configuration.toMap();

        int rowIndex = 0;
        for (String key : dump.keySet()) {
            double value = dump.get(key);
            Row row = conf.createRow(rowIndex);
            Cell keyCell = row.createCell(0);
            Cell valueCell = row.createCell(1);
            keyCell.setCellValue(key);
            valueCell.setCellValue(value);
            ++rowIndex;
        }

        setReadableColumnWidths(conf, 2);
    }

    /**
     * Applies a consistent readable width to generated metadata columns.
     *
     * @param sheet sheet to format
     * @param columns number of leading columns to resize
     */
    private static void setReadableColumnWidths(Sheet sheet, int columns) {
        for (int i = 0; i < columns; ++i) {
            sheet.setColumnWidth(i, 28 * 256);
        }
    }

    /** Compresses the complete run directory when compressed-result output is enabled. */
    private static void compressFolder() {
        if (Configuration.COMPRESSED_RESULTS) {
            File compressedFile = new File(Configuration.OUTPUT_DIRECTORY + ".zip");
            try {
                zipFolder(new File(Configuration.OUTPUT_DIRECTORY), compressedFile);
                Console.info("Reporter: Folder compressed in: " + compressedFile.getAbsolutePath());
            } catch (IOException ex) {
                Error.trigger("Output cannot be compressed: " + compressedFile.getAbsolutePath() + "\n.ERROR: " + ex, ex);
            }
        }
    }

    /**
     * Opens the target ZIP stream and recursively adds the output directory.
     *
     * @param sourceFolder run directory to archive
     * @param targetFile ZIP file to create
     * @throws IOException if the archive cannot be created or written
     */
    private static void zipFolder(File sourceFolder, File targetFile) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(targetFile))) {
            zipFile(sourceFolder, sourceFolder.getName(), zip);
        }
    }

    /**
     * Recursively adds visible files to an open ZIP stream while preserving directory paths.
     *
     * @param fileToZip file or directory being traversed
     * @param fileName archive-relative path
     * @param zip open destination stream
     * @throws IOException if a source file cannot be read or the entry cannot be written
     */
    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zip) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipFile(childFile, fileName + "/" + childFile.getName(), zip);
                }
            }
            return;
        }
        try (FileInputStream input = new FileInputStream(fileToZip)) {
            zip.putNextEntry(new ZipEntry(fileName));
            byte[] bytes = new byte[1024];
            int length;
            while ((length = input.read(bytes)) >= 0) {
                zip.write(bytes, 0, length);
            }
        }
    }

    /**
     * Writes the completed workbook under a timestamped name and triggers optional compression.
     *
     * @param workbook completed output workbook
     */
    private static void writeDisk(XSSFWorkbook workbook) {
        System.gc(); //call garbage collector (memory leaks?)
        Console.info("Saving results in: " + (new File(Configuration.OUTPUT_DIRECTORY)).getAbsolutePath());

        String fullFileName = Configuration.OUTPUT_DIRECTORY + "/" + Configuration.FILE_NAME;
        try {
            DateFormat df = new SimpleDateFormat("dd-MM-yy(HH-mm-ss)");
            fullFileName += "_" + df.format(new Date()) + ".xlsx";

            try (FileOutputStream file = new FileOutputStream(fullFileName)) {
                workbook.write(file);
            }
            Console.info("Reporter: File saved.");
            compressFolder();
        } catch (IOException ex) {
            Error.trigger("Input cannot be created: " + fullFileName + "\n.ERROR: " + ex, ex);
        }
    }

}
