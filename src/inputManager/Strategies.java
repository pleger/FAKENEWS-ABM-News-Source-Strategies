package inputManager;

import utils.Error;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Stores workbook-defined, named groups of source attributes used by customized scenarios. */
public final class Strategies {
    private static final Map<String, List<String>> definitions = new LinkedHashMap<>();

    private Strategies() {
    }

    /** Replaces the strategy catalog when a workbook is loaded. */
    public static void set(Map<String, List<String>> loadedDefinitions) {
        definitions.clear();
        for (Map.Entry<String, List<String>> entry : loadedDefinitions.entrySet()) {
            definitions.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    /** Clears definitions so one workbook cannot leak strategies into the next load. */
    public static void clear() {
        definitions.clear();
    }

    /**
     * Resolves named strategies and additional explicit attributes into one stable, duplicate-free
     * attribute selection. An empty result retains the scenario shortcut meaning {@code ALL}.
     */
    public static ArrayList<String> resolve(List<String> strategyNames, List<String> explicitAttributes) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String strategyName : strategyNames) {
            List<String> attributes = definitions.get(strategyName);
            Error.setAssert(attributes != null, "Strategies: unknown strategy: " + strategyName);
            Error.setAssert(!attributes.isEmpty(), "Strategies: strategy has no attributes: " + strategyName);
            resolved.addAll(attributes);
        }
        resolved.addAll(explicitAttributes);
        return new ArrayList<>(resolved);
    }
}
