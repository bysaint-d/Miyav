package az.miyav.android.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentWalkHistoryBinding;
import az.miyav.android.databinding.ItemWalkHistoryBinding;
import az.miyav.application.WalkingService;
import az.miyav.domain.walk.WalkHistoryEntry;
import az.miyav.domain.walk.WalkStats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The member's past walks with weekly totals, longest walk and daily streak; each opens its route. */
public class WalkHistoryFragment extends Fragment {
    private FragmentWalkHistoryBinding binding;
    private final Walks adapter = new Walks();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalkHistoryBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.walkList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.walkList.setAdapter(adapter);
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnRefreshListener(this::load);
        renderStats(WalkingService.stats(List.of(), Instant.now()));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (adapter.getItemCount() == 0 && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkingService().history(app.auth().requireSession());
        }, walks -> {
            binding.refresh.setRefreshing(false);
            adapter.replace(walks);
            renderStats(WalkingService.stats(walks, Instant.now()));
            if (walks.isEmpty()) {
                States.message(binding.state, R.string.walk_history_empty, null);
            } else {
                States.hide(binding.state);
            }
        }, code -> {
            binding.refresh.setRefreshing(false);
            States.message(binding.state, Codes.text(code), this::load);
        });
    }

    private void renderStats(WalkStats stats) {
        Context context = requireContext();
        binding.weekDistance.setText(km(context, stats.weekMeters()));
        binding.weekLabel.setText(getResources().getQuantityString(R.plurals.walk_stats_week, stats.weekWalks(), stats.weekWalks()));
        binding.weekTime.setText(clock(stats.weekTime()));
        binding.totalView.setText(getString(R.string.walk_stats_total, km(context, stats.totalMeters()), stats.totalWalks()));
        binding.longestView.setText(getString(R.string.walk_stats_longest, km(context, stats.longestMeters())));
        binding.streakView.setText(getResources().getQuantityString(R.plurals.walk_stats_streak, stats.streakDays(), stats.streakDays()));
    }

    static String km(Context context, double meters) {
        return context.getString(R.string.walk_km, meters / 1000.0);
    }

    /** "1:05" for an hour and five minutes, "45 dəq" below an hour. */
    static String clock(Duration duration) {
        long minutes = duration.toMinutes();
        return minutes < 60 ? minutes + "′" : String.format(java.util.Locale.US, "%d:%02d", minutes / 60, minutes % 60);
    }

    private void open(WalkHistoryEntry walk) {
        Bundle args = new Bundle();
        args.putString("walkId", walk.id().toString());
        Ui.nav(this).navigate(R.id.action_global_walk_route, args);
    }

    private final class Walks extends RecyclerView.Adapter<Walks.Holder> {
        private final List<WalkHistoryEntry> rows = new ArrayList<>();

        void replace(List<WalkHistoryEntry> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemWalkHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            WalkHistoryEntry walk = rows.get(position);
            Context context = holder.itemView.getContext();
            ItemWalkHistoryBinding view = holder.binding;
            view.emojiView.setText(walk.species() == null ? "🐾" : Labels.speciesEmoji(walk.species()));
            view.titleView.setText(walk.petName().isBlank() ? getString(R.string.walk_history_untitled) : walk.petName());
            view.dateView.setText(Labels.date(walk.startedAt()));
            view.distanceView.setText(km(context, walk.distanceMeters()));
            Duration duration = walk.duration(Instant.now());
            view.durationView.setText(walk.open() ? getString(R.string.walk_history_now) : clock(duration));
            boolean route = walk.hasRoute();
            view.routeView.setText(route ? R.string.walk_history_route : R.string.walk_history_no_route);
            view.routeView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                    route ? R.color.pastel_success : R.color.surface_muted)));
            view.routeView.setTextColor(ContextCompat.getColor(context, route ? R.color.success : R.color.text_secondary));
            holder.itemView.setOnClickListener(card -> open(walk));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemWalkHistoryBinding binding;

            Holder(ItemWalkHistoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
