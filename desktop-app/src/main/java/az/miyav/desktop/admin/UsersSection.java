package az.miyav.desktop.admin;

import az.miyav.domain.admin.UserSummary;
import az.miyav.domain.user.UserRole;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Members: who joined, how active they are, their pets and walks, and account moderation. */
public final class UsersSection extends AdminSection {
    private enum Filter { ALL, ACTIVE, NEW, QUIET, HELPERS, SUSPENDED }

    private final TableView<UserSummary> table = new TableView<>();
    private final TextField search = new TextField();
    private final ChoiceBox<Filter> filter = new ChoiceBox<>();
    private final VBox detail = new VBox(12);
    private List<UserSummary> users = List.of();
    private UUID focus;

    public UsersSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "users";
    }

    @Override
    public String icon() {
        return "👥";
    }

    @Override
    public String titleKey() {
        return "console.users";
    }

    @Override
    public String subtitleKey() {
        return "console.users_subtitle";
    }

    @Override
    protected Node build() {
        search.setPromptText(text("console.users_search"));
        search.textProperty().addListener((observable, before, now) -> render());
        HBox.setHgrow(search, Priority.ALWAYS);
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setConverter(new StringConverter<>() {
            @Override
            public String toString(Filter value) {
                return value == null ? "" : text("console.users_filter_" + value.name().toLowerCase(java.util.Locale.ROOT));
            }

            @Override
            public Filter fromString(String text) {
                return null;
            }
        });
        filter.setOnAction(event -> render());
        buildTable();
        detail.getStyleClass().add("card");
        detail.setPrefWidth(320);
        detail.setMinWidth(300);
        showDetail(null);
        HBox body = new HBox(16, table, detail);
        HBox.setHgrow(table, Priority.ALWAYS);
        table.setPrefHeight(600);
        return new VBox(14, Components.row(10, search, filter), body);
    }

    @Override
    public void refresh() {
        context.load(() -> context.insights().users(context.session()), list -> {
            users = list;
            context.remember(list);
            render();
        });
    }

    @Override
    public void focus(UUID id) {
        focus = id;
        select();
    }

    private void render() {
        Predicate<UserSummary> chosen = switch (filter.getValue() == null ? Filter.ALL : filter.getValue()) {
            case ALL -> user -> true;
            case ACTIVE -> user -> !user.suspended() && user.activity(context.now()) == UserSummary.Activity.ACTIVE;
            case NEW -> user -> user.activity(context.now()) == UserSummary.Activity.NEW;
            case QUIET -> user -> user.activity(context.now()) == UserSummary.Activity.QUIET;
            case HELPERS -> UserSummary::walkHelper;
            case SUSPENDED -> UserSummary::suspended;
        };
        List<UserSummary> visible = new ArrayList<>();
        for (UserSummary user : users) {
            if (chosen.test(user) && user.matches(search.getText())) {
                visible.add(user);
            }
        }
        table.getItems().setAll(visible);
        table.setPlaceholder(Components.empty("👥", text("console.empty_users_title"),
                users.isEmpty() ? text("console.empty_users_body") : text("console.empty_filter")));
        select();
    }

    private void select() {
        if (focus == null) {
            return;
        }
        for (UserSummary user : users) {
            if (user.id().equals(focus)) {
                if (!table.getItems().contains(user)) {
                    search.setText("");
                    filter.setValue(Filter.ALL);
                }
                table.getSelectionModel().select(user);
                table.scrollTo(user);
                focus = null;
                return;
            }
        }
    }

    private void buildTable() {
        TableColumn<UserSummary, UserSummary> person = new TableColumn<>(text("console.col_member"));
        person.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        person.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(UserSummary user, boolean empty) {
                super.updateItem(user, empty);
                setText(null);
                setGraphic(empty || user == null ? null : Components.row(10, avatar(user, 34),
                        new VBox(1, Components.label(user.fullName(), "cell-title"),
                                Components.label(text("role." + user.role().databaseValue()), "caption"))));
            }
        });
        person.setPrefWidth(230);
        TableColumn<UserSummary, UserSummary> status = new TableColumn<>(text("console.col_status"));
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(UserSummary user, boolean empty) {
                super.updateItem(user, empty);
                setText(null);
                setGraphic(empty || user == null ? null : statusBadge(user));
            }
        });
        status.setPrefWidth(120);
        table.getColumns().setAll(List.of(person, status,
                column(text("console.col_joined"), user -> Components.date(user.joinedAt(), context.locale()), 120),
                column(text("console.col_city"), user -> user.city().isEmpty() ? "—" : user.city(), 110),
                column(text("console.col_pets"), user -> String.valueOf(user.pets()), 70),
                column(text("console.col_walks"), user -> String.valueOf(user.walks()), 70),
                column(text("console.col_listings"), user -> String.valueOf(user.listings()), 80),
                column(text("console.col_last_active"), user -> Components.ago(user.lastActiveAt(), context.now(), context), 110)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((observable, before, user) -> showDetail(user));
    }

    private TableColumn<UserSummary, String> column(String title, Function<UserSummary, String> value, double width) {
        TableColumn<UserSummary, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private Node statusBadge(UserSummary user) {
        if (user.suspended()) {
            return Components.badge(text("console.user_suspended"), Components.Tone.DANGER);
        }
        return switch (user.activity(context.now())) {
            case NEW -> Components.badge(text("console.user_new"), Components.Tone.PEACH);
            case ACTIVE -> Components.badge(text("console.user_active"), Components.Tone.MINT);
            case QUIET -> Components.badge(text("console.user_quiet"), Components.Tone.NEUTRAL);
        };
    }

    private StackPane avatar(UserSummary user, double size) {
        StackPane avatar = Components.avatar(user.initials(), size);
        if (user.avatarPath() != null) {
            String path = user.avatarPath();
            context.load(() -> context.graph().users().avatar(path), bytes -> {
                var image = Components.image(bytes, size * 2, size * 2);
                if (image != null) {
                    ImageView view = new ImageView(image);
                    view.setFitWidth(size);
                    view.setFitHeight(size);
                    view.setClip(new Circle(size / 2, size / 2, size / 2));
                    avatar.getChildren().setAll(view);
                }
            }, code -> {
            });
        }
        return avatar;
    }

    private void showDetail(UserSummary user) {
        detail.getChildren().clear();
        if (user == null) {
            detail.setAlignment(Pos.CENTER);
            detail.getChildren().add(Components.empty("👤", text("console.users_select_title"), text("console.users_select_body")));
            return;
        }
        detail.setAlignment(Pos.TOP_LEFT);
        String area = user.district().isEmpty() ? user.city() : user.district() + ", " + user.city();
        detail.getChildren().addAll(
                Components.row(12, avatar(user, 64), new VBox(4, Components.label(user.fullName(), "card-title"),
                        Components.row(6, statusBadge(user), Components.badge(text("role." + user.role().databaseValue()), Components.Tone.SAGE)))),
                fact(text("console.detail_area"), area.isEmpty() ? "—" : area),
                fact(text("console.col_joined"), Components.date(user.joinedAt(), context.locale())),
                fact(text("console.col_last_active"), Components.ago(user.lastActiveAt(), context.now(), context)),
                Components.wrapped(text("console.member_counts", user.pets(), user.walks(), user.listings()), "muted"),
                Components.wrapped(text("console.member_requests", user.requests()), "muted"));
        if (user.role() != UserRole.ADMIN) {
            Button moderate = new Button(text(user.suspended() ? "console.unsuspend" : "admin.suspend"));
            moderate.getStyleClass().add(user.suspended() ? "primary" : "danger");
            moderate.setMaxWidth(Double.MAX_VALUE);
            moderate.setOnAction(event -> context.load(() -> {
                context.graph().admin().suspend(context.session(), user.id(), !user.suspended(), "desktop");
                return Boolean.TRUE;
            }, done -> {
                context.status(text("console.saved"));
                focus = user.id();
                refresh();
            }));
            detail.getChildren().add(moderate);
        }
    }

    private Node fact(String label, String value) {
        return new VBox(2, Components.label(label, "caption"), Components.wrapped(value));
    }
}
