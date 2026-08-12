package endorsement;

import agent.NewsSource;
import inputManager.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Maintains an SNS user's endorsement history and provides the temporal and source filters used
 * by interaction, word-of-mouth, selection, and reporting stages of the simulation.
 */
public class Endorsements {
    private final List<Endorsement> endors;

    /** Creates an empty mutable endorsement history for a new or reset user. */
    public Endorsements() {
        endors = new ArrayList<>();
    }

    /**
     * Wraps a list produced by a filter operation.
     *
     * @param endors endorsement records represented by this collection
     */
    public Endorsements(List<Endorsement> endors) {
        this.endors = endors;
    }

    /**
     * Appends one initial, interaction, or word-of-mouth event to user memory.
     *
     * @param endor event to append
     */
    public void add(Endorsement endor) {
        endors.add(endor);
    }

    /** Clears accumulated memory when an SNS user is reinitialized for another run. */
    public void clear() {
        endors.clear();
    }

    /**
     * Appends a batch generated for one source interaction.
     *
     * @param endors events to append
     */
    public void addAll(Endorsements endors) {
        this.endors.addAll(endors.endors);
    }

    /**
     * Creates a filtered view as a separate list without mutating the stored history.
     *
     * @param filter inclusion predicate
     * @return collection containing matching records
     */
    private Endorsements filter(Predicate<Endorsement> filter) {
        return new Endorsements(endors.stream().filter(filter).collect(Collectors.toList()));
    }

    /**
     * Visits records in storage order for conversion to report rows.
     *
     * @param fun operation invoked for each endorsement
     */
    public void forEach(Consumer<Endorsement> fun) {
        endors.iterator().forEachRemaining(fun);
    }

    /**
     * Retains endorsements visible within the configured memory window at a period.
     * Infinite memory keeps all records, including initialization events.
     *
     * @param period period from which the memory window is measured
     * @return non-mutating filtered collection
     */
    public Endorsements filterByMemory(int period) {
        return filter(endor -> Configuration.MEMORY == Configuration.MEMORY_INFINITE ||
                endor.getPeriod() > period - Configuration.MEMORY);
    }

    /**
     * Selects events assigned to one period for decision lookup and reporting.
     *
     * @param period exact period to retain
     * @return non-mutating filtered collection
     */
    public Endorsements filterByPeriod(int period) {
        return filter(endor -> endor.getPeriod() == period);
    }

    /**
     * Selects the history belonging to one source before its aggregate evaluation is computed.
     *
     * @param newsSource source matched by name
     * @return non-mutating filtered collection
     */
    public Endorsements filterByNewsSource(NewsSource newsSource) {
        return filter(endor -> endor.getNewsSource().getName().equals(newsSource.getName()));
    }

    /**
     * Excludes one dimension, primarily to remove word-of-mouth markers from source decisions.
     *
     * @param attName exact attribute name to exclude
     * @return non-mutating filtered collection
     */
    public Endorsements removeByAttribute (String attName)  {
        return filter(endor -> !endor.getAttributeName().equals(attName));
    }

    /**
     * Extracts numeric contributions for aggregation by {@link agent.Interaction}.
     *
     * @return values in endorsement storage order
     */
    public double[] toArray() {
        double[] values = new double[endors.size()];

        for (int i = 0; i < endors.size(); ++i) {
             values[i] = endors.get(i).getValue();
        }
        return values;
    }

    /**
     * Recovers the source selected in a period from its generated non-WOM endorsements.
     * All ordinary attribute events for an interaction refer to the same source.
     *
     * @param period decision period
     * @return selected source, or {@code null} when no selection was recorded
     */
    public NewsSource getSelectedNewsSource(int period){
        List<Endorsement> periodTransaction = filterByPeriod(period).removeByAttribute("WORD OF MOUTH").endors;
        //System.out.println("getSelectedNewsSource:"+periodTransaction.get(0).getNewsSource().getName());

        return periodTransaction.size() > 0? periodTransaction.get(0).getNewsSource(): null;
    }

    /**
     * Reports history size for validation and tests.
     *
     * @return number of stored endorsement events
     */
    public int size() {
        return endors.size();
    }
}
