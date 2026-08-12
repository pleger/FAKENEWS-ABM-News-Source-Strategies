package endorsement;

import utils.Console;
import utils.Error;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;

/**
 * Stores an ordered set of named endorsement attributes and their numeric values.
 * This base representation keeps source and user attributes aligned by name so the
 * endorsement package can evaluate the same model dimensions for both domain types.
 */
public class Attributes {
    protected final ArrayList<String> names;
    protected final ArrayList<Double[]> values;

    /**
     * Creates an attribute collection while isolating its list structure from the input lists.
     * The value arrays themselves remain shared and are treated as model data by subclasses.
     *
     * @param names ordered attribute names
     * @param values values corresponding positionally to {@code names}
     */
    public Attributes(ArrayList<String> names, ArrayList<Double[]> values) {
        this.names = new ArrayList<>(names);
        this.values = new ArrayList<>(values);
    }

    /**
     * Returns the number of modeled attributes available to evaluation and reporting code.
     *
     * @return number of attribute names
     */
    public int size() {
        return names.size();
    }

    /**
     * Retrieves the values at an attribute position, as used by specialized source and user views.
     *
     * @param i zero-based attribute position
     * @return the stored value array
     * @throws IndexOutOfBoundsException if {@code i} is outside the collection
     */
    public Double[] getValues(int i) {
        return values.get(i);
    }

    /**
     * Resolves an attribute by name and returns the values consumed by endorsement strategies.
     *
     * @param name attribute name, matched without regard to case
     * @return the stored value array for the matching attribute
     */
    public Double[] getValues(String name) {
        return getValues(getIndex(name));
    }

    /**
     * Exposes the ordered attribute names for input validation and report headers.
     *
     * @return a new array containing the attribute names
     */
    public String[] getNames() {return this.names.toArray(new String[0]);}

    /**
     * Retrieves the name paired with a positional value entry.
     *
     * @param i zero-based attribute position
     * @return attribute name at {@code i}
     * @throws IndexOutOfBoundsException if {@code i} is outside the collection
     */
    public String getName(int i) {
        return this.names.get(i);
    }

    /**
     * Finds the position that aligns a named source attribute with its user counterpart.
     * A missing model dimension is treated as a fatal invariant violation by {@link Error}.
     *
     * @param name attribute name, matched without regard to case
     * @return zero-based position of the matching attribute
     */
    public int getIndex(String name) {
        int index = -1;
        for (int i = 0; i < names.size(); ++i) {
            if (names.get(i).equalsIgnoreCase(name)) {
                index = i;
                break;
            }
        }

        Error.setAssert(index != -1, "Attributes: " + name + " not found");
        return index;
    }

    /**
     * Visits each name/value pair in model order, supporting copy and scenario transformations.
     *
     * @param fun operation invoked once for each attribute
     */
    public void forEach(BiConsumer<String, Double[]> fun) {
        for (int i = 0; i < names.size(); ++i) {
            String name = names.get(i);
            Double[] values = this.values.get(i);
            fun.accept(name, values);
        }
    }

    /**
     * Checks that all requested scenario dimensions exist before an intervention is applied.
     * Missing names are logged through {@link Console}.
     *
     * @param names attribute names that must be present
     * @return {@code true} when every requested name exists
     */
    public boolean contains(String[] names) {
        for (String name: names) {
            if (!this.names.contains(name)) {
                Console.error(name + " is not found");
                return false;
            }
        }
        return true;
    }


    /**
     * Formats names and values for configuration and agent diagnostic logging.
     *
     * @return readable representation of this collection
     */
    @Override
    public String toString() {
        StringBuilder valueString = new StringBuilder();
        for (int i = 0; i < values.size(); ++i) {
            Double[] oneValues = values.get(i);
            String name = names.get(i);
            valueString.append(name).append(" [").append(Arrays.toString(oneValues)).append("], ");
        }

        return "Attributes{" +
                valueString +
                '}';
    }
}
