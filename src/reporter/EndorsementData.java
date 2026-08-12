package reporter;

import java.util.Arrays;
import java.util.List;

/** Immutable worksheet row for one attribute-level endorsement event. */
public class EndorsementData {
    public final int simulationId;
    public final int period;
    public final int snsUserId;
    public final String newsSourceName;
    public final String attribute;
    public final double value;

    /**
     * Captures an endorsement for deferred detailed-output writing.
     *
     * @param simulationId run identifier
     * @param period endorsement period
     * @param snsUserId user that owns the endorsement
     * @param newsSourceName associated source
     * @param attribute model dimension or WOM marker
     * @param value signed endorsement contribution
     */
    public EndorsementData(int simulationId, int period, int snsUserId, String newsSourceName, String attribute, double value) {
        this.simulationId = simulationId;
        this.period = period;
        this.snsUserId = snsUserId;
        this.newsSourceName = newsSourceName;
        this.attribute = attribute;
        this.value = value;
    }

    /**
     * Defines the schema used by endorsement worksheet pages.
     *
     * @return stable worksheet headers matching the DTO field order
     */
    public static List<String> getHeader() {
        return Arrays.asList("SimulationId", "Period", "UserId", "Source", "Attribute", "Value");
    }
}
