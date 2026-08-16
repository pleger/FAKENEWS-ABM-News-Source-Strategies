import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reproducibly migrates FAKENEWS_BASELINE_3.xlsx to the header-based strategy schema while
 * preserving every existing configuration, user, source, and reach value.
 */
public final class CreateBaseline4Workbook {
    private static final String CREDIBILITY = "CREDIBILIDAD DE LA FUENTE";

    private CreateBaseline4Workbook() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CreateBaseline4Workbook SOURCE.xlsx DESTINATION.xlsx");
        }

        File source = new File(args[0]);
        File destination = new File(args[1]);
        if (!source.isFile()) {
            throw new IllegalArgumentException("Source workbook does not exist: " + source);
        }
        if (source.getCanonicalFile().equals(destination.getCanonicalFile())) {
            throw new IllegalArgumentException("Destination must differ from source");
        }

        try (FileInputStream input = new FileInputStream(source);
             Workbook workbook = WorkbookFactory.create(input)) {
            validateBaseline3Configuration(workbook.getSheet("Configuration"));
            LinkedHashMap<String, Double> probabilities = deriveLegacyProbabilities(workbook);
            replaceSourceBehavior(workbook, probabilities);
            replaceStrategies(workbook);
            replaceScenario(workbook);

            File parent = destination.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Could not create destination directory: " + parent);
            }
            try (FileOutputStream output = new FileOutputStream(destination)) {
                workbook.write(output);
            }
        }

        validateOutput(destination);
        System.out.println("Created and validated: " + destination.getAbsolutePath());
    }

    private static void validateBaseline3Configuration(Sheet configuration) {
        require(configuration != null, "Configuration sheet is missing");
        Map<String, Double> values = new LinkedHashMap<>();
        for (Row row : configuration) {
            if (row.getCell(0) != null && row.getCell(1) != null) {
                values.put(row.getCell(0).toString().trim().toUpperCase(),
                        row.getCell(1).getNumericCellValue());
            }
        }
        require(values.get("MEMORY") != null && values.get("MEMORY") == -1.0,
                "Baseline 3 must contain MEMORY=-1");
        require(values.get("MEMORY_HALF_LIFE") != null && values.get("MEMORY_HALF_LIFE") == 0.33,
                "Baseline 3 must contain MEMORY_HALF_LIFE=0.33");
        require(values.get("CONTACTS") != null && values.get("CONTACTS") == 5.0,
                "Baseline 3 must contain CONTACTS=5");
        require(values.get("FRIENDS") != null && values.get("FRIENDS") == 1.0,
                "Baseline 3 must contain FRIENDS=1");
        require(values.get("SAVED_FAKENEWS") != null && values.get("SAVED_FAKENEWS") == 1.0,
                "Baseline 3 must contain SAVED_FAKENEWS=1");
    }

    private static LinkedHashMap<String, Double> deriveLegacyProbabilities(Workbook workbook) {
        Sheet configuration = workbook.getSheet("Configuration");
        int levels = 2;
        for (Row row : configuration) {
            if (row.getCell(0) != null && "LEVELS".equalsIgnoreCase(row.getCell(0).toString().trim())) {
                levels = (int) row.getCell(1).getNumericCellValue();
            }
        }

        Sheet sources = workbook.getSheet("NewsSources");
        require(sources != null, "NewsSources sheet is missing");
        Row namesRow = sources.getRow(1);
        require(namesRow != null, "NewsSources source-name row is missing");
        List<String> names = new ArrayList<>();
        for (Cell cell : namesRow) {
            if (cell.getColumnIndex() > 0 && (cell.getColumnIndex() + 1) % levels == 0) {
                names.add(cell.toString().trim().toUpperCase());
            }
        }

        Row credibilityRow = null;
        for (Row row : sources) {
            if (row.getCell(0) != null && CREDIBILITY.equalsIgnoreCase(row.getCell(0).toString().trim())) {
                credibilityRow = row;
                break;
            }
        }
        require(credibilityRow != null, "NewsSources credibility row is missing");

        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); ++i) {
            double probability = credibilityRow.getCell(1 + i * levels).getNumericCellValue();
            require(probability >= 0.0 && probability <= 1.0,
                    "Derived probability is outside [0,1] for " + names.get(i));
            result.put(names.get(i), probability);
        }
        require(!result.isEmpty(), "No source probabilities could be derived");
        return result;
    }

    private static void replaceSourceBehavior(Workbook workbook, LinkedHashMap<String, Double> probabilities) {
        removeSheetIfPresent(workbook, "SourceBehavior");
        Sheet sheet = workbook.createSheet("SourceBehavior");
        CellStyle header = createHeaderStyle(workbook);
        CellStyle probability = workbook.createCellStyle();
        probability.setDataFormat(workbook.createDataFormat().getFormat("0.000"));

        Row headerRow = sheet.createRow(0);
        writeHeader(headerRow, header, "SOURCE", "FAKE_NEWS_PROBABILITY");
        int rowIndex = 1;
        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(entry.getKey());
            Cell probabilityCell = row.createCell(1);
            probabilityCell.setCellValue(entry.getValue());
            probabilityCell.setCellStyle(probability);
        }

        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createDecimalConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, "0", "1");
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(1, probabilities.size(), 1, 1));
        validation.setShowErrorBox(true);
        validation.createErrorBox("Invalid probability", "Enter a value between 0 and 1.");
        sheet.addValidationData(validation);
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 28 * 256);
    }

    private static void replaceStrategies(Workbook workbook) {
        removeSheetIfPresent(workbook, "Strategies");
        Sheet sheet = workbook.createSheet("Strategies");
        CellStyle header = createHeaderStyle(workbook);
        Row headerRow = sheet.createRow(0);
        writeHeader(headerRow, header, "STRATEGY", "ATTRIBUTE");

        LinkedHashMap<String, String[]> definitions = new LinkedHashMap<>();
        definitions.put("ENGAGEMENT", new String[]{
                "EMOCIONES EVOCADAS POSITIVAS", "EMOCIONES EVOCADAS NEGATIVAS",
                "LENGUAJE SENCILLO", "SENSACIONALISMO DE LA NOTICIA",
                "DIVERSIÓN DE LA INFORMACIÓN", "CONTENIDO AUDIOVISUAL", "HASHTAGS"});
        definitions.put("PROXIMITY", new String[]{
                "PROXIMIDAD POLÍTICA", "PROXIMIDAD GEOGRÁFICA AL PROBLEMA",
                "PROXIMIDAD SOCIAL AL PROBLEMA"});
        definitions.put("INFORMATIONAL_CAMOUFLAGE", new String[]{
                "CALIDAD DE LA INFORMACIÓN", "ENLACES"});
        definitions.put("CREDIBILITY_CAMOUFLAGE", new String[]{CREDIBILITY});

        int rowIndex = 1;
        for (Map.Entry<String, String[]> entry : definitions.entrySet()) {
            for (String attribute : entry.getValue()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(attribute);
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 32 * 256);
        sheet.setColumnWidth(1, 40 * 256);
    }

    private static void replaceScenario(Workbook workbook) {
        int oldIndex = workbook.getSheetIndex("Scenario");
        if (oldIndex >= 0) workbook.removeSheetAt(oldIndex);
        Sheet sheet = workbook.createSheet("Scenario");
        if (oldIndex >= 0) workbook.setSheetOrder("Scenario", oldIndex);
        CellStyle header = createHeaderStyle(workbook);
        Row headerRow = sheet.createRow(0);
        writeHeader(headerRow, header, "FROM", "TO", "START_PERIOD", "END_PERIOD",
                "STRATEGIES", "ATTRIBUTES");
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("TRADITIONAL_MEDIA");
        row.createCell(1).setCellValue("FAKE_NEWS_SOURCE");
        row.createCell(2).setCellValue(1);
        row.createCell(3).setCellValue(-1);
        row.createCell(4).setCellValue("ENGAGEMENT");
        row.createCell(5).setCellValue("");
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 32 * 256);
        sheet.setColumnWidth(5, 40 * 256);
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private static void writeHeader(Row row, CellStyle style, String... values) {
        for (int i = 0; i < values.length; ++i) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private static void removeSheetIfPresent(Workbook workbook, String name) {
        int index = workbook.getSheetIndex(name);
        if (index >= 0) workbook.removeSheetAt(index);
    }

    private static void validateOutput(File output) throws Exception {
        try (FileInputStream input = new FileInputStream(output);
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet behavior = workbook.getSheet("SourceBehavior");
            Sheet strategies = workbook.getSheet("Strategies");
            Sheet scenario = workbook.getSheet("Scenario");
            require(behavior != null && behavior.getLastRowNum() == 4,
                    "SourceBehavior must contain four source rows");
            require(strategies != null && strategies.getLastRowNum() == 13,
                    "Strategies must contain thirteen definition rows");
            require(scenario != null && "START_PERIOD".equals(scenario.getRow(0).getCell(2).toString()),
                    "Scenario must use the header-based schema");
            require("ENGAGEMENT".equals(scenario.getRow(1).getCell(4).toString()),
                    "Scenario proposal must select ENGAGEMENT");
            validateBaseline3Configuration(workbook.getSheet("Configuration"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
