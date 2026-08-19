package agent;

import endorsement.AttributesSNSUser;
import endorsement.Endorsement;
import endorsement.EndorsementFactory;
import endorsement.Endorsements;
import endorsement.WomRecommendationEffect;
import gui.DataChart;
import inputManager.Configuration;
import inputManager.InnerSNSUser;
import utils.Console;
import utils.Error;
import utils.Randomness;
import reporter.ReportRegister;
import reporter.Reporter;
import reporter.EndorsementData;
import simulation.FlyWeight;
import simulation.Simulation;
import simulation.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent that knows a subset of news sources, accumulates endorsement memory, selects a source to
 * repost each period, and exchanges word-of-mouth recommendations through a random contact network.
 */
public class SNSUser implements Step, FlyWeight, ReportRegister {
    private static final String WORD_OF_MOUTH_ATTRIBUTE = "WORD OF MOUTH";
    private static int counter = 0;

    private final int ID;
    private final AttributesSNSUser attribute;
    private final List<SNSUser> friends;
    private final Endorsements endors;
    private final List<PendingWomEndorsement> pendingWom;
    private List<NewsSource> knownNewsSources;

    private final DataChart data;
    private double currentNewsSourceEvaluation;

    /**
     * Creates an agent from the loaded prototype; population creation is owned by the package factory.
     *
     * @param ib input prototype containing shared user attribute means
     */
    SNSUser(InnerSNSUser ib) {
        this.ID = counter++;
        this.friends = new ArrayList<>();
        this.knownNewsSources = new ArrayList<>();
        this.endors = new Endorsements();
        this.pendingWom = new ArrayList<>();

        ArrayList<Double[]> values = new ArrayList<>();
        for (Double value : ib.attributeValues) {
            values.add(new Double[]{value});
        }

        attribute = new AttributesSNSUser(ib.attributeNames, values);
        data = new DataChart(Integer.toString(ID));

        Console.info("SNSUser: " + this);
    }

    /**
     * Builds this agent's random contact set according to configured contact and friendship rates.
     * Simulation reset invokes this before period execution begins.
     *
     * @param snsUsers complete population from which contacts are sampled
     */
    public void setFriends(List<SNSUser> snsUsers) {
        if (Configuration.NETWORK_TOPOLOGY == 1) {
            setSmallWorldFriends(snsUsers);
            return;
        }
        int friendCounter = 0;
        int friendSize = Math.min((int) (Configuration.CONTACTS * Configuration.FRIENDS), Math.max(0, snsUsers.size() - 1));

        while (friendCounter < friendSize) {
            SNSUser potentialContact = snsUsers.get((int) (Randomness.nextDouble() * snsUsers.size()));
            if (addFriend(potentialContact)) {
                ++friendCounter;
            }
        }
    }

    private void setSmallWorldFriends(List<SNSUser> snsUsers) {
        int friendSize = Math.min((int) (Configuration.CONTACTS * Configuration.FRIENDS),
                Math.max(0, snsUsers.size() - 1));
        for (int offset = 1; offset <= friendSize; ++offset) {
            SNSUser candidate = snsUsers.get((ID + offset) % snsUsers.size());
            if (Randomness.nextDouble() < Configuration.NETWORK_REWIRING_PROBABILITY) {
                candidate = null;
            }
            while (candidate == null || candidate == this || friends.contains(candidate)) {
                candidate = snsUsers.get((int) (Randomness.nextDouble() * snsUsers.size()));
            }
            addFriend(candidate);
        }
    }

    /**
     * Adds a distinct contact while preventing self-links and duplicates.
     *
     * @param potentialContact sampled population member
     * @return {@code true} if the contact was newly added
     */
    private boolean addFriend(SNSUser potentialContact) {
        if (potentialContact != this && !friends.contains(potentialContact)) {
            friends.add(potentialContact);
            return true;
        }
        return false;
    }

    /**
     * Exposes accumulated evidence to the interaction pipeline.
     *
     * @return mutable endorsement memory used by interaction and reporting stages
     */
    public Endorsements getEndorsements() {
        return endors;
    }

