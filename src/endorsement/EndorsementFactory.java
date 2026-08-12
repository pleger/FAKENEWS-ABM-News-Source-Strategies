package endorsement;

import agent.SNSUser;
import agent.NewsSource;

import java.util.function.BiFunction;

/**
 * Builds attribute-level endorsement events from agent and source model state.
 * It bridges domain objects in the agent package with the evaluation strategies in this package.
 */
public class EndorsementFactory {

    /**
     * Creates deterministic seed endorsements for every source known during simulation reset.
     *
     * @param period seed period, conventionally {@code -1}
     * @param snsUser user whose attribute weights drive evaluation
     * @param newsSource source whose most likely levels are evaluated
     * @return initial endorsement events for all source attributes
     */
    public static Endorsements createInitial(int period, SNSUser snsUser, NewsSource newsSource) {
        return create(period,snsUser,newsSource, EndorsementEvalStrategies::BY_MAX);
    }
    /**
     * Creates stochastic endorsements after a user selects a source during a simulation period.
     *
     * @param period current simulation period
     * @param snsUser interacting user
     * @param newsSource selected news source
     * @return sampled endorsement events for all source attributes
     */
    public static Endorsements createByStep(int period, SNSUser snsUser, NewsSource newsSource) {
        return create(period,snsUser,newsSource, EndorsementEvalStrategies::BY_PROBABILITY);
    }

    /**
     * Applies one evaluation strategy and materializes its positional results as named events.
     *
     * @param period period assigned to the generated records
     * @param snsUser owner of the user weights
     * @param newsSource owner of the source distributions and attribute names
     * @param strategy deterministic or stochastic scoring function
     * @return generated endorsement collection
     */
    private static Endorsements create(int period, SNSUser snsUser, NewsSource newsSource, BiFunction<Double[], Double, Double> strategy) {
        Endorsements endors = new Endorsements();

        AttributesNewsSource aNewsSource = newsSource.getAttributes();
        AttributesSNSUser aSNSUser = snsUser.getAttribute();

        double[] results = EndorsementEvalStrategies.evaluate(aNewsSource, aSNSUser, strategy);

        for (int i = 0; i < results.length; ++i) {
            endors.add(new Endorsement(period, newsSource, aNewsSource.getName(i), results[i]));
        }

        return endors;
    }
}
