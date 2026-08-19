package experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Focused re-executions that expose the mechanism behind the six RQ3 recommendation policies. */
public final class RecommendationDiagnosticsStudy implements StudyProvider {
    private static final List<String> COMBINED = Arrays.asList(
            "ENGAGEMENT", "PROXIMITY", "INFORMATIONAL_CAMOUFLAGE", "CREDIBILITY_CAMOUFLAGE");

    @Override
    public StudySpecification create(Path baseWorkbook) {
        List<Policy> legacy = Arrays.asList(
                new Policy("disabled", 0, -1, 1, 0, 1.0, 1.0, 1.0),
                new Policy("oracle", 1, -1, 1, 0, 1.0, 1.0, 1.0));
        List<Policy> revision = Arrays.asList(
                new Policy("discovery", 1, 0, 0, 0, 1.0, 1.0, 1.0),
                new Policy("flag_only", 1, -1, 0, 0, 1.0, 1.0, 1.0),
                new Policy("imperfect_fast", 1, -1, 0, 5, 0.80, 0.90, 0.95),
                new Policy("imperfect_delayed", 1, -1, 0, 25, 0.50, 0.75, 0.90));
        ExperimentSpecification first = new ExperimentSpecification(
                "E3d-mechanism-diagnostics-legacy",
                "Re-execute the disabled and oracle cells with observational WOM diagnostics.",
                conditions(legacy), seeds(1001, 30));
        ExperimentSpecification second = new ExperimentSpecification(
                "E3e-mechanism-diagnostics-revision",
                "Re-execute realistic policy cells with observational WOM diagnostics.",
                conditions(revision), seeds(3001, 30));
        ResearchQuestionSpecification rq = new ResearchQuestionSpecification(
                "RQ3", "Which recommendation-process events generate the RQ3 policy contrasts?",
                Arrays.asList(first, second));
        return new StudySpecification("news-source-strategies-rq3-diagnostics",
                "Recommendation-process diagnostics for the paper revision", baseWorkbook,
                Collections.singletonList(rq));
    }

    private static List<ConditionSpecification> conditions(List<Policy> policies) {
        ArrayList<ConditionSpecification> result = new ArrayList<>();
        for (Policy policy : policies) {
            result.add(condition(policy, false));
            result.add(condition(policy, true));
        }
        return result;
    }

    private static ConditionSpecification condition(Policy policy, boolean combined) {
        Map<String, Double> configuration = baseConfiguration();
        configuration.put("WOM", (double) policy.wom);
        configuration.put("WOM_FAKE_NEWS_EFFECT", (double) policy.fakeEffect);
        configuration.put("WOM_TRUE_NEWS_EFFECT", (double) policy.trueEffect);
        configuration.put("WOM_LABEL_DELAY", (double) policy.delay);
        configuration.put("WOM_LABEL_COVERAGE", policy.coverage);
        configuration.put("WOM_LABEL_SENSITIVITY", policy.sensitivity);
        configuration.put("WOM_LABEL_SPECIFICITY", policy.specificity);
        ScenarioSpecification scenario = combined
                ? new ScenarioSpecification("TRADITIONAL_MEDIA", "FAKE_NEWS_SOURCE", 1, -1,
                        COMBINED, Collections.emptyList()) : null;
        configuration.put("SCENARIO", combined ? -2.0 : 0.0);
        return new ConditionSpecification(policy.id + "_" + (combined ? "combined" : "control") + "_r14_7",
                configuration, scenario, 14.7);
    }

    private static Map<String, Double> baseConfiguration() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("PERIODS", 400.0); values.put("AGENTS", 500.0);
        values.put("CONTACTS", 5.0); values.put("FRIENDS", 1.0);
        values.put("REPETITIONS", 0.0); values.put("GUI", 0.0);
        values.put("LEARNING_PERIODS", 0.0); values.put("MEMORY", -1.0);
        values.put("MEMORY_HALF_LIFE", 0.33); values.put("SOURCE_REACH", 1.0);
        values.put("WOM_RECEIVER_SCALE", 0.5);
        values.put("USER_ACTIVITY_PROBABILITY", 1.0); values.put("NETWORK_TOPOLOGY", 0.0);
        values.put("NETWORK_REWIRING_PROBABILITY", 0.1); values.put("SOURCE_ATTRIBUTE_CONTRAST", 1.0);
        values.put("SAVED_ENDORSEMENTS", 0.0); values.put("SAVED_REPOSTS_PER_SOURCE", 1.0);
        values.put("SAVED_DETAILED_AGENT_DECISIONS", 0.0); values.put("SAVED_AGENT_DECISIONS", 0.0);
        values.put("SAVED_FAKENEWS", 1.0); values.put("SAVED_WOM_DIAGNOSTICS", 1.0);
        values.put("COMPRESSED_RESULTS", 0.0);
        return values;
    }

    private static List<Long> seeds(long first, int count) {
        ArrayList<Long> result = new ArrayList<>();
        for (long value = first; value < first + count; ++value) result.add(value);
        return result;
    }

    private static final class Policy {
        private final String id;
        private final int wom;
        private final int fakeEffect;
        private final int trueEffect;
        private final int delay;
        private final double coverage;
        private final double sensitivity;
        private final double specificity;

        private Policy(String id, int wom, int fakeEffect, int trueEffect, int delay,
                       double coverage, double sensitivity, double specificity) {
            this.id = id; this.wom = wom; this.fakeEffect = fakeEffect; this.trueEffect = trueEffect;
            this.delay = delay; this.coverage = coverage;
            this.sensitivity = sensitivity; this.specificity = specificity;
        }
    }
}
