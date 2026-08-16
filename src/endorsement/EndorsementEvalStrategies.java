package endorsement;

import inputManager.Configuration;
import utils.Error;
import utils.Randomness;

import java.util.function.BiFunction;

/**
 * Converts news-source level distributions and SNS-user weights into signed endorsements.
 * {@link EndorsementFactory} uses the deterministic strategy for initialization and the
 * probabilistic strategy for period interactions, feeding the agent source-selection pipeline.
 */
public class EndorsementEvalStrategies {

    /**
     * Evaluates every source attribute against the like-named user weight using a supplied strategy.
     * The positional result is later converted into {@link Endorsement} records by the factory.
     *
     * @param anewsSources source probability distributions in model order
     * @param asnsUser user weights containing the same named model dimensions
     * @param strategy level-selection and scoring function to apply to each dimension
     * @return one signed endorsement value per source attribute
     */
    public static double[] evaluate(AttributesNewsSource anewsSources, AttributesSNSUser asnsUser, BiFunction<Double[], Double, Double> strategy) {
        int attributesNumber = anewsSources.size();
        double[] results = new double[attributesNumber];

        Error.setAssert(Configuration.ATTRIBUTES_SOURCE == attributesNumber, "EndorsementEvaluation: Wrong number of attributes of newsSource");

        for (int i = 0; i < attributesNumber; ++i) {
            String nameAtt = anewsSources.getName(i);
            Double[] valuesNewsSource = anewsSources.getValues(nameAtt);
            Double valueSNSUser = asnsUser.getValue(nameAtt);

            results[i] = strategy.apply(valuesNewsSource, valueSNSUser);
        }
        return results;
    }


    /**
     * Scores the most probable level of an attribute distribution deterministically.
     * {@link EndorsementFactory#createInitial(int, agent.SNSUser, agent.NewsSource)} uses this
     * strategy to seed a user's source memory without an initial random draw.
     *
     * @param attributes probability or weight assigned to each configured level
     * @param mean SNS-user weight for the corresponding attribute
     * @return signed contribution produced for the highest-valued level
     */
    public static Double BY_MAX(Double[] attributes, Double mean) {
        int index = -1;
        double max = Double.MAX_VALUE*-1;

        for (int i = 0; i < Configuration.LEVELS; ++i) {
            if (max < attributes[i]) {
                max = attributes[i];
                index = i;
            }
        }

        Error.setAssert(index != -1, "Endorsement Evaluation: MAX index not found");
        return calculateEndorsementFormula(index + 1, mean, Configuration.LEVELS);
    }

    /**
     * Samples a level from the cumulative source distribution and scores that outcome.
     * Period interactions use this stochastic strategy to model varying content observations.
     *
     * @param attributes cumulative sampling weights for the configured levels
     * @param mean SNS-user weight for the corresponding attribute
     * @return signed contribution produced for the sampled level
     */
    public static Double BY_PROBABILITY(Double[] attributes, Double mean) {
        double random = Randomness.nextDouble();
        double acc = 0;
        int index = -1;

        for (int i = 0; i < Configuration.LEVELS; ++i) {
            acc += attributes[i];
            if (acc >= random) {
                index = i;
                break;
            }
        }

        Error.setAssert(index != -1, "Endorsement: Evaluation BY_PROBABILITY index not found");
        return calculateEndorsementFormula(index + 1, mean, Configuration.LEVELS);
    }

    /**
     * Maps a one-based ordinal level around the scale midpoint to a signed, user-weighted value.
     * The asymmetric positive and negative factors determine how sampled attributes contribute
     * to the exponential source evaluation performed by the agent package.
     *
     * @param index one-based selected level
     * @param mean SNS-user weight for the attribute
     * @param levels total number of configured ordinal levels
     * @return signed endorsement contribution for the selected level
     */
    private static Double calculateEndorsementFormula(int index, Double mean, int levels) {
        int k = (int) Math.floor(index - levels / 2.0);
        k = levels % 2 == 0 && k <= 0 ? k - 1 : k;
        double div = levels % 2 == 0 ? levels : levels - 1;

        double result = 0;
        if (k > 0) {
            result = mean * k * (2.0 / div);
        }

        if (k < 0) {
            result = mean * k * (1.0 / div);
        }

        return result;
    }
}
