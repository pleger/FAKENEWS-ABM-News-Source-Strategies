package inputManager;

import utils.Console;
import utils.Error;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import scenarios.ScenarioFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves and parses the Excel input workbook, populating configuration, source, user, and
 * scenario stores that the factories use to construct a simulation.
 */
public class Loader {
    private static Sheet configuration;
    private static Sheet newsSources;
    private static Sheet snsUsers;
    private static Sheet sourceReach;
    private static Sheet sourceBehavior;
    private static Sheet strategies;
    private static Sheet scenario;
    private static Workbook workbook;

    /**
     * Resolves an input name or path, creates its output directory, and loads all model sheets.
     *
     * @param file workbook name, workbook path, or empty string to use configured fallbacks
     */
    public static void load(String file) {
        File input = determineInputFile(file);
        Configuration.setPath(stripExtension(input.getName()));
        Console.info("Loader: Reading input from: " + input.getPath());
        read(input);
    }

    /**
     * Opens a workbook read-only, resets cached scenarios, and converts its sheets into input stores.
     * Any parsing failure is reported as a fatal execution error.
     *
     * @param file resolved workbook file
     */
    private static void read(File file) {
        try {
            close();
            ScenarioFactory.clear();
            Scenarios.clear();
            Strategies.clear();

            workbook = WorkbookFactory.create(file, null, true);
            showAvailableSheets(workbook);

            configuration = workbook.getSheet("Configuration");
            Configuration.set(readConfiguration(getConfiguration()));

            newsSources = workbook.getSheet("NewsSources");
            snsUsers = workbook.getSheet("SNSUsers");
            sourceReach = workbook.getSheet("SourceReach");
            sourceBehavior = workbook.getSheet("SourceBehavior");
            strategies = workbook.getSheet("Strategies");
            scenario =  (Configuration.SCENARIO != Configuration.DISABLED)? workbook.getSheet("Scenario"): null;

            HashMap<String, ArrayList<Double[]>> sourceAttributes =
                    readNewsSourceAttributes(getNewsSources(), Configuration.LEVELS);
            ArrayList<String> sourceNames =
                    readNewsSourceNames(getNewsSources(), Configuration.LEVELS);
            HashMap<String, Double> fakeNewsProbabilities = null;
            if (sourceBehavior == null) {
                Console.warn("Loader: SourceBehavior is absent; preserving the legacy rule that "
                        + "derives fake-news probability from current low credibility");
            } else {
                fakeNewsProbabilities = readSourceBehavior(sourceBehavior, sourceNames);
            }

            NewsSources.set(sourceAttributes, sourceNames, readSourceReach(getSourceReach()),
                    fakeNewsProbabilities);

            if (strategies != null) {
                Strategies.set(readStrategies(strategies, sourceAttributes.keySet()));
            }

            SNSUsers.set(readSNSUsers(getSNSUsers()));
            if (Configuration.SCENARIO != Configuration.DISABLED) readScenario(getScenario());

            Configuration.setAttributes(NewsSources.attributeSize(), SNSUsers.attributeSize());
            Configuration.setNewsSources(NewsSources.getInnerNewsSources().size());
        } catch (Exception ex) {
            // Configuration validation deliberately throws so it remains unit-testable; the
            // application boundary converts every invalid input into a fatal model-start error.
            Error.trigger("Loader.read: Input cannot be opened: " + file.getAbsolutePath(), ex);
        }
    }

    /** Closes the active workbook so CLI execution releases its input resource. */
    public static void close() {
        if (workbook != null) {
            try {
                workbook.close();
            } catch (IOException ex) {
                Console.warn("Loader.close: input workbook could not be closed: " + ex);
            }
            workbook = null;
        }
    }

    /**
     * Logs workbook sheet names to make input-layout failures diagnosable.
     *
     * @param workbook open input workbook
     */
    private static void showAvailableSheets(Workbook workbook) {
        StringBuilder names = new StringBuilder();
        for(int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.append(workbook.getSheetName(i)).append(",");
        }
        
        String text = names.length() > 0 ? names.substring(0, names.length() - 1) : "";
        Console.info("Loader: Sheets available in the input file: " + text);
    }

    /**
     * Parses the single custom-scenario row into the scenario input store. Attribute cells after
     * the required source, target, and period cells are optional: an effective empty list is the
     * customized-scenario shortcut for copying every source attribute.
     *
     * @param scenario required Scenario worksheet
     */
    private static void readScenario(Sheet scenario) {
        Console.info("Loader: Reading Scenario");
        Row firstRow = scenario.getRow(0);
        Error.setAssert(firstRow != null, "Scenario: worksheet is empty");
        boolean newFormat = isNewScenarioFormat(firstRow);
        Row row = scenario.getRow(newFormat ? 1 : 0);
        Error.setAssert(row != null, "Scenario: scenario definition row is missing");

        String from = requiredString(row, 0, "Scenario FROM").toUpperCase();
        String to = requiredString(row, 1, "Scenario TO").toUpperCase();
        int start = requiredInteger(row, 2, "Scenario START_PERIOD");
        int end = -1;
        ArrayList<String> strategyNames = new ArrayList<>();
        ArrayList<String> attNames = new ArrayList<>();

        if (newFormat) {
            Cell endCell = row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (endCell != null) {
                end = integerCell(endCell, "Scenario END_PERIOD");
            }
            addCommaSeparatedNames(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL),
                    strategyNames);
        }

