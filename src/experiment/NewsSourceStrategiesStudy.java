package experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed executable design for the news-source-strategy paper. */
public final class NewsSourceStrategiesStudy implements StudyProvider {
    private static final String FROM = "TRADITIONAL_MEDIA";
    private static final String TO = "FAKE_NEWS_SOURCE";
    private static final String CREDIBILITY = "CREDIBILITY_CAMOUFLAGE";
    private static final String INFORMATIONAL = "INFORMATIONAL_CAMOUFLAGE";
    private static final List<String> ALL_STRATEGIES = Arrays.asList(
            "ENGAGEMENT", "PROXIMITY", INFORMATIONAL, CREDIBILITY);

    public NewsSourceStrategiesStudy() {
    }

    @Override
    public StudySpecification create(Path baseWorkbook) {
        List<ResearchQuestionSpecification> questions = Arrays.asList(
                rq1(), rq2(), rq3(), rq4());
        return new StudySpecification(
                "news-source-strategies",
                "Evaluations of News Source Strategies to Disseminate Fake News in X",
                baseWorkbook,
                questions);
    }

    private static ResearchQuestionSpecification rq1() {
        ArrayList<ConditionSpecification> conditions = new ArrayList<>();
        conditions.add(condition("control", baseConfiguration(), null, null));
        conditions.add(strategyCondition("engagement", Arrays.asList("ENGAGEMENT"), 1, -1));
        conditions.add(strategyCondition("proximity", Arrays.asList("PROXIMITY"), 1, -1));
        conditions.add(strategyCondition("informational", Arrays.asList(INFORMATIONAL), 1, -1));
        conditions.add(strategyCondition("credibility", Arrays.asList(CREDIBILITY), 1, -1));
        conditions.add(strategyCondition("all", ALL_STRATEGIES, 1, -1));
        ExperimentSpecification experiment = new ExperimentSpecification(
                "E1-strategy-comparison",
                "Selective imitation increases malicious-source and fake-news repost shares.",
                conditions, seeds(1001, 30));
        return new ResearchQuestionSpecification(
                "RQ1",
                "How do source imitation strategies affect fake-news dissemination?",
                Arrays.asList(experiment));
    }

    private static ResearchQuestionSpecification rq2() {
        ArrayList<ConditionSpecification> conditions = new ArrayList<>();
        conditions.add(condition("control", baseConfiguration(), null, null));
        for (String strategy : Arrays.asList(CREDIBILITY, INFORMATIONAL)) {
            for (int start : new int[]{1, 101, 201}) {
                for (int duration : new int[]{25, 100, -1}) {
                    int end = duration == -1 ? -1 : start + duration - 1;
                    String durationLabel = duration == -1 ? "permanent" : "d" + duration;
                    String id = strategyId(strategy) + "_p" + start + "_" + durationLabel;
                    conditions.add(strategyCondition(id, Arrays.asList(strategy), start, end));
                }
            }
        }
        ExperimentSpecification experiment = new ExperimentSpecification(
                "E2-timing-duration",
                "Earlier and longer campaigns have larger cumulative and persistent effects.",
                conditions, seeds(1001, 30));
        return new ResearchQuestionSpecification(
                "RQ2",
                "How do campaign timing and duration affect efficacy and persistence?",
                Arrays.asList(experiment));
    }

    private static ResearchQuestionSpecification rq3() {
        ArrayList<ConditionSpecification> conditions = new ArrayList<>();
        List<List<String>> strategyLevels = Arrays.asList(
                new ArrayList<>(), Arrays.asList(CREDIBILITY), Arrays.asList(INFORMATIONAL));
        for (List<String> strategies : strategyLevels) {
            String strategyLabel = strategies.isEmpty() ? "control" : strategyId(strategies.get(0));
            for (double reach : new double[]{14.7, 10.0, 5.0, 1.0, 0.0}) {
                for (int wom : new int[]{0, 1}) {
                    Map<String, Double> configuration = baseConfiguration();
                    configuration.put("SOURCE_REACH", 1.0);
                    configuration.put("WOM", (double) wom);
                    ScenarioSpecification scenario = strategies.isEmpty()
                            ? null : scenario(strategies, 1, -1);
                    String reachLabel = Double.toString(reach).replace('.', '_');
                    String id = strategyLabel + "_reach" + reachLabel + "_wom" + wom;
                    conditions.add(condition(id, configuration, scenario, reach));
                }
            }
        }
        ExperimentSpecification experiment = new ExperimentSpecification(
                "E3-reach-mitigation",
                "Reach restrictions reduce dissemination, while WOM partly bypasses them.",
                conditions, seeds(1001, 30));
        return new ResearchQuestionSpecification(
                "RQ3",
                "How effectively does limiting malicious-source reach mitigate dissemination?",
                Arrays.asList(experiment));
    }

