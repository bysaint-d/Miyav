package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentMyWalksBinding;
import az.miyav.domain.walk.MyWalks;
import az.miyav.domain.walk.WalkListing;

import java.time.Instant;
import java.util.List;

/**
 * "Gəzintilərim": where every walk stands from the member's side. Agreed walks, the offers they sent with the
 * owner's answer, and their own requests with the number of offers waiting for a decision.
 */
public class MyWalksFragment extends Fragment {
    static final String ARG_TAB = "tab";
    static final String TAB_AGREED = "agreed";
    static final String TAB_OFFERS = "offers";
    static final String TAB_REQUESTS = "requests";
    private static final String ARG_HOME_TAB = "homeTab";
    private static final String STATE_TAB = "my_walks_tab";
    private FragmentMyWalksBinding binding;
    private WalkListingAdapter adapter;
    private MyWalks walks;
    private int tab = R.id.tabAgreed;

    static void open(Fragment from, String tab) {
        Bundle args = new Bundle();
        args.putString(ARG_TAB, tab);
        Ui.nav(from).navigate(R.id.action_global_my_walks, args);
    }

    /** "İşlərim" in the bottom navigation: no back arrow, opens on the jobs the member posted. */
    static MyWalksFragment asTab() {
        MyWalksFragment fragment = new MyWalksFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TAB, TAB_REQUESTS);
        args.putBoolean(ARG_HOME_TAB, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMyWalksBinding.inflate(inflater, container, false);
        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt(STATE_TAB, tab);
        } else if (walks == null && getArguments() != null) {
            tab = switch (getArguments().getString(ARG_TAB, TAB_AGREED)) {
                case TAB_OFFERS -> R.id.tabOffers;
                case TAB_REQUESTS -> R.id.tabRequests;
                default -> R.id.tabAgreed;
            };
        }
        if (getArguments() != null && getArguments().getBoolean(ARG_HOME_TAB, false)) {
            binding.toolbar.setNavigationIcon(null);
            binding.toolbar.setTitle(R.string.nav_my_jobs);
        } else {
            binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        }
        adapter = new WalkListingAdapter(Ui.app(this), this::openWalk);
        binding.walkList.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.walkList.setAdapter(adapter);
        binding.tabGroup.check(tab);
        binding.tabGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                tab = checkedId;
                render();
            }
        });
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnRefreshListener(this::load);
        binding.newButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_walk_editor));
        render();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, tab);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (walks == null && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.walkListings().myWalks(app.auth().requireSession());
        }, loaded -> {
            binding.refresh.setRefreshing(false);
            walks = loaded;
            render();
        }, code -> {
            binding.refresh.setRefreshing(false);
            if (walks == null) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void render() {
        binding.newButton.setVisibility(tab == R.id.tabRequests ? View.VISIBLE : View.GONE);
        if (walks == null) {
            return;
        }
        Instant now = Instant.now();
        List<WalkListing> shown;
        int empty;
        if (tab == R.id.tabOffers) {
            shown = walks.sentOffers();
            empty = R.string.my_walks_empty_offers;
        } else if (tab == R.id.tabRequests) {
            shown = walks.requests(now);
            empty = R.string.my_walks_empty_requests;
        } else {
            shown = walks.agreements(now);
            empty = R.string.my_walks_empty_agreed;
        }
        adapter.stages(walks);
        adapter.replace(shown);
        if (shown.isEmpty()) {
            States.message(binding.state, empty, null);
        } else {
            States.hide(binding.state);
        }
    }

    private void openWalk(WalkListing listing) {
        Bundle args = new Bundle();
        args.putString("listingId", listing.id().toString());
        Ui.nav(this).navigate(R.id.action_global_walk_detail, args);
    }
}
