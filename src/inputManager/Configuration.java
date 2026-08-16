package inputManager;

import endorsement.WomRecommendationEffect;
import utils.Console;
import utils.Error;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central validated runtime configuration shared by loading, agent construction, simulation,
 * endorsement evaluation, reporting, and chart generation. Workbook values replace defaults,
 * after which CLI overrides may update selected public settings.
 */
public class Configuration {
    public final static String DEFAULT_FILE_NAME = "FAKENEWS_BASELINE_2";
    public final static int DISABLED = -1;
    public final static int MEMORY_INFINITE = -1;
    public final static double MEMORY_HALF_LIFE_DISABLED = -1.0;
    // TODO: Unify the Excel/UI disabled value with the internal disabled scenario representation.
    private final static int EXCEL_DISABLED = 0;
    private final static int CUSTOMIZED_SCENARIO = -2;

    private final static int D_PERIODS = 30;
    private final static int D_AGENTS = 10;
    private final static int D_CONTACTS = 17;
    private final static double D_FRIENDS = .7;
    private final static int D_LEVELS = 2; //2 or 3
    private final static int D_REPETITIONS = 0;
    private final static boolean D_GUI = false; //could be removed
    private final static double D_BASE = 1.2;
    private final static int D_MEMORY = 25;
    private final static double D_MEMORY_HALF_LIFE = MEMORY_HALF_LIFE_DISABLED;
    private final static int D_LEARNING_PERIODS = 100;
    private final static boolean D_SOURCE_REACH = false;
    private final static boolean D_WOM = false;
    private final static double D_WOM_RECEIVER_SCALE = 0.5;
    private final static WomRecommendationEffect D_WOM_FAKE_NEWS_EFFECT = WomRecommendationEffect.PENALIZE;
    private final static WomRecommendationEffect D_WOM_TRUE_NEWS_EFFECT = WomRecommendationEffect.REWARD;
    private final static int D_SCENARIO = DISABLED;

    private final static boolean D_COMPRESSED_RESULTS = false;
    private final static boolean D_SAVED_ENDORSEMENTS = false;
    private final static boolean D_SAVED_AGENT_DECISIONS = false;
    private final static boolean D_SAVED_DETAILED_AGENT_DECISIONS = false;
    private final static boolean D_SAVED_REPOSTS_PER_SOURCE = false;
    private final static boolean D_SAVED_FAKENEWS = false;
    private final static long LARGE_EXPERIMENT_OPERATIONS_WARNING_THRESHOLD = 1_000_000L;

    private final static String PERIODS_KEY = "PERIODS";
    private final static String AGENTS_KEY = "AGENTS";
    private final static String CONTACTS_KEY = "CONTACTS";
    private final static String FRIENDS_KEY = "FRIENDS";
    private final static String LEVELS_KEY = "LEVELS";
    private final static String REPETITIONS_KEY = "REPETITIONS";
    private final static String GUI_KEY = "GUI";
    private final static String BASE_KEY = "BASE";
    private final static String MEMORY_KEY = "MEMORY";
    private final static String MEMORY_HALF_LIFE_KEY = "MEMORY_HALF_LIFE";
    private final static String SOURCE_REACH_KEY = "SOURCE_REACH";
    private final static String WOM_KEY = "WOM";
    private final static String WOM_RECEIVER_SCALE_KEY = "WOM_RECEIVER_SCALE";
    private final static String WOM_FAKE_NEWS_EFFECT_KEY = "WOM_FAKE_NEWS_EFFECT";
    private final static String WOM_TRUE_NEWS_EFFECT_KEY = "WOM_TRUE_NEWS_EFFECT";
    private final static String SCENARIO_KEY = "SCENARIO";
    private final static String LEARNING_PERIODS_KEY = "LEARNING_PERIODS";
    private final static String COMPRESSED_RESULTS_KEY = "COMPRESSED_RESULTS";
    private final static String SAVED_ENDORSEMENTS_KEY = "SAVED_ENDORSEMENTS";
    private final static String SAVED_AGENT_DECISIONS_KEY = "SAVED_AGENT_DECISIONS";
    private final static String SAVED_DETAILED_AGENT_DECISIONS_KEY = "SAVED_DETAILED_AGENT_DECISIONS";
    private final static String SAVED_REPOSTS_PER_SOURCE_KEY = "SAVED_REPOSTS_PER_SOURCE";
    private final static String SAVED_FAKENEWS_KEY = "SAVED_FAKENEWS";

