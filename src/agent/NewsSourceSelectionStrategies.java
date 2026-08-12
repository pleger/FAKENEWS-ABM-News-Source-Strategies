package agent;

import utils.Error;

import java.util.Map;

/**
 * Selects a news-source identifier from aggregate evaluations produced by {@link Interaction}.
 * Deterministic selection supports tests and WOM, while stochastic selection drives repost choice.
 */
public class NewsSourceSelectionStrategies {
    private static final double FALLBACK_WEIGHT = 1.0;

    /**
     * Selects the identifier with the greatest evaluation, retaining iteration order for ties.
     * SNS-user WOM uses this to choose the strongest recommendation received from contacts.
     *
     * @param evaluations source identifiers mapped to evaluation scores
     * @return identifier with the maximum score
     */
    public static int BY_MAX(Map<Integer, Double> evaluations) {
        int selected = -1;
        double max = Double.MAX_VALUE * -1;

        for (Map.Entry<Integer, Double> entry : evaluations.entrySet()) {
            if (max < entry.getValue()) {
                max = entry.getValue();
                selected = entry.getKey();
            }
        }

        Error.setAssert(selected != -1, "NewsSourceSelectionStrategies.BY_MAX: no newsSource selected info{size:" + evaluations.size() + ",max:" + max + "}");
        return selected;
    }

    /**
     * Randomly selects a source in proportion to its evaluation when scores are nonnegative.
     * Mixed or nonpositive score sets are shifted to positive fallback weights first.
     *
     * @param evaluations candidate identifiers mapped to aggregate scores
     * @return sampled source identifier
     */
    public static int BY_PROBABILITY(Map<Integer, Double> evaluations) {
        if (evaluations.isEmpty()) {
            Error.trigger("NewsSourceSelectionStrategies.BY_PROBABILITY: no evaluations available");
        }

        int selected = -1;
        double random = Math.random();
        double sum = sum(evaluations);
        double acc = 0;

        if (sum > 0 && min(evaluations) >= 0) {
            for (Map.Entry<Integer, Double> entry : evaluations.entrySet()) {
                acc += entry.getValue() / sum;

                if (acc >= random) {
                    selected = entry.getKey();
                    break;
                }
            }
        } else {
            selected = byShiftedProbability(evaluations, random);
        }

        Error.setAssert(selected != -1, "NewsSourceSelectionStrategies.BY_PROBABILITY: no newsSource selected, info{size:" + evaluations.size() + ",acc:" + acc + ",random:" + random + "}");
        return selected;
    }

    /**
     * Shifts all scores relative to the minimum so every candidate has a positive sampling weight.
     *
     * @param evaluations source evaluations containing negative or nonpositive totals
     * @param random uniform draw in {@code [0,1)}
     * @return sampled identifier, falling back to the final entry for rounding gaps
     */
    private static int byShiftedProbability(Map<Integer, Double> evaluations, double random) {
        double min = min(evaluations);
        double sum = 0;
        double acc = 0;
        int last = -1;

        for (Map.Entry<Integer, Double> entry : evaluations.entrySet()) {
            sum += entry.getValue() - min + FALLBACK_WEIGHT;
            last = entry.getKey();
        }

        for (Map.Entry<Integer, Double> entry : evaluations.entrySet()) {
            acc += (entry.getValue() - min + FALLBACK_WEIGHT) / sum;

            if (acc >= random) {
                return entry.getKey();
            }
        }

        return last;
    }

    /**
     * Computes the normalization total used by direct probability sampling.
     *
     * @param evaluations candidate scores
     * @return sum of all scores
     */
    private static double sum(Map<Integer, Double> evaluations) {
        double sum = 0;
        for (Map.Entry<Integer, Double> entry : evaluations.entrySet()) {
            sum += entry.getValue();
        }
        return sum;
    }

    /**
     * Finds the offset required to shift candidate scores into positive weights.
     *
     * @param evaluations candidate scores
     * @return smallest score
     */
    private static double min(Map<Integer, Double> evaluations) {
        double min = Double.MAX_VALUE;
        for (Map.Entry<Integer, Double> entry : evaluations.entrySet()) {
            min = Math.min(min, entry.getValue());
        }
        return min;
    }
}
