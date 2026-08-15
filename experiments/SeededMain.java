import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

/** Experiment-only launcher that seeds the singleton used by Math.random before invoking Main. */
public final class SeededMain {
    private SeededMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !"--seed".equals(args[0])) {
            throw new IllegalArgumentException("Usage: SeededMain --seed <long> [Main options]");
        }
        long seed = Long.parseLong(args[1]);
        Class<?> holder = Class.forName("java.lang.Math$RandomNumberGeneratorHolder");
        Field field = holder.getDeclaredField("randomNumberGenerator");
        field.setAccessible(true);
        ((Random) field.get(null)).setSeed(seed);
        Main.main(Arrays.copyOfRange(args, 2, args.length));
    }
}
