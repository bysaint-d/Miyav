package az.miyav.desktop.admin;

import az.miyav.domain.premium.PremiumPlan;
import az.miyav.domain.premium.PremiumRequest;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Premium requests: the owner's payment receipt next to the listing, method and amount. Approving makes the
 * listing premium for the paid days; rejecting asks for a short reason the owner receives.
 */
public final class PremiumSection extends AdminSection {
    private final ToggleGroup filter = new ToggleGroup();
    private final VBox list = new VBox(12);
    private List<PremiumRequest> requests = List.of();

    public PremiumSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "premium";
    }

    @Override
    public String icon() {
        return "★";
    }

    @Override
    public String titleKey() {
        return "console.premium";
    }

    @Override
    public String subtitleKey() {
        return "console.premium_subtitle";
    }

    @Override
    protected Node build() {
        Label destinations = Components.wrapped(text("console.premium_destinations", PremiumPlan.M10_NUMBER, PremiumPlan.CARD_NUMBER,
                PremiumPlan.PRICE.toPlainString(), PremiumPlan.DAYS), "muted");
        return new VBox(16, Components.row(8, chip("console.premium_pending", PremiumRequest.Status.PENDING),
                chip("console.premium_approved", PremiumRequest.Status.APPROVED),
                chip("console.premium_rejected", PremiumRequest.Status.REJECTED), chip("console.filter_all", null)),
                destinations, list);
    }

    private ToggleButton chip(String key, PremiumRequest.Status status) {
        ToggleButton chip = new ToggleButton(text(key));
        chip.getStyleClass().add("chip");
        chip.setUserData(status);
        chip.setToggleGroup(filter);
        chip.setSelected(status == PremiumRequest.Status.PENDING);
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
        context.load(() -> context.graph().premium().queue(context.session(), null), loaded -> {
            requests = loaded;
            render();
        });
    }

    private void render() {
        Object wanted = filter.getSelectedToggle() == null ? null : filter.getSelectedToggle().getUserData();
        list.getChildren().clear();
        for (PremiumRequest request : requests) {
            if (wanted == null || wanted == request.status()) {
                list.getChildren().add(card(request));
            }
        }
        if (list.getChildren().isEmpty()) {
            list.getChildren().add(Components.card(Components.empty("★", text("console.premium_empty_title"),
                    text("console.premium_empty_body"))));
        }
    }

    private Node card(PremiumRequest request) {
        StackPane receipt = new StackPane(Components.label(text("console.premium_receipt"), "caption"));
        receipt.getStyleClass().add("photo-placeholder");
        receipt.setPrefSize(160, 200);
        receipt.setMinSize(160, 200);
        context.load(() -> context.graph().premium().receipt(context.session(), request), bytes -> {
            var image = Components.image(bytes, 900, 900);
            if (image != null) {
                ImageView view = new ImageView();
                Components.fill(view, image, 160, 200, 14);
                view.setStyle("-fx-cursor: hand;");
                view.setOnMouseClicked(event -> az.miyav.desktop.PhotoViewer.open(context.window(), text("console.premium_receipt"),
                        List.of(request.receiptPath()), 0, path -> context.graph().premium().receipt(context.session(), request)));
                receipt.getChildren().setAll(view);
            }
        }, code -> receipt.getChildren().setAll(Components.label(text("console.premium_receipt_missing"), "caption")));

        Label listing = Components.label(request.listingName().isEmpty() ? "—" : request.listingName(), "card-title");
        listing.setStyle("-fx-cursor: hand; -fx-underline: true;");
        listing.setOnMouseClicked(event -> context.open("adoption", request.listingId()));
        Label owner = Components.label(text("console.premium_owner", request.ownerName().isEmpty() ? "—" : request.ownerName()), "muted");
        owner.setStyle("-fx-cursor: hand;");
        owner.setOnMouseClicked(event -> context.open("users", request.ownerId()));
        String method = text(request.method() == PremiumRequest.Method.M10 ? "console.premium_m10" : "console.premium_card");
        Label payment = Components.label(text("console.premium_payment", request.amount().stripTrailingZeros().toPlainString(),
                method, request.days()), "cell-title");
        Label sent = Components.label(text("console.premium_sent_at", Components.dateTime(request.createdAt(), context.locale())), "caption");
        VBox info = new VBox(6, listing, owner, payment, sent);
        request.premiumUntil().ifPresent(until -> info.getChildren().add(Components.label(
                text("console.premium_until", Components.date(until, context.locale())), "caption")));
        if (!request.note().isBlank()) {
            info.getChildren().add(Components.wrapped(text("console.premium_note", request.note()), "muted"));
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox actions = new VBox(8, badge(request));
        if (request.pending()) {
            Button approve = new Button(text("console.premium_approve"));
            approve.getStyleClass().add("primary");
            approve.setMaxWidth(Double.MAX_VALUE);
            approve.setOnAction(event -> context.load(() -> {
                context.graph().premium().approve(context.session(), request);
                return Boolean.TRUE;
            }, done -> {
                context.status(text("console.premium_approved_done"));
                refresh();
            }));
            Button reject = new Button(text("console.premium_reject"));
            reject.getStyleClass().add("danger");
            reject.setMaxWidth(Double.MAX_VALUE);
            reject.setOnAction(event -> reject(request));
            actions.getChildren().addAll(approve, reject);
        }
        actions.setMinWidth(170);
        return Components.card(Components.row(18, receipt, info, actions));
    }

    private Node badge(PremiumRequest request) {
        return switch (request.status()) {
            case PENDING -> Components.badge(text("console.premium_pending"), Components.Tone.WARNING);
            case APPROVED -> Components.badge(text("console.premium_approved"), Components.Tone.MINT);
            case REJECTED -> Components.badge(text("console.premium_rejected"), Components.Tone.NEUTRAL);
        };
    }

    private void reject(PremiumRequest request) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(context.window());
        dialog.setTitle(text("console.premium_reject"));
        dialog.setHeaderText(text("console.premium_reject_reason"));
        dialog.getEditor().setPromptText(text("console.premium_reject_hint"));
        dialog.showAndWait().ifPresent(reason -> context.load(() -> {
            context.graph().premium().reject(context.session(), request, reason);
            return Boolean.TRUE;
        }, done -> {
            context.status(text("console.premium_rejected_done"));
            refresh();
        }));
    }
}
