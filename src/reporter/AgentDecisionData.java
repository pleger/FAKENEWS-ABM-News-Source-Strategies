package reporter;

import java.util.Arrays;
import java.util.List;

/** Immutable worksheet row describing the source selected by one SNS user in one period. */
public class AgentDecisionData {
    public final int simulationId;
    public final int period;
    public final int snsUserId;
    public final String newsSourceName;
    public final double evaluation;

    /**
     * Captures a selected-source result for deferred workbook writing.
     *
     * @param simulationId run identifier
     * @param period decision period
     * @param snsUserId selecting user identifier
     * @param newsSourceName selected source name
     * @param evaluation aggregate score of the selected source
     */
    public AgentDecisionData(int simulationId, int period, int snsUserId, String newsSourceName, double evaluation) {
        this.simulationId = simulationId;
        this.period = period;
        this.snsUserId = snsUserId;
        this.newsSourceName = newsSourceName;
        this.evaluation = evaluation;
    }

    /**
     * Defines the schema shared by selected and detailed decision worksheets.
     *
     * @return stable column headers matching the DTO field order
     */
    public static List<String> getHeader() {
        return Arrays.asList("SimulationId", "Period", "UserId", "Source", "Evaluation");
    }
}
