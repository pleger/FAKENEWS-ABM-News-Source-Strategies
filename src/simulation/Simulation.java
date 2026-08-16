package simulation;

import agent.SNSUser;
import agent.NewsSource;
import gui.Chart;
import inputManager.Configuration;
import utils.Console;
import utils.Randomness;
import reporter.ReportRegister;
import reporter.Reporter;
import scenarios.ScenarioManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates complete FAKENEWS-ABM runs: network initialization, user decisions, scenario
 * activation, reporting, word-of-mouth propagation, optional charting, and repetition reset.
 */
public class Simulation implements FlyWeight, Step, ReportRegister {
    public static int ID = 0;

    private final int periods;
    private final List<SNSUser> snsUsers;
    private final List<NewsSource> newsSources;

    /**
     * Creates an orchestrator around factory-built agents and sources and initializes its first run.
     *
     * @param snsUsers agent population
     * @param newsSources runtime source population
     * @param periods number of discrete periods per run
     */
    public Simulation(List<SNSUser> snsUsers, List<NewsSource> newsSources, int periods) {
        this.periods = periods;
        this.snsUsers = snsUsers;
        this.newsSources = newsSources;

        reinit();
        Console.info("Simulation: created with " + snsUsers.size() + " snsUsers and " + newsSources.size() + " newsSources");
    }

    /**
     * Advances the run identifier and restores sources before rebuilding user state for the next
     * execution. Source restoration must precede initial endorsement generation so a scenario from
     * the previous repetition cannot contaminate the next repetition's initial conditions.
     */
    @Override
    public void reinit() {
        ++Simulation.ID;
        newsSources.iterator().forEachRemaining(NewsSource::reinit);
        ScenarioManager.reinit();
        snsUsers.iterator().forEachRemaining(SNSUser::reinit);
        snsUsers.iterator().forEachRemaining(snsUser -> snsUser.setFriends(snsUsers));
        snsUsers.iterator().forEachRemaining(snsUser -> snsUser.setKnowNewsSources(filterReach(newsSources)));
        snsUsers.iterator().forEachRemaining(SNSUser::setInitialEndorsements);
        System.gc(); //clean memory
    }

    /**
     * Samples the sources initially visible to one user when reach filtering is enabled.
     *
     * @param newsSources complete runtime source population
     * @return all sources when filtering is disabled, otherwise a newly sampled subset
     */
    private List<NewsSource> filterReach(List<NewsSource> newsSources) {
        if (!Configuration.SOURCE_REACH) {
            return newsSources; //all newsSources
        }

        List<NewsSource> filteredNewsSource = new ArrayList<>();

        double random;
        for (NewsSource mk : newsSources) {
            random = Randomness.nextDouble();
            if (random < mk.getReach()) {
                filteredNewsSource.add(mk);
            }
        }

        return filteredNewsSource;
    }

    /**
     * Counts total selections and cumulative distinct reposters per source, then registers both
     * report rows for a post-learning period.
     *
     * @param period period whose decisions are aggregated
     */
    private void generateRepostsPerData(int period) {
        int[] reposts = new int[newsSources.size()];
        int[] uniqueReposters = new int[newsSources.size()];

        snsUsers.iterator().forEachRemaining(snsUser -> {
            NewsSource selectedNewsSource = snsUser.getLastSelectMarked(period);

            if (selectedNewsSource != null) {
                selectedNewsSource.addSNSUsers(snsUser.getID());
                reposts[selectedNewsSource.getID()]++;
            }
        });

        newsSources.iterator().forEachRemaining(newsSource -> uniqueReposters[newsSource.getID()] = newsSource.getUniqueReposters());

        Reporter.addRepostsByNewsSourceData(ID, period, reposts);
        Reporter.addRepostsUniqueByNewsSourceData(ID, period, uniqueReposters);
    }

    /** Registers every source's already-generated publication classification for this period. */
    private void generateFakeNewsPerSourceData(int period) {
        boolean[] fakeNews = new boolean[newsSources.size()];
        for (NewsSource newsSource : newsSources) {
            fakeNews[newsSource.getID()] = newsSource.isFakeNews(period);
        }
        Reporter.addFakeNewsPerSourceData(ID, period, fakeNews);
    }

