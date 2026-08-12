import agent.SNSUserFactory;
import agent.NewsSourceFactory;
import inputManager.Configuration;
import inputManager.Loader;
import gui.Chart;
import utils.Console;
import reporter.Reporter;
import simulation.Simulation;

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

        Loader.load(options.inputFile);
        options.applyOverrides();
        Reporter.clear();

        try {
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
        private Boolean gui;

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
                    case "--gui":
                        options.gui = true;
                        break;
                    case "--no-gui":
                        options.gui = false;
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

        /** Applies only explicitly supplied CLI overrides after workbook configuration is installed. */
        private void applyOverrides() {
            if (periods != null) Configuration.PERIODS = periods;
            if (agents != null) Configuration.AGENTS = agents;
            if (repetitions != null) Configuration.REPETITIONS = repetitions;
            if (learningPeriods != null) Configuration.LEARNING_PERIODS = learningPeriods;
            if (wom != null) Configuration.WOM = wom;
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
            System.out.println("      --gui / --no-gui     Enable or disable charts.");
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