    private final static String[] REQUIRED_PARAMETERS = new String[]{
            PERIODS_KEY, AGENTS_KEY, CONTACTS_KEY, FRIENDS_KEY, LEVELS_KEY, REPETITIONS_KEY, GUI_KEY,
            BASE_KEY, MEMORY_KEY, SOURCE_REACH_KEY, WOM_KEY, SCENARIO_KEY, LEARNING_PERIODS_KEY,
            SAVED_ENDORSEMENTS_KEY, SAVED_REPOSTS_PER_SOURCE_KEY, SAVED_DETAILED_AGENT_DECISIONS_KEY,
            SAVED_AGENT_DECISIONS_KEY, COMPRESSED_RESULTS_KEY
    };

    private final static Set<String> REQUIRED_PARAMETER_SET = new HashSet<>(Arrays.asList(REQUIRED_PARAMETERS));
    private final static Set<String> SUPPORTED_PARAMETER_SET = new HashSet<>(REQUIRED_PARAMETER_SET);

    static {
        SUPPORTED_PARAMETER_SET.add(SAVED_FAKENEWS_KEY);
        SUPPORTED_PARAMETER_SET.add(MEMORY_HALF_LIFE_KEY);
        SUPPORTED_PARAMETER_SET.add(WOM_RECEIVER_SCALE_KEY);
        SUPPORTED_PARAMETER_SET.add(WOM_FAKE_NEWS_EFFECT_KEY);
        SUPPORTED_PARAMETER_SET.add(WOM_TRUE_NEWS_EFFECT_KEY);
    }

    public static String FILE_NAME;
    public static String OUTPUT_DIRECTORY;

    public static int NEWS_SOURCES;
    public static int ATTRIBUTES_SOURCE;
    public static int ATTRIBUTES_USER;

    public static int PERIODS = D_PERIODS;
    public static int AGENTS = D_AGENTS;
    public static int CONTACTS = D_CONTACTS;
    public static double FRIENDS = D_FRIENDS;
    public static int LEVELS = D_LEVELS; //2 or 3
    public static int REPETITIONS = D_REPETITIONS;
    public static boolean GUI = D_GUI;
    public static double BASE = D_BASE;
    public static int MEMORY = D_MEMORY;
    public static double MEMORY_HALF_LIFE = D_MEMORY_HALF_LIFE;
    public static boolean SOURCE_REACH = D_SOURCE_REACH;
    public static boolean WOM = D_WOM;
    public static double WOM_RECEIVER_SCALE = D_WOM_RECEIVER_SCALE;
    public static WomRecommendationEffect WOM_FAKE_NEWS_EFFECT = D_WOM_FAKE_NEWS_EFFECT;
    public static WomRecommendationEffect WOM_TRUE_NEWS_EFFECT = D_WOM_TRUE_NEWS_EFFECT;
    public static int SCENARIO = D_SCENARIO;
    public static int LEARNING_PERIODS = D_LEARNING_PERIODS;

    //debug to save information
    public static boolean COMPRESSED_RESULTS = D_COMPRESSED_RESULTS;
    public static boolean SAVED_REPOSTS_PER_SOURCE = D_SAVED_REPOSTS_PER_SOURCE;
    public static boolean SAVED_DETAILED_AGENT_DECISIONS = D_SAVED_DETAILED_AGENT_DECISIONS;
    public static boolean SAVED_AGENT_DECISIONS = D_SAVED_AGENT_DECISIONS;
    public static boolean SAVED_ENDORSEMENTS = D_SAVED_ENDORSEMENTS;
    public static boolean SAVED_FAKENEWS = D_SAVED_FAKENEWS;

