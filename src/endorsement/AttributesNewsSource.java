package endorsement;

import inputManager.Configuration;

import java.util.ArrayList;

/**
 * Represents each news-source attribute as a probability distribution over configured levels.
 * Instances feed endorsement evaluation and can be copied or transformed by scenario execution.
 */
public class AttributesNewsSource extends Attributes {

    /**
     * Creates the source-specific view of loaded endorsement attributes.
     *
     * @param names ordered model dimensions
     * @param values level probabilities corresponding to each name
     */
    public AttributesNewsSource(ArrayList<String> names, ArrayList<Double[]> values) {
        super(names, values);
    }

    /**
     * Creates an independent list container used as the starting point for scenario changes.
     *
     * @return a new source-attribute collection with the same entries
     */
    public AttributesNewsSource copy() {
        return new AttributesNewsSource(new ArrayList<>(this.names), new ArrayList<>(this.values));
    }

    /**
     * Produces a transformed collection with one named distribution replaced, leaving this
     * instance unchanged so scenario previews cannot mutate the live source accidentally.
     *
     * @param name exact attribute name to replace
     * @param newValues replacement level distribution
     * @return transformed attribute collection
     */
    public AttributesNewsSource replace(String name, Double[] newValues) {
        ArrayList<String> resultNames = new ArrayList<>();
        ArrayList<Double[]> resultValues = new ArrayList<>();

        forEach((attrName, attrValues) -> {
            resultNames.add(attrName);
            if (attrName.equals(name)) {
                resultValues.add(newValues);
            } else {
                resultValues.add(attrValues);
            }
        });

        return new AttributesNewsSource(resultNames, resultValues);
    }

    /**
     * Applies parallel name/value replacements for a multi-attribute scenario intervention.
     *
     * @param names exact attribute names to replace
     * @param newValues replacement distributions aligned with {@code names}
     * @return transformed attribute collection
     */
    public AttributesNewsSource replaceAll(String[] names, Double[][] newValues) {
        AttributesNewsSource result = copy();
        for (int i = 0; i < names.length; ++i) {
            result = result.replace(names[i], newValues[i]);
        }
        return result;
    }

    /**
     * Copies selected distributions from another source, implementing the attribute-transfer
     * operation configured by the scenarios package.
     *
     * @param names attribute names to copy
     * @param attm source collection from which distributions are read
     * @return transformed copy of this collection
     */
    public AttributesNewsSource replaceAll(String[] names, AttributesNewsSource attm) {
        Double[][] values = new Double[names.length][Configuration.LEVELS];

        for (int i = 0; i < names.length; ++i) {
            values[i] = attm.getValues(names[i]);
        }
        return replaceAll(names, values);
    }
}
