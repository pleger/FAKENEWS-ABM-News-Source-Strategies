package experiment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Plans and executes study runs as isolated JVM processes with resumable outputs. */
public final class StudyRunner {
    private final StudySpecification study;
    private final Path projectRoot;

    public StudyRunner(StudySpecification study) {
        this(study, Paths.get("").toAbsolutePath().normalize());
    }

    public StudyRunner(StudySpecification study, Path projectRoot) {
        this.study = study;
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    /** Flattens selected questions into explicit condition/seed runs. */
    public List<SimulationRunSpecification> plan(Set<String> questionIds, int seedLimit,
                                                 int maxRuns, Path outputRoot) {
        if (seedLimit < 0 || maxRuns < 0) throw new IllegalArgumentException("Limits must be nonnegative");
        ArrayList<SimulationRunSpecification> runs = new ArrayList<>();
        Path absoluteRoot = outputRoot.toAbsolutePath().normalize();
        for (ResearchQuestionSpecification question : study.select(questionIds)) {
            for (ExperimentSpecification experiment : question.getExperiments()) {
                int seeds = seedLimit == 0 ? experiment.getSeeds().size()
                        : Math.min(seedLimit, experiment.getSeeds().size());
                for (ConditionSpecification condition : experiment.getConditions()) {
                    Path workbook = absoluteRoot.resolve("workbooks")
                            .resolve(question.getId()).resolve(experiment.getId())
                            .resolve(condition.getId() + ".xlsx");
                    for (int seedIndex = 0; seedIndex < seeds; ++seedIndex) {
                        long seed = experiment.getSeeds().get(seedIndex);
                        Path runOutput = absoluteRoot.resolve("results")
                                .resolve(question.getId()).resolve(experiment.getId())
                                .resolve(condition.getId()).resolve("seed-" + seed);
                        runs.add(new SimulationRunSpecification(question.getId(), experiment.getId(),
                                condition, seed, workbook, runOutput));
                        if (maxRuns > 0 && runs.size() >= maxRuns) return runs;
                    }
                }
            }
        }
        return runs;
    }

    /** Executes incomplete runs, preserving completed run directories for safe resumption. */
    public void execute(List<SimulationRunSpecification> runs, Path outputRoot, int jobs)
            throws IOException, InterruptedException {
        if (jobs < 1) throw new IllegalArgumentException("jobs must be positive");
        Path absoluteRoot = outputRoot.toAbsolutePath().normalize();
        Files.createDirectories(absoluteRoot);
        writeStudyMetadata(absoluteRoot);
        buildConditionWorkbooks(runs);
        writeManifest(absoluteRoot, runs);

        ExecutorService executor = Executors.newFixedThreadPool(jobs);
        ArrayList<Future<?>> futures = new ArrayList<>();
        try {
            for (SimulationRunSpecification run : runs) {
                if (isComplete(run)) continue;
                futures.add(executor.submit(() -> {
                    executeOne(run);
                    synchronized (StudyRunner.this) {
                        try {
                            writeManifest(absoluteRoot, runs);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    throw new RuntimeException(cause);
                }
            }
        } finally {
            executor.shutdownNow();
            writeManifest(absoluteRoot, runs);
        }
    }

    private void buildConditionWorkbooks(List<SimulationRunSpecification> runs) throws IOException {
        LinkedHashMap<Path, ConditionSpecification> workbooks = new LinkedHashMap<>();
        for (SimulationRunSpecification run : runs) workbooks.put(run.getWorkbook(), run.getCondition());
        for (Map.Entry<Path, ConditionSpecification> entry : workbooks.entrySet()) {
            if (!Files.isRegularFile(entry.getKey())) {
                WorkbookConditionBuilder.build(study.getBaseWorkbook(), entry.getKey(), entry.getValue());
            }
        }
    }

    private void executeOne(SimulationRunSpecification run) {
        Path output = run.getOutputDirectory();
        Path complete = output.resolve(".complete");
        Path failed = output.resolve(".failed");
        Path log = output.resolve("run.log");
        try {
            Files.createDirectories(output);
            Files.deleteIfExists(failed);
            String classpath = projectRoot.resolve("build/classes") + File.pathSeparator
                    + projectRoot.resolve("lib/*");
            ProcessBuilder builder = new ProcessBuilder(
                    javaExecutable(), "-cp", classpath, "Main",
                    "--input", run.getWorkbook().toAbsolutePath().toString(),
                    "--seed", Long.toString(run.getSeed()),
                    "--repetitions", "0",
                    "--no-gui",
                    "--output-directory", output.toAbsolutePath().toString());
            builder.directory(projectRoot.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            int exit = builder.start().waitFor();
            if (exit != 0) throw new IllegalStateException("Main exited with status " + exit);
            if (resultWorkbook(output) == null) {
                throw new IllegalStateException("Main produced no result workbook");
            }
            Files.write(complete, ("seed=" + run.getSeed() + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception exception) {
            try {
                Files.write(failed, (exception.toString() + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {
                // The original failure remains available through the thrown exception.
            }
            throw new RuntimeException("Run failed: " + run.runId() + "; see " + log, exception);
        }
    }

    private static String javaExecutable() {
        Path executable = Paths.get(System.getProperty("java.home"), "bin", "java");
        return executable.toString();
    }

    private boolean isComplete(SimulationRunSpecification run) {
        return Files.isRegularFile(run.getOutputDirectory().resolve(".complete"))
                && resultWorkbook(run.getOutputDirectory()) != null;
    }

    private static Path resultWorkbook(Path directory) {
        if (!Files.isDirectory(directory)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.xlsx")) {
            for (Path path : stream) {
                if (!path.getFileName().toString().startsWith("~$")) return path.toAbsolutePath();
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private void writeStudyMetadata(Path outputRoot) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("study_id\t").append(study.getId()).append('\n');
        text.append("title\t").append(study.getTitle()).append('\n');
        text.append("base_workbook\t").append(study.getBaseWorkbook().toAbsolutePath()).append('\n');
        Path metadata = outputRoot.resolve("study.tsv");
        if (Files.isRegularFile(metadata)) {
            String existing = new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8);
            if (!existing.equals(text.toString())) {
                throw new IllegalArgumentException(
                        "Output directory belongs to a different study or base workbook: " + outputRoot);
            }
        }
        Files.write(metadata, text.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeManifest(Path outputRoot, List<SimulationRunSpecification> runs) throws IOException {
        StringBuilder manifest = new StringBuilder();
        manifest.append("study\tresearch_question\texperiment\tcondition\tseed\tstrategy\tfrom\tto")
                .append("\tstart_period\tend_period\ttarget_reach\tmemory\tmemory_half_life")
                .append("\twom\twom_receiver_scale\twom_fake_effect\twom_true_effect")
                .append("\twom_label_delay\twom_label_coverage\twom_label_sensitivity\twom_label_specificity")
                .append("\tcontacts\tfriends\tuser_activity\tnetwork_topology\tnetwork_rewiring")
                .append("\tbase\tsource_attribute_contrast\ttraditional_fake_probability")
                .append("\tunknown_fake_probability\ttarget_fake_probability\tmixed_fake_probability")
                .append("\tstatus\tinput_workbook\tresult_workbook\tlog\n");
        for (SimulationRunSpecification run : runs) {
            ConditionSpecification condition = run.getCondition();
            ScenarioSpecification scenario = condition.getScenario();
            Map<String, Double> configuration = condition.getConfiguration();
            Path result = resultWorkbook(run.getOutputDirectory());
            String status = isComplete(run) ? "COMPLETE"
                    : Files.isRegularFile(run.getOutputDirectory().resolve(".failed")) ? "FAILED" : "PENDING";
            append(manifest, study.getId(), run.getResearchQuestionId(), run.getExperimentId(),
                    condition.getId(), Long.toString(run.getSeed()),
                    scenario == null ? "NONE" : scenario.strategyLabel(),
                    scenario == null ? "" : scenario.getFrom(), scenario == null ? "" : scenario.getTo(),
                    scenario == null ? "" : Integer.toString(scenario.getStartPeriod()),
                    scenario == null ? "" : Integer.toString(scenario.getEndPeriod()),
                    number(condition.getTargetReachPercentage()), number(configuration.get("MEMORY")),
                    number(configuration.get("MEMORY_HALF_LIFE")), number(configuration.get("WOM")),
                    number(configuration.get("WOM_RECEIVER_SCALE")), number(configuration.get("WOM_FAKE_NEWS_EFFECT")),
                    number(configuration.get("WOM_TRUE_NEWS_EFFECT")), number(configuration.get("WOM_LABEL_DELAY")),
                    number(configuration.get("WOM_LABEL_COVERAGE")), number(configuration.get("WOM_LABEL_SENSITIVITY")),
                    number(configuration.get("WOM_LABEL_SPECIFICITY")), number(configuration.get("CONTACTS")),
                    number(configuration.get("FRIENDS")), number(configuration.get("USER_ACTIVITY_PROBABILITY")),
                    number(configuration.get("NETWORK_TOPOLOGY")), number(configuration.get("NETWORK_REWIRING_PROBABILITY")),
                    number(configuration.get("BASE")), number(configuration.get("SOURCE_ATTRIBUTE_CONTRAST")),
                    number(condition.getSourceBehaviorOverrides().get("TRADITIONAL_MEDIA")),
                    number(condition.getSourceBehaviorOverrides().get("UNKNOWN_MEDIA")),
                    number(condition.getSourceBehaviorOverrides().get("FAKE_NEWS_SOURCE")),
                    number(condition.getSourceBehaviorOverrides().get("MIXED_SOURCE")), status,
                    run.getWorkbook().toAbsolutePath().toString(), result == null ? "" : result.toString(),
                    run.getOutputDirectory().resolve("run.log").toAbsolutePath().toString());
        }
        Path temporary = outputRoot.resolve("manifest.tsv.tmp");
        Files.write(temporary, manifest.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary, outputRoot.resolve("manifest.tsv"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String number(Double value) {
        return value == null ? "" : Double.toString(value);
    }

    private static void append(StringBuilder target, String... values) {
        for (int index = 0; index < values.length; ++index) {
            if (index > 0) target.append('\t');
            target.append(values[index].replace('\t', ' ').replace('\n', ' '));
        }
        target.append('\n');
    }
}