    /**
     * Supplies user preferences to endorsement generation.
     *
     * @return the user's model-dimension weights used to create endorsements
     */
    public AttributesSNSUser getAttribute() {
        return attribute;
    }

    /**
     * Identifies this agent across reporting and unique-reposter tracking.
     *
     * @return stable zero-based identifier used in reports and source reposter sets
     */
    public int getID() {
        return ID;
    }

    /** Resets identifier allocation before the factory creates a new agent population. */
    static void resetCounter() {
        counter = 0;
    }

    /**
     * Supplies this agent's decisions to optional chart generation.
     *
     * @return period/source selections accumulated for optional GUI rendering
     */
    public DataChart getDataSeries() {
        return data;
    }

    /**
     * Supplies the current selection score to contacts and reporting.
     *
     * @return score of the most recently selected source, propagated through WOM and reports
     */
    public double getCurrentNewsSourceEvaluation() {
        return currentNewsSourceEvaluation;
    }

    /**
     * Seeds deterministic attribute endorsements for every initially known source.
     * This supplies evidence for the first source-selection decision.
     */
    public void setInitialEndorsements() {
        knownNewsSources.iterator().forEachRemaining(newsSource -> endors.addAll(EndorsementFactory.createInitial(-1, this, newsSource)));
    }

    /**
     * Replaces the sources visible to this user after reach filtering during simulation reset.
     *
     * @param newsSources sources initially known by the user
     */
    public void setKnowNewsSources(List<NewsSource> newsSources) {
        this.knownNewsSources = new ArrayList<>(newsSources);
    }

    /**
     * Summarizes the constructed contact network for diagnostics.
     *
     * @return number of contacts, used in network diagnostics
     */
    public int getFriendCount() {
        return friends.size();
    }

    /**
     * Summarizes initial/current source visibility for diagnostics.
     *
     * @return number of currently known sources, used in network diagnostics
     */
    public int getKnownNewsSourceCount() {
        return knownNewsSources.size();
    }

    /**
     * Selects and endorses one known source for a period, registers the decision, and records GUI data.
     * Users with no known sources intentionally perform no action.
     *
     * @param period current simulation period
     */
    @Override
    public void doStep(int period) {
        deliverPendingWom(period);
        if (Configuration.USER_ACTIVITY_PROBABILITY < 1.0 &&
                Randomness.nextDouble() >= Configuration.USER_ACTIVITY_PROBABILITY) {
            return;
        }
        if (!knownNewsSources.isEmpty()) { //snsUser could not ignore all newsSources
            endors.addAll(Interaction.interact(period, this, knownNewsSources));
            report(period);

            //adding data to draw (should be removed later)
            data.addData(period, endors.getSelectedNewsSource(period).getID());
        }
    }

    /**
     * Stores the selected source's aggregate score for decision reporting and contact recommendations.
     *
     * @param evaluation aggregate score of the current selection
     */
    public void setCurrentEvaluation(double evaluation) {
        this.currentNewsSourceEvaluation = evaluation;
    }

    /**
     * Converts this user's events for one period into reporter DTOs tagged with the active run ID.
     *
     * @param period period to export
     * @return report rows for all matching endorsements
     */
    public ArrayList<EndorsementData> getEndorsementData(int period) {
        Endorsements currentEndors = endors.filterByPeriod(period);
        ArrayList<EndorsementData> endorsData = new ArrayList<>();
        currentEndors.forEach(endor -> endorsData.add(new EndorsementData(Simulation.ID, endor.getPeriod(), ID, endor.getNewsSource().getName(),
                endor.getAttributeName(), endor.getValue())));

        return endorsData;
    }

