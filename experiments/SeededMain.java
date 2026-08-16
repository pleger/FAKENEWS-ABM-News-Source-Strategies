/** Backward-compatible experiment launcher; Main now supports seeding directly. */
public final class SeededMain {
    private SeededMain() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || !"--seed".equals(args[0])) {
            throw new IllegalArgumentException("Usage: SeededMain --seed <long> [Main options]");
        }
        Main.main(args);
    }
}