    /**
     * Validates workbook configuration and installs every supported value or its default.
     * Warnings identify configurations that are valid but likely to suppress output or create
     * very large detailed reports.
     *
     * @param conf uppercase configuration keys mapped to numeric workbook values
     * @throws IllegalArgumentException when a supplied value violates its model constraint
     */
    public static void set(HashMap<String, Double> conf) {
        checkConfigurationInput(conf);

        PERIODS = conf.get(PERIODS_KEY) != null ? conf.get(PERIODS_KEY).intValue() : D_PERIODS;
        AGENTS = conf.get(AGENTS_KEY) != null ? conf.get(AGENTS_KEY).intValue() : D_AGENTS;
        CONTACTS = conf.get(CONTACTS_KEY) != null ? conf.get(CONTACTS_KEY).intValue() : D_CONTACTS;
        FRIENDS = conf.get(FRIENDS_KEY) != null ? conf.get(FRIENDS_KEY) : D_FRIENDS;
        LEVELS = conf.get(LEVELS_KEY) != null ? conf.get(LEVELS_KEY).intValue() : D_LEVELS;
        REPETITIONS = conf.get(REPETITIONS_KEY) != null ? conf.get(REPETITIONS_KEY).intValue() : D_REPETITIONS;
        GUI = conf.get(GUI_KEY) != null ? conf.get(GUI_KEY) == 1 : D_GUI;
        BASE = conf.get(BASE_KEY) != null ? conf.get(BASE_KEY) : D_BASE;
        MEMORY = conf.get(MEMORY_KEY) != null ? conf.get(MEMORY_KEY).intValue() : D_MEMORY;
        MEMORY_HALF_LIFE = conf.get(MEMORY_HALF_LIFE_KEY) != null
                ? conf.get(MEMORY_HALF_LIFE_KEY) : D_MEMORY_HALF_LIFE;
        SOURCE_REACH = conf.get(SOURCE_REACH_KEY) != null ? conf.get(SOURCE_REACH_KEY) == 1 : D_SOURCE_REACH;
        WOM = conf.get(WOM_KEY) != null ? conf.get(WOM_KEY) == 1 : D_WOM;
        WOM_RECEIVER_SCALE = conf.get(WOM_RECEIVER_SCALE_KEY) != null
                ? conf.get(WOM_RECEIVER_SCALE_KEY) : D_WOM_RECEIVER_SCALE;
        WOM_FAKE_NEWS_EFFECT = conf.get(WOM_FAKE_NEWS_EFFECT_KEY) != null
                ? WomRecommendationEffect.fromConfigurationValue(conf.get(WOM_FAKE_NEWS_EFFECT_KEY).intValue())
                : D_WOM_FAKE_NEWS_EFFECT;
        WOM_TRUE_NEWS_EFFECT = conf.get(WOM_TRUE_NEWS_EFFECT_KEY) != null
                ? WomRecommendationEffect.fromConfigurationValue(conf.get(WOM_TRUE_NEWS_EFFECT_KEY).intValue())
                : D_WOM_TRUE_NEWS_EFFECT;
        SCENARIO = conf.get(SCENARIO_KEY) != null ? normalizeScenario(conf.get(SCENARIO_KEY).intValue()) : D_SCENARIO;
        LEARNING_PERIODS = conf.get(LEARNING_PERIODS_KEY) != null ? conf.get(LEARNING_PERIODS_KEY).intValue() : D_LEARNING_PERIODS;

        COMPRESSED_RESULTS = conf.get(COMPRESSED_RESULTS_KEY) != null ? conf.get(COMPRESSED_RESULTS_KEY) == 1 : D_COMPRESSED_RESULTS;
        SAVED_ENDORSEMENTS = conf.get(SAVED_ENDORSEMENTS_KEY) != null ? conf.get(SAVED_ENDORSEMENTS_KEY) == 1 : D_SAVED_ENDORSEMENTS;
        SAVED_AGENT_DECISIONS = conf.get(SAVED_AGENT_DECISIONS_KEY) != null ? conf.get(SAVED_AGENT_DECISIONS_KEY) == 1 : D_SAVED_AGENT_DECISIONS;
        SAVED_DETAILED_AGENT_DECISIONS = conf.get(SAVED_DETAILED_AGENT_DECISIONS_KEY) != null ? conf.get(SAVED_DETAILED_AGENT_DECISIONS_KEY) == 1 : D_SAVED_DETAILED_AGENT_DECISIONS;
        SAVED_REPOSTS_PER_SOURCE = conf.get(SAVED_REPOSTS_PER_SOURCE_KEY) != null ? conf.get(SAVED_REPOSTS_PER_SOURCE_KEY) == 1 : D_SAVED_REPOSTS_PER_SOURCE;
        SAVED_FAKENEWS = conf.get(SAVED_FAKENEWS_KEY) != null ? conf.get(SAVED_FAKENEWS_KEY) == 1 : D_SAVED_FAKENEWS;

        warnIfLearningPeriodsCoverSimulation();
        warnIfMemoryModesOverlap();
        warnIfLargeExperimentSavesDetailedResults();
    }

