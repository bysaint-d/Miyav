package az.miyav.desktop.admin;

import az.miyav.domain.admin.ActivityItem;
import az.miyav.domain.admin.CityActivity;
import az.miyav.domain.admin.DailyCount;
import az.miyav.domain.admin.PlatformOverview;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Community health at a glance: members, pets, walking, NearMe and adoption, from live counts. */
public final class OverviewSection extends AdminSection {
    private final VBox root = new VBox(20);
    private final GridPane stats = new GridPane();
    private final VBox activityCard = new VBox();
    private final VBox requestsCard = new VBox();
    private final VBox citiesCard = new VBox();
    private final VBox recentUsers = new VBox(10);
    private final VBox recentWalks = new VBox(10);
    private final VBox recentListings = new VBox(10);
    private final Label heroLine = Components.wrapped("", "page-subtitle");

    public OverviewSection(ConsoleContext context) {
        super(context);
    }

    @Override
    public String id() {
        return "overview";
    }

    @Override
    public String icon() {
        return "◎";
    }

    @Override
    public String titleKey() {
        return "console.overview";
    }

    @Override
    public String subtitleKey() {
        return "console.overview_subtitle";
    }

    @Override
    protected Node build() {
        VBox hero = new VBox(6, Components.label(text("console.hero_title"), "page-title"), heroLine);
        hero.getStyleClass().add("hero");
        GridPane charts = new GridPane();
        charts.setHgap(16);
        charts.setVgap(16);
        charts.add(card(text("console.chart_activity"), activityCard), 0, 0);
        charts.add(card(text("console.chart_requests"), requestsCard), 1, 0);
        charts.add(card(text("console.recent_users"), recentUsers), 0, 1);
        charts.add(card(text("console.recent_walks"), recentWalks), 1, 1);
        charts.add(card(text("console.recent_listings"), recentListings), 0, 2);
        charts.add(card(text("console.chart_cities"), citiesCard), 1, 2);
        javafx.scene.layout.ColumnConstraints half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        charts.getColumnConstraints().addAll(half, half);
        root.getChildren().addAll(hero, stats, charts);
        return root;
    }

    @Override
    public void refresh() {
        context.load(() -> context.insights().overview(context.session()), this::show);
    }

    private void show(PlatformOverview overview) {
        PlatformOverview.Counts counts = overview.counts();
        heroLine.setText(text("console.hero_line", counts.users(), counts.walks(), overview.activeListings()));
        Components.fillGrid(stats, List.of(
                Components.stat("👥", text("console.stat_members"), String.valueOf(counts.users()),
                        text("console.stat_members_hint", counts.newUsersThisWeek())),
                Components.stat("✦", text("console.stat_active"), String.valueOf(counts.activeUsers()),
                        text("console.stat_active_hint")),
                Components.stat("🐾", text("console.stat_pets"), String.valueOf(counts.pets()),
                        text("console.stat_pets_hint")),
                Components.stat("🐕", text("console.stat_requests"), String.valueOf(overview.walkingRequests()),
                        text("console.stat_requests_hint", counts.upcomingRequests())),
                Components.stat("📍", text("console.stat_walks"), String.valueOf(counts.walks()),
                        text("console.stat_walks_hint", counts.liveWalks())),
                Components.stat("🏡", text("console.stat_adoption"), String.valueOf(overview.activeListings()),
                        text("console.stat_adoption_hint", overview.completedAdoptions()))), 3);
        activityCard.getChildren().setAll(activityChart(overview.registrations(), overview.walks()));
        requestsCard.getChildren().setAll(requestsChart(overview.requestsByStatus()));
        citiesCard.getChildren().setAll(citiesChart(overview.cities()));
        feed(recentUsers, overview.recentUsers(), "👤", "console.empty_recent_users");
        feed(recentWalks, overview.recentWalks(), "🐕", "console.empty_recent_walks");
        feed(recentListings, overview.recentListings(), "🏡", "console.empty_recent_listings");
    }

