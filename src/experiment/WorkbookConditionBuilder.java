package experiment;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Creates one auditable condition workbook using the same Apache POI runtime as the model. */
public final class WorkbookConditionBuilder {
    private static final DataFormatter FORMATTER = new DataFormatter();

    private WorkbookConditionBuilder() {
    }

    public static void build(Path baseWorkbook, Path destination,
                             ConditionSpecification condition) throws IOException {
        if (!Files.isRegularFile(baseWorkbook)) {
            throw new IllegalArgumentException("Base workbook does not exist: " + baseWorkbook);
        }
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, condition.getId() + "-", ".xlsx.tmp");
        try (FileInputStream input = new FileInputStream(baseWorkbook.toFile());
             Workbook workbook = WorkbookFactory.create(input)) {
            writeConfiguration(requiredSheet(workbook, "Configuration"), condition.getConfiguration());
            if (condition.getScenario() != null) {
                writeScenario(requiredSheet(workbook, "Scenario"), requiredSheet(workbook, "Strategies"),
                        condition.getScenario(), condition.getConfiguration());
            }
            if (condition.getTargetReachPercentage() != null) {
                String target = condition.getScenario() == null
                        ? "FAKE_NEWS_SOURCE" : condition.getScenario().getTo();
                writeSourceReach(requiredSheet(workbook, "SourceReach"), target,
                        condition.getTargetReachPercentage());
            }
            if (!condition.getSourceBehaviorOverrides().isEmpty()) {
                writeSourceBehavior(requiredSheet(workbook, "SourceBehavior"),
                        condition.getSourceBehaviorOverrides());
            }
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                workbook.write(output);
            }
        } catch (Exception exception) {
            Files.deleteIfExists(temporary);
            if (exception instanceof IOException) throw (IOException) exception;
            throw new IllegalArgumentException("Cannot build condition " + condition.getId() + ": "
                    + exception.getMessage(), exception);
        }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Sheet requiredSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) throw new IllegalArgumentException("Workbook is missing sheet: " + name);
        return sheet;
    }

    private static void writeConfiguration(Sheet sheet, Map<String, Double> values) {
        Set<String> remaining = new HashSet<>(values.keySet());
        for (Row row : sheet) {
            Cell keyCell = row.getCell(0);
            if (keyCell == null) continue;
            String key = FORMATTER.formatCellValue(keyCell).trim().toUpperCase();
            Double value = values.get(key);
            if (value != null) {
                cell(row, 1).setCellValue(value);
                remaining.remove(key);
            }
        }
        for (String key : values.keySet()) {
            if (!remaining.contains(key)) continue;
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue(key);
            row.createCell(1).setCellValue(values.get(key));
        }
    }

    private static void writeScenario(Sheet scenarioSheet, Sheet strategiesSheet,
                                      ScenarioSpecification scenario,
                                      Map<String, Double> configuration) {
        Row header = scenarioSheet.getRow(0);
        if (header == null || !"FROM".equalsIgnoreCase(text(header.getCell(0)))
                || !"START_PERIOD".equalsIgnoreCase(text(header.getCell(2)))) {
            throw new IllegalArgumentException("Study requires the header-based Scenario schema");
        }
        Double periods = configuration.get("PERIODS");
        if (periods != null && scenario.getEndPeriod() > periods.intValue()) {
            throw new IllegalArgumentException("Scenario END_PERIOD exceeds PERIODS");
        }
        Set<String> availableStrategies = strategyNames(strategiesSheet);
        for (String strategy : scenario.getStrategies()) {
            if (!availableStrategies.contains(strategy)) {
                throw new IllegalArgumentException("Scenario references unknown strategy: " + strategy);
            }
        }

        Row row = scenarioSheet.getRow(1);
        if (row == null) row = scenarioSheet.createRow(1);
        cell(row, 0).setCellValue(scenario.getFrom());
        cell(row, 1).setCellValue(scenario.getTo());
        cell(row, 2).setCellValue(scenario.getStartPeriod());
        cell(row, 3).setCellValue(scenario.getEndPeriod());
        cell(row, 4).setCellValue(String.join(",", scenario.getStrategies()));
        int neededColumns = 5 + scenario.getAttributes().size();
        int lastColumn = Math.max(row.getLastCellNum(), neededColumns);
        for (int column = 5; column < lastColumn; ++column) {
            Cell existing = row.getCell(column);
            if (existing != null) existing.setCellType(CellType.BLANK);
        }
        for (int index = 0; index < scenario.getAttributes().size(); ++index) {
            cell(row, index + 5).setCellValue(scenario.getAttributes().get(index));
        }
    }

    private static Set<String> strategyNames(Sheet sheet) {
        HashSet<String> names = new HashSet<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); ++rowIndex) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getCell(0) != null) {
                names.add(text(row.getCell(0)).toUpperCase());
            }
        }
        return names;
    }

    private static void writeSourceReach(Sheet sheet, String source, double percentage) {
        for (Row row : sheet) {
            if (source.equalsIgnoreCase(text(row.getCell(0)))) {
                cell(row, 1).setCellValue(percentage);
                return;
            }
        }
        throw new IllegalArgumentException("SourceReach does not contain source: " + source);
    }

    private static void writeSourceBehavior(Sheet sheet, Map<String, Double> overrides) {
        Set<String> remaining = new HashSet<>(overrides.keySet());
        for (Row row : sheet) {
            String source = text(row.getCell(0)).toUpperCase();
            if (overrides.containsKey(source)) {
                cell(row, 1).setCellValue(overrides.get(source));
                remaining.remove(source);
            }
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("SourceBehavior does not contain sources: " + remaining);
        }
    }

    private static Cell cell(Row row, int column) {
        Cell result = row.getCell(column);
        return result == null ? row.createCell(column) : result;
    }

    private static String text(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }
}
