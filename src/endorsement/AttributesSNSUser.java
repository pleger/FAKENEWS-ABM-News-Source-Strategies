package endorsement;

import java.util.ArrayList;

/**
 * Holds the scalar weights with which an SNS user values each endorsement dimension.
 * These weights are paired by name with {@link AttributesNewsSource} during evaluation.
 */
public class AttributesSNSUser extends Attributes {
    /**
     * Creates a user-attribute view from the prototype loaded by the input package.
     *
     * @param names ordered model dimensions
     * @param values single-element arrays containing user weights
     */
    public AttributesSNSUser(ArrayList<String> names, ArrayList<Double[]> values) {
        super(names, values);
    }

    /**
     * Returns the scalar user weight stored at a model position.
     *
     * @param i zero-based attribute position
     * @return first value in the attribute entry
     */
    public double getValue(int i) {
        return getValues(i)[0];
    }
    /**
     * Returns the user weight paired with a named source distribution during evaluation.
     *
     * @param name attribute name
     * @return scalar user weight
     */
    public double getValue(String name) {
        return getValues(name)[0];
    }
}
