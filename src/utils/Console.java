package utils;

import inputManager.Configuration;

import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Project logging facade that lazily configures console and per-run file output for loaders,
 * simulation components, reporters, and fatal-error handling.
 */
public class Console {
    private static Logger logger = null;
    private static final String FILE_NAME = "output.log";

    /** Initializes the shared logger on first use and attaches the current run log when available. */
    private static void initLoggerRequired() {
        if (logger == null) {
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
            logger = Logger.getAnonymousLogger();
            logger.setLevel(Level.INFO);
            setLogFile();
        }
    }

    /** Attaches {@code output.log} inside the configured run directory when one has been created. */
    private static void setLogFile() {
        if (Configuration.OUTPUT_DIRECTORY == null || Configuration.OUTPUT_DIRECTORY.trim().isEmpty()) {
            return;
        }

        try {
            FileHandler fh = new FileHandler(Configuration.OUTPUT_DIRECTORY+"/"+ FILE_NAME);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (Exception e) {
            System.err.println("ERROR: Console.setLogFile: output.log could not be created: " + e);
        }
    }

    /** Closes existing handlers so a newly loaded workbook can log to its own output directory. */
    public static void resetLogFile() {
        if (logger != null) {
            for (java.util.logging.Handler handler : logger.getHandlers()) {
                handler.close();
                logger.removeHandler(handler);
            }
            logger = null;
        }
    }

    /**
     * Logs the final CLI message and closes the primary handler.
     *
     * @param msg completion message
     */
    public static void end(Object msg) {
        info(msg);
        logger.getHandlers()[0].close();
    }

    /**
     * Emits a fine-grained diagnostic through the shared logger.
     *
     * @param msg message object converted with {@link Object#toString()}
     */
    public static void debug(Object msg) {
        initLoggerRequired();
        logger.fine(msg.toString());
    }

    /**
     * Emits normal execution progress used throughout the simulation pipeline.
     *
     * @param msg message object converted with {@link Object#toString()}
     */
    public static void info(Object msg) {
        initLoggerRequired();
        logger.info(msg.toString());
    }

    /**
     * Emits a severe diagnostic without changing control flow.
     *
     * @param msg error message
     */
    public static void error(Object msg) {
        initLoggerRequired();
        logger.severe(msg.toString());
    }

    /**
     * Emits a severe diagnostic with its causal stack trace.
     *
     * @param msg error message
     * @param throwable associated failure
     */
    public static void error(Object msg, Throwable throwable) {
        initLoggerRequired();
        logger.log(Level.SEVERE, msg.toString(), throwable);
    }

    /**
     * Emits a recoverable configuration or execution warning.
     *
     * @param msg warning message
     */
    public static void warn(Object msg) {
        initLoggerRequired();
        logger.warning(msg.toString());
    }

    /**
     * Logs a failed soft assertion but permits execution to continue.
     *
     * @param assertion condition expected to be true
     * @param msg diagnostic emitted when false
     */
    public static void setAssert(boolean assertion, Object msg) {
        initLoggerRequired();
        if (!assertion) error(msg.toString());
    }
}
