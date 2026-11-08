package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;

import az.miyav.android.R;
import az.miyav.android.databinding.FragmentSplashBinding;

public class SplashFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return FragmentSplashBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.run(this, () -> Ui.app(this).auth().restore(), state -> openHome(), code -> openHome());
    }

    /** Guest or signed in, the app always opens on Home; sign-in is offered only when an action needs it. */
    private void openHome() {
        NavController nav = Ui.nav(this);
        NavDestination current = nav.getCurrentDestination();
        if (current != null && current.getId() == R.id.splashFragment) {
            nav.navigate(R.id.action_global_home);
        }
    }
}
