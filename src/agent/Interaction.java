package agent;

import endorsement.EndorsementFactory;
import endorsement.Endorsements;
import inputManager.Configuration;
import utils.Error;
import simulation.Simulation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes one SNS-user/news-source interaction: evaluate known sources, select one, and generate
 * the new endorsement events that extend the user's memory for subsequent periods.
 */
public class Interaction {

    /**
     * Runs the source-decision pipeline for one user and delegates event creation to the
     * endorsement package.
     *
     * @param period current simulation period
     * @param snsUser user making the decision
     * @param newsSources sources currently known to that user
     * @return new attribute endorsements for the selected source
     */
    public static Endorsements interact(int period, SNSUser snsUser, List<NewsSource> newsSources) {
        NewsSource selectedNewsSource = selectNewsSource(period, snsUser, newsSources);
        Error.setAssert(selectedNewsSource != null, "Interaction: No NewsSource selected. Selected:" + selectedNewsSource + " newsSourceSize:" + newsSources.size() + " snsUserSize:" + snsUser.getID());

        return EndorsementFactory.createByStep(period, snsUser, selectedNewsSource);
    }

    /**
     * Aggregates remembered endorsements for every known source, reports each candidate score,
     * probabilistically selects a source, and stores its score for WOM propagation and reports.
     *
     * @param period current period used to apply memory filtering
     * @param snsUser decision-making user
     * @param newsSources candidate sources
     * @return selected source, or {@code null} if the selected identifier cannot be resolved
     */
    private static NewsSource selectNewsSource(int period, SNSUser snsUser, List<NewsSource> newsSources) {
        Map<Integer, Double> evaluations = new HashMap<>();

        for (NewsSource newsSource : newsSources) {
            Endorsements endors = snsUser.getEndorsements().filterByNewsSource(newsSource).filterByMemory(period);
            double eval = evaluateNewsSource(endors, period);
            evaluations.put(newsSource.getID(), eval);

            report(period, snsUser, newsSource, eval);
        }
        
        int idSelected = NewsSourceSelectionStrategies.BY_PROBABILITY(evaluations);
        NewsSource mkSelected = null;

        for (NewsSource mk: newsSources) {
            if (mk.getID() == idSelected) {
                mkSelected = mk;
                break;
            }
        }
        
        snsUser.setCurrentEvaluation(evaluations.get(idSelected));
        return mkSelected;
    }

    /**
     * Converts signed endorsement contributions into the aggregate score used for selection.
     * Positive and negative values are exponentiated with {@link Configuration#BASE} while
     * retaining their sign.
     *
     * @param values remembered endorsement contributions for one source
     * @return aggregate source evaluation
     */
    private static double evaluateNewsSource(double[] values) {
        double result = 0;

        for (double value : values) {
            result += evaluateEndorsement(value);
        }
        return result;
    }

    /** Transforms one signed endorsement while preserving a zero contribution as neutral. */
    private static double evaluateEndorsement(double value) {
        if (value > 0) {
            return Math.pow(Configuration.BASE, value);
        }
        if (value < 0) {
            return -Math.pow(Configuration.BASE, Math.abs(value));
        }
        return 0.0;
    }

    /**
     * Evaluates remembered events after applying optional exponential decay to each event's
     * transformed contribution. Initial period {@code -1} events are treated as current at the
     * first decision so enabling decay does not weaken the initial source evidence before use.
     */
    private static double evaluateNewsSource(Endorsements endorsements, int currentPeriod) {
        final double[] result = {0.0};
        endorsements.forEach(endorsement -> {
            double contribution = evaluateEndorsement(endorsement.getValue());
            result[0] += contribution * memoryDecayWeight(endorsement.getPeriod(), currentPeriod);
        });
        return result[0];
    }

    /** Returns one for disabled decay, otherwise a half-life weight based on event age. */
    private static double memoryDecayWeight(int eventPeriod, int currentPeriod) {
        if (Configuration.MEMORY_HALF_LIFE == Configuration.MEMORY_HALF_LIFE_DISABLED) {
            return 1.0;
        }

        int effectiveEventPeriod = Math.max(1, eventPeriod);
        int age = currentPeriod - effectiveEventPeriod;
        Error.setAssert(age >= 0, "Interaction: endorsement belongs to future period " + eventPeriod +
                " while evaluating period " + currentPeriod);
        return Math.pow(0.5, age / Configuration.MEMORY_HALF_LIFE);
    }

    /**
     * Registers a candidate-level score for optional detailed decision output.
     *
     * @param period evaluated period
     * @param snsUser evaluated user
     * @param newsSource evaluated candidate source
     * @param eval aggregate candidate score
     */
    private static void report(int period, SNSUser snsUser, NewsSource newsSource, double eval) {
        reporter.Reporter.addDetailedAgentDecisionData(Simulation.ID, period, snsUser.getID(), newsSource.getName(), eval);
    }
}
