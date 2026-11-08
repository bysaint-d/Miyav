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
import az.miyav.domain.auth.AuthSession;

import java.util.Optional;

public class SplashFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return FragmentSplashBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.run(this, () -> {
            try {
                return Ui.app(this).authService().restore();
            } catch (RuntimeException exception) {
                return Optional.<AuthSession>empty();
            }
        }, session -> {
            NavController nav = Ui.nav(this);
            NavDestination current = nav.getCurrentDestination();
            if (current == null || current.getId() != R.id.splashFragment) {
                return;
            }
            nav.navigate(session.isPresent() ? R.id.action_global_home : R.id.action_global_login);
        }, code -> Ui.nav(this).navigate(R.id.action_global_login));
    }
}
