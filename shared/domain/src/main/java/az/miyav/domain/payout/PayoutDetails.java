package az.miyav.domain.payout;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Where a walker is paid: account holder, bank and Azerbaijani IBAN, with the walker agreement they accepted. */
public final class PayoutDetails {
    /** Bumped whenever the walker agreement text changes, so members accept the new wording. */
    public static final String AGREEMENT_VERSION = "2026-11-10";
    private static final Pattern AZ_IBAN = Pattern.compile("AZ[0-9]{2}[A-Z0-9]{4}[A-Z0-9]{20}");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final String holderName;
    private final String bankName;
    private final String iban;
    private final String agreementVersion;
    private final Instant agreementAcceptedAt;

    public PayoutDetails(String holderName, String bankName, String iban, String agreementVersion, Instant agreementAcceptedAt) {
        this.holderName = Objects.requireNonNull(holderName, "holderName");
        this.bankName = Objects.requireNonNull(bankName, "bankName");
        this.iban = Objects.requireNonNull(iban, "iban");
        this.agreementVersion = Objects.requireNonNull(agreementVersion, "agreementVersion");
        this.agreementAcceptedAt = agreementAcceptedAt;
    }

    /** Upper case without spaces, the form stored and checked. */
    public static String normalizeIban(String value) {
        return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    /** An Azerbaijani IBAN (AZ, 28 characters) whose check digits are right (ISO 13616 mod 97). */
    public static boolean validIban(String value) {
        String iban = normalizeIban(value);
        if (!AZ_IBAN.matcher(iban).matches()) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    /** "AZ21 NABZ 0000 ..." for display. */
    public static String groupedIban(String value) {
        String iban = normalizeIban(value);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < iban.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                grouped.append(' ');
            }
            grouped.append(iban.charAt(i));
        }
        return grouped.toString();
    }

    public String holderName() {
        return holderName;
    }

    public String bankName() {
        return bankName;
    }

    public String iban() {
        return iban;
    }

    public String agreementVersion() {
        return agreementVersion;
    }

    public Instant agreementAcceptedAt() {
        return agreementAcceptedAt;
    }

    /** False when the member accepted an older agreement and must accept the current one again. */
    public boolean agreementCurrent() {
        return AGREEMENT_VERSION.equals(agreementVersion);
    }
}
