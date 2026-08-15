package endorsement;

/**
 * Defines how a social recommendation affects the receiver's future evaluation of its source.
 * The configured code is deliberately separate from the user's WOM attribute: the attribute
 * controls recommendation strength, while this policy controls its direction or suppression.
 */
public enum WomRecommendationEffect {
    PENALIZE(-1),
    IGNORE(0),
    REWARD(1);

    private final int configurationValue;

    WomRecommendationEffect(int configurationValue) {
        this.configurationValue = configurationValue;
    }

    /**
     * Returns the numeric representation used by Excel and CLI configuration.
     *
     * @return {@code -1} for penalize, {@code 0} for ignore, or {@code 1} for reward
     */
    public int getConfigurationValue() {
        return configurationValue;
    }

    /**
     * Converts a validated numeric configuration value into its domain representation.
     *
     * @param value configured value
     * @return matching WOM recommendation effect
     * @throws IllegalArgumentException when the value is not {@code -1}, {@code 0}, or {@code 1}
     */
    public static WomRecommendationEffect fromConfigurationValue(int value) {
        for (WomRecommendationEffect effect : values()) {
            if (effect.configurationValue == value) {
                return effect;
            }
        }
        throw new IllegalArgumentException("WOM recommendation effect must be -1, 0, or 1: " + value);
    }
}
