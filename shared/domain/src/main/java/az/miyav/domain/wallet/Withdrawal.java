package az.miyav.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A walker's request to be paid from their MIYAV balance, and once paid, its receipt: the account that was
 * paid, the gross amount, the service fee, the net transfer and the bank reference. The walker's name, balance
 * and walk count are filled in for the administrators' queue only.
 */
public final class Withdrawal {
    public enum Status {
        PENDING("pending"), PAID("paid"), REJECTED("rejected"), CANCELLED("cancelled");

        private final String databaseValue;

        Status(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static Status fromDatabase(String value) {
            for (Status status : values()) {
                if (status.databaseValue.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown withdrawal status: " + value);
        }
    }

    private final UUID id;
    private final long receiptNo;
    private final UUID walkerId;
    private final BigDecimal amount;
    private final BigDecimal feePercent;
    private final BigDecimal fee;
    private final BigDecimal net;
    private final String holderName;
    private final String bankName;
    private final String iban;
    private final Status status;
    private final String transferReference;
    private final String note;
    private final Instant processedAt;
    private final Instant createdAt;
    private String walkerName = "";
    private BigDecimal walkerAvailable;
    private int walkerCompleted;

    public Withdrawal(UUID id, long receiptNo, UUID walkerId, BigDecimal amount, BigDecimal feePercent, BigDecimal fee,
                      BigDecimal net, String holderName, String bankName, String iban, Status status,
                      String transferReference, String note, Instant processedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.receiptNo = receiptNo;
        this.walkerId = walkerId;
        this.amount = Wallet.money(amount);
        this.feePercent = Objects.requireNonNull(feePercent, "feePercent");
        this.fee = Wallet.money(fee);
        this.net = Wallet.money(net);
        this.holderName = holderName == null ? "" : holderName;
        this.bankName = bankName == null ? "" : bankName;
        this.iban = iban == null ? "" : iban;
        this.status = Objects.requireNonNull(status, "status");
        this.transferReference = transferReference == null ? "" : transferReference;
        this.note = note == null ? "" : note;
        this.processedAt = processedAt;
        this.createdAt = createdAt;
    }

    /** Adds what the administrators' queue shows next to a request. */
    public Withdrawal withWalker(String name, BigDecimal available, int completed) {
        Withdrawal copy = new Withdrawal(id, receiptNo, walkerId, amount, feePercent, fee, net, holderName, bankName, iban,
                status, transferReference, note, processedAt, createdAt);
        copy.walkerName = name == null ? "" : name;
        copy.walkerAvailable = available == null ? null : Wallet.money(available);
        copy.walkerCompleted = completed;
        return copy;
    }

    /** "MIYAV-000042": the number printed on the receipt. */
    public String receiptNumber() {
        return String.format(java.util.Locale.ROOT, "MIYAV-%06d", receiptNo);
    }

    /** The IBAN with all but the country, check digits and last four characters hidden. */
    public String maskedIban() {
        if (iban.length() < 8) {
            return iban;
        }
        return iban.substring(0, 4) + " •••• " + iban.substring(iban.length() - 4);
    }

    public boolean pending() {
        return status == Status.PENDING;
    }

    public boolean paid() {
        return status == Status.PAID;
    }

    public UUID id() {
        return id;
    }

    public long receiptNo() {
        return receiptNo;
    }

    public Optional<UUID> walkerId() {
        return Optional.ofNullable(walkerId);
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal feePercent() {
        return feePercent;
    }

    public BigDecimal fee() {
        return fee;
    }

    public BigDecimal net() {
        return net;
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

    public Status status() {
        return status;
    }

    public String transferReference() {
        return transferReference;
    }

    public String note() {
        return note;
    }

    public Optional<Instant> processedAt() {
        return Optional.ofNullable(processedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String walkerName() {
        return walkerName;
    }

    public Optional<BigDecimal> walkerAvailable() {
        return Optional.ofNullable(walkerAvailable);
    }

    public int walkerCompleted() {
        return walkerCompleted;
    }
}
