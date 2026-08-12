package reporter;

/** Contract for simulation participants that publish state for a completed period. */
public interface ReportRegister {
    /**
     * Converts current domain state into reporter records for a period.
     *
     * @param period period being reported
     */
    void report(int period);
}
