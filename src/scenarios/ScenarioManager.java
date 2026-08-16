package scenarios;

import inputManager.Configuration;

/** Coordinates scenario activation within the simulation's per-period lifecycle. */
public class ScenarioManager {

    /**
     * Delegates the current period to the selected scenario unless scenarios are disabled.
     *
     * @param period current simulation period
     */
    public static void apply(int period) {
        if (Configuration.SCENARIO != Configuration.DISABLED) {
            Scenario sc = ScenarioFactory.get(Configuration.SCENARIO);
            sc.apply(period);
        }
    }

    /** Clears stateful campaign activation between simulation repetitions. */
    public static void reinit() {
        if (Configuration.SCENARIO != Configuration.DISABLED) {
            ScenarioFactory.get(Configuration.SCENARIO).reset();
        }
    }
}
