package gui;

import agent.NewsSource;
import agent.SNSUser;
import inputManager.Configuration;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;
import reporter.Reporter;
import reporter.RepostsPerSourceData;
import scenarios.Scenario;
import scenarios.ScenarioFactory;
import simulation.Simulation;
import utils.Console;
import utils.Error;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds, optionally displays, and saves XChart visualizations from simulation report data. */
public class Chart {

    /**
     * Saves separate per-period and cumulative-unique-reposter charts for the active simulation.
     *
     * @param newsSources source population defining series order and labels
     */
    public static void displayReposts(List<NewsSource> newsSources) {
        List<? extends RepostsPerSourceData> totalRows = Reporter.getTotalRepostsPerSourceData();
        List<? extends RepostsPerSourceData> uniqueRows = Reporter.getUniqueRepostersPerSourceData();
        if (totalRows.isEmpty() || uniqueRows.isEmpty()) {
            Console.warn("Chart: repost data is empty; enable SAVED_REPOSTS_PER_SOURCE to generate repost charts");
            return;
        }

        Console.info("Chart: Creating per-period and cumulative unique-reposter charts for simulation "
                + Simulation.ID);
        XYChart totalChart = createRepostChart("Simulation " + Simulation.ID + " - Reposts per Period",
                "Reposts in period");
        addRepostSeries(totalChart,
                DataRepostChart.createPerSimulation(newsSources, totalRows, Simulation.ID));
        addScenarioMarker(totalChart);

        XYChart uniqueChart = createRepostChart(
                "Simulation " + Simulation.ID + " - Cumulative Unique Reposters",
                "Cumulative unique reposters");
        addRepostSeries(uniqueChart,
                DataRepostChart.createPerSimulation(newsSources, uniqueRows, Simulation.ID));
        addScenarioMarker(uniqueChart);

        if (Configuration.REPETITIONS == 0) {
            drawChart(totalChart);
            drawChart(uniqueChart);
        }
        saveChart(totalChart, "Simulation_" + Simulation.ID + "_RepostsPerPeriod");
        saveChart(uniqueChart, "Simulation_" + Simulation.ID + "_UniqueReposters");
    }

    /**
     * Saves mean and sample-standard-deviation charts across all completed simulations.
     *
     * @param newsSources source population defining series order and labels
     */
    public static void displayAggregateReposts(List<NewsSource> newsSources) {
        List<? extends RepostsPerSourceData> totalRows = Reporter.getTotalRepostsPerSourceData();
        List<? extends RepostsPerSourceData> uniqueRows = Reporter.getUniqueRepostersPerSourceData();
        if (totalRows.isEmpty() || uniqueRows.isEmpty()) {
            Console.warn("Chart: aggregate repost data is empty; aggregate charts were not generated");
            return;
        }

        Console.info("Chart: Creating across-simulation mean and standard-deviation charts");
        XYChart totalChart = createRepostChart(
                "All Simulations - Mean Reposts per Period (\u00b1 1 SD)", "Mean reposts in period");
        addRepostSeries(totalChart, DataRepostChart.createAggregate(newsSources, totalRows));
        addScenarioMarker(totalChart);

        XYChart uniqueChart = createRepostChart(
                "All Simulations - Mean Cumulative Unique Reposters (\u00b1 1 SD)",
                "Mean cumulative unique reposters");
        addRepostSeries(uniqueChart, DataRepostChart.createAggregate(newsSources, uniqueRows));
        addScenarioMarker(uniqueChart);

        saveChart(totalChart, "Aggregate_RepostsPerPeriod_MeanSD");
        saveChart(uniqueChart, "Aggregate_UniqueReposters_MeanSD");
    }

    /**
     * Builds per-user source-selection series and renders/saves the resulting chart.
     *
     * @param snsUsers agents supplying recorded decisions
     * @param newsSources sources supplying Y-axis labels
     */
    public static void displaySelection(List<SNSUser> snsUsers, List<NewsSource> newsSources) {
        XYChart selectionChart = createSelectionChart(newsSources);
        snsUsers.forEach(snsUser -> registerSelectionSeries(selectionChart, snsUser.getDataSeries()));
        if (Configuration.REPETITIONS == 0) {
            drawChart(selectionChart);
        }
        saveChart(selectionChart, "Simulation_" + Simulation.ID + "_Selections");
    }

