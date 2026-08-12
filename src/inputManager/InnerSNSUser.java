package inputManager;

import java.util.ArrayList;

/**
 * Input-stage prototype containing the common attribute weights used to create SNS-user agents.
 */
public class InnerSNSUser {
    public final ArrayList<String> attributeNames;
    public final ArrayList<Double> attributeValues;

    /** Creates an empty prototype for population by {@link SNSUsers#set(HashMap)}. */
    InnerSNSUser() {
        attributeNames = new ArrayList<>();
        attributeValues = new ArrayList<>();
    }

    /**
     * Appends one user model dimension while parsing the SNSUsers worksheet.
     *
     * @param name attribute name
     * @param value user weight for that dimension
     */
    public void addAttribute(String name, double value) {
        attributeNames.add(name);
        attributeValues.add(value);
    }

    /**
     * Formats the prototype for input diagnostics.
     *
     * @return diagnostic text containing all prototype attributes and weights
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("SNSUser");

        for (int i = 0; i < attributeNames.size(); ++i) {
            text.append("{").append(attributeNames.get(i)).append(":").append(attributeValues.get(i)).append("}");
        }

        return text.toString();
    }
}
