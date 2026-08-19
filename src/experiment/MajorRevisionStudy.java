package experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Additional experiments requested by the major-revision methodological audit. */
public final class MajorRevisionStudy implements StudyProvider {
    private static final String FROM = "TRADITIONAL_MEDIA";
    private static final String TO = "FAKE_NEWS_SOURCE";
    private static final String CREDIBILITY = "CREDIBILITY_CAMOUFLAGE";
    private static final String INFORMATIONAL = "INFORMATIONAL_CAMOUFLAGE";
    private static final List<String> COMBINED = Arrays.asList(
            "ENGAGEMENT", "PROXIMITY", INFORMATIONAL, CREDIBILITY);
    private static final String[] SOURCES = {
            "TRADITIONAL_MEDIA", "UNKNOWN_MEDIA", "FAKE_NEWS_SOURCE", "MIXED_SOURCE"};

    @Override
    public StudySpecification create(Path baseWorkbook) {
        return new StudySpecification(
                "news-source-strategies-major-revision",
                "Major-revision experiments for news-source strategies on X",
                baseWorkbook,
                Arrays.asList(rq3(), rq4()));
    }

    private static ResearchQuestionSpecification rq3() {
        ArrayList<ConditionSpecification> conditions = new ArrayList<>();
        ArrayList<ConditionSpecification> legacySeedConditions = new ArrayList<>();
        List<Policy> newPolicies = Arrays.asList(
                new Policy("discovery", 1, 0, 0, 0, 1.0, 1.0, 1.0),
                new Policy("flag_only", 1, -1, 0, 0, 1.0, 1.0, 1.0),
                new Policy("imperfect_fast", 1, -1, 0, 5, 0.80, 0.90, 0.95),
                new Policy("imperfect_delayed", 1, -1, 0, 25, 0.50, 0.75, 0.90));
        for (Policy policy : newPolicies) {
            for (double reach : new double[]{14.7, 5.0, 1.0}) {
                for (Strategy strategy : strategies()) {
                    conditions.add(policyCondition(policy, strategy, reach, 0.5,
                            policy.id + "_" + strategy.id + "_r" + label(reach)));
                }
            }
        }

        // The original study already supplies disabled/oracle cells for the first three levels.
        for (Policy policy : Arrays.asList(
                new Policy("disabled", 0, -1, 1, 0, 1.0, 1.0, 1.0),
                new Policy("oracle", 1, -1, 1, 0, 1.0, 1.0, 1.0))) {
            for (double reach : new double[]{14.7, 5.0, 1.0}) {
                legacySeedConditions.add(policyCondition(policy, new Strategy("combined", COMBINED), reach, 0.5,
                        policy.id + "_combined_r" + label(reach)));
            }
        }

        Policy realistic = new Policy("imperfect_fast", 1, -1, 0, 5, 0.80, 0.90, 0.95);
        for (double scale : new double[]{0.25, 1.0}) {
            for (Strategy strategy : strategies()) {
                conditions.add(policyCondition(realistic, strategy, 14.7, scale,
                        "scale" + label(scale) + "_" + strategy.id + "_r14_7"));
            }
        }
        ExperimentSpecification experiment = new ExperimentSpecification(
                "E3b-recommendation-realism",
                "Recommendation effects remain qualified under delayed, incomplete, and imperfect labels.",
                conditions, seeds(3001, 30));
        ExperimentSpecification legacySeedExperiment = new ExperimentSpecification(
                "E3c-combined-legacy-policies",
                "Combined-strategy cells use the original RQ3 seeds for paired comparisons with legacy controls.",
                legacySeedConditions, seeds(1001, 30));
        return new ResearchQuestionSpecification(
                "RQ3", "How do reach limits interact with realistic recommendation policies?",
                Arrays.asList(experiment, legacySeedExperiment));
    }

