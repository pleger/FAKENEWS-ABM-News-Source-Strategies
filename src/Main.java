import agent.SNSUserFactory;
import agent.NewsSourceFactory;
import endorsement.WomRecommendationEffect;
import inputManager.Configuration;
import inputManager.Loader;
import gui.Chart;
import utils.Console;
import reporter.Reporter;
import simulation.Simulation;
import utils.Randomness;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Command-line entry point that loads one workbook, applies CLI overrides, constructs the model,
 * executes all configured repetitions, writes reports, and releases input resources.
 */
public class Main {

    /**
     * Runs the complete Java reference implementation or handles a help/listing command.
     *
     * @param args command-line options and optional workbook name/path
     */
    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        if (options.help) {
            CliOptions.printHelp();
            return;
        }
        if (options.listInputs) {
            CliOptions.listInputs();
            return;
        }

        options.prepareExecutionEnvironment();
        try {
            Loader.load(options.inputFile);
            options.applyOverrides();
            Reporter.clear();
            Console.info("MAIN: Configuration loaded -> {" + Configuration.toStringConfiguration() + " }");
            Simulation s = new Simulation(SNSUserFactory.createFromInput(), NewsSourceFactory.createFromInput(),
                    Configuration.PERIODS);

            Instant start = Instant.now();
            for (int i = 1; i <= Configuration.REPETITIONS + 1; ++i) {
                Console.info(s);
                s.run();
            }
            if (Configuration.GUI && Configuration.REPETITIONS > 0) {
                Chart.displayAggregateReposts(NewsSourceFactory.getNewsSources());
            }
            Instant end = Instant.now();


            Duration timeElapsed = Duration.between(start, end);
            Console.info("Main: Simulation executions took " + timeElapsed.toMinutes() + " mins");
            Reporter.write();
            Console.end("Main: End.");
        } finally {
            Loader.close();
            options.clearExecutionEnvironment();
        }
    }

    /** Parsed command-line state kept separate from workbook configuration until loading completes. */
    private static final class CliOptions {
        private String inputFile = "";
        private boolean help = false;
        private boolean listInputs = false;
        private Integer periods;
        private Integer agents;
        private Integer repetitions;
        private Integer learningPeriods;
        private Boolean wom;
        private WomRecommendationEffect womFakeNewsEffect;
        private WomRecommendationEffect womTrueNewsEffect;
        private Boolean gui;
        private Long seed;
        private String outputDirectory;

        /**
         * Parses supported flags, overrides, and a positional input name.
         *
         * @param args raw command-line arguments
         * @return parsed option state
         * @throws IllegalArgumentException for unknown options, missing values, or invalid integers
         */
        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; ++i) {
                String arg = args[i];
                switch (arg) {
                    case "-h":
                    case "--help":
                        options.help = true;
                        break;
                    case "--list-inputs":
                        options.listInputs = true;
                        break;
                    case "-i":
                    case "--input":
                        options.inputFile = requireValue(args, ++i, arg);
                        break;
                    case "--periods":
                        options.periods = Integer.parseInt(requireValue(args, ++i, arg));
                        break;
                    case "--agents":
                        options.agents = Integer.parseInt(requireValue(args, ++i, arg));
                        break;
                    case "--repetitions":
                        options.repetitions = Integer.parseInt(requireValue(args, ++i, arg));
                        break;
                    case "--learning-periods":
                        options.learningPeriods = Integer.parseInt(requireValue(args, ++i, arg));
                        break;
                    case "--wom":
                        options.wom = true;
                        break;
                    case "--no-wom":
                        options.wom = false;
                        break;
                    case "--wom-fake-news-effect":
                        options.womFakeNewsEffect = parseWomEffect(requireValue(args, ++i, arg), arg);
                        break;
                    case "--wom-true-news-effect":
                        options.womTrueNewsEffect = parseWomEffect(requireValue(args, ++i, arg), arg);
                        break;
                    case "--gui":
                        options.gui = true;
                        break;
                    case "--no-gui":
                        options.gui = false;
                        break;
                    case "--seed":
                        options.seed = Long.parseLong(requireValue(args, ++i, arg));
                        break;
                    case "--output-directory":
                        options.outputDirectory = requireValue(args, ++i, arg);
                        break;
                    default:
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + arg + ". Use --help.");
                        }
                        options.inputFile = arg;
                }
            }
            return options;
        }

        /** Installs process-scoped reproducibility and output controls before workbook loading. */
        private void prepareExecutionEnvironment() {
            if (seed != null) Randomness.setSeed(seed);
            if (outputDirectory != null) {
                if (outputDirectory.trim().isEmpty()) {
                    throw new IllegalArgumentException("--output-directory must not be blank.");
                }
                System.setProperty("fakenews.outputDirectory", outputDirectory);
            }
        }

        /** Prevents repeated direct invocations of Main in one JVM from leaking CLI state. */
        private void clearExecutionEnvironment() {
            if (seed != null) Randomness.clearSeed();
            if (outputDirectory != null) System.clearProperty("fakenews.outputDirectory");
        }

        /**
         * Retrieves the token following a value-bearing option.
         *
         * @param args complete argument array
         * @param index expected value position
         * @param option option name used in diagnostics
         * @return value token
         * @throws IllegalArgumentException when no token exists at {@code index}
         */
        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        /** Parses the signed WOM policy used for fake or true recommended publications. */
        private static WomRecommendationEffect parseWomEffect(String value, String option) {
            try {
                return WomRecommendationEffect.fromConfigurationValue(Integer.parseInt(value));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(option + " must be -1 (penalize), 0 (ignore), or 1 (reward).");
            }
        }

        /** Applies only explicitly supplied CLI overrides after workbook configuration is installed. */
        private void applyOverrides() {
            if (periods != null) Configuration.PERIODS = periods;
            if (agents != null) Configuration.AGENTS = agents;
            if (repetitions != null) Configuration.REPETITIONS = repetitions;
            if (learningPeriods != null) Configuration.LEARNING_PERIODS = learningPeriods;
            if (wom != null) Configuration.WOM = wom;
            if (womFakeNewsEffect != null) Configuration.WOM_FAKE_NEWS_EFFECT = womFakeNewsEffect;
            if (womTrueNewsEffect != null) Configuration.WOM_TRUE_NEWS_EFFECT = womTrueNewsEffect;
            if (gui != null) Configuration.GUI = gui;
        }

        /** Prints CLI usage without loading or executing a simulation. */
        private static void printHelp() {
            System.out.println("FAKENEWS-ABM");
            System.out.println();
            System.out.println("Usage:");
            System.out.println("  java -cp \"build/classes:lib/*\" Main --input <input-name-or-xlsx> [options]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  -i, --input <file>       Input workbook name or path.");
            System.out.println("      --list-inputs        Show available input workbooks.");
            System.out.println("      --periods <n>        Override PERIODS from the workbook.");
            System.out.println("      --agents <n>         Override AGENTS from the workbook.");
            System.out.println("      --repetitions <n>    Override REPETITIONS from the workbook.");
            System.out.println("      --learning-periods <n>");
            System.out.println("                            Override LEARNING_PERIODS from the workbook.");
            System.out.println("      --wom / --no-wom     Enable or disable contact-based sharing.");
            System.out.println("      --wom-fake-news-effect <-1|0|1>");
            System.out.println("                            Penalize, ignore, or reward a fake-news recommendation.");
            System.out.println("      --wom-true-news-effect <-1|0|1>");
            System.out.println("                            Penalize, ignore, or reward a true-news recommendation.");
            System.out.println("      --gui / --no-gui     Enable or disable charts.");
            System.out.println("      --seed <long>         Use a reproducible random seed.");
            System.out.println("      --output-directory <path>");
            System.out.println("                            Write this run to an explicit directory.");
            System.out.println("  -h, --help               Show this help.");
        }

        /** Lists non-temporary Excel workbooks available in the primary input directory. */
        private static void listInputs() {
            File inputDir = new File("input");
            File[] files = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx") && !name.startsWith("~$"));
            if (files == null || files.length == 0) {
                System.out.println("No .xlsx inputs found in " + inputDir.getAbsolutePath());
                return;
            }
            Arrays.sort(files);
            for (File file : files) {
                System.out.println(file.getName());
            }
        }
    }
}