        int attributesStart = newFormat ? 5 : 3;
        for (int i = attributesStart; i < row.getLastCellNum(); ++i) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            addCommaSeparatedNames(cell, attNames);
        }

        Error.setAssert(start >= 1 && start <= Configuration.PERIODS,
                "Scenario: START_PERIOD must be within 1.." + Configuration.PERIODS);
        ArrayList<String> resolvedAttributes = Strategies.resolve(strategyNames, attNames);
        validateScenarioDefinition(from, to, resolvedAttributes);
        Scenarios.set(from, to, start, end, strategyNames, resolvedAttributes);
    }

    /** Validates scenario source names and dimensions during input loading, before simulation starts. */
    private static void validateScenarioDefinition(String from, String to, List<String> attributes) {
        InnerNewsSource source = null;
        InnerNewsSource target = null;
        for (InnerNewsSource candidate : NewsSources.getInnerNewsSources()) {
            if (candidate.name.equals(from)) source = candidate;
            if (candidate.name.equals(to)) target = candidate;
        }
        Error.setAssert(source != null, "Scenario: FROM source was not found: " + from);
        Error.setAssert(target != null, "Scenario: TO source was not found: " + to);

        List<String> required = attributes.isEmpty() ? source.attributeNames : attributes;
        Error.setAssert(source.attributeNames.containsAll(required),
                "Scenario: some attributes were not found in FROM source: " + required);
        Error.setAssert(target.attributeNames.containsAll(required),
                "Scenario: some attributes were not found in TO source: " + required);
    }

    /** Detects the header-based scenario schema without changing legacy row-zero semantics. */
    private static boolean isNewScenarioFormat(Row firstRow) {
        Cell from = firstRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        Cell start = firstRow.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return from != null && start != null
                && "FROM".equalsIgnoreCase(from.toString().trim())
                && "START_PERIOD".equalsIgnoreCase(start.toString().trim());
    }

    /** Appends comma-separated, uppercase identifiers from one optional workbook cell. */
    private static void addCommaSeparatedNames(Cell cell, ArrayList<String> destination) {
        if (cell == null || cell.toString().trim().isEmpty()) {
            return;
        }
        for (String token : cell.toString().split(",")) {
            String normalized = token.trim().toUpperCase();
            if (!normalized.isEmpty()) {
                destination.add(normalized);
            }
        }
    }

    private static String requiredString(Row row, int column, String label) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        Error.setAssert(cell != null && !cell.toString().trim().isEmpty(), label + " is required");
        return cell.toString().trim();
    }

    private static int requiredInteger(Row row, int column, String label) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        Error.setAssert(cell != null, label + " is required");
        return integerCell(cell, label);
    }

    private static int integerCell(Cell cell, String label) {
        Error.setAssert(cell.getCellType() == Cell.CELL_TYPE_NUMERIC, label + " must be numeric");
        double value = cell.getNumericCellValue();
        Error.setAssert(Double.isFinite(value) && value == Math.rint(value), label + " must be an integer");
        return (int) value;
    }

    /** Reads and validates one explicit objective fake-news probability for every source. */
    private static HashMap<String, Double> readSourceBehavior(Sheet sheet, List<String> sourceNames) {
        Console.info("Loader: Reading SourceBehavior");
        Row header = sheet.getRow(0);
        Error.setAssert(header != null
                        && "SOURCE".equalsIgnoreCase(requiredString(header, 0, "SourceBehavior SOURCE header"))
                        && "FAKE_NEWS_PROBABILITY".equalsIgnoreCase(
                                requiredString(header, 1, "SourceBehavior FAKE_NEWS_PROBABILITY header")),
                "SourceBehavior: expected headers SOURCE and FAKE_NEWS_PROBABILITY");

        HashMap<String, Double> result = new HashMap<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); ++rowIndex) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) == null) {
                continue;
            }
            String source = requiredString(row, 0, "SourceBehavior SOURCE").toUpperCase();
            Cell probabilityCell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Error.setAssert(probabilityCell != null
                            && probabilityCell.getCellType() == Cell.CELL_TYPE_NUMERIC,
                    "SourceBehavior: probability must be numeric for " + source);
            double probability = probabilityCell.getNumericCellValue();
            Error.setAssert(Double.isFinite(probability) && probability >= 0.0 && probability <= 1.0,
                    "SourceBehavior: probability must be within [0,1] for " + source);
            Error.setAssert(!result.containsKey(source),
                    "SourceBehavior: duplicate source: " + source);
            result.put(source, probability);
        }

        Set<String> expected = new LinkedHashSet<>(sourceNames);
        Error.setAssert(result.keySet().equals(expected),
                "SourceBehavior: sources must exactly match NewsSources; expected " + expected
                        + " but found " + result.keySet());
        return result;
    }

    /** Reads the normalized STRATEGY/ATTRIBUTE catalog and validates model dimensions. */
    private static Map<String, List<String>> readStrategies(Sheet sheet, Set<String> validAttributes) {
        Console.info("Loader: Reading Strategies");
        Row header = sheet.getRow(0);
        Error.setAssert(header != null
                        && "STRATEGY".equalsIgnoreCase(requiredString(header, 0, "Strategies STRATEGY header"))
                        && "ATTRIBUTE".equalsIgnoreCase(requiredString(header, 1, "Strategies ATTRIBUTE header")),
                "Strategies: expected headers STRATEGY and ATTRIBUTE");

        Map<String, List<String>> result = new LinkedHashMap<>();
        Set<String> pairs = new LinkedHashSet<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); ++rowIndex) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) == null) {
                continue;
            }
            String strategy = requiredString(row, 0, "Strategies STRATEGY").toUpperCase();
            String attribute = requiredString(row, 1, "Strategies ATTRIBUTE").toUpperCase();
            Error.setAssert(validAttributes.contains(attribute),
                    "Strategies: attribute is not present in NewsSources: " + attribute);
            Error.setAssert(pairs.add(strategy + "\u0000" + attribute),
                    "Strategies: duplicate strategy/attribute pair: " + strategy + " / " + attribute);
            result.computeIfAbsent(strategy, key -> new ArrayList<>()).add(attribute);
        }
        Error.setAssert(!result.isEmpty(), "Strategies: worksheet contains no definitions");
        return result;
    }


    /**
     * Reads source visibility percentages and normalizes them to probabilities.
     *
     * @param sourceReach SourceReach worksheet
     * @return uppercase source names mapped to values in {@code [0,1]}
     */
    private static HashMap<String, Double> readSourceReach(Sheet sourceReach) {
        Console.info("Loader: Reading NewsSource Reach");
        HashMap<String, Double> reach = new HashMap<>();

        for (Row row : sourceReach) {
            reach.put(row.getCell(0).getStringCellValue().toUpperCase(), row.getCell(1).getNumericCellValue() / 100.0);
        }
        return reach;
    }

    /**
     * Reads numeric configuration entries before validation by {@link Configuration}.
     *
     * @param conf Configuration worksheet
     * @return uppercase keys mapped to workbook values
     */
    private static HashMap<String, Double> readConfiguration(Sheet conf) {
        Console.info("Loader: Reading Configuration");
        HashMap<String, Double> confs = new HashMap<>();
        for (Row row : conf) {
            confs.put(row.getCell(0).getStringCellValue().toUpperCase(), row.getCell(1).getNumericCellValue());
        }
        return confs;
    }

    /**
     * Extracts source names from the grouped header cells of the NewsSources worksheet.
     *
     * @param newsSource NewsSources worksheet
     * @param levels number of columns occupied by each source
     * @return source names in worksheet order
     */
    private static ArrayList<String> readNewsSourceNames(Sheet newsSource, int levels) {
        ArrayList<String> newsSourceNames = new ArrayList<>();

        for (Row row : newsSource) {
            if (row.getRowNum() == 1) {
                for (Cell cell : row) {
                    if (cell.getColumnIndex() > 0 && (cell.getColumnIndex() + 1) % levels == 0) {
                        newsSourceNames.add(cell.getStringCellValue().toUpperCase());
                    }
                }
            }
        }
        return newsSourceNames;
    }

    /**
     * Regroups worksheet cells into one level distribution per attribute and source.
     *
     * @param newsSource NewsSources worksheet
     * @param levels configured distribution width
     * @return attribute names mapped to source-ordered distributions
     */
    private static HashMap<String, ArrayList<Double[]>> readNewsSourceAttributes(Sheet newsSource, int levels) {
        Console.info("Loader: Reading NewsSource Attributes");
        HashMap<String, ArrayList<Double[]>> datas = new HashMap<>();
        ArrayList<Double> endor = new ArrayList<>();
        ArrayList<Double[]> endors = new ArrayList<>();

        for (Row row : newsSource) {
            String name = "NO NAME";

            //get data from attributes
            if (row.getRowNum() > 2) {  // where starts attributes
                for (Cell cell : row) {
                    if (cell.getColumnIndex() == 0) {  //adding attributeName
                        name = cell.getStringCellValue().toUpperCase();
                    } else {
                        endor.add(cell.getNumericCellValue());
                        if (cell.getColumnIndex() % levels == 0) {
                            Double[] oneEndorsement = new Double[levels];
                            endors.add(endor.toArray(oneEndorsement));
                            endor.clear();
                        }
                    }
                }

                datas.put(name, new ArrayList<>(endors));
                endors.clear();
            }
        }
        return datas;
    }

    /**
     * Reads the prototype SNS-user weight for every named endorsement dimension.
     *
     * @param snsUser SNSUsers worksheet
     * @return uppercase attribute names mapped to scalar weights
     */
    private static HashMap<String, Double> readSNSUsers(Sheet snsUser) {
        Console.info("Loader: Reading SNSUsers");
        HashMap<String, Double> snsUsers = new HashMap<>();
        for (Row row : snsUser) {
            snsUsers.put(row.getCell(0).getStringCellValue().toUpperCase(), row.getCell(1).getNumericCellValue());
        }
        return snsUsers;
    }

    /**
     * Resolves direct paths, optional extensions, singular/plural input directories, and legacy
     * {@code input.txt} selection in that precedence order.
     *
     * @param inputFileName requested workbook name or path
     * @return first existing candidate, or the unresolved direct file for downstream error reporting
     */
    private static File determineInputFile(String inputFileName) {
        if (inputFileName.isEmpty()) {
            try (BufferedReader br = new BufferedReader(new FileReader("input.txt"))) {
                inputFileName = br.readLine();
            } catch (Exception e) {
                //First error without a initialize console
                System.out.println("MAIN: input.txt not found");
            }
        }

        inputFileName = inputFileName.isEmpty() ? Configuration.DEFAULT_FILE_NAME : inputFileName;
        ArrayList<File> candidates = new ArrayList<>();
        File given = new File(inputFileName);
        candidates.add(given);
        if (!inputFileName.toLowerCase().endsWith(".xlsx")) {
            candidates.add(new File(inputFileName + ".xlsx"));
            candidates.add(new File("input", inputFileName + ".xlsx"));
            candidates.add(new File("inputs", inputFileName + ".xlsx"));
        } else {
            candidates.add(new File("input", inputFileName));
            candidates.add(new File("inputs", inputFileName));
        }

        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate;
            }
        }
        return given;
    }

    /**
     * Derives the run/output label from a workbook file name.
     *
     * @param name workbook file name
     * @return name without a case-insensitive {@code .xlsx} suffix
     */
    private static String stripExtension(String name) {
        return name.toLowerCase().endsWith(".xlsx") ? name.substring(0, name.length() - 5) : name;
    }

    /**
     * Enforces that a required worksheet was present before parsing continues.
     *
     * @param sheet resolved sheet, possibly {@code null}
     * @param name expected worksheet name for diagnostics
     */
    private static void verifyLoadedSheet(Sheet sheet, String name) {
        if (sheet == null) Error.trigger("Sheet '"+name+"' has not been loaded");
    }

    /**
     * Supplies the user sheet to parsing and output-copy flows.
     *
     * @return validated SNSUsers worksheet from the active workbook
     */
    public static Sheet getSNSUsers() {
        verifyLoadedSheet(snsUsers, "SNSUsers");
        return snsUsers;
    }

    /**
     * Supplies the source sheet to parsing and output-copy flows.
     *
     * @return validated NewsSources worksheet from the active workbook
     */
    public static Sheet getNewsSources() {
        verifyLoadedSheet(newsSources, "NewsSources");
        return newsSources;
    }

    /**
     * Supplies the reach sheet to parsing and output-copy flows.
     *
     * @return validated SourceReach worksheet from the active workbook
     */
    public static Sheet getSourceReach() {
        verifyLoadedSheet(sourceReach, "SourceReach");
        return sourceReach;
    }

    /** Returns the optional explicit source-behavior worksheet, or {@code null} for legacy inputs. */
    public static Sheet getSourceBehavior() {
        return sourceBehavior;
    }

    /** Returns the optional strategy-catalog worksheet, or {@code null} for legacy inputs. */
    public static Sheet getStrategies() {
        return strategies;
    }

    /**
     * Supplies the optional intervention sheet to parsing and output-copy flows.
     *
     * @return validated Scenario worksheet when scenario execution is enabled
     */
    public static Sheet getScenario() {
        verifyLoadedSheet(scenario, "Scenario");
        return scenario;
    }

    /**
     * Supplies the configuration sheet to the initial loading stage.
     *
     * @return validated Configuration worksheet from the active workbook
     */
    private static Sheet getConfiguration() {
        verifyLoadedSheet(configuration, "Configuration");
        return configuration;
    }
}
