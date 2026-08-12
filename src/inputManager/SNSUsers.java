package inputManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Static store for the workbook's SNS-user prototype, which the agent factory expands into the
 * configured population size.
 */
public class SNSUsers {
    private final static ArrayList<InnerSNSUser> INNER_SNS_USERS = new ArrayList<>();

    /**
     * Replaces the loaded prototype with the supplied named user weights.
     *
     * @param data attribute names mapped to prototype weights
     */
    public static void set(HashMap<String, Double> data) {
        INNER_SNS_USERS.clear();
        InnerSNSUser prototypeUser = new InnerSNSUser();

        for (Map.Entry<String, Double> entry : data.entrySet()) {
            String attributeName = entry.getKey();
            double value = entry.getValue();
            prototypeUser.addAttribute(attributeName, value);
        }
        INNER_SNS_USERS.add(prototypeUser);
    }

    /**
     * Reports the loaded user dimensionality to global configuration.
     *
     * @return number of prototype dimensions used for source/user consistency checks
     */
    public static int attributeSize() {
        return getPrototypeUser().attributeValues.size();
    }

    /**
     * Exposes the loaded input-stage user definitions.
     *
     * @return loaded prototype list retained for compatibility with existing input flows
     */
    public static ArrayList<InnerSNSUser> getUsers() {
        return INNER_SNS_USERS;
    }

    /**
     * Selects the single workbook prototype used for population construction.
     *
     * @return the prototype expanded by {@link agent.SNSUserFactory}
     */
    public static InnerSNSUser getPrototypeUser() {
        return getUsers().get(0);
    }

    /**
     * Formats all loaded prototypes for input diagnostics.
     *
     * @return multiline diagnostic representation of loaded user prototypes
     */
    public static String toStringSNSUsers() {
        StringBuilder text = new StringBuilder();
        for (InnerSNSUser snsUser: INNER_SNS_USERS) {
            text.append(snsUser).append("\n");
        }
        return text.toString();
    }
}
