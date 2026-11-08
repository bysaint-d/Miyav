package az.miyav.desktop.admin;

import az.miyav.domain.moderation.Report;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Reports sent by members about other members, with resolve and quick access to both profiles. */
public final class ReportsSection extends AdminSection {
    private final ToggleGroup filter = new ToggleGroup();
    private final VBox list = new VBox(12);
    private List<Report> reports = List.of();

    public ReportsSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "reports";
    }

    @Override
    public String icon() {
        return "⚑";
    }

    @Override
    public String titleKey() {
        return "console.reports";
    }

    @Override
    public String subtitleKey() {
        return "console.reports_subtitle";
    }

    @Override
    protected Node build() {
        return new VBox(16, Components.row(8, chip("console.reports_open", true), chip("console.reports_resolved", false),
                chip("console.filter_all", null)), list);
    }

    private ToggleButton chip(String key, Boolean open) {
        ToggleButton chip = new ToggleButton(text(key));
        chip.getStyleClass().add("chip");
        chip.setUserData(open);
        chip.setToggleGroup(filter);
        chip.setSelected(Boolean.TRUE.equals(open));
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
        context.members(members -> render());
        context.load(() -> context.graph().admin().reports(context.session()), loaded -> {
            reports = loaded;
            render();
        });
    }

    private void render() {
        Object open = filter.getSelectedToggle() == null ? null : filter.getSelectedToggle().getUserData();
        list.getChildren().clear();
        for (Report report : reports) {
            if (open == null || open.equals(!report.resolved())) {
                list.getChildren().add(card(report));
            }
        }
        if (list.getChildren().isEmpty()) {
            list.getChildren().add(Components.card(Components.empty("⚑", text("console.empty_reports_title"),
                    text("console.empty_reports_body"))));
        }
    }

    private Node card(Report report) {
        String reporter = context.memberName(report.reporterId());
        String reported = context.memberName(report.reportedUserId());
        VBox text = new VBox(4,
                Components.label(text("console.report_line", reporter.isEmpty() ? "—" : reporter, reported.isEmpty() ? "—" : reported), "cell-title"),
                Components.wrapped(report.reason(), "muted"),
                Components.label(Components.dateTime(report.createdAt(), context.locale()), "caption"));
        HBox.setHgrow(text, Priority.ALWAYS);
        Button profile = new Button(text("console.open_reported"));
        profile.getStyleClass().add("ghost");
        profile.setOnAction(event -> context.open("users", report.reportedUserId()));
        HBox row = Components.row(14, Components.avatar("⚑", 40), text,
                Components.badge(text(report.resolved() ? "console.reports_resolved" : "console.reports_open"),
                        report.resolved() ? Components.Tone.NEUTRAL : Components.Tone.WARNING), profile);
        if (!report.resolved()) {
            Button resolve = new Button(text("admin.resolve"));
            resolve.getStyleClass().add("primary");
            resolve.setOnAction(event -> context.load(() -> {
                context.graph().admin().resolveReport(context.session(), report.id(), "desktop");
                return Boolean.TRUE;
            }, done -> {
                context.status(text("console.saved"));
                refresh();
            }));
            row.getChildren().add(resolve);
        }
        VBox card = Components.card(row);
        return card;
    }
}