    /**
     * Creates an output directory required by logs and generated workbooks.
     *
     * @param output directory path to create
     */
    private static void creatingOutputFolder(String output) {
        try {
            File dir = new File(output);
            if (!dir.exists() && !dir.mkdirs()) {
                Error.trigger("Directory cannot be created: " + output);
            }
        } catch (SecurityException se) {
            Error.trigger("Directory cannot be created: " + output + "\n ERROR: " + se, se);
        }
    }

    /**
     * Establishes the input label and timestamped run directory before logging begins.
     *
     * @param fileName workbook-derived run label without its extension
     */
    public static void setPath(String fileName) {
        FILE_NAME = fileName;
        DateFormat df = new SimpleDateFormat("dd-MM-yy(HH-mm-ss)");
        String requestedOutput = System.getProperty("fakenews.outputDirectory");
        OUTPUT_DIRECTORY = requestedOutput == null || requestedOutput.trim().isEmpty()
                ? "output/" + fileName + "_" + df.format(new Date())
                : requestedOutput;

        //checking and creating the output folder
        if (Files.notExists(Paths.get("output"))) {
            creatingOutputFolder("output");
        }

        //making the simulation directory
        try {
            File output = new File(OUTPUT_DIRECTORY);
            if (!output.exists() && !output.mkdirs()) {
                Error.trigger("Configuration.setPath: Directory cannot be created: " + OUTPUT_DIRECTORY);
            }
            Console.resetLogFile();
            Console.info("Configuration.setPath: Directory ready: " + OUTPUT_DIRECTORY);
        } catch (SecurityException se) {
            Error.trigger("Configuration.setPath: Directory cannot be created: " + OUTPUT_DIRECTORY +
                    "Configuration.setPath: ERROR: " + se, se);
        }
    }

    /**
     * Records loaded source/user attribute counts so endorsement evaluation can check alignment.
     *
     * @param newsSources number of attributes defined for each news source
     * @param snsUsers number of weights defined for the SNS-user prototype
     */
    public static void setAttributes(int newsSources, int snsUsers) {
        set("ATTRIBUTES_SOURCE", newsSources);
        set("ATTRIBUTES_USER", snsUsers);
    }

    /**
     * Records the loaded source count used to size simulation reports.
     *
     * @param newsSources number of loaded source definitions
     */
    public static void setNewsSources(int newsSources) {
        set("NEWS_SOURCES", newsSources);
    }

