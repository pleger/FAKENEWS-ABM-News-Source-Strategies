package simulation;

/** Contract for simulation participants that advance once per discrete period. */
public interface Step {
    /**
     * Performs the receiver's behavior for one period.
     *
     * @param period one-based current simulation period
     */
    void doStep(int period);
}
