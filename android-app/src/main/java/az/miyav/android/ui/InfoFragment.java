package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentInfoBinding;

/** Public help, terms of service and privacy policy pages, readable without an account. */
public class InfoFragment extends Fragment {
    static final String HELP = "help";
    static final String TERMS = "terms";
    static final String PRIVACY = "privacy";

    static void open(Fragment from, String kind) {
        Bundle args = new Bundle();
        args.putString("kind", kind);
        Ui.nav(from).navigate(R.id.action_global_info, args);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentInfoBinding binding = FragmentInfoBinding.inflate(inflater, container, false);
        String kind = requireArguments().getString("kind", HELP);
        binding.toolbar.setTitle(switch (kind) {
            case TERMS -> R.string.legal_terms;
            case PRIVACY -> R.string.legal_privacy;
            default -> R.string.legal_help;
        });
        binding.bodyView.setText(switch (kind) {
            case TERMS -> R.string.legal_terms_body;
            case PRIVACY -> R.string.legal_privacy_body;
            default -> R.string.legal_help_body;
        });
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        return binding.getRoot();
    }
}