    private static ConditionSpecification policyCondition(Policy policy, Strategy strategy,
                                                            double reach, double scale, String id) {
        Map<String, Double> configuration = baseConfiguration();
        configuration.put("SOURCE_REACH", 1.0);
        configuration.put("WOM", (double) policy.wom);
        configuration.put("WOM_FAKE_NEWS_EFFECT", (double) policy.fakeEffect);
        configuration.put("WOM_TRUE_NEWS_EFFECT", (double) policy.trueEffect);
        configuration.put("WOM_LABEL_DELAY", (double) policy.delay);
        configuration.put("WOM_LABEL_COVERAGE", policy.coverage);
        configuration.put("WOM_LABEL_SENSITIVITY", policy.sensitivity);
        configuration.put("WOM_LABEL_SPECIFICITY", policy.specificity);
        configuration.put("WOM_RECEIVER_SCALE", scale);
        ScenarioSpecification scenario = strategy.attributes.isEmpty() ? null
                : scenario(strategy.attributes);
        configuration.put("SCENARIO", scenario == null ? 0.0 : -2.0);
        return new ConditionSpecification(id, configuration, scenario, reach);
    }

    private static ResearchQuestionSpecification rq4() {
        ExperimentSpecification global = new ExperimentSpecification(
                "E4b-global-sensitivity",
                "Strategy conclusions are screened across joint parameter uncertainty.",
                globalSensitivityConditions(), seeds(4001, 10));
        ExperimentSpecification structural = new ExperimentSpecification(
                "E4c-structural-sensitivity",
                "Strategy conclusions are screened across activity and network topology.",
                structuralConditions(), seeds(5001, 10));
        return new ResearchQuestionSpecification(
                "RQ4", "Are strategy conclusions robust to parameter and structural uncertainty?",
                Arrays.asList(structural, global));
    }

    private static List<ConditionSpecification> globalSensitivityConditions() {
        ArrayList<ConditionSpecification> result = new ArrayList<>();
        List<double[]> points = morrisPoints(8, 10, 20260817L);
        for (int point = 0; point < points.size(); ++point) {
            double[] x = points.get(point);
            Map<String, Double> behavior = new LinkedHashMap<>();
            behavior.put(SOURCES[0], scale(x[5], 0.05, 0.15));
            behavior.put(SOURCES[1], scale(x[6], 0.15, 0.30));
            behavior.put(SOURCES[2], scale(x[7], 0.55, 0.80));
            behavior.put(SOURCES[3], scale(x[8], 0.30, 0.55));
            for (Strategy strategy : Arrays.asList(
                    new Strategy("control", Collections.emptyList()),
                    new Strategy("credibility", Collections.singletonList(CREDIBILITY)),
                    new Strategy("combined", COMBINED))) {
                Map<String, Double> configuration = baseConfiguration();
                configuration.put("MEMORY", -1.0);
                configuration.put("MEMORY_HALF_LIFE", logScale(x[0], 0.33, 5.0));
                configuration.put("CONTACTS", (double) Math.max(3, Math.min(10, (int) Math.round(scale(x[1], 3, 10)))));
                configuration.put("SOURCE_REACH", 1.0);
                configuration.put("WOM_RECEIVER_SCALE", scale(x[3], 0.25, 1.0));
                configuration.put("BASE", scale(x[4], 1.10, 1.40));
                configuration.put("SOURCE_ATTRIBUTE_CONTRAST", scale(x[9], 0.75, 1.25));
                ScenarioSpecification scenario = strategy.attributes.isEmpty() ? null : scenario(strategy.attributes);
                configuration.put("SCENARIO", scenario == null ? 0.0 : -2.0);
                String id = String.format("m%02d_%s", point + 1, strategy.id);
                result.add(new ConditionSpecification(id, configuration, scenario,
                        scale(x[2], 1.0, 14.7), behavior));
            }
        }
        return result;
    }