    /**
     * Routes a normalized numeric setting to its typed global runtime field.
     *
     * @param name supported configuration or derived-count key
     * @param value numeric value to install
     */
    private static void set(String name, double value) {
        switch (name.toUpperCase()) {
            case PERIODS_KEY:
                PERIODS = (int) value;
                break;
            case AGENTS_KEY:
                AGENTS = (int) value;
                break;
            case CONTACTS_KEY:
                CONTACTS = (int) value;
                break;
            case FRIENDS_KEY:
                FRIENDS = value;
                break;
            case "ATTRIBUTES_SOURCE":
                ATTRIBUTES_SOURCE = (int) value;
                break;
            case "ATTRIBUTES_USER":
                ATTRIBUTES_USER = (int) value;
                break;
            case "NEWS_SOURCES":
                NEWS_SOURCES = (int) value;
                break;
            case REPETITIONS_KEY:
                REPETITIONS = (int) value;
                break;
            case LEVELS_KEY:
                LEVELS = (int) value;
                break;
            case GUI_KEY:
                GUI = value == 1;
                break;
            case BASE_KEY:
                BASE = value;
                break;
            case MEMORY_KEY:
                MEMORY = (int) value;
                break;
            case MEMORY_HALF_LIFE_KEY:
                MEMORY_HALF_LIFE = value;
                break;
            case SOURCE_REACH_KEY:
                SOURCE_REACH = value == 1;
                break;
            case WOM_KEY:
                WOM = value == 1;
                break;
            case WOM_RECEIVER_SCALE_KEY:
                WOM_RECEIVER_SCALE = value;
                break;
            case WOM_FAKE_NEWS_EFFECT_KEY:
                WOM_FAKE_NEWS_EFFECT = WomRecommendationEffect.fromConfigurationValue((int) value);
                break;
            case WOM_TRUE_NEWS_EFFECT_KEY:
                WOM_TRUE_NEWS_EFFECT = WomRecommendationEffect.fromConfigurationValue((int) value);
                break;
            case SCENARIO_KEY:
                SCENARIO = normalizeScenario((int) value);
                break;
            case LEARNING_PERIODS_KEY:
                LEARNING_PERIODS = (int) value;
                break;
            case COMPRESSED_RESULTS_KEY:
                COMPRESSED_RESULTS = value == 1;
                break;
            case SAVED_ENDORSEMENTS_KEY:
                SAVED_ENDORSEMENTS = value == 1;
                break;
            case SAVED_DETAILED_AGENT_DECISIONS_KEY:
                SAVED_DETAILED_AGENT_DECISIONS = value == 1;
                break;
            case SAVED_AGENT_DECISIONS_KEY:
                SAVED_AGENT_DECISIONS = value == 1;
                break;
            case SAVED_REPOSTS_PER_SOURCE_KEY:
                SAVED_REPOSTS_PER_SOURCE = value == 1;
                break;
            case SAVED_FAKENEWS_KEY:
                SAVED_FAKENEWS = value == 1;
                break;
            default:
                Console.error("CONFIGURATOR.SET: Wrong Parameter: " + name.toUpperCase());
        }
    }

    /**
     * Warns about missing or unknown keys and delegates type/range checks for known settings.
     *
     * @param conf values parsed from the Configuration worksheet
     * @throws IllegalArgumentException when any supplied known value is invalid
     */
    private static void checkConfigurationInput(HashMap<String, Double> conf) {
        for (Map.Entry<String, Double> entry : conf.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                failConfiguration(entry.getKey() + " must be a finite numeric value.");
            }
        }

        for (String param : REQUIRED_PARAMETERS) {
            if (!conf.containsKey(param)) {
                Console.warn(param + " is missing.");
            }
        }

        for (String param : conf.keySet()) {
            if (!SUPPORTED_PARAMETER_SET.contains(param)) {
                Console.warn(param + " is not a recognized configuration parameter.");
            }
        }

