package gui;

import java.util.ArrayList;

/** Stores one SNS user's period-to-source selection series for optional GUI charting. */
public class DataChart {
    private final String seriesName;
    private final ArrayList<Integer> xData;
    private final ArrayList<Integer> yData;

    /**
     * Creates an empty chart series for an agent.
     *
     * @param seriesName label shown in the chart legend
     */
    public DataChart(String seriesName) {
        this.seriesName = seriesName;
        xData = new ArrayList<>();
        yData = new ArrayList<>();
    }

    /**
     * Appends one period and selected source identifier to the series.
     *
     * @param x simulation period
     * @param y selected source identifier
     */
    public void addData(int x, int y) {
        xData.add(x);
        yData.add(y);
    }

    /**
     * Exposes recorded periods to chart assembly.
     *
     * @return mutable period values consumed by XChart
     */
    public ArrayList<Integer> getXData() {
        return xData;
    }

    /**
     * Exposes recorded source identifiers to chart assembly.
     *
     * @return mutable selected-source values consumed by XChart
     */
    public ArrayList<Integer> getYData() {
        return yData;
    }

    /**
     * Identifies the agent series in the chart legend.
     *
     * @return legend label for this series
     */
    public String getName() {
        return seriesName;
    }
}