    /**
     * Executes every period in order, including decisions, interventions, reports, WOM, and logs;
     * renders configured charts and prepares reusable objects for the next repetition afterward.
     */
    public void run() {
        Console.info("Simulation: Starting " + Simulation.ID + " with periods=" + periods +
                ", learningPeriods=" + Configuration.LEARNING_PERIODS +
                ", wom=" + Configuration.WOM +
                ", sourceReach=" + Configuration.SOURCE_REACH);
        logNetworkSummary();

        for (int period = 1; period <= periods; ++period) {
            Console.info("Simulation: Period " + period + "/" + periods + " started");
            ScenarioManager.apply(period);
            doStep(period);
            report(period);

            int recommendations = 0;
            if (Configuration.WOM) {
                for (SNSUser snsUser : snsUsers) {
                    if (snsUser.receiveRecommendation(period)) {
                        ++recommendations;
                    }
                }
            }

            logPeriodSummary(period, recommendations);
        }

        if (Configuration.GUI) {
            //Chart.displaySelection(snsUsers, newsSources);
            Chart.displayReposts(newsSources);
        }

        Console.info("Simulation: Completed " + Simulation.ID + " periods=" + periods);
        reinit();
    }

    /** Logs average source visibility and contact counts after network initialization. */
    private void logNetworkSummary() {
        int totalKnownSources = 0;
        int totalFriends = 0;

        for (SNSUser snsUser : snsUsers) {
            totalKnownSources += snsUser.getKnownNewsSourceCount();
            totalFriends += snsUser.getFriendCount();
        }

        double averageKnownSources = snsUsers.isEmpty() ? 0 : (double) totalKnownSources / snsUsers.size();
        double averageFriends = snsUsers.isEmpty() ? 0 : (double) totalFriends / snsUsers.size();

        Console.info("Simulation: Network ready for " + Simulation.ID +
                " users=" + snsUsers.size() +
                ", newsSources=" + newsSources.size() +
                ", avgKnownSources=" + String.format(Locale.US, "%.2f", averageKnownSources) +
                ", avgFriends=" + String.format(Locale.US, "%.2f", averageFriends));
    }

    /**
     * Logs selection coverage, learning/reporting state, and WOM activity for a completed period.
     *
     * @param period completed period
     * @param recommendations number of users that received a recommendation
     */
    private void logPeriodSummary(int period, int recommendations) {
        int selections = 0;
        for (SNSUser snsUser : snsUsers) {
            if (snsUser.getLastSelectMarked(period) != null) {
                ++selections;
            }
        }

        String reportingState = period > Configuration.LEARNING_PERIODS ? "saved" : "learning";
        Console.info("Simulation: Period " + period + "/" + periods +
                " completed selections=" + selections + "/" + snsUsers.size() +
                ", reportState=" + reportingState +
                ", womRecommendations=" + recommendations);
    }


    /**
     * Advances all SNS-user agents through their source-selection behavior.
     *
     * @param period current simulation period
     */
    @Override
    public void doStep(int period) {
        newsSources.iterator().forEachRemaining(newsSource -> {newsSource.doStep(period);});
        snsUsers.iterator().forEachRemaining(snsUser -> snsUser.doStep(period));
    }

    /**
     * Registers aggregate repost data after learning and exports all current-period endorsements.
     *
     * @param period period being reported
     */
    @Override
    public void report(int period) {
        generateFakeNewsPerSourceData(period);
        if (period > Configuration.LEARNING_PERIODS) generateRepostsPerData(period);
        snsUsers.iterator().forEachRemaining(snsUser -> Reporter.addEndorsementData(snsUser.getEndorsementData(period)));
    }

    /**
     * Formats run identity and population sizes for repetition logging.
     *
     * @return diagnostic summary of the active run and its population sizes
     */
    @Override
    public String toString() {
        return "Simulation{" +
                "ID=" + Simulation.ID +
                ", periods=" + periods +
                ", snsUsers=" + snsUsers.size() +
                ", newsSources=" + newsSources.size() +
                '}';
    }
}
