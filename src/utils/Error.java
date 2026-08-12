package utils;

/**
 * Fatal-error utility used for model invariants and unrecoverable I/O failures; unlike
 * {@link Console#setAssert(boolean, Object)}, failed assertions here terminate the process.
 */
public class Error {

    /**
     * Logs an unrecoverable error and stops the JVM execution.
     *
     * @param msg error diagnostic
     */
    public static void trigger(Object msg) {
        Console.error(msg);
        stopExecution();
    }

    /**
     * Logs an unrecoverable error with its cause and stops the JVM execution.
     *
     * @param msg error diagnostic
     * @param ex associated failure
     */
    public static void trigger(Object msg, Throwable ex) {
        Console.error(msg, ex);
        stopExecution();
    }

    /** Logs a termination trace and exits with a nonzero process status. */
    private static void stopExecution() {
        Console.error("This execution has to stop. This is the current execution trace:",
                new IllegalStateException("Execution trace"));
        System.exit(1);
    }

    /**
     * Enforces a fatal project invariant.
     *
     * @param test condition that must be true
     * @param msg diagnostic used when the condition fails
     */
    public static void setAssert(boolean test, Object msg) {
        if (!test) trigger(msg);
    }
}