    private static ResearchQuestionSpecification rq4() {
        ArrayList<ConditionSpecification> conditions = new ArrayList<>();
        for (int contacts : new int[]{3, 5, 10}) {
            for (MemorySetting memory : Arrays.asList(
                    new MemorySetting("window25", 25, -1.0),
                    new MemorySetting("half0_33", -1, 0.33),
                    new MemorySetting("half1", -1, 1.0),
                    new MemorySetting("half5", -1, 5.0))) {
                for (boolean strategy : new boolean[]{false, true}) {
                    Map<String, Double> configuration = baseConfiguration();
                    configuration.put("CONTACTS", (double) contacts);
                    configuration.put("MEMORY", (double) memory.memory);
                    configuration.put("MEMORY_HALF_LIFE", memory.halfLife);
                    String id = (strategy ? "credibility" : "control") + "_c" + contacts + "_" + memory.id;
                    conditions.add(condition(id, configuration,
                            strategy ? scenario(Arrays.asList(CREDIBILITY), 1, -1) : null, null));
                }
            }
        }
        ExperimentSpecification experiment = new ExperimentSpecification(
                "E4-sensitivity-robustness",
                "The strategy ranking remains stable under plausible behavioral assumptions.",
                conditions, seeds(1001, 15));
        return new ResearchQuestionSpecification(
                "RQ4",
                "Are strategy-effect conclusions robust to memory and connectivity assumptions?",
                Arrays.asList(experiment));
    }

    private static ConditionSpecification strategyCondition(String id, List<String> strategies,
                                                            int start, int end) {
        return condition(id, baseConfiguration(), scenario(strategies, start, end), null);
    }

    private static ScenarioSpecification scenario(List<String> strategies, int start, int end) {
        return new ScenarioSpecification(FROM, TO, start, end, strategies, new ArrayList<>());
    }

    private static ConditionSpecification condition(String id, Map<String, Double> configuration,
                                                    ScenarioSpecification scenario, Double targetReach) {
        configuration.put("SCENARIO", scenario == null ? 0.0 : -2.0);
        return new ConditionSpecification(id, configuration, scenario, targetReach);
    }

    private static Map<String, Double> baseConfiguration() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("PERIODS", 400.0);
        values.put("AGENTS", 500.0);
        values.put("CONTACTS", 5.0);
        values.put("FRIENDS", 1.0);
        values.put("REPETITIONS", 0.0);
        values.put("GUI", 0.0);
        values.put("LEARNING_PERIODS", 0.0);
        values.put("MEMORY", -1.0);
        values.put("MEMORY_HALF_LIFE", 0.33);
        values.put("SOURCE_REACH", 0.0);
        values.put("WOM", 1.0);
        values.put("WOM_FAKE_NEWS_EFFECT", -1.0);
        values.put("WOM_TRUE_NEWS_EFFECT", 1.0);
        values.put("SAVED_ENDORSEMENTS", 0.0);
        values.put("SAVED_REPOSTS_PER_SOURCE", 1.0);
        values.put("SAVED_DETAILED_AGENT_DECISIONS", 0.0);
        values.put("SAVED_AGENT_DECISIONS", 0.0);
        values.put("SAVED_FAKENEWS", 1.0);
        values.put("COMPRESSED_RESULTS", 0.0);
        return values;
    }

    private static List<Long> seeds(long first, int count) {
        ArrayList<Long> result = new ArrayList<>();
        for (long seed = first; seed < first + count; ++seed) result.add(seed);
        return result;
    }

    private static String strategyId(String strategy) {
        return strategy.toLowerCase().replace("_camouflage", "");
    }

    private static final class MemorySetting {
        private final String id;
        private final int memory;
        private final double halfLife;

        private MemorySetting(String id, int memory, double halfLife) {
            this.id = id;
            this.memory = memory;
            this.halfLife = halfLife;
        }
    }
}
