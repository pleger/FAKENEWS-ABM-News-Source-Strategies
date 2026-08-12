package agent;

import inputManager.InnerNewsSource;
import inputManager.NewsSources;
import utils.Error;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates and indexes runtime news sources from the static structures populated by the input
 * manager, providing identifier and name resolution to interactions, scenarios, and WOM logic.
 */
public class NewsSourceFactory {

    private static ArrayList<NewsSource> newsSources;

    /**
     * Rebuilds the source population from the currently loaded workbook and resets identifiers.
     *
     * @return newly created sources in input order
     */
    public static List<NewsSource> createFromInput() {
        ArrayList<InnerNewsSource> innerNewsSources =  NewsSources.getInnerNewsSources();
        newsSources = new ArrayList<>();
        NewsSource.resetCounter();
        innerNewsSources.iterator().forEachRemaining(innerNewsSource -> newsSources.add(new NewsSource(innerNewsSource)));
        return newsSources;
    }


    /**
     * Resolves an identifier within an explicitly supplied candidate list.
     *
     * @param newsSources list to search
     * @param id source identifier
     * @return matching source, or {@code null} when absent
     */
    public static NewsSource getNewsSource(List<NewsSource> newsSources, int id) {
        for (NewsSource mk : newsSources) {
            if (mk.getID() == id) {
                return mk;
            }
        }
        return null;
    }

    /**
     * Resolves an identifier in the most recently created global source population.
     *
     * @param id source identifier
     * @return matching source, or {@code null} when absent
     */
    public static NewsSource getNewsSource(int id) {
        return getNewsSource(newsSources, id);
    }

    /**
     * Resolves a required source name within a supplied list for scenarios and configuration flows.
     * A missing name is treated as a fatal model error.
     *
     * @param newsSources list to search
     * @param name exact source name
     * @return matching source
     */
    public static NewsSource getNewsSource(List<NewsSource> newsSources, String name) {
        int id = -1;
        for (NewsSource mk : newsSources) {
            if (mk.getName().equals(name)) {
                id = mk.getID();
            }
        }
        Error.setAssert(id != -1, "ERROR. NewsSourceFactory: no newsSource found:"+ name);
        return getNewsSource(newsSources, id);
    }

    /**
     * Resolves a required name in the most recently created global population.
     *
     * @param name exact source name
     * @return matching source
     */
    public static NewsSource getNewsSource(String name) {
        return getNewsSource(newsSources, name);
    }

    /**
     * Exposes the current population to scenarios and reporting.
     *
     * @return the mutable source population created for the current execution
     */
    public static ArrayList<NewsSource> getNewsSources() {return newsSources;}
}
