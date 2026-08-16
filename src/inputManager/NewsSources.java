package inputManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Static input store that reorganizes workbook source columns into source-centric definitions for
 * {@link agent.NewsSourceFactory} and exposes names used by reporting.
 */
public class NewsSources {
    private final static ArrayList<InnerNewsSource> innerNewsSources = new ArrayList<>();

    /**
     * Replaces loaded source data and aligns each attribute distribution with its source definition.
     *
     * @param data attribute names mapped to source-ordered distributions
     * @param names source names in worksheet order
     * @param reach source names mapped to normalized visibility probabilities
     */
    public static void set(HashMap<String, ArrayList<Double[]>> data, ArrayList<String> names,
                           HashMap<String,Double> reach, HashMap<String, Double> fakeNewsProbabilities) {
        innerNewsSources.clear();
        for (String name : names) {
            Double fakeNewsProbability = fakeNewsProbabilities == null
                    ? null
                    : fakeNewsProbabilities.get(name);
            innerNewsSources.add(new InnerNewsSource(name, reach.get(name), fakeNewsProbability));
        }

        for (Map.Entry<String, ArrayList<Double[]>> entry : data.entrySet()) {
            String attributeName = entry.getKey();
            ArrayList<Double[]> values = entry.getValue();

            for (int i = 0; i < values.size(); ++i) {
                innerNewsSources.get(i).addAttribute(attributeName, values.get(i));
            }
        }
    }

    /**
     * Exposes source-centric input records for runtime construction.
     *
     * @return loaded source definitions consumed by the runtime factory
     */
    public static ArrayList<InnerNewsSource> getInnerNewsSources() {
        return innerNewsSources;
    }

    /**
     * Formats the common source dimensions for report metadata.
     *
     * @return space-separated attribute names from the first source for worksheet headers
     */
    public static String attributeNames() {
        InnerNewsSource newsSource = innerNewsSources.get(0);
        StringBuilder text = new StringBuilder();
        for (String endorName : newsSource.attributeNames) {
            text.append(endorName).append(" ");
        }
        return text.toString();
    }


    /**
     * Reports the source population size to configuration and consumers.
     *
     * @return number of loaded source definitions
     */
    public static int size() {
        return innerNewsSources.size();
    }

    /**
     * Reports source dimensionality to global configuration.
     *
     * @return number of source attributes used to validate endorsement evaluation
     */
    public static int attributeSize() {
        return innerNewsSources.get(0).attributeNames.size();
    }

    /**
     * Formats source order as dynamic aggregate-report columns.
     *
     * @return space-separated source names used to build report column headers
     */
    public static String newsSourceNames() {
        StringBuilder text = new StringBuilder();
        for (InnerNewsSource newsSource : innerNewsSources) {
            text.append(newsSource.name).append(" ");
        }
        return text.toString();
    }

    /**
     * Formats loaded sources for input diagnostics.
     *
     * @return multiline diagnostic representation of all loaded source definitions
     */
    public static String toStringNewsSources() {
        StringBuilder text = new StringBuilder();
        for (InnerNewsSource newsSource : innerNewsSources) {
            text.append(newsSource).append("\n");
        }
        return text.toString();
    }
}
