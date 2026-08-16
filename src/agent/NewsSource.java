package agent;

import endorsement.AttributesNewsSource;
import inputManager.InnerNewsSource;
import simulation.Step;
import utils.Console;
import utils.Error;
import utils.Randomness;
import simulation.FlyWeight;

import java.util.*;

/**
 * Runtime representation of a news-source type, including reach, endorsement distributions,
 * scenario-adjustable attributes, and unique-reposter state for the current simulation run.
 */
public class NewsSource implements FlyWeight, Step {
    private static int counter = 0;

    private final int ID;
    private final String name;
    private final double reach;
    private static final String CREDIBILITY = "CREDIBILIDAD DE LA FUENTE";
    private final Double fakeNewsProbability;
    private final InnerNewsSource innerNewsSource;
    private AttributesNewsSource attributes;
    private Set<Integer> uniqueSNSUsers;
    private Map<Integer,Boolean> fakenews;

    /**
     * Materializes a source from input data; construction is restricted to the package factory.
     *
     * @param innerNewsSource loaded source definition
     */
    NewsSource(InnerNewsSource innerNewsSource) {
        this.ID = counter++;
        this.name = innerNewsSource.name;
        this.reach = innerNewsSource.reach;
        this.fakeNewsProbability = innerNewsSource.fakeNewsProbability;
        this.attributes = new AttributesNewsSource(innerNewsSource.attributeNames, innerNewsSource.attributeValues);
        this.innerNewsSource = innerNewsSource;
        this.fakenews = new HashMap<Integer,Boolean>();

        reinit();
        Console.info("NewsSource: " + this);
    }

    /**
     * Identifies this source across selection and reporting structures.
     *
     * @return stable zero-based identifier used in selection maps and report arrays
     */
    public int getID() {
        return ID;
    }

    /** Resets source identifier allocation before a new factory population is created. */
    public static void resetCounter() {
        counter = 0;
    }

    /**
     * Exposes the canonical input name of this source.
     *
     * @return source name used in input matching, scenarios, and reports
     */
    public String getName() {
        return name;
    }

    /**
     * Supplies visibility sampling with the loaded source reach.
     *
     * @return probability that a user initially knows this source when reach filtering is enabled
     */
    public double getReach() {
        return reach;
    }

    /**
     * Returns the configured objective fake-news probability, or {@code null} when this source was
     * loaded from a legacy workbook that retains the credibility-based rule.
     */
    public Double getFakeNewsProbability() {
        return fakeNewsProbability;
    }

    /**
     * Supplies distributions for endorsement generation and scenario processing.
     *
     * @return current source attributes, including any active scenario transformation
     */
    public AttributesNewsSource getAttributes() {
        return attributes;
    }

    /**
     * Summarizes the cumulative unique audience for reporting.
     *
     * @return number of distinct SNS-user identifiers recorded for this run
     */
    public int getUniqueReposters() {
        return uniqueSNSUsers.size();
    }

    /**
     * Marks a user as a reposter so {@link simulation.Simulation} can report unique reach.
     *
     * @param idSNSUser identifier of the reposting user
     */
    public void addSNSUsers(int idSNSUser) {
        uniqueSNSUsers.add(idSNSUser);
    }

    /**
     * Installs scenario-transformed attributes for future endorsement generation.
     *
     * @param attributes replacement source attributes
     */
    public void setAttributes(AttributesNewsSource attributes) {
        this.attributes = attributes;
    }

    private boolean publishesFakeNews() {
       double probability = fakeNewsProbability == null
               ? attributes.getValues(CREDIBILITY)[0]
               : fakeNewsProbability;
       return probability > Randomness.nextDouble();
    }

    /**
     * Returns whether this source published fake news during an already processed period.
     * The simulation establishes this state by calling {@link #doStep(int)} before users receive
     * recommendations for the same period.
     * Missing state is a fatal simulation-invariant violation enforced by
     * {@link Error#setAssert(boolean, Object)}.
     *
     * @param period processed simulation period whose publication is queried
     * @return {@code true} when the publication generated for the period was fake news
     */
    public boolean isFakeNews(int period) {
        Boolean fakeNews = this.fakenews.get(period);
        Error.setAssert(fakeNews != null, "NewsSource " + name
                + " has no fake-news state for period " + period
                + "; doStep(period) must run before this query");
        return fakeNews;
    }

    /**
     * Returns the publication status from the greatest period processed by this source.
     * An empty publication history is a fatal simulation-invariant violation enforced by
     * {@link Error#setAssert(boolean, Object)}.
     *
     * @return {@code true} when the most recently processed publication was fake news
     */
    public boolean wasLastFakeNews() {
        Error.setAssert(!this.fakenews.isEmpty(), "NewsSource " + name
                + " has no fake-news state; doStep(period) must run before this query");
        int lastPeriod = Collections.max(this.fakenews.keySet());
        return this.fakenews.get(lastPeriod);
    }

    /**
     * Clears per-run reposter state and restores the original input attributes before the next run.
     */
    @Override
    public void reinit() {
        this.uniqueSNSUsers = new HashSet<>();
        this.attributes = new AttributesNewsSource(innerNewsSource.attributeNames, innerNewsSource.attributeValues);
        this.fakenews.clear();
    }

    /**
     * Generates and stores this source's publication status for the current simulation period.
     * This runs before SNS-user decisions and recommendations, making the result available to
     * {@link SNSUser#receiveRecommendation(int)} during the same period.
     *
     * @param period current simulation period
     */
    @Override
    public void doStep(int period) {
        this.fakenews.put(period, publishesFakeNews());
    }

    /**
     * Formats loaded identity, reach, and attributes for diagnostics.
     *
     * @return diagnostic representation used when sources are created and logged
     */
    @Override
    public String toString() {
        return "NewsSource{" +
                "id=" + ID + "," +
                "name='" + name + '\'' + "," +
                "reach='" + reach + '\'' + "," +
                "fakeNewsProbability='" + (fakeNewsProbability == null
                        ? "LEGACY_CREDIBILITY" : fakeNewsProbability) + '\'' + "," +
                "attributes=" + attributes + "," +
                "fakenews='" + (fakenews.isEmpty()? "": wasLastFakeNews()) + '\'' +
                '}';
    }

}
