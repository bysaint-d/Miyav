package az.miyav.desktop.user;

import az.miyav.desktop.admin.Components;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.payout.PayoutDetails;
import az.miyav.domain.wallet.Wallet;
import az.miyav.domain.wallet.Withdrawal;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * "MIYAV hesabım", as on the phone: what the walker earned, withdrawals with their receipts, and the bank
 * details with the walker agreement that a withdrawal needs.
 */
public final class WalletPage extends UserPage {
    private static final List<String> BANKS = List.of("Kapital Bank", "ABB", "PAŞA Bank", "Bank Respublika", "Xalq Bank",
            "Unibank", "AccessBank", "Yelo Bank", "Rabitəbank", "Bank of Baku", "Expressbank", "Yapı Kredi Bank Azərbaycan",
            "Ziraat Bank Azərbaycan", "VTB (Azərbaycan)", "Premium Bank", "Turan Bank", "AzərTürk Bank", "Naxçıvanbank", "BTB");
    private final Label available = Components.label("", "stat-value");
    private final Label summary = Components.wrapped("", "muted");
    private final Label hint = Components.wrapped("", "caption");
    private final Button withdraw = new Button();
    private final VBox history = new VBox(10);
    private final TextField holder = new TextField();
    private final ComboBox<String> bank = new ComboBox<>();
    private final TextField iban = new TextField();
    private final CheckBox agreement = new CheckBox();
    private final Label payoutStatus = Components.wrapped("", "muted");
    private Wallet wallet;
    private PayoutDetails payout;

    public WalletPage(UserContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "wallet";
    }

    @Override
    public String titleKey() {
        return "user.nav.wallet";
    }

    @Override
    public String icon() {
        return "₼";
    }

