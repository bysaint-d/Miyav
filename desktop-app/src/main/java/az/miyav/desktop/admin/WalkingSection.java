package az.miyav.desktop.admin;

import az.miyav.domain.walk.WalkActivity;
import az.miyav.domain.walk.WalkRequest;
import az.miyav.domain.walk.WalkRequestStatus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Pet walking, MIYAV's core: requests between owners and walkers, plus walks shared through NearMe.
 * Moderators can review a request and cancel it when it is inappropriate.
 */
public final class WalkingSection extends AdminSection {
    private final ToggleGroup view = new ToggleGroup();
    private final ToggleGroup statusFilter = new ToggleGroup();
    private final FlowPane statusChips = new FlowPane(8, 8);
    private final TextField search = new TextField();
    private final TableView<WalkRequest> requests = new TableView<>();
    private final TableView<WalkActivity> walks = new TableView<>();
    private final VBox detail = new VBox(12);
    private final HBox summary = new HBox(16);
    private final VBox content = new VBox(16);
    private List<WalkRequest> loaded = List.of();

    public WalkingSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "walking";
    }

    @Override
    public String icon() {
        return "🐕";
    }

    @Override
    public String titleKey() {
        return "console.walking";
    }

    @Override
    public String subtitleKey() {
        return "console.walking_subtitle";
    }

    @Override
    protected Node build() {
        ToggleButton requestsTab = tab(text("console.walking_requests"), true);
        ToggleButton walksTab = tab(text("console.walking_nearme"), false);
        view.selectedToggleProperty().addListener((observable, before, now) -> {
            if (now == null) {
                before.setSelected(true);
                return;
            }
            showTab(now == requestsTab);
        });
        search.setPromptText(text("console.walking_search"));
        search.textProperty().addListener((observable, before, now) -> applyFilter());
        HBox.setHgrow(search, Priority.ALWAYS);

        buildRequestsTable();
        buildWalksTable();
        detail.getStyleClass().add("card");
        detail.setPrefWidth(320);
        detail.setMinWidth(300);
        showDetail(null);

        content.getChildren().setAll(requestsPane());
        VBox root = new VBox(18, summary, Components.row(8, requestsTab, walksTab), content);
        return root;
    }

    private ToggleButton tab(String label, boolean selected) {
        ToggleButton tab = new ToggleButton(label);
        tab.setToggleGroup(view);
        tab.setSelected(selected);
        return tab;
    }

    private Node requestsPane() {
        HBox tableAndDetail = new HBox(16, requests, detail);
        HBox.setHgrow(requests, Priority.ALWAYS);
        requests.setPrefHeight(520);
        return new VBox(12, Components.row(10, search), statusChips, tableAndDetail);
    }

    private void showTab(boolean requestsTab) {
        content.getChildren().setAll(requestsTab ? requestsPane() : walks);
        walks.setPrefHeight(560);
    }

    @Override
    public void refresh() {
        context.load(() -> context.insights().walkRequests(context.session(), Set.of(), "", context.now()), list -> {
            loaded = list;
            renderChips();
            applyFilter();
        });
        context.load(() -> context.insights().walkActivity(context.session()), list -> walks.getItems().setAll(list));
        context.load(() -> context.insights().overview(context.session()), overview -> summary.getChildren().setAll(
                Components.stat("🐕", text("console.stat_requests_open"), String.valueOf(overview.counts().upcomingRequests()),
                        text("console.stat_requests_open_hint")),
                Components.stat("📍", text("console.stat_live_walks"), String.valueOf(overview.counts().liveWalks()),
                        text("console.stat_live_walks_hint", overview.counts().walks())),
                Components.stat("🤝", text("console.stat_helpers"), String.valueOf(overview.counts().activeHelpers()),
                        text("console.stat_helpers_hint", overview.counts().verifiedHelpers()))));
    }

    private void renderChips() {
        Map<WalkRequestStatus, Integer> counts = context.insights().countByStatus(loaded, context.now());
        Object selected = statusFilter.getSelectedToggle() == null ? null : statusFilter.getSelectedToggle().getUserData();
        statusChips.getChildren().clear();
        ToggleButton all = chip(text("console.filter_all") + " · " + loaded.size(), null);
        statusChips.getChildren().add(all);
        for (WalkRequestStatus status : WalkRequestStatus.values()) {
            statusChips.getChildren().add(chip(statusText(status) + " · " + counts.get(status), status));
        }
        statusFilter.getToggles().stream()
                .filter(toggle -> java.util.Objects.equals(toggle.getUserData(), selected))
                .findFirst()
                .orElse(all)
                .setSelected(true);
    }

    private ToggleButton chip(String label, WalkRequestStatus status) {
        ToggleButton chip = new ToggleButton(label);
        chip.getStyleClass().add("chip");
        chip.setUserData(status);
        chip.setToggleGroup(statusFilter);
        chip.setOnAction(event -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
            }
            applyFilter();
        });
        return chip;
    }

    private void applyFilter() {
        Object selected = statusFilter.getSelectedToggle() == null ? null : statusFilter.getSelectedToggle().getUserData();
        Set<WalkRequestStatus> statuses = selected == null ? Set.of() : EnumSet.of((WalkRequestStatus) selected);
        List<WalkRequest> visible = new ArrayList<>();
        for (WalkRequest request : loaded) {
            if ((statuses.isEmpty() || statuses.contains(request.status(context.now()))) && request.matches(search.getText())) {
                visible.add(request);
            }
        }
        requests.getItems().setAll(visible);
        requests.setPlaceholder(Components.empty("🐕", text("console.empty_requests_title"),
                loaded.isEmpty() ? text("console.empty_requests_body") : text("console.empty_filter")));
    }

    private void buildRequestsTable() {
        requests.getColumns().setAll(
                column(text("console.col_when"), request -> Components.dateTime(request.startsAt(), context.locale())
                        + " – " + Components.time(request.endsAt(), context.locale()), 170),
                column(text("console.col_people"), request -> request.owner().name() + "  →  " + request.walker().name(), 230),
                column(text("console.col_pet"), request -> Components.speciesGlyph(request.petType()) + "  "
                        + (request.petName().isEmpty() ? "—" : request.petName()), 130),
                column(text("console.col_service"), request -> text("service." + request.service().databaseValue()), 110),
                column(text("console.col_area"), request -> request.area().isEmpty() ? "—" : request.area(), 140),
                statusColumn());
        requests.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        requests.getSelectionModel().selectedItemProperty().addListener((observable, before, request) -> showDetail(request));
    }

    private TableColumn<WalkRequest, WalkRequestStatus> statusColumn() {
        TableColumn<WalkRequest, WalkRequestStatus> status = new TableColumn<>(text("console.col_status"));
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status(context.now())));
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(WalkRequestStatus value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : Components.badge(statusText(value), tone(value)));
            }
        });
        status.setPrefWidth(130);
        return status;
    }

    private void showDetail(WalkRequest request) {
        detail.getChildren().clear();
        if (request == null) {
            detail.getChildren().add(Components.empty("☝", text("console.walking_select_title"), text("console.walking_select_body")));
            detail.setAlignment(Pos.CENTER);
            return;
        }
        detail.setAlignment(Pos.TOP_LEFT);
        WalkRequestStatus status = request.status(context.now());
        detail.getChildren().addAll(
                Components.row(8, Components.badge(statusText(status), tone(status)),
                        Components.badge(text("service." + request.service().databaseValue()), Components.Tone.SAGE)),
                Components.label(Components.speciesGlyph(request.petType()) + "  "
                        + (request.petName().isEmpty() ? text("console.pet_unnamed") : request.petName()), "detail-title"),
                fact(text("console.detail_owner"), request.owner().name()),
                fact(text("console.detail_walker"), request.walker().name()),
                fact(text("console.detail_when"), Components.dateTime(request.startsAt(), context.locale()) + " – "
                        + Components.time(request.endsAt(), context.locale()) + "  (" + minutes(request.duration()) + ")"),
                fact(text("console.detail_area"), request.area().isEmpty() ? "—" : request.area()),
                fact(text("console.detail_price"), request.totalPrice().stripTrailingZeros().toPlainString() + " ₼"),
                fact(text("console.detail_notes"), request.notes().isEmpty() ? "—" : request.notes()));
        Button owner = new Button(text("console.open_member", request.owner().name()));
        owner.getStyleClass().add("ghost");
        owner.setOnAction(event -> context.open("users", request.owner().id()));
        Button walker = new Button(text("console.open_member", request.walker().name()));
        walker.getStyleClass().add("ghost");
        walker.setOnAction(event -> context.open("users", request.walker().id()));
        detail.getChildren().addAll(owner, walker);
        if (status.cancellableByModerator()) {
            Button cancel = new Button(text("console.cancel_request"));
            cancel.getStyleClass().add("danger");
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(event -> cancel(request));
            detail.getChildren().add(cancel);
        }
    }

    private void cancel(WalkRequest request) {
        TextInputDialog reason = new TextInputDialog();
        reason.initOwner(context.window());
        reason.setTitle(text("console.cancel_request"));
        reason.setHeaderText(text("console.cancel_request_confirm"));
        reason.setContentText(text("console.moderation_note"));
        reason.showAndWait().ifPresent(note -> context.load(() -> {
            context.graph().admin().cancelWalkRequest(context.session(), request, context.now(), note);
            return Boolean.TRUE;
        }, done -> {
            context.status(text("console.cancelled"));
            refresh();
        }, code -> {
            Alert failed = new Alert(Alert.AlertType.ERROR, text(code), ButtonType.OK);
            failed.initOwner(context.window());
            failed.showAndWait();
        }));
    }

    private void buildWalksTable() {
        TableColumn<WalkActivity, String> walker = new TableColumn<>(text("console.col_walker"));
        walker.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().userName()));
        TableColumn<WalkActivity, String> pet = new TableColumn<>(text("console.col_pet"));
        pet.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Components.speciesGlyph(cell.getValue().species().databaseValue())
                + "  " + cell.getValue().petName()));
        TableColumn<WalkActivity, String> started = new TableColumn<>(text("console.col_started"));
        started.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Components.dateTime(cell.getValue().startedAt(), context.locale())));
        TableColumn<WalkActivity, String> length = new TableColumn<>(text("console.col_duration"));
        length.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().endedAt()
                .map(end -> minutes(Duration.between(cell.getValue().startedAt(), end)))
                .orElse(cell.getValue().live() ? text("console.walk_live") : "—")));
        TableColumn<WalkActivity, String> message = new TableColumn<>(text("console.col_message"));
        message.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().message().isEmpty() ? "—" : cell.getValue().message()));
        TableColumn<WalkActivity, WalkActivity> status = new TableColumn<>(text("console.col_status"));
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(WalkActivity value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                String state = value.status().databaseValue();
                setGraphic(Components.badge(text("walk.session." + state),
                        value.live() ? Components.Tone.MINT : "paused".equals(state) ? Components.Tone.WARNING : Components.Tone.NEUTRAL));
            }
        });
        walks.getColumns().setAll(List.of(walker, pet, started, length, message, status));
        walks.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        walks.setPlaceholder(Components.empty("📍", text("console.empty_walks_title"), text("console.empty_walks_body")));
        walks.setRowFactory(table -> {
            javafx.scene.control.TableRow<WalkActivity> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    context.open("users", row.getItem().userId());
                }
            });
            return row;
        });
    }

    private <T> TableColumn<WalkRequest, String> column(String title, Function<WalkRequest, String> value, double width) {
        TableColumn<WalkRequest, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private Node fact(String label, String value) {
        Label name = Components.label(label, "caption");
        Label content = Components.wrapped(value);
        return new VBox(2, name, content);
    }

    private String minutes(Duration duration) {
        return text("console.minutes", Math.max(0, duration.toMinutes()));
    }

    private String statusText(WalkRequestStatus status) {
        return text("walk.request." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    static Components.Tone tone(WalkRequestStatus status) {
        return switch (status) {
            case PENDING -> Components.Tone.WARNING;
            case ACCEPTED -> Components.Tone.SAGE;
            case IN_PROGRESS -> Components.Tone.MINT;
            case COMPLETED -> Components.Tone.NEUTRAL;
            case DECLINED, CANCELLED -> Components.Tone.DANGER;
        };
    }
}
