package az.miyav.desktop.admin;

import az.miyav.domain.payout.PayoutDetails;
import az.miyav.domain.wallet.Withdrawal;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * Walkers' withdrawal requests: the account to pay, the gross amount, the service fee and the net to transfer.
 * After the bank transfer the administrator records its reference, which the walker receives as a receipt.
 */
public final class WithdrawalsSection extends AdminSection {
    private final ToggleGroup filter = new ToggleGroup();
    private final VBox list = new VBox(12);
    private List<Withdrawal> requests = List.of();

    public WithdrawalsSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "withdrawals";
    }

    @Override
    public String icon() {
        return "₼";
    }

    @Override
    public String titleKey() {
        return "console.withdrawals";
    }

    @Override
    public String subtitleKey() {
        return "console.withdrawals_subtitle";
    }

    @Override
    protected Node build() {
        return new VBox(16, Components.row(8, chip("console.withdrawals_pending", Withdrawal.Status.PENDING),
                chip("console.withdrawals_paid", Withdrawal.Status.PAID),
                chip("console.withdrawals_rejected", Withdrawal.Status.REJECTED), chip("console.filter_all", null)),
                Components.wrapped(text("console.withdrawals_rules"), "muted"), list);
    }

    private ToggleButton chip(String key, Withdrawal.Status status) {
        ToggleButton chip = new ToggleButton(text(key));
        chip.getStyleClass().add("chip");
        chip.setUserData(status);
        chip.setToggleGroup(filter);
        chip.setSelected(status == Withdrawal.Status.PENDING);
        chip.setOnAction(event -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
            }
            render();
        });
        return chip;
    }

    @Override
    public void refresh() {
        context.load(() -> context.graph().wallets().queue(context.session(), null), loaded -> {
            requests = loaded;
            render();
        });
    }

    private void render() {
        Object wanted = filter.getSelectedToggle() == null ? null : filter.getSelectedToggle().getUserData();
        list.getChildren().clear();
        for (Withdrawal request : requests) {
            if (wanted == null || wanted == request.status()) {
                list.getChildren().add(card(request));
            }
        }
        if (list.getChildren().isEmpty()) {
            list.getChildren().add(Components.card(Components.empty("₼", text("console.withdrawals_empty_title"),
                    text("console.withdrawals_empty_body"))));
        }
    }

    private Node card(Withdrawal request) {
        Label walker = Components.label(request.walkerName().isEmpty() ? "—" : request.walkerName(), "card-title");
        request.walkerId().ifPresent(id -> {
            walker.setStyle("-fx-cursor: hand; -fx-underline: true;");
            walker.setOnMouseClicked(event -> context.open("users", id));
        });
        Label receipt = Components.label(request.receiptNumber(), "caption");
        Label transfer = Components.label(text("console.withdrawals_transfer", money(request.net()), money(request.amount()),
                pct(request.feePercent()), money(request.fee())), "cell-title");
        String grouped = PayoutDetails.groupedIban(request.iban());
        Label account = Components.label(text("console.withdrawals_account", request.holderName(), request.bankName(), grouped), "muted");
        Button copy = new Button(text("console.withdrawals_copy_iban"));
        copy.getStyleClass().add("ghost");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(request.iban());
            Clipboard.getSystemClipboard().setContent(content);
            context.status(text("console.withdrawals_copied"));
        });
        Label sent = Components.label(text("console.premium_sent_at", Components.dateTime(request.createdAt(), context.locale())), "caption");
        VBox info = new VBox(6, walker, receipt, transfer, Components.row(8, account, copy), sent);
        request.walkerAvailable().ifPresent(available -> info.getChildren().add(Components.label(
                text("console.withdrawals_balance", money(available), request.walkerCompleted()), "caption")));
        if (!request.transferReference().isBlank()) {
            info.getChildren().add(Components.label(text("console.withdrawals_reference", request.transferReference()), "caption"));
        }
        request.processedAt().ifPresent(at -> info.getChildren().add(Components.label(
                text("console.withdrawals_processed", Components.dateTime(at, context.locale())), "caption")));
        if (!request.note().isBlank()) {
            info.getChildren().add(Components.wrapped(text("console.premium_note", request.note()), "muted"));
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox actions = new VBox(8, badge(request));
        boolean own = request.walkerId().map(id -> id.equals(context.session().userId())).orElse(false);
        if (request.pending() && !own) {
            Button pay = new Button(text("console.withdrawals_mark_paid"));
            pay.getStyleClass().add("primary");
            pay.setMaxWidth(Double.MAX_VALUE);
            pay.setOnAction(event -> markPaid(request));
            Button reject = new Button(text("console.premium_reject"));
            reject.getStyleClass().add("danger");
            reject.setMaxWidth(Double.MAX_VALUE);
            reject.setOnAction(event -> reject(request));
            actions.getChildren().addAll(pay, reject);
        } else if (request.pending()) {
            actions.getChildren().add(Components.wrapped(text("access.own_withdrawal"), "caption"));
        }
        actions.setMinWidth(190);
        actions.setMaxWidth(220);
        return Components.card(Components.row(18, info, actions));
    }

    private Node badge(Withdrawal request) {
        return switch (request.status()) {
            case PENDING -> Components.badge(text("console.withdrawals_pending"), Components.Tone.WARNING);
            case PAID -> Components.badge(text("console.withdrawals_paid"), Components.Tone.MINT);
            case REJECTED -> Components.badge(text("console.withdrawals_rejected"), Components.Tone.NEUTRAL);
            case CANCELLED -> Components.badge(text("console.withdrawals_cancelled"), Components.Tone.NEUTRAL);
        };
    }

    private void markPaid(Withdrawal request) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(context.window());
        dialog.setTitle(text("console.withdrawals_mark_paid"));
        dialog.setHeaderText(text("console.withdrawals_reference_prompt", money(request.net()),
                PayoutDetails.groupedIban(request.iban())));
        dialog.getEditor().setPromptText(text("console.withdrawals_reference_hint"));
        dialog.showAndWait().ifPresent(reference -> context.load(() -> {
            context.graph().wallets().markPaid(context.session(), request, reference);
            return Boolean.TRUE;
        }, done -> {
            context.status(text("console.withdrawals_paid_done"));
            refresh();
        }));
    }

    private void reject(Withdrawal request) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(context.window());
        dialog.setTitle(text("console.premium_reject"));
        dialog.setHeaderText(text("console.withdrawals_reject_reason"));
        dialog.getEditor().setPromptText(text("console.withdrawals_reject_hint"));
        dialog.showAndWait().ifPresent(reason -> context.load(() -> {
            context.graph().wallets().reject(context.session(), request, reason);
            return Boolean.TRUE;
        }, done -> {
            context.status(text("console.withdrawals_rejected_done"));
            refresh();
        }));
    }

    private static String money(BigDecimal amount) {
        BigDecimal plain = amount.stripTrailingZeros();
        return (plain.scale() < 0 ? plain.setScale(0) : plain).toPlainString();
    }

    private static String pct(BigDecimal percent) {
        return money(percent);
    }
}
