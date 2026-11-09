package az.miyav.android.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.DialogPhotoViewerBinding;
import az.miyav.android.databinding.ItemPhotoZoomBinding;

import java.util.ArrayList;
import java.util.List;

/** Full-screen listing photos: swipe between them, pinch or double-tap to zoom. */
public class PhotoViewerDialog extends DialogFragment {
    private static final String PATHS = "paths";
    private static final String START = "start";
    private static final String SOURCE = "source";
    private static final int LISTING = 0;
    private static final int WALK_PET = 1;
    private static final int CHAT = 2;
    private DialogPhotoViewerBinding binding;

    static void open(FragmentManager manager, List<String> paths, int start) {
        show(manager, paths, start, LISTING);
    }

    /** The pet photo of a walk request, which lives in the pets bucket rather than with listings. */
    static void openWalkPet(FragmentManager manager, String path) {
        show(manager, List.of(path), 0, WALK_PET);
    }

    /** A photo sent in a chat, from the private messages bucket. */
    static void openChat(FragmentManager manager, String path) {
        show(manager, List.of(path), 0, CHAT);
    }

    private static void show(FragmentManager manager, List<String> paths, int start, int source) {
        if (paths.isEmpty() || manager.findFragmentByTag("photo-viewer") != null) {
            return;
        }
        Bundle args = new Bundle();
        args.putStringArrayList(PATHS, new ArrayList<>(paths));
        args.putInt(START, start);
        args.putInt(SOURCE, source);
        PhotoViewerDialog dialog = new PhotoViewerDialog();
        dialog.setArguments(args);
        dialog.show(manager, "photo-viewer");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.ThemeOverlay_Miyav_PhotoViewer);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogPhotoViewerBinding.inflate(inflater, container, false);
        List<String> paths = requireArguments().getStringArrayList(PATHS);
        int start = Math.max(0, Math.min(requireArguments().getInt(START), paths.size() - 1));
        LinearLayoutManager layout = new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        binding.pager.setLayoutManager(layout);
        binding.pager.setAdapter(new Pages(Ui.app(this), paths, requireArguments().getInt(SOURCE)));
        new PagerSnapHelper().attachToRecyclerView(binding.pager);
        binding.pager.scrollToPosition(start);
        binding.counter.setVisibility(paths.size() > 1 ? View.VISIBLE : View.GONE);
        binding.counter.setText(getString(R.string.format_counter, start + 1, paths.size()));
        binding.pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                int position = layout.findFirstCompletelyVisibleItemPosition();
                if (newState == RecyclerView.SCROLL_STATE_IDLE && position >= 0) {
                    binding.counter.setText(getString(R.string.format_counter, position + 1, paths.size()));
                }
            }
        });
        binding.closeButton.setOnClickListener(view -> dismiss());
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private static final class Pages extends RecyclerView.Adapter<Pages.Holder> {
        private final MiyavApplication app;
        private final List<String> paths;
        private final int source;

        Pages(MiyavApplication app, List<String> paths, int source) {
            this.app = app;
            this.paths = paths;
            this.source = source;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemPhotoZoomBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String path = paths.get(position);
            switch (source) {
                case WALK_PET -> app.photos().walkPetFull(holder.binding.photoView, path);
                case CHAT -> app.photos().chat(holder.binding.photoView, path, 1440, ImageView.ScaleType.MATRIX);
                default -> app.photos().listingFull(holder.binding.photoView, path);
            }
        }

        @Override
        public int getItemCount() {
            return paths.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemPhotoZoomBinding binding;

            Holder(ItemPhotoZoomBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