    @Override
    protected Node build() {
        withdraw.setText(text("user.wallet.withdraw"));
        withdraw.getStyleClass().add("primary");
        withdraw.setOnAction(event -> withdraw());
        VBox balance = Components.card(text("user.wallet.available"), available, summary, Components.row(12, withdraw), hint);

        holder.setPromptText(text("user.payout.holder"));
        bank.getItems().setAll(BANKS);
        bank.setEditable(true);
        bank.setPromptText(text("user.payout.bank"));
        iban.setPromptText("AZ21 NABZ 0000 0000 1370 1000 1944");
        iban.setStyle("-fx-font-family: monospace;");
        agreement.setText(text("user.payout.accept"));
        Button read = new Button(text("user.payout.read"));
        read.getStyleClass().add("ghost");
        read.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, text("user.payout.agreement_text"), ButtonType.OK);
            alert.initOwner(context.window());
            alert.setTitle(text("user.payout.agreement"));
            alert.setHeaderText(text("user.payout.agreement"));
            alert.getDialogPane().setPrefWidth(560);
            alert.showAndWait();
        });
        Button save = new Button(text("profile.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(event -> savePayout());
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.getColumnConstraints().setAll(ProfilePage.labelColumn(), new javafx.scene.layout.ColumnConstraints());
        form.addRow(0, Components.label(text("user.payout.holder"), "caption"), holder);
        form.addRow(1, Components.label(text("user.payout.bank"), "caption"), bank);
        form.addRow(2, Components.label(text("user.payout.iban"), "caption"), iban);
        holder.setPrefWidth(360);
        bank.setPrefWidth(360);
        iban.setPrefWidth(360);
        VBox bankCard = Components.card(text("user.payout.title"), Components.wrapped(text("user.payout.intro"), "muted"), form,
                Components.row(8, read), agreement, Components.row(12, save, payoutStatus));

        VBox column = new VBox(16, balance, Components.card(text("user.wallet.history"), history), bankCard);
        column.setMaxWidth(720);
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        return scroll;
    }

    @Override
    public void refresh() {
        context.load(() -> {
            AuthSession session = context.session();
            return new Loaded(context.graph().wallets().wallet(session), context.graph().wallets().mine(session),
                    context.graph().payouts().mine(session).orElse(null));
        }, this::fill);
    }

    private void fill(Loaded loaded) {
        wallet = loaded.wallet;
        payout = loaded.payout;
        available.setText(money(wallet.available()) + " ₼");
        String text = text("user.wallet.summary", money(wallet.earned()), money(wallet.pending()), money(wallet.withdrawn()),
                wallet.completedWalks());
        if (wallet.held().signum() > 0) {
            text += "\n" + text("user.wallet.held", money(wallet.held()));
        }
        summary.setText(text);
        boolean ready = payout != null && payout.agreementCurrent();
        withdraw.setDisable(!wallet.canWithdraw());
        hint.setText(!ready ? text("user.wallet.hint_payout")
                : wallet.pending().signum() > 0 ? text("user.wallet.hint_pending")
                : wallet.available().compareTo(wallet.minimum()) < 0 ? text("user.wallet.hint_minimum", money(wallet.minimum()))
                : text("user.wallet.hint_fee", money(wallet.feePercent())));
        history.getChildren().clear();
        for (Withdrawal item : loaded.history) {
            history.getChildren().add(row(item));
        }
        if (loaded.history.isEmpty()) {
            history.getChildren().add(Components.wrapped(text("user.wallet.history_empty"), "muted"));
        }
        if (payout != null) {
            holder.setText(payout.holderName());
            bank.setValue(payout.bankName());
            iban.setText(PayoutDetails.groupedIban(payout.iban()));
            agreement.setSelected(payout.agreementCurrent());
            payoutStatus.setText(payout.agreementAcceptedAt() == null ? "" : text(payout.agreementCurrent()
                    ? "user.payout.accepted_at" : "user.payout.outdated", Components.date(payout.agreementAcceptedAt(), context.locale())));
        }
    }

    private Node row(Withdrawal item) {
        Label amount = Components.label(money(item.amount()) + " ₼", "cell-title");
        Label detail = Components.label(text("user.wallet.row", Components.date(item.createdAt(), context.locale()),
                money(item.net()), item.maskedIban()), "muted");
        VBox info = new VBox(2, amount, detail);
        if (item.status() == Withdrawal.Status.REJECTED && !item.note().isBlank()) {
            info.getChildren().add(Components.wrapped(item.note(), "caption"));
        }
        HBox row = Components.row(12, info, Components.spacer(), Components.badge(text("console.withdrawals_"
                + item.status().databaseValue()), item.paid() ? Components.Tone.MINT
                : item.pending() ? Components.Tone.WARNING : Components.Tone.NEUTRAL));
        if (item.paid()) {
            Button receipt = new Button(text("user.wallet.receipt"));
            receipt.getStyleClass().add("ghost");
            receipt.setOnAction(event -> receipt(item));
            row.getChildren().add(receipt);
        } else if (item.pending()) {
            Button cancel = new Button(text("user.wallet.cancel"));
            cancel.getStyleClass().add("ghost");
            cancel.setOnAction(event -> context.load(() -> {
                context.graph().wallets().cancel(context.session(), item);
                return Boolean.TRUE;
            }, done -> {
                context.status(text("user.wallet.cancelled"));
                refresh();
            }));
            row.getChildren().add(cancel);
        }
        return row;
    }

    private void withdraw() {
        if (wallet == null) {
            return;
        }
        if (payout == null || !payout.agreementCurrent()) {
            context.status(text("user.wallet.hint_payout"));
            holder.requestFocus();
            return;
        }
        TextInputDialog dialog = new TextInputDialog(money(wallet.available()));
        dialog.initOwner(context.window());
        dialog.setTitle(text("user.wallet.withdraw"));
        dialog.setHeaderText(text("user.wallet.withdraw_header", money(wallet.minimum()), money(wallet.available()),
                money(wallet.feePercent()), payout.bankName(), PayoutDetails.groupedIban(payout.iban())));
        dialog.setContentText(text("user.wallet.amount"));
        Label breakdown = Components.wrapped("", "muted");
        dialog.getEditor().textProperty().addListener((observable, before, value) -> breakdown.setText(breakdown(value)));
        breakdown.setText(breakdown(dialog.getEditor().getText()));
        dialog.getDialogPane().setExpandableContent(null);
        ((GridPane) dialog.getDialogPane().getContent()).add(breakdown, 1, 1);
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        BigDecimal amount = parse(answer.get());
        if (amount == null) {
            context.status(text("withdrawal.minimum"));
            return;
        }
        Wallet seen = wallet;
        context.load(() -> context.graph().wallets().request(context.session(), seen, amount), id -> {
            context.status(text("user.wallet.requested"));
            refresh();
        });
    }

    private String breakdown(String value) {
        BigDecimal amount = parse(value);
        return amount == null || wallet == null ? "" : text("user.wallet.breakdown", money(wallet.feePercent()),
                money(wallet.feeFor(amount)), money(wallet.netFor(amount)));
    }

    private void receipt(Withdrawal item) {
        String body = text("user.wallet.receipt_text", item.receiptNumber(),
                item.processedAt().map(at -> Components.dateTime(at, context.locale())).orElse("—"), item.holderName(), item.bankName(),
                item.maskedIban(), money(item.amount()), money(item.feePercent()), money(item.fee()), money(item.net()),
                item.transferReference());
        ButtonType copy = new ButtonType(text("user.wallet.copy"));
        Alert alert = new Alert(Alert.AlertType.INFORMATION, body, copy, ButtonType.OK);
        alert.initOwner(context.window());
        alert.setTitle(text("user.wallet.receipt"));
        alert.setHeaderText(text("user.wallet.receipt_title", item.receiptNumber()));
        alert.showAndWait().filter(copy::equals).ifPresent(chosen -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(body);
            Clipboard.getSystemClipboard().setContent(content);
            context.status(text("user.wallet.copied"));
        });
    }

    private void savePayout() {
        String name = holder.getText();
        String bankName = bank.getEditor().getText() == null || bank.getEditor().getText().isBlank()
                ? bank.getValue() : bank.getEditor().getText();
        String number = iban.getText();
        boolean accepted = agreement.isSelected();
        if (!PayoutDetails.validIban(number)) {
            payoutStatus.setText(text("payout.iban"));
            return;
        }
        payoutStatus.setText("…");
        context.load(() -> {
            context.graph().payouts().save(context.session(), name, bankName, number, accepted);
            return Boolean.TRUE;
        }, done -> {
            payoutStatus.setText(text("user.payout.saved"));
            refresh();
        }, code -> payoutStatus.setText(text(code)));
    }

    private static BigDecimal parse(String value) {
        try {
            String text = value == null ? "" : value.trim().replace(',', '.');
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String money(BigDecimal amount) {
        BigDecimal plain = amount.stripTrailingZeros();
        return (plain.scale() < 0 ? plain.setScale(0) : plain).toPlainString();
    }

    private record Loaded(Wallet wallet, List<Withdrawal> history, PayoutDetails payout) {
    }
}
