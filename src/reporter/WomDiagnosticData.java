package reporter;

import java.util.Arrays;
import java.util.List;

/** Mutable aggregate of recommendation-process events for one simulation period. */
public final class WomDiagnosticData {
    public final int simulationId;
    public final int period;
    public int receiversWithRecommendation;
    public int contactRecommendations;
    public int duplicateSourceRecommendations;
    public int exactMaximumTies;
    public int newSourceDiscoveries;
    public int labelsCovered;
    public int labelsUncovered;
    public int truePositiveLabels;
    public int falseNegativeLabels;
    public int trueNegativeLabels;
    public int falsePositiveLabels;
    public int rewardedRecommendations;
    public int penalizedRecommendations;
    public int ignoredRecommendations;
    public int endorsementsScheduled;
    public int endorsementsDelivered;

    public WomDiagnosticData(int simulationId, int period) {
        this.simulationId = simulationId;
        this.period = period;
    }

    public static List<String> getHeader() {
        return Arrays.asList(
                "SimulationId", "Period", "ReceiversWithRecommendation", "ContactRecommendations",
                "DuplicateSourceRecommendations", "ExactMaximumTies", "NewSourceDiscoveries",
                "LabelsCovered", "LabelsUncovered", "TruePositiveLabels", "FalseNegativeLabels",
                "TrueNegativeLabels", "FalsePositiveLabels", "RewardedRecommendations",
                "PenalizedRecommendations", "IgnoredRecommendations", "EndorsementsScheduled",
                "EndorsementsDelivered");
    }
}
