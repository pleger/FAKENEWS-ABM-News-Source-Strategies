package simulation;

/**
 * Lifecycle contract for domain objects whose allocated identity is reused across simulation runs
 * while run-specific state is reset.
 */
public interface FlyWeight {
    /** Restores receiver state required to begin the next simulation execution. */
    void reinit();
}
