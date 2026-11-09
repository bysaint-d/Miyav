package az.miyav.domain.pet;

/** Age bands offered by the listing filter, in months. The upper bound is exclusive; null means open. */
public enum AgeRange {
    ANY(null, null),
    BABY(0, 6),
    YOUNG(6, 24),
    ADULT(24, 96),
    SENIOR(96, null);

    private final Integer minMonths;
    private final Integer maxMonthsExclusive;

    AgeRange(Integer minMonths, Integer maxMonthsExclusive) {
        this.minMonths = minMonths;
        this.maxMonthsExclusive = maxMonthsExclusive;
    }

    public Integer minMonths() {
        return minMonths;
    }

    public Integer maxMonthsExclusive() {
        return maxMonthsExclusive;
    }

    public boolean contains(int ageMonths) {
        return (minMonths == null || ageMonths >= minMonths)
                && (maxMonthsExclusive == null || ageMonths < maxMonthsExclusive);
    }
}
