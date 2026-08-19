package experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replaces the structural cells after enforcing the intended fixed-outdegree small-world network. */
public final class StructuralCorrectionStudy implements StudyProvider {
    private static final List<String> COMBINED = Arrays.asList(
            "ENGAGEMENT", "PROXIMITY", "INFORMATIONAL_CAMOUFLAGE", "CREDIBILITY_CAMOUFLAGE");

    @Override
    public StudySpecification create(Path baseWorkbook) {
        ExperimentSpecification experiment = new ExperimentSpecification(
                "E4c-structural-sensitivity",
                "Corrected fixed-outdegree activity and topology sensitivity.",
                conditions(), seeds(5001, 10));
        ResearchQuestionSpecification rq = new ResearchQuestionSpecification(
                "RQ4", "Are strategy conclusions robust to activity and network topology?",
                Collections.singletonList(experiment));
        return new StudySpecification("news-source-strategies-structural-correction",
                "Corrected structural sensitivity for the paper revision", baseWorkbook,
                Collections.singletonList(rq));
    }

    private static List<ConditionSpecification> conditions() {
        ArrayList<ConditionSpecification> result = new ArrayList<>();
        for (int topology : new int[]{0, 1}) {
            for (double activity : new double[]{0.25, 0.50, 1.0}) {
                result.add(condition(topology, activity, "control", Collections.emptyList()));
                result.add(condition(topology, activity, "credibility",
                        Collections.singletonList("CREDIBILITY_CAMOUFLAGE")));
                result.add(condition(topology, activity, "combined", COMBINED));
            }
        }
        return result;
    }

    private static ConditionSpecification condition(int topology, double activity, String strategy,
                                                     List<String> attributes) {
        Map<String, Double> configuration = baseConfiguration();
        configuration.put("NETWORK_TOPOLOGY", (double) topology);
        configuration.put("USER_ACTIVITY_PROBABILITY", activity);
        ScenarioSpecification scenario = attributes.isEmpty() ? null
                : new ScenarioSpecification("TRADITIONAL_MEDIA", "FAKE_NEWS_SOURCE", 1, -1,
                        attributes, Collections.emptyList());
        configuration.put("SCENARIO", scenario == null ? 0.0 : -2.0);
        String id = "top" + topology + "_a" + Double.toString(activity).replace('.', '_') + "_" + strategy;
        return new ConditionSpecification(id, configuration, scenario, null);
    }

    private static Map<String, Double> baseConfiguration() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("PERIODS", 400.0); values.put("AGENTS", 500.0);
        values.put("CONTACTS", 5.0); values.put("FRIENDS", 1.0);
        values.put("REPETITIONS", 0.0); values.put("GUI", 0.0);
        values.put("LEARNING_PERIODS", 0.0); values.put("MEMORY", -1.0);
        values.put("MEMORY_HALF_LIFE", 0.33); values.put("SOURCE_REACH", 0.0);
        values.put("WOM", 1.0); values.put("WOM_RECEIVER_SCALE", 0.5);
        values.put("WOM_FAKE_NEWS_EFFECT", -1.0); values.put("WOM_TRUE_NEWS_EFFECT", 1.0);
        values.put("WOM_LABEL_DELAY", 0.0); values.put("WOM_LABEL_COVERAGE", 1.0);
        values.put("WOM_LABEL_SENSITIVITY", 1.0); values.put("WOM_LABEL_SPECIFICITY", 1.0);
        values.put("NETWORK_REWIRING_PROBABILITY", 0.1); values.put("SOURCE_ATTRIBUTE_CONTRAST", 1.0);
        values.put("SAVED_ENDORSEMENTS", 0.0); values.put("SAVED_REPOSTS_PER_SOURCE", 1.0);
        values.put("SAVED_DETAILED_AGENT_DECISIONS", 0.0); values.put("SAVED_AGENT_DECISIONS", 0.0);
        values.put("SAVED_FAKENEWS", 1.0); values.put("SAVED_WOM_DIAGNOSTICS", 0.0);
        values.put("COMPRESSED_RESULTS", 0.0);
        return values;
    }

    private static List<Long> seeds(long first, int count) {
        ArrayList<Long> result = new ArrayList<>();
        for (long value = first; value < first + count; ++value) result.add(value);
        return result;
    }
}
