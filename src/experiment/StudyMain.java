package experiment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** CLI for planning or executing the paper's complete Java-orchestrated study. */
public final class StudyMain {
    private StudyMain() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help) {
            printHelp();
            return;
        }
        StudySpecification study = loadStudy(options.studyClass, options.baseWorkbook);
        StudyRunner runner = new StudyRunner(study);
        Path output = options.output != null ? options.output : defaultOutput(study.getId());
        List<SimulationRunSpecification> runs = runner.plan(
                options.questions, options.seedLimit, options.maxRuns, output);
        printPlan(study, runs, output, options.execute);
        if (options.execute) {
            runner.execute(runs, output, options.jobs);
            System.out.println("Study completed: " + output.toAbsolutePath());
        } else {
            System.out.println("Plan only. Add --execute to run it.");
        }
    }

    private static StudySpecification loadStudy(String className, Path baseWorkbook) throws Exception {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("--study-class is required. Use --help for details.");
        }
        Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
        if (!(instance instanceof StudyProvider)) {
            throw new IllegalArgumentException(className + " does not implement experiment.StudyProvider");
        }
        return ((StudyProvider) instance).create(baseWorkbook);
    }

    private static Path defaultOutput(String studyId) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return Paths.get("output", "studies", studyId + "_" + timestamp);
    }

    private static void printPlan(StudySpecification study, List<SimulationRunSpecification> runs,
                                  Path output, boolean execute) {
        LinkedHashSet<String> questions = new LinkedHashSet<>();
        LinkedHashSet<String> experiments = new LinkedHashSet<>();
        LinkedHashSet<String> conditions = new LinkedHashSet<>();
        for (SimulationRunSpecification run : runs) {
            questions.add(run.getResearchQuestionId());
            experiments.add(run.getExperimentId());
            conditions.add(run.getResearchQuestionId() + "/" + run.getExperimentId() + "/"
                    + run.getCondition().getId());
        }
        System.out.println(study.getTitle());
        System.out.println("Mode: " + (execute ? "EXECUTE" : "PLAN"));
        System.out.println("Research questions: " + questions);
        System.out.println("Experiments: " + experiments.size());
        System.out.println("Conditions: " + conditions.size());
        System.out.println("Simulation runs: " + runs.size());
        System.out.println("Output: " + output.toAbsolutePath());
    }

    private static void printHelp() {
        System.out.println("Usage: java -cp \"build/classes:lib/*\" experiment.StudyMain [options]");
        System.out.println("  --study-class CLASS    StudyProvider implementation; required.");
        System.out.println("  --questions RQ1,RQ2   Select questions; default is all.");
        System.out.println("  --seeds N             Use the first N declared common seeds per experiment.");
        System.out.println("  --max-runs N          Limit total runs, useful for smoke tests.");
        System.out.println("  --jobs N              Concurrent isolated JVMs; default 2.");
        System.out.println("  --base FILE           Base workbook; default FAKENEWS_BASELINE_3.");
        System.out.println("  --output DIRECTORY    New or existing resumable study directory.");
        System.out.println("  --execute             Execute; omission produces a safe plan only.");
        System.out.println("  -h, --help            Show this help.");
    }

    private static final class Options {
        private Set<String> questions = new LinkedHashSet<>();
        private String studyClass;
        private int seedLimit = 0;
        private int maxRuns = 0;
        private int jobs = 2;
        private Path baseWorkbook = Paths.get("input/FAKENEWS_BASELINE_3.xlsx");
        private Path output;
        private boolean execute;
        private boolean help;

        private static Options parse(String[] args) {
            Options options = new Options();
            for (int index = 0; index < args.length; ++index) {
                String argument = args[index];
                switch (argument) {
                    case "--study-class":
                        options.studyClass = value(args, ++index, argument);
                        break;
                    case "--questions":
                        for (String question : value(args, ++index, argument).split(",")) {
                            if (!question.trim().isEmpty()) options.questions.add(question.trim().toUpperCase());
                        }
                        break;
                    case "--seeds":
                        options.seedLimit = positive(value(args, ++index, argument), argument);
                        break;
                    case "--max-runs":
                        options.maxRuns = positive(value(args, ++index, argument), argument);
                        break;
                    case "--jobs":
                        options.jobs = positive(value(args, ++index, argument), argument);
                        break;
                    case "--base":
                        options.baseWorkbook = Paths.get(value(args, ++index, argument));
                        break;
                    case "--output":
                        options.output = Paths.get(value(args, ++index, argument));
                        break;
                    case "--execute":
                        options.execute = true;
                        break;
                    case "-h":
                    case "--help":
                        options.help = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown option: " + argument);
                }
            }
            return options;
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            return args[index];
        }

        private static int positive(String value, String option) {
            int result = Integer.parseInt(value);
            if (result < 1) throw new IllegalArgumentException(option + " must be positive");
            return result;
        }
    }
}
