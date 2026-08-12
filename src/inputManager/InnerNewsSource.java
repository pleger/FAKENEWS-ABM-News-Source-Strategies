package inputManager;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Mutable input-stage representation of one workbook news-source column group.
 * {@link agent.NewsSourceFactory} later converts it into a runtime source.
 */
public class InnerNewsSource {
    public final String name;
    public final double reach;
    public final ArrayList<String> attributeNames;
    public final ArrayList<Double[]> attributeValues;

    /**
     * Starts a source definition before attribute rows are attached.
     *
     * @param name workbook source name
     * @param reach normalized visibility probability
     */
    InnerNewsSource(String name, double reach) {
        this.name = name;
        this.reach = reach;
        attributeNames = new ArrayList<>();
        attributeValues = new ArrayList<>();
    }

    /**
     * Appends one named level distribution while the workbook is being reorganized by source.
     *
     * @param name attribute name
     * @param values probabilities for the configured levels
     */
    void addAttribute(String name, Double[] values) {
        attributeNames.add(name);
        attributeValues.add(values);
    }

    /**
     * Formats the source definition for input diagnostics.
     *
     * @return diagnostic text containing reach and all loaded attribute distributions
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(name);
        text.append("{reach:").append(reach).append("}");

        for (int i = 0; i < attributeNames.size(); ++i) {
            String result = Arrays.toString(attributeValues.get(i));
            text.append("{").append(attributeNames.get(i)).append(":").append(result).append("}");
        }

        return text.toString();
    }
}
