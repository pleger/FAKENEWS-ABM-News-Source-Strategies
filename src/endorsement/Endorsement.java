package endorsement;

import agent.NewsSource;

/**
 * Records one attribute-level contribution made by an SNS user toward a news source.
 * Collections of these records form the memory used to evaluate and select sources each period.
 */
public class Endorsement {

    private final int period;
    private final NewsSource newsSource;
    private final String attributeName;
    private final double value;

    /**
     * Creates an immutable endorsement event for simulation memory and reporting.
     *
     * @param period period in which the event applies; initial endorsements use {@code -1}
     * @param newsSource source to which the contribution belongs
     * @param attributeName model dimension that produced the contribution
     * @param value signed endorsement contribution
     */
    public Endorsement(int period, NewsSource newsSource, String attributeName, double value) {
        this.period = period;
        this.newsSource = newsSource;
        this.attributeName = attributeName;
        this.value = value;
    }

    /**
     * Returns the timestamp used by memory and reporting filters.
     *
     * @return simulation period of this endorsement
     */
    public int getPeriod() {
        return period;
    }

    /**
     * Returns the source whose accumulated evaluation receives this contribution.
     *
     * @return associated news source
     */
    public NewsSource getNewsSource() {
        return newsSource;
    }

    /**
     * Returns the signed value aggregated by source-selection evaluation.
     *
     * @return endorsement contribution
     */
    public double getValue() {
        return value;
    }

    /**
     * Returns the dimension used to distinguish ordinary endorsements from word of mouth.
     *
     * @return attribute name
     */
    public String getAttributeName() {
        return attributeName;
    }
}
