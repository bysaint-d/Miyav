package az.miyav.domain.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A walker's MIYAV balance as the server computes it: earned from completed walks, held in a pending withdrawal,
 * already paid out, and what can still be withdrawn. The fee percent and minimum come from the server too.
 */
public final class Wallet {
    private final BigDecimal earned;
    private final BigDecimal held;
    private final BigDecimal pending;
    private final BigDecimal withdrawn;
    private final BigDecimal available;
    private final int completedWalks;
    private final BigDecimal feePercent;
    private final BigDecimal minimum;

    /**
     * {@code held} is money from walks the walker marked done themselves in the last 48 hours; it becomes
     * available when the owner rates the walk or the 48 hours pass.
     */
    public Wallet(BigDecimal earned, BigDecimal held, BigDecimal pending, BigDecimal withdrawn, BigDecimal available,
                  int completedWalks, BigDecimal feePercent, BigDecimal minimum) {
        this.earned = money(earned);
        this.held = money(held);
        this.pending = money(pending);
        this.withdrawn = money(withdrawn);
        this.available = money(available);
        this.completedWalks = completedWalks;
        this.feePercent = Objects.requireNonNull(feePercent, "feePercent");
        this.minimum = money(minimum);
    }

    /** The fee taken from {@code amount}, rounded the same way as the server (half up, two decimals). */
    public BigDecimal feeFor(BigDecimal amount) {
        return money(amount).multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal netFor(BigDecimal amount) {
        return money(amount).subtract(feeFor(amount));
    }

    public boolean canWithdraw() {
        return available.compareTo(minimum) >= 0 && pending.signum() == 0;
    }

    public BigDecimal earned() {
        return earned;
    }

    public BigDecimal held() {
        return held;
    }

    public BigDecimal pending() {
        return pending;
    }

    public BigDecimal withdrawn() {
        return withdrawn;
    }

    public BigDecimal available() {
        return available;
    }

    public int completedWalks() {
        return completedWalks;
    }

    public BigDecimal feePercent() {
        return feePercent;
    }

    public BigDecimal minimum() {
        return minimum;
    }

    static BigDecimal money(BigDecimal value) {
        return Objects.requireNonNull(value, "amount").setScale(2, RoundingMode.HALF_UP);
    }
}