    /**
     * Selects the strongest source recommended by contacts, adds the source to this user's known
     * set when necessary, and applies the configured fake/true-news WOM policy. An ignored
     * recommendation still makes an unknown source discoverable but creates no endorsement.
     *
     * @param period period whose contact decisions are observed
     * @return {@code true} if a recommendation was available and processed
     */
    public boolean receiveRecommendation(int period) {
        int[] candidateCounts = new int[2];
        Map<Integer, Double> currentEvaluations = collectRecommendationEvaluations(period, candidateCounts);

        if (currentEvaluations.isEmpty()) {
            return false;
        }

        boolean exactMaximumTie = hasExactMaximumTie(currentEvaluations);
        int selectedId = selectRecommendedSource(currentEvaluations);
        boolean newlyDiscovered = NewsSourceFactory.getNewsSource(knownNewsSources, selectedId) == null;
        Reporter.recordWomSelection(Simulation.ID, period, candidateCounts[0], candidateCounts[1],
                exactMaximumTie, newlyDiscovered);
        NewsSource recommendedSource = resolveAndRememberSource(selectedId);
        addWordOfMouthEndorsement(period, recommendedSource);
        return true;
    }

    /**
     * Collects current-period friend decisions by source, retaining the strongest evaluation when
     * several friends recommend the same source.
     *
     * @param period period whose friend decisions are observed
     * @return source identifiers mapped to their strongest recommendation evaluation
     */
    private Map<Integer, Double> collectRecommendationEvaluations(int period, int[] counts) {
        Map<Integer, Double> evaluations = new HashMap<>();

        for (SNSUser friend : friends) {
            NewsSource selectedSource = friend.getLastSelectMarked(period);
            if (selectedSource != null) {
                ++counts[0];
                if (evaluations.containsKey(selectedSource.getID())) ++counts[1];
                evaluations.merge(selectedSource.getID(), friend.getCurrentNewsSourceEvaluation(), Math::max);
            }
        }

        return evaluations;
    }

    private boolean hasExactMaximumTie(Map<Integer, Double> evaluations) {
        double maximum = Double.NEGATIVE_INFINITY;
        int matches = 0;
        for (double value : evaluations.values()) {
            int comparison = Double.compare(value, maximum);
            if (comparison > 0) {
                maximum = value;
                matches = 1;
            } else if (comparison == 0) {
                ++matches;
            }
        }
        return matches > 1;
    }

    /**
     * Applies the deterministic WOM selection policy to the aggregated friend evaluations.
     *
     * @param evaluations source identifiers mapped to strongest recommendation evaluations
     * @return selected source identifier
     */
    private int selectRecommendedSource(Map<Integer, Double> evaluations) {
        return NewsSourceSelectionStrategies.BY_MAX(evaluations);
    }

    /**
     * Resolves a recommendation against this user's known sources, remembering a globally valid
     * source only when it is encountered for the first time.
     *
     * @param sourceId recommended source identifier
     * @return known or newly discovered source
     */
    private NewsSource resolveAndRememberSource(int sourceId) {
        NewsSource recommendedSource = NewsSourceFactory.getNewsSource(knownNewsSources, sourceId);
        if (recommendedSource != null) {
            return recommendedSource;
        }

        recommendedSource = NewsSourceFactory.getNewsSource(sourceId);
        Error.setAssert(recommendedSource != null,
                "SNSUser.receiveRecommendation: recommended source not found: " + sourceId);
        knownNewsSources.add(recommendedSource);
        return recommendedSource;
    }

