package experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Paper research question and the experiments designed to answer it. */
public final class ResearchQuestionSpecification {
    private final String id;
    private final String question;
    private final List<ExperimentSpecification> experiments;

    public ResearchQuestionSpecification(String id, String question,
                                         List<ExperimentSpecification> experiments) {
        if (id == null || !id.matches("RQ[0-9]+")) {
            throw new IllegalArgumentException("Research-question id must look like RQ1: " + id);
        }
        if (experiments.isEmpty()) throw new IllegalArgumentException(id + " requires an experiment");
        this.id = id;
        this.question = question;
        this.experiments = Collections.unmodifiableList(new ArrayList<>(experiments));
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public List<ExperimentSpecification> getExperiments() { return experiments; }
}
