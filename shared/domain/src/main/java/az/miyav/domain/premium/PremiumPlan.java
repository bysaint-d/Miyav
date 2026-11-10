package az.miyav.domain.premium;

import java.math.BigDecimal;

/**
 * What a premium listing costs and where the money goes. The server fixes the same price and length on every
 * request; these values are only shown to the member.
 */
public final class PremiumPlan {
    public static final BigDecimal PRICE = new BigDecimal("5");
    public static final int DAYS = 7;
    public static final String M10_NUMBER = "050 347 73 06";
    public static final String CARD_NUMBER = "4169 7388 1235 5232";

    private PremiumPlan() {
    }

    /** The number to copy for a transfer, without spaces. */
    public static String destination(PremiumRequest.Method method) {
        return (method == PremiumRequest.Method.M10 ? M10_NUMBER : CARD_NUMBER).replace(" ", "");
    }
}