    private static List<ConditionSpecification> structuralConditions() {
        ArrayList<ConditionSpecification> result = new ArrayList<>();
        for (int topology : new int[]{0, 1}) {
            for (double activity : new double[]{0.25, 0.50, 1.0}) {
                for (Strategy strategy : Arrays.asList(
                        new Strategy("control", Collections.emptyList()),
                        new Strategy("credibility", Collections.singletonList(CREDIBILITY)),
                        new Strategy("combined", COMBINED))) {
                    Map<String, Double> configuration = baseConfiguration();
                    configuration.put("NETWORK_TOPOLOGY", (double) topology);
                    configuration.put("NETWORK_REWIRING_PROBABILITY", 0.10);
                    configuration.put("USER_ACTIVITY_PROBABILITY", activity);
                    ScenarioSpecification scenario = strategy.attributes.isEmpty() ? null : scenario(strategy.attributes);
                    configuration.put("SCENARIO", scenario == null ? 0.0 : -2.0);
                    String id = "top" + topology + "_a" + label(activity) + "_" + strategy.id;
                    result.add(new ConditionSpecification(id, configuration, scenario, null));
                }
            }
        }
        return result;
    }

    /** Deterministic elementary-effects trajectories on a five-level unit cube. */
    private static List<double[]> morrisPoints(int trajectories, int factors, long seed) {
        ArrayList<double[]> result = new ArrayList<>();
        Random random = new Random(seed);
        double delta = 0.25;
        for (int trajectory = 0; trajectory < trajectories; ++trajectory) {
            double[] current = new double[factors];
            ArrayList<Integer> order = new ArrayList<>();
            for (int factor = 0; factor < factors; ++factor) {
                current[factor] = random.nextInt(4) * delta;
                order.add(factor);
            }
            Collections.shuffle(order, random);
            result.add(current.clone());
            for (int factor : order) {
                current[factor] += delta;
                result.add(current.clone());
            }
        }
        return result;
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
        values.put("USER_ACTIVITY_PROBABILITY", 1.0); values.put("NETWORK_TOPOLOGY", 0.0);
        values.put("NETWORK_REWIRING_PROBABILITY", 0.1); values.put("SOURCE_ATTRIBUTE_CONTRAST", 1.0);
        values.put("SCENARIO", 0.0); values.put("SAVED_ENDORSEMENTS", 0.0);
        values.put("SAVED_REPOSTS_PER_SOURCE", 1.0); values.put("SAVED_DETAILED_AGENT_DECISIONS", 0.0);
        values.put("SAVED_AGENT_DECISIONS", 0.0); values.put("SAVED_FAKENEWS", 1.0);
        values.put("COMPRESSED_RESULTS", 0.0);
        return values;
    }

    private static ScenarioSpecification scenario(List<String> strategies) {
        return new ScenarioSpecification(FROM, TO, 1, -1, strategies, new ArrayList<>());
    }

    private static List<Strategy> strategies() {
        return Arrays.asList(
                new Strategy("control", Collections.emptyList()),
                new Strategy("credibility", Collections.singletonList(CREDIBILITY)),
                new Strategy("informational", Collections.singletonList(INFORMATIONAL)),
                new Strategy("combined", COMBINED));
    }

    private static List<Long> seeds(long first, int count) {
        ArrayList<Long> result = new ArrayList<>();
        for (long value = first; value < first + count; ++value) result.add(value);
        return result;
    }

    private static double scale(double x, double low, double high) { return low + x * (high - low); }
    private static double logScale(double x, double low, double high) {
        return Math.exp(Math.log(low) + x * (Math.log(high) - Math.log(low)));
    }
    private static String label(double value) { return Double.toString(value).replace('.', '_'); }

    private static final class Strategy {
        private final String id; private final List<String> attributes;
        private Strategy(String id, List<String> attributes) { this.id = id; this.attributes = attributes; }
    }

    private static final class Policy {
        private final String id; private final int wom; private final int fakeEffect; private final int trueEffect;
        private final int delay; private final double coverage; private final double sensitivity; private final double specificity;
        private Policy(String id, int wom, int fakeEffect, int trueEffect, int delay,
                       double coverage, double sensitivity, double specificity) {
            this.id = id; this.wom = wom; this.fakeEffect = fakeEffect; this.trueEffect = trueEffect;
            this.delay = delay; this.coverage = coverage; this.sensitivity = sensitivity; this.specificity = specificity;
        }
    }
}