    private Node activityChart(List<DailyCount> registrations, List<DailyCount> walks) {
        CategoryAxis days = new CategoryAxis();
        NumberAxis values = new NumberAxis();
        values.setMinorTickVisible(false);
        values.setTickUnit(1);
        LineChart<String, Number> chart = new LineChart<>(days, values);
        chart.setCreateSymbols(true);
        chart.setAnimated(false);
        chart.setPrefHeight(240);
        chart.getData().add(series(text("console.series_registrations"), registrations));
        chart.getData().add(series(text("console.series_walks"), walks));
        return chart;
    }

    private XYChart.Series<String, Number> series(String name, List<DailyCount> points) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("d MMM", context.locale());
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (DailyCount point : points) {
            series.getData().add(new XYChart.Data<>(format.format(point.day()), point.count()));
        }
        return series;
    }

    private Node requestsChart(Map<String, Integer> byStatus) {
        if (byStatus.isEmpty()) {
            return Components.empty("🐕", text("console.empty_requests_title"), text("console.empty_requests_body"));
        }
        PieChart chart = new PieChart();
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setLabelsVisible(false);
        chart.setPrefHeight(240);
        byStatus.forEach((status, count) -> chart.getData().add(new PieChart.Data(text("walk.status." + status) + " · " + count, count)));
        return chart;
    }

    private Node citiesChart(List<CityActivity> cities) {
        if (cities.isEmpty()) {
            return Components.empty("🗺", text("console.empty_cities_title"), text("console.empty_cities_body"));
        }
        BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        chart.setAnimated(false);
        chart.setPrefHeight(240);
        chart.setCategoryGap(28);
        XYChart.Series<String, Number> members = new XYChart.Series<>();
        members.setName(text("console.series_members"));
        XYChart.Series<String, Number> listings = new XYChart.Series<>();
        listings.setName(text("console.series_listings"));
        XYChart.Series<String, Number> helpers = new XYChart.Series<>();
        helpers.setName(text("console.series_helpers"));
        for (CityActivity city : cities) {
            members.getData().add(new XYChart.Data<>(city.city(), city.users()));
            listings.getData().add(new XYChart.Data<>(city.city(), city.listings()));
            helpers.getData().add(new XYChart.Data<>(city.city(), city.helpers()));
        }
        chart.setData(FXCollections.observableArrayList(List.of(members, listings, helpers)));
        return chart;
    }

    private void feed(VBox target, List<ActivityItem> items, String glyph, String emptyKey) {
        target.getChildren().clear();
        if (items.isEmpty()) {
            target.getChildren().add(Components.wrapped(text(emptyKey), "muted"));
            return;
        }
        for (ActivityItem item : items) {
            Label title = Components.label(item.title().isEmpty() ? "—" : item.title(), "cell-title");
            Label detail = Components.label(item.detail(), "caption");
            VBox lines = new VBox(2, title, detail);
            HBox.setHgrow(lines, Priority.ALWAYS);
            HBox line = Components.row(12, Components.avatar(glyph, 36), lines,
                    Components.badge(statusLabel(item), tone(item)),
                    Components.label(Components.ago(item.at(), context.now(), context), "caption"));
            target.getChildren().add(line);
        }
    }

    private String statusLabel(ActivityItem item) {
        return switch (item.kind()) {
            case REGISTRATION -> text("role." + item.status());
            case WALK -> text("walk.session." + item.status());
            case ADOPTION -> text("listing.status." + item.status());
        };
    }

    private static Components.Tone tone(ActivityItem item) {
        return switch (item.status()) {
            case "active", "admin" -> Components.Tone.MINT;
            case "adopted", "sitter" -> Components.Tone.SAGE;
            case "paused", "draft" -> Components.Tone.WARNING;
            case "deleted" -> Components.Tone.DANGER;
            default -> Components.Tone.NEUTRAL;
        };
    }

    private static VBox card(String title, VBox content) {
        VBox card = Components.card(title, content);
        card.setPadding(new Insets(20));
        card.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(card, Priority.ALWAYS);
        GridPane.setFillWidth(card, true);
        return card;
    }
}
