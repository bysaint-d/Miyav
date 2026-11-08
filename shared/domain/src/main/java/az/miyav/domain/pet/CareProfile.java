package az.miyav.domain.pet;

public final class CareProfile {
    private final boolean needsOutdoorTime;
    private final boolean typicallyLitterTrained;
    private final String labelKey;

    public CareProfile(boolean needsOutdoorTime, boolean typicallyLitterTrained, String labelKey) {
        this.needsOutdoorTime = needsOutdoorTime;
        this.typicallyLitterTrained = typicallyLitterTrained;
        this.labelKey = labelKey;
    }

    public boolean needsOutdoorTime() {
        return needsOutdoorTime;
    }

    public boolean typicallyLitterTrained() {
        return typicallyLitterTrained;
    }

    public String labelKey() {
        return labelKey;
    }
}
