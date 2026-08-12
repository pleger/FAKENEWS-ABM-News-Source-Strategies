package reporter;

import inputManager.InnerNewsSource;
import inputManager.NewsSources;

import java.util.ArrayList;
import java.util.List;

/** Immutable report row containing one fake-news publication status per source and period. */
public class FakeNewsPerSourceData {
    public final int simulationId;
    public final int period;
    public final boolean[] fakeNews;

    /**
     * Captures one period's source states and clones them so later simulation resets cannot alter
     * accumulated report data.
     *
     * @param simulationId run identifier
     * @param period publication period
     * @param fakeNews source-ID-indexed fake-news statuses
     */
    public FakeNewsPerSourceData(int simulationId, int period, boolean[] fakeNews) {
        this.simulationId = simulationId;
        this.period = period;
        this.fakeNews = fakeNews.clone();
    }

    /**
     * Builds the worksheet schema in the same stable source order used by the status array.
     *
     * @return simulation and period columns followed by all loaded source names
     */
    public static List<String> getHeader() {
        List<String> header = new ArrayList<>();
        header.add("Simulation");
        header.add("Period");
        for (InnerNewsSource source : NewsSources.getInnerNewsSources()) {
            header.add(source.name);
        }
        return header;
    }
}
