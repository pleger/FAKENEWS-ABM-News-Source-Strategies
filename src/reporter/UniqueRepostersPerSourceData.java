package reporter;

/** Aggregate row specializing repost counts as cumulative distinct reposters per source. */
public class UniqueRepostersPerSourceData extends RepostsPerSourceData{
    /**
     * Captures unique-reposter counts using the shared source-indexed row representation.
     *
     * @param simulationId run identifier
     * @param period aggregation period
     * @param reposts distinct-user counts indexed by source identifier
     */
    public UniqueRepostersPerSourceData(int simulationId, int period, int[] reposts) {
        super(simulationId,period,reposts);
    }
}
