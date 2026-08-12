package gui;

import agent.NewsSource;
import reporter.Reporter;
import reporter.RepostsPerSourceData;
import simulation.Simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Chart-ready repost time series for one news source. A series can represent one simulation or an
 * across-simulation mean with a sample-standard-deviation error value for every period.
 */
public class DataRepostChart {
    private final String name;
    private final List<Integer> xData;
    private final List<Double> yData;
    private final List<Double> deviationData;

    /**
     * Creates a named chart series without variability values.
     *
     * @param name source label
     * @param xData periods
     * @param yData repost counts
     */
    public DataRepostChart(String name, List<Integer> xData, List<Double> yData) {
        this(name, xData, yData, new ArrayList<>());
    }

    /**
     * Creates a named chart series with aligned mean and variability values.
     *
     * @param name source label
     * @param xData periods
     * @param yData mean repost counts
     * @param deviationData sample standard deviations, or an empty list for a single-run series
     */
    public DataRepostChart(String name, List<Integer> xData, List<Double> yData,
                           List<Double> deviationData) {
        if (xData.size() != yData.size() || (!deviationData.isEmpty() && xData.size() != deviationData.size())) {
            throw new IllegalArgumentException("DataRepostChart requires aligned X, Y, and deviation values");
        }
        this.name = name;
        this.xData = new ArrayList<>(xData);
        this.yData = new ArrayList<>(yData);
        this.deviationData = new ArrayList<>(deviationData);
    }

    /** @return period coordinates consumed by XChart */
    public List<Integer> getXData() {
        return xData;
    }

    /** @return source counts or across-run mean counts */
    public List<Double> getYData() {
        return yData;
    }

    /** @return sample standard deviations, or an empty list for a single simulation */
    public List<Double> getDeviationData() {
        return deviationData;
    }

    /** @return source label used in the chart legend */
    public String getName() {
        return name;
    }

    /** @return {@code true} when the series includes variability values */
    public boolean hasDeviationData() {
        return !deviationData.isEmpty();
    }

    /**
     * Compatibility entry point that creates cumulative unique-reposter series for the active run.
     *
     * @param newsSources source population defining series order and labels
     * @return active-run cumulative unique-reposter series
     */
    public static DataRepostChart[] createDataRepostChart(List<NewsSource> newsSources) {
        return createPerSimulation(newsSources, Reporter.getUniqueRepostersPerSourceData(), Simulation.ID);
    }

    /**
     * Splits report rows for one simulation into one ordered series per source.
     *
     * @param newsSources source population defining series order and labels
     * @param rows total-repost or unique-reposter report rows
     * @param simulationId simulation whose rows should be plotted
     * @return source-indexed chart series ordered by period
     */
    public static DataRepostChart[] createPerSimulation(List<NewsSource> newsSources,
                                                         List<? extends RepostsPerSourceData> rows,
                                                         int simulationId) {
        List<RepostsPerSourceData> selectedRows = new ArrayList<>();
        for (RepostsPerSourceData row : rows) {
            validateSourceCount(newsSources, row);
            if (row.simulationId == simulationId) {
                selectedRows.add(row);
            }
        }
        selectedRows.sort(Comparator.comparingInt(row -> row.period));

        DataRepostChart[] result = emptySeries(newsSources);
        for (RepostsPerSourceData row : selectedRows) {
            for (int source = 0; source < result.length; ++source) {
                result[source].xData.add(row.period);
                result[source].yData.add((double) row.reposts[source]);
            }
        }
        return result;
    }

    /**
     * Aggregates aligned simulation rows into period/source means and sample standard deviations.
     * Every simulation must provide exactly one row for every included period.
     *
     * @param newsSources source population defining series order and labels
     * @param rows total-repost or unique-reposter report rows from all completed simulations
     * @return source-indexed mean series with sample-standard-deviation values
     */
    public static DataRepostChart[] createAggregate(List<NewsSource> newsSources,
                                                     List<? extends RepostsPerSourceData> rows) {
        DataRepostChart[] result = emptySeries(newsSources);
        if (rows.isEmpty()) {
            return result;
        }

        Set<Integer> simulationIds = new TreeSet<>();
        Map<Integer, Map<Integer, RepostsPerSourceData>> rowsByPeriod = new TreeMap<>();
        for (RepostsPerSourceData row : rows) {
            validateSourceCount(newsSources, row);
            simulationIds.add(row.simulationId);
            Map<Integer, RepostsPerSourceData> periodRows = rowsByPeriod.computeIfAbsent(
                    row.period, ignored -> new HashMap<>());
            if (periodRows.put(row.simulationId, row) != null) {
                throw new IllegalStateException("Duplicate repost row for simulation " + row.simulationId
                        + " and period " + row.period);
            }
        }

        for (Map.Entry<Integer, Map<Integer, RepostsPerSourceData>> periodEntry : rowsByPeriod.entrySet()) {
            if (!periodEntry.getValue().keySet().equals(simulationIds)) {
                throw new IllegalStateException("Repost rows are not aligned at period " + periodEntry.getKey());
            }

            for (int source = 0; source < newsSources.size(); ++source) {
                double sum = 0.0;
                for (RepostsPerSourceData row : periodEntry.getValue().values()) {
                    sum += row.reposts[source];
                }
                double mean = sum / simulationIds.size();
                double squaredDifferences = 0.0;
                for (RepostsPerSourceData row : periodEntry.getValue().values()) {
                    double difference = row.reposts[source] - mean;
                    squaredDifferences += difference * difference;
                }
                double deviation = simulationIds.size() == 1
                        ? 0.0
                        : Math.sqrt(squaredDifferences / (simulationIds.size() - 1));

                result[source].xData.add(periodEntry.getKey());
                result[source].yData.add(mean);
                result[source].deviationData.add(deviation);
            }
        }
        return result;
    }

    private static DataRepostChart[] emptySeries(List<NewsSource> newsSources) {
        DataRepostChart[] result = new DataRepostChart[newsSources.size()];
        for (int source = 0; source < newsSources.size(); ++source) {
            result[source] = new DataRepostChart(newsSources.get(source).getName(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        return result;
    }

    private static void validateSourceCount(List<NewsSource> newsSources, RepostsPerSourceData row) {
        if (row.reposts.length != newsSources.size()) {
            throw new IllegalStateException("Repost row for simulation " + row.simulationId
                    + " period " + row.period + " contains " + row.reposts.length
                    + " sources; expected " + newsSources.size());
        }
    }
}
