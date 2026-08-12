package reporter;

/**
 * Candidate-level decision row; it reuses the decision shape but is written for every evaluated
 * source rather than only the source selected by an SNS user.
 */
public class DetailedAgentDecisionData extends AgentDecisionData {

    /**
     * Captures one candidate source score for detailed decision analysis.
     *
     * @param simulationId run identifier
     * @param period evaluation period
     * @param snsUserId evaluating user identifier
     * @param newsSourceName candidate source name
     * @param evaluation aggregate candidate score
     */
    public DetailedAgentDecisionData(int simulationId, int period, int snsUserId, String newsSourceName, double evaluation) {
        super(simulationId, period, snsUserId, newsSourceName, evaluation);
    }
}