        validatePositiveInt(conf, PERIODS_KEY);
        validatePositiveInt(conf, AGENTS_KEY);
        validateNonNegativeInt(conf, CONTACTS_KEY);
        validateRange(conf, FRIENDS_KEY, 0.0, 1.0);
        validateLevels(conf);
        validateNonNegativeInt(conf, REPETITIONS_KEY);
        validateBoolean(conf, GUI_KEY);
        validateGreaterThan(conf, BASE_KEY, 0.0);
        validateMemory(conf);
        validateMemoryHalfLife(conf);
        validateBoolean(conf, SOURCE_REACH_KEY);
        validateBoolean(conf, WOM_KEY);
        validateNonNegative(conf, WOM_RECEIVER_SCALE_KEY);
        validateWomRecommendationEffect(conf, WOM_FAKE_NEWS_EFFECT_KEY);
        validateWomRecommendationEffect(conf, WOM_TRUE_NEWS_EFFECT_KEY);
        validateScenario(conf);
        validateNonNegativeInt(conf, LEARNING_PERIODS_KEY);
        validateBoolean(conf, SAVED_ENDORSEMENTS_KEY);
        validateBoolean(conf, SAVED_REPOSTS_PER_SOURCE_KEY);
        validateBoolean(conf, SAVED_FAKENEWS_KEY);
        validateBoolean(conf, SAVED_DETAILED_AGENT_DECISIONS_KEY);
        validateBoolean(conf, SAVED_AGENT_DECISIONS_KEY);
        validateBoolean(conf, COMPRESSED_RESULTS_KEY);
    }

    /**
     * Requires a present parameter to be an integer greater than zero.
     *
     * @param conf configuration map
     * @param param key to validate when present
     */
    private static void validatePositiveInt(HashMap<String, Double> conf, String param) {
        if (conf.containsKey(param)) {
            validateInteger(conf, param);
            if (conf.get(param).intValue() <= 0) {
                failConfiguration(param + " must be greater than 0.");
            }
        }
    }

    /**
     * Requires a present parameter to be a nonnegative integer.
     *
     * @param conf configuration map
     * @param param key to validate when present
     */
    private static void validateNonNegativeInt(HashMap<String, Double> conf, String param) {
        if (conf.containsKey(param)) {
            validateInteger(conf, param);
            if (conf.get(param).intValue() < 0) {
                failConfiguration(param + " must be greater than or equal to 0.");
            }
        }
    }

    /**
     * Rejects fractional numeric values for settings used as counts or sentinel integers.
     *
     * @param conf configuration map containing {@code param}
     * @param param key whose numeric value must be integral
     */
    private static void validateInteger(HashMap<String, Double> conf, String param) {
        double value = conf.get(param);
        if (value != Math.rint(value)) {
            failConfiguration(param + " must be an integer.");
        }
    }

    /**
     * Requires a present value to fall within an inclusive numeric range.
     *
     * @param conf configuration map
     * @param param key to validate
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     */
    private static void validateRange(HashMap<String, Double> conf, String param, double min, double max) {
        if (conf.containsKey(param) && (conf.get(param) < min || conf.get(param) > max)) {
            failConfiguration(param + " must be between " + min + " and " + max + ".");
        }
    }

    /**
     * Requires a present numeric value to exceed a lower bound.
     *
     * @param conf configuration map
     * @param param key to validate
     * @param min exclusive lower bound
     */
    private static void validateGreaterThan(HashMap<String, Double> conf, String param, double min) {
        if (conf.containsKey(param) && conf.get(param) <= min) {
            failConfiguration(param + " must be greater than " + min + ".");
        }
    }

    /**
     * Restricts endorsement distributions to the binary or ternary scales implemented by scoring.
     *
     * @param conf configuration map
     */
    private static void validateLevels(HashMap<String, Double> conf) {
        if (conf.containsKey(LEVELS_KEY)) {
            validateInteger(conf, LEVELS_KEY);
            int levels = conf.get(LEVELS_KEY).intValue();
            if (levels != 2 && levels != 3) {
                failConfiguration(LEVELS_KEY + " must be 2 or 3.");
            }
        }
    }

    /**
     * Accepts either the infinite-memory sentinel or a nonnegative period window.
     *
     * @param conf configuration map
     */
    private static void validateMemory(HashMap<String, Double> conf) {
        if (conf.containsKey(MEMORY_KEY)) {
            validateInteger(conf, MEMORY_KEY);
            int memory = conf.get(MEMORY_KEY).intValue();
            if (memory != MEMORY_INFINITE && memory < 0) {
                failConfiguration(MEMORY_KEY + " must be MEMORY_INFINITE (" + MEMORY_INFINITE +
                        ") or greater than or equal to 0.");
            }
        }
    }

    /**
     * Accepts the disabled sentinel or a strictly positive exponential-memory half-life.
     * A value of zero is undefined because the decay exponent divides by the half-life.
     *
     * @param conf configuration map
     */
    private static void validateMemoryHalfLife(HashMap<String, Double> conf) {
        if (!conf.containsKey(MEMORY_HALF_LIFE_KEY)) {
            return;
        }

        double halfLife = conf.get(MEMORY_HALF_LIFE_KEY);
        if (halfLife != MEMORY_HALF_LIFE_DISABLED && halfLife <= 0.0) {
            failConfiguration(MEMORY_HALF_LIFE_KEY + " must be " + MEMORY_HALF_LIFE_DISABLED +
                    " (disabled) or greater than 0.");
        }
    }

    /** Requires a present numeric scale to be zero or positive. */
    private static void validateNonNegative(HashMap<String, Double> conf, String param) {
        if (conf.containsKey(param) && conf.get(param) < 0.0) {
            failConfiguration(param + " must be greater than or equal to 0.");
        }
    }

    /**
     * Accepts the Excel/legacy disabled values or the customized-scenario identifier.
     *
     * @param conf configuration map
     */
    private static void validateScenario(HashMap<String, Double> conf) {
        if (conf.containsKey(SCENARIO_KEY)) {
            validateInteger(conf, SCENARIO_KEY);
            int scenario = conf.get(SCENARIO_KEY).intValue();
            if (scenario != EXCEL_DISABLED && scenario != DISABLED && scenario != CUSTOMIZED_SCENARIO) {
                failConfiguration(SCENARIO_KEY + " must be " + EXCEL_DISABLED + " (disabled), " +
                        DISABLED + " (disabled legacy), or " + CUSTOMIZED_SCENARIO + " (customized).");
            }
        }
    }

    /**
     * Converts the workbook's zero disabled value to the internal disabled sentinel.
     *
     * @param scenario workbook or internal scenario identifier
     * @return normalized internal identifier
     */
    private static int normalizeScenario(int scenario) {
        return scenario == EXCEL_DISABLED ? DISABLED : scenario;
    }

    /**
     * Requires a present workbook boolean to use its numeric {@code 0}/{@code 1} encoding.
     *
     * @param conf configuration map
     * @param param key to validate
     */
    private static void validateBoolean(HashMap<String, Double> conf, String param) {
        if (conf.containsKey(param)) {
            double value = conf.get(param);
            if (value != 0.0 && value != 1.0) {
                failConfiguration(param + " must be 0 or 1.");
            }
        }
    }

    /**
     * Requires an optional WOM outcome policy to be an integer in the supported signed scale.
     *
     * @param conf configuration map
     * @param param fake-news or true-news WOM policy key
     */
    private static void validateWomRecommendationEffect(HashMap<String, Double> conf, String param) {
        if (!conf.containsKey(param)) {
            return;
        }

        validateInteger(conf, param);
        int value = conf.get(param).intValue();
        if (value < -1 || value > 1) {
            failConfiguration(param + " must be -1 (penalize), 0 (ignore), or 1 (reward).");
        }
    }

    /**
     * Produces the consistent exception used to abort invalid configuration loading.
     *
     * @param message constraint-specific diagnostic
     * @throws IllegalArgumentException always, with the standardized prefix
     */
    private static void failConfiguration(String message) {
        throw new IllegalArgumentException("Invalid configuration: " + message);
    }

    /** Warns when the learning window prevents all period-level results from being saved. */
    private static void warnIfLearningPeriodsCoverSimulation() {
        if (LEARNING_PERIODS >= PERIODS) {
            Console.warn("Configuration: LEARNING_PERIODS (" + LEARNING_PERIODS +
                    ") is greater than or equal to PERIODS (" + PERIODS +
                    "). No post-learning periods will be reported; repost charts may have no data.");
        }
    }

    /** Warns when gradual decay is combined with the legacy abrupt memory cutoff. */
    private static void warnIfMemoryModesOverlap() {
        if (MEMORY != MEMORY_INFINITE && MEMORY_HALF_LIFE != MEMORY_HALF_LIFE_DISABLED) {
            Console.warn("Configuration: MEMORY and MEMORY_HALF_LIFE are both active. " +
                    "Endorsements will decay gradually and then be removed by the MEMORY cutoff. " +
                    "Use MEMORY=-1 for a pure exponential-decay experiment.");
        }
    }

    /**
     * Estimates agent-period work and warns when detailed output is enabled for a large run.
     */
    private static void warnIfLargeExperimentSavesDetailedResults() {
        if (!SAVED_ENDORSEMENTS && !SAVED_AGENT_DECISIONS && !SAVED_DETAILED_AGENT_DECISIONS &&
                !SAVED_REPOSTS_PER_SOURCE) {
            return;
        }

        long simulationRuns = (long) REPETITIONS + 1L;
        long agentPeriodOperations = simulationRuns * PERIODS * AGENTS;
        if (agentPeriodOperations < LARGE_EXPERIMENT_OPERATIONS_WARNING_THRESHOLD) {
            return;
        }

        String enabledDetails = enabledDetailedResultKeys();
        Console.warn("Configuration: large experiment configured with detailed result saving enabled (" +
                enabledDetails + "). Estimated agent-period operations=" + agentPeriodOperations +
                " from runs=" + simulationRuns +
                ", periods=" + PERIODS +
                ", agents=" + AGENTS +
                ". This can create very large workbooks and slow execution; disable unneeded SAVED_* options " +
                "or enable COMPRESSED_RESULTS for large experiments.");
    }

    /**
     * Builds the option list included in the large-report warning.
     *
     * @return comma-separated enabled detailed-output keys
     */
    private static String enabledDetailedResultKeys() {
        StringBuilder keys = new StringBuilder();
        appendEnabledKey(keys, SAVED_ENDORSEMENTS, SAVED_ENDORSEMENTS_KEY);
        appendEnabledKey(keys, SAVED_AGENT_DECISIONS, SAVED_AGENT_DECISIONS_KEY);
        appendEnabledKey(keys, SAVED_DETAILED_AGENT_DECISIONS, SAVED_DETAILED_AGENT_DECISIONS_KEY);
        appendEnabledKey(keys, SAVED_REPOSTS_PER_SOURCE, SAVED_REPOSTS_PER_SOURCE_KEY);
        return keys.toString();
    }

    /**
     * Adds one enabled option to a comma-separated diagnostic list.
     *
     * @param keys accumulating option list
     * @param enabled whether the option should be included
     * @param key configuration key to append
     */
    private static void appendEnabledKey(StringBuilder keys, boolean enabled, String key) {
        if (!enabled) {
            return;
        }

        if (keys.length() > 0) {
            keys.append(", ");
        }
        keys.append(key);
    }

    /**
     * Serializes current runtime settings in stable workbook/report order.
     *
     * @return insertion-ordered configuration map using numeric boolean encodings
     */
    public static Map<String, Double> toMap() {
        Map<String, Double> conf = new LinkedHashMap<>();
        conf.put(PERIODS_KEY, (double) PERIODS);
        conf.put(AGENTS_KEY, (double) AGENTS);
        conf.put(CONTACTS_KEY, (double) CONTACTS);
        conf.put(FRIENDS_KEY, FRIENDS);
        conf.put(LEVELS_KEY, (double) LEVELS);
        conf.put(REPETITIONS_KEY, (double) REPETITIONS);
        conf.put(GUI_KEY, GUI ? 1.0 : 0.0);
        conf.put(BASE_KEY, BASE);
        conf.put(MEMORY_KEY, (double) MEMORY);
        conf.put(MEMORY_HALF_LIFE_KEY, MEMORY_HALF_LIFE);
        conf.put(SOURCE_REACH_KEY, SOURCE_REACH ? 1.0 : 0.0);
        conf.put(WOM_KEY, WOM ? 1.0 : 0.0);
        conf.put(WOM_RECEIVER_SCALE_KEY, WOM_RECEIVER_SCALE);
        conf.put(WOM_FAKE_NEWS_EFFECT_KEY, (double) WOM_FAKE_NEWS_EFFECT.getConfigurationValue());
        conf.put(WOM_TRUE_NEWS_EFFECT_KEY, (double) WOM_TRUE_NEWS_EFFECT.getConfigurationValue());
        conf.put(SCENARIO_KEY, (double) SCENARIO);
        conf.put(LEARNING_PERIODS_KEY, (double) LEARNING_PERIODS);

        conf.put(COMPRESSED_RESULTS_KEY, COMPRESSED_RESULTS ? 1.0 : 0.0);
        conf.put(SAVED_ENDORSEMENTS_KEY, SAVED_ENDORSEMENTS ? 1.0 : 0.0);
        conf.put(SAVED_DETAILED_AGENT_DECISIONS_KEY, SAVED_DETAILED_AGENT_DECISIONS ? 1.0 : 0.0);
        conf.put(SAVED_AGENT_DECISIONS_KEY, SAVED_AGENT_DECISIONS ? 1.0 : 0.0);
        conf.put(SAVED_REPOSTS_PER_SOURCE_KEY, SAVED_REPOSTS_PER_SOURCE ? 1.0 : 0.0);
        conf.put(SAVED_FAKENEWS_KEY, SAVED_FAKENEWS ? 1.0 : 0.0);

        return conf;
    }

    /**
     * Formats the normalized settings for CLI startup logging.
     *
     * @return current runtime configuration formatted for startup logging
     */
    public static String toStringConfiguration() {
        return Configuration.toMap().toString();
    }
}
