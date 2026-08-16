package experiment;

import java.nio.file.Path;

/** One isolated JVM execution for a condition and random seed. */
public final class SimulationRunSpecification {
    private final String researchQuestionId;
    private final String experimentId;
    private final ConditionSpecification condition;
    private final long seed;
    private final Path workbook;
    private final Path outputDirectory;

    public SimulationRunSpecification(String researchQuestionId, String experimentId,
                                      ConditionSpecification condition, long seed,
                                      Path workbook, Path outputDirectory) {
        this.researchQuestionId = researchQuestionId;
        this.experimentId = experimentId;
        this.condition = condition;
        this.seed = seed;
        this.workbook = workbook;
        this.outputDirectory = outputDirectory;
    }

    public String getResearchQuestionId() { return researchQuestionId; }
    public String getExperimentId() { return experimentId; }
    public ConditionSpecification getCondition() { return condition; }
    public long getSeed() { return seed; }
    public Path getWorkbook() { return workbook; }
    public Path getOutputDirectory() { return outputDirectory; }
    public String runId() { return condition.getId() + "_s" + seed; }
}
