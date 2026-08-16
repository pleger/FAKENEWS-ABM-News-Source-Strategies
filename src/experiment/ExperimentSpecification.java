package experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reproducible comparison of conditions linked to one hypothesis and seed set. */
public final class ExperimentSpecification {
    private final String id;
    private final String hypothesis;
    private final List<ConditionSpecification> conditions;
    private final List<Long> seeds;

    public ExperimentSpecification(String id, String hypothesis,
                                   List<ConditionSpecification> conditions, List<Long> seeds) {
        if (id == null || !id.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Experiment id must be filesystem-safe: " + id);
        }
        if (conditions.isEmpty() || seeds.isEmpty()) {
            throw new IllegalArgumentException("Experiment requires conditions and seeds: " + id);
        }
        this.id = id;
        this.hypothesis = hypothesis;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
        this.seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
    }

    public String getId() { return id; }
    public String getHypothesis() { return hypothesis; }
    public List<ConditionSpecification> getConditions() { return conditions; }
    public List<Long> getSeeds() { return seeds; }
}
