package utils;

import java.util.Random;

/** Central pseudo-random stream, optionally seeded for reproducible simulation runs. */
public final class Randomness {
    private static Random random = new Random();

    private Randomness() {
    }

    /** Restarts the model's random stream from a reproducible seed. */
    public static void setSeed(long seed) {
        random = new Random(seed);
    }

    /** Restores non-deterministic seeding for ordinary interactive executions. */
    public static void clearSeed() {
        random = new Random();
    }

    /** Returns the next uniformly distributed value in {@code [0,1)}. */
    public static double nextDouble() {
        return random.nextDouble();
    }
}
