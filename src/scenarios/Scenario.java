package scenarios;

import agent.NewsSource;
import agent.NewsSourceFactory;
import endorsement.AttributesNewsSource;
import inputManager.Configuration;
import utils.Console;
import utils.Error;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Scheduled intervention that copies selected endorsement distributions from one runtime news
 * source to another, allowing experiments to change source behavior during execution. An empty
 * workbook attribute list is interpreted as the shortcut for copying every source attribute.
 */
public class Scenario {
    private final int id;
    private final int start;
    private final String from;
    private final String to;
    private final String[] atts;


    /**
     * Creates an immutable intervention definition from workbook scenario data.
     *
     * @param id scenario identifier selected in configuration
     * @param start period at which the transfer is applied
     * @param from source providing attribute distributions
     * @param to source receiving attribute distributions
     * @param atts exact attribute names to transfer, or an empty list to transfer all attributes
     */
    public Scenario(int id, int start, String from, String to, ArrayList<String> atts) {
        this.id = id;
        this.start = start;
        this.from = from;
        this.to = to;
        this.atts = atts.toArray(new String[0]);
    }

    /**
     * Applies the transfer exactly at the configured start period; other periods are no-ops.
     *
     * @param period current simulation period
     */
    public void apply(int period) {
        if (period == this.start) {
            Console.info("ScenarioManager: Applying Scenario " + Configuration.SCENARIO +"  [" + this + "]");
            copyAttributes(NewsSourceFactory.getNewsSource(from), NewsSourceFactory.getNewsSource(to), atts);
        }
    }

    /**
     * Exposes this definition to factory lookup.
     *
     * @return identifier used by {@link ScenarioFactory} for lookup
     */
    public int getId() {
        return id;
    }

    /**
     * Exposes the intervention period so reports and charts can mark the behavioral change.
     *
     * @return period at which this scenario applies its attribute transfer
     */
    public int getStartPeriod() {
        return start;
    }

    /**
     * Computes the attributes a source would have after this intervention without mutating it.
     * Reporter previews use this to describe scenario changes safely.
     *
     * @param newsSource source whose post-scenario attributes are requested
     * @return unchanged attributes for other sources, or a transformed copy for the target
     */
    public AttributesNewsSource attributesAfterApplyingTo(NewsSource newsSource) {
        if (!newsSource.getName().equals(to)) {
            return newsSource.getAttributes();
        }

        AttributesNewsSource attFrom = NewsSourceFactory.getNewsSource(from).getAttributes();
        AttributesNewsSource attTo = newsSource.getAttributes();
        return buildAttributes(attFrom, attTo, atts);
    }

    /**
     * Builds, validates, and installs the target source's transformed attribute collection.
     *
     * @param from source providing distributions
     * @param to source to mutate
     * @param names attribute names to transfer
     */
    private static void copyAttributes(NewsSource from, NewsSource to, String[] names) {
        AttributesNewsSource attFrom = from.getAttributes();
        AttributesNewsSource attTo = to.getAttributes();
        String[] resolvedNames = resolveAttributeNames(attFrom, names);
        AttributesNewsSource newAttTo = buildAttributes(attFrom, attTo, resolvedNames);
        checkDifference(attTo, newAttTo, resolvedNames);
        to.setAttributes(newAttTo);
    }

    /**
     * Expands the empty-list shortcut to the complete ordered schema of the providing source.
     *
     * @param attFrom source attribute collection
     * @param names configured names, possibly empty
     * @return configured names or every source attribute name when none were configured
     */
    private static String[] resolveAttributeNames(AttributesNewsSource attFrom, String[] names) {
        return names.length == 0 ? attFrom.getNames() : names;
    }

    /**
     * Validates source dimensions and creates a non-mutating transfer result.
     *
     * @param attFrom distributions to copy from
     * @param attTo base target collection
     * @param names dimensions to transfer
     * @return transformed target attributes
     */
    private static AttributesNewsSource buildAttributes(AttributesNewsSource attFrom, AttributesNewsSource attTo, String[] names) {
        String[] resolvedNames = resolveAttributeNames(attFrom, names);
        checkAttributes(attFrom, resolvedNames, "source");
        checkAttributes(attTo, resolvedNames, "target");
        return attTo.replaceAll(resolvedNames, attFrom);
    }

    /**
     * Treats a missing requested source dimension as a fatal scenario-definition error.
     *
     * @param attm attributes to inspect
     * @param names required dimensions
     * @param role scenario role included in fatal diagnostics
     */
    private static void checkAttributes(AttributesNewsSource attm, String[] names, String role) {
        if (!attm.contains(names)) {
            Error.trigger("Scenario.checkAttributes: some attributes were not found in the " + role
                    + ": " + Arrays.toString(names));
        }
    }

    /**
     * Describes the workbook selection for logs and the generated scenario report.
     *
     * @return {@code ALL} for the empty-list shortcut, otherwise the explicit attribute names
     */
    public String getAttributeSelectionDescription() {
        return atts.length == 0 ? "ALL" : Arrays.toString(atts);
    }

    /**
     * Warns when transferred level values do not differ, helping diagnose ineffective scenarios.
     *
     * @param oldAtt target attributes before transfer
     * @param newAtt target attributes after transfer
     * @param names transferred dimensions
     */
    private static void checkDifference(AttributesNewsSource oldAtt, AttributesNewsSource newAtt, String[] names) {
        for (String name : names) {
            Double[] oldValues = oldAtt.getValues(name);
            Double[] newValues = newAtt.getValues(name);

            for (int j = 0; j < oldValues.length; ++j) {
                if (oldValues[j].equals(newValues[j])) {
                    String oldTextValues = Arrays.toString(oldValues);
                    String newTextValues = Arrays.toString(newValues);
                    Console.warn("No difference for " + name + " => Old values:["+ oldTextValues + "]  &  New values:[" + newTextValues + "] - Value's Index:"+j);
                }
            }
        }
    }

    /**
     * Formats the intervention for activation logging.
     *
     * @return diagnostic description logged when the intervention activates
     */
    @Override
    public String toString() {
        return "Scenario{" +
                "ID=" + this.id +
                ", start=" + this.start +
                ", from=" + this.from +
                ", to=" + this.to +
                ", attributes={" + getAttributeSelectionDescription() + "}" +
                '}';
    }
}
