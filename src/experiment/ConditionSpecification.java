package experiment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One concrete factor combination whose workbook is shared by all seeded runs. */
public final class ConditionSpecification {
    private final String id;
    private final Map<String, Double> configuration;
    private final ScenarioSpecification scenario;
    private final Double targetReachPercentage;

    public ConditionSpecification(String id, Map<String, Double> configuration,
                                  ScenarioSpecification scenario, Double targetReachPercentage) {
        if (id == null || !id.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Condition id must be filesystem-safe: " + id);
        }
        if (targetReachPercentage != null &&
                (targetReachPercentage < 0.0 || targetReachPercentage > 100.0)) {
            throw new IllegalArgumentException("Target reach must be within 0..100");
        }
        this.id = id;
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        this.scenario = scenario;
        this.targetReachPercentage = targetReachPercentage;
    }

    public String getId() { return id; }
    public Map<String, Double> getConfiguration() { return configuration; }
    public ScenarioSpecification getScenario() { return scenario; }
    public Double getTargetReachPercentage() { return targetReachPercentage; }
}
