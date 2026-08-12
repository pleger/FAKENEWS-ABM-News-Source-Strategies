package agent;

import inputManager.Configuration;
import inputManager.InnerSNSUser;
import inputManager.SNSUsers;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands the single SNS-user input prototype into the configured runtime agent population.
 */
public class SNSUserFactory {

    /**
     * Rebuilds all users for a simulation execution and resets their stable identifiers.
     *
     * @return newly created agent population of {@link Configuration#AGENTS} users
     */
    public static List<SNSUser> createFromInput() {
        ArrayList<SNSUser> snsUsers = new ArrayList<>();
        InnerSNSUser innerSNSUser =  SNSUsers.getPrototypeUser();
        SNSUser.resetCounter();

        for (int i = 0; i < Configuration.AGENTS; i++) {
            snsUsers.add(new SNSUser(innerSNSUser));
        }
        return snsUsers;
    }
}
