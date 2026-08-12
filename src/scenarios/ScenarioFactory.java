package scenarios;

import inputManager.Configuration;
import inputManager.Scenarios;
import utils.Error;

import java.util.ArrayList;
import java.util.List;

/**
 * Lazily exposes scenarios selected by global configuration and isolates scenario cache lifecycle
 * from workbook loading and period execution.
 */
public class ScenarioFactory {

    public final static int CUSTOMIZED = -2;

    private final static List<Scenario> scenarios = new ArrayList<>();

    /**
     * Retrieves a configured scenario, constructing the cache from input on first access.
     *
     * @param id requested scenario identifier
     * @return matching scenario
     */
    public static Scenario get(int id) {
        if (scenarios.isEmpty()) {
            makeScenarios();
        }
        return getScenario(id);
    }

    /** Clears cached scenarios before a different workbook is loaded. */
    public static void clear() {
        scenarios.clear();
    }

    /**
     * Searches the materialized scenario cache and stops execution if the identifier is invalid.
     *
     * @param id scenario identifier
     * @return matching scenario
     */
    private static Scenario getScenario(int id) {
        for (Scenario sc : scenarios) {
            if (sc.getId() == id) {
                return sc;
            }
        }
        Error.trigger("ScenarioFactory.getScenario: Wrong Scenario: " + Configuration.SCENARIO);
        return null;
    }


    /** Loads the custom workbook scenario when that scenario mode is configured. */
    private static void makeScenarios() {
        if (Configuration.SCENARIO == CUSTOMIZED) {
            scenarios.add(Scenarios.getScenario());
        }
    }
}
