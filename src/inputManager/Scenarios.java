package inputManager;

import scenarios.Scenario;
import scenarios.ScenarioFactory;

import java.util.ArrayList;

/**
 * Holds the custom scenario parsed from the workbook until the scenarios package requests it.
 */
public class Scenarios {

    private static Scenario scenario;

    /**
     * Constructs and stores the workbook-defined attribute-transfer intervention.
     *
     * @param from source from which distributions will be copied
     * @param to source that will receive the distributions
     * @param start activation period
     * @param attributesName attribute names to transfer
     */
    public static void set(String from, String to, int start, int end,
                           ArrayList<String> strategyNames, ArrayList<String> attributesName) {
       scenario = new Scenario(ScenarioFactory.CUSTOMIZED, start, end, from, to,
               strategyNames, attributesName);
    }

    /**
     * Supplies the parsed definition to the scenario factory.
     *
     * @return the currently loaded custom scenario
     */
    public static Scenario getScenario() {
        return scenario;
    }

    /** Clears stale scenario state before another workbook is loaded. */
    public static void clear() {
        scenario = null;
    }
}
