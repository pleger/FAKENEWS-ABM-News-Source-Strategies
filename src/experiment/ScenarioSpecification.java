package experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Scenario intervention attached to one experimental condition. */
public final class ScenarioSpecification {
    private final String from;
    private final String to;
    private final int startPeriod;
    private final int endPeriod;
    private final List<String> strategies;
    private final List<String> attributes;

    public ScenarioSpecification(String from, String to, int startPeriod, int endPeriod,
                                 List<String> strategies, List<String> attributes) {
        if (from == null || from.trim().isEmpty() || to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Scenario FROM and TO must not be blank");
        }
        if (startPeriod < 1 || (endPeriod != -1 && endPeriod < startPeriod)) {
            throw new IllegalArgumentException("Invalid scenario interval: " + startPeriod + ".." + endPeriod);
        }
        this.from = from.trim().toUpperCase();
        this.to = to.trim().toUpperCase();
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
        this.strategies = immutableUppercase(strategies);
        this.attributes = immutableUppercase(attributes);
    }

    private static List<String> immutableUppercase(List<String> values) {
        ArrayList<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) result.add(value.trim().toUpperCase());
        }
        return Collections.unmodifiableList(result);
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public int getStartPeriod() { return startPeriod; }
    public int getEndPeriod() { return endPeriod; }
    public List<String> getStrategies() { return strategies; }
    public List<String> getAttributes() { return attributes; }
    public String strategyLabel() { return strategies.isEmpty() ? "NONE" : String.join(",", strategies); }
}