    /**
     * Schedules the configured WOM contribution so it can affect source evaluation next period.
     *
     * @param period period in which the recommendation was received
     * @param recommendedSource source endorsed by the recommendation
     */
    private void addWordOfMouthEndorsement(int period, NewsSource recommendedSource) {
        boolean fakeNews = recommendedSource.isFakeNews(period);
        if (Configuration.WOM_LABEL_COVERAGE < 1.0 &&
                Randomness.nextDouble() >= Configuration.WOM_LABEL_COVERAGE) {
            Reporter.recordWomUncovered(Simulation.ID, period);
            return;
        }
        boolean observedFake = fakeNews;
        if (fakeNews && Configuration.WOM_LABEL_SENSITIVITY < 1.0) {
            observedFake = Randomness.nextDouble() < Configuration.WOM_LABEL_SENSITIVITY;
        } else if (!fakeNews && Configuration.WOM_LABEL_SPECIFICITY < 1.0) {
            observedFake = Randomness.nextDouble() >= Configuration.WOM_LABEL_SPECIFICITY;
        }
        WomRecommendationEffect effect = observedFake
                ? Configuration.WOM_FAKE_NEWS_EFFECT
                : Configuration.WOM_TRUE_NEWS_EFFECT;
        Reporter.recordWomLabel(Simulation.ID, period, fakeNews, observedFake, effect);
        if (effect == WomRecommendationEffect.IGNORE) {
            return;
        }

        double magnitude = Math.abs(attribute.getValue(WORD_OF_MOUTH_ATTRIBUTE))
                * Configuration.WOM_RECEIVER_SCALE;
        double value = magnitude * effect.getConfigurationValue();

        int deliveryPeriod = period + 1 + Configuration.WOM_LABEL_DELAY;
        Reporter.recordWomScheduled(Simulation.ID, period);
        if (Configuration.WOM_LABEL_DELAY == 0) {
            endors.add(new Endorsement(deliveryPeriod, recommendedSource, WORD_OF_MOUTH_ATTRIBUTE, value));
            if (deliveryPeriod <= Configuration.PERIODS) {
                Reporter.recordWomDelivered(Simulation.ID, deliveryPeriod);
            }
        } else {
            pendingWom.add(new PendingWomEndorsement(deliveryPeriod, recommendedSource, value));
        }
    }

    private void deliverPendingWom(int period) {
        for (int index = pendingWom.size() - 1; index >= 0; --index) {
            PendingWomEndorsement pending = pendingWom.get(index);
            if (pending.period == period) {
                endors.add(new Endorsement(period, pending.source, WORD_OF_MOUTH_ATTRIBUTE, pending.value));
                Reporter.recordWomDelivered(Simulation.ID, period);
                pendingWom.remove(index);
            }
        }
    }

    /**
     * Recovers the source this user selected in a period from endorsement memory.
     *
     * @param period decision period
     * @return selected source, or {@code null} when no decision exists
     */
    public NewsSource getLastSelectMarked(int period) {
        return endors.getSelectedNewsSource(period);
    }

    /**
     * Clears run-specific decisions, contacts, and visibility before the simulation rebuilds them.
     */
    @Override
    public void reinit() {
        currentNewsSourceEvaluation = Double.MAX_VALUE * -1;
        endors.clear();
        pendingWom.clear();
        friends.clear();
        knownNewsSources.clear();
    }

    /**
     * Registers the selected source and score in the per-agent decision report.
     *
     * @param period period being reported
     */
    @Override
    public void report(int period) {
        Reporter.addAgentDecisionData(Simulation.ID, period, getID(), getLastSelectMarked(period).getName(), this.currentNewsSourceEvaluation);
    }

    /**
     * Formats the agent's current model state for creation and repetition logs.
     *
     * @return diagnostic representation of identity, attributes, visibility, and current score
     */
    @Override
    public String toString() {
        StringBuilder attributeValue = new StringBuilder();
        StringBuilder knowMks = new StringBuilder();

        for (int i = 0; i < attribute.size(); ++i) {
            attributeValue.append(attribute.getName(i)).append("[").append(attribute.getValue(i)).append("], ");
        }

        for (NewsSource knownNewsSource : knownNewsSources) {
            knowMks.append(knownNewsSource.getName()).append(",");
        }

        return "SNSUser{" +
                "ID=" + ID +
                ", attribute=" + attributeValue +
                ", knownNewsSources={" + knowMks + "}" +
                ", currentEvaluation={" + currentNewsSourceEvaluation + "}" +
                '}';
    }

    private static final class PendingWomEndorsement {
        private final int period;
        private final NewsSource source;
        private final double value;

        private PendingWomEndorsement(int period, NewsSource source, double value) {
            this.period = period;
            this.source = source;
            this.value = value;
        }
    }
}