    private static XYChart createRepostChart(String title, String yAxisTitle) {
        XYChart result = new XYChartBuilder().width(800).height(600).title(title)
                .xAxisTitle("Period").yAxisTitle(yAxisTitle).build();
        result.getStyler().setYAxisDecimalPattern("#0.##")
                .setXAxisDecimalPattern("#0")
                .setYAxisMin(0.0)
                .setLegendPosition(Styler.LegendPosition.InsideNE);
        return result;
    }

    private static XYChart createSelectionChart(List<NewsSource> newsSources) {
        XYChart result = new XYChartBuilder().width(800).height(600)
                .title("Simulation " + Simulation.ID + " - Source Selection")
                .xAxisTitle("Period").yAxisTitle("NewsSource").build();
        result.getStyler().setYAxisDecimalPattern("#0")
                .setXAxisDecimalPattern("#0")
                .setYAxisMax(newsSources.size() * 1.0)
                .setLegendPosition(Styler.LegendPosition.InsideNE);
        Map<Double, Object> labels = new HashMap<>();
        for (NewsSource newsSource : newsSources) {
            labels.put(newsSource.getID() * 1.0, newsSource.getName());
        }
        result.setYAxisLabelOverrideMap(labels);
        return result;
    }

    private static void addRepostSeries(XYChart target, DataRepostChart[] data) {
        for (DataRepostChart series : data) {
            if (series.getXData().isEmpty()) {
                continue;
            }
            if (series.hasDeviationData()) {
                target.addSeries(series.getName(), series.getXData(), series.getYData(),
                        series.getDeviationData());
            } else {
                target.addSeries(series.getName(), series.getXData(), series.getYData());
            }
        }
    }

    /** Adds a dashed intervention line only when its period lies inside the chart's data range. */
    private static void addScenarioMarker(XYChart target) {
        if (Configuration.SCENARIO == Configuration.DISABLED || target.getSeriesMap().isEmpty()) {
            return;
        }
        Scenario scenario = ScenarioFactory.get(Configuration.SCENARIO);
        int start = scenario.getStartPeriod();
        double minimumPeriod = Double.MAX_VALUE;
        double maximumPeriod = Double.MIN_VALUE;
        double maximumValue = 0.0;
        for (XYSeries series : target.getSeriesMap().values()) {
            for (double value : series.getXData()) {
                minimumPeriod = Math.min(minimumPeriod, value);
                maximumPeriod = Math.max(maximumPeriod, value);
            }
            for (double value : series.getYData()) {
                maximumValue = Math.max(maximumValue, value);
            }
        }
        if (start < minimumPeriod || start > maximumPeriod) {
            return;
        }

        XYSeries marker = target.addSeries("Scenario at period " + start,
                new double[]{start, start}, new double[]{0.0, Math.max(1.0, maximumValue)});
        marker.setLineColor(Color.GRAY);
        marker.setLineStyle(SeriesLines.DASH_DASH);
        marker.setMarker(SeriesMarkers.NONE);
    }

    private static void registerSelectionSeries(XYChart target, DataChart dataChart) {
        target.addSeries(dataChart.getName(), dataChart.getXData(), dataChart.getYData());
    }

    private static void drawChart(XYChart target) {
        new SwingWrapper<>(target).displayChart();
    }

    /** Saves a PNG under the current run's already timestamped output directory. */
    private static void saveChart(XYChart target, String baseName) {
        String fileName = Configuration.OUTPUT_DIRECTORY + "/" + baseName;
        try {
            Console.info("Chart: Saving " + baseName + ".png");
            BitmapEncoder.saveBitmap(target, fileName, BitmapEncoder.BitmapFormat.PNG);
        } catch (IOException ex) {
            Error.trigger("Image cannot be saved: " + fileName + ".png", ex);
        }
    }
}
