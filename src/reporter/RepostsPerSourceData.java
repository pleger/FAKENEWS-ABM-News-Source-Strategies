package reporter;

import inputManager.NewsSources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable aggregate row containing one count per source for a simulation period. */
public class RepostsPerSourceData {
    public final int simulationId;
    public final int period;
    public final int[] reposts;

    /**
     * Captures aggregate counts and clones the array so later simulation mutations cannot alter it.
     *
     * @param simulationId run identifier
     * @param period aggregation period
     * @param reposts source-ID-indexed count array
     */
    public RepostsPerSourceData(int simulationId, int period, int[] reposts) {
        this.simulationId = simulationId;
        this.period = period;
        this.reposts = reposts.clone();
    }

    /**
     * Builds the schema used for both total and unique-reposter worksheets.
     *
     * @return run/period columns followed by source names in report-array order
     */
    public static List<String> getHeader() {
        List<String> header = new ArrayList<>();
        header.add("SimulationId");
        header.add("Period");
        header.addAll(Arrays.asList(NewsSources.newsSourceNames().split(" ")));
        return header;
    }
}
