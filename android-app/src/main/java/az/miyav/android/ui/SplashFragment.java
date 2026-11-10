package az.miyav.android.ui;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentSplashBinding;

/**
 * A short branded intro (about 0.6 s). The stored session is already adopted at start-up, so Home opens
 * without waiting for the network; the server check finishes in the background and an expired session
 * quietly falls back to guest browsing.
 */
public class SplashFragment extends Fragment {
    private static final long INTRO_MS = 620;
    private FragmentSplashBinding binding;
    private final Runnable open = this::openHome;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MiyavApplication app = Ui.app(this);
        app.io().execute(() -> app.auth().restore());
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.post(open);
            return;
        }
        animateIntro();
        view.postDelayed(open, INTRO_MS);
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.getRoot().removeCallbacks(open);
        }
        binding = null;
        super.onDestroyView();
    }

    private void animateIntro() {
        float rise = 14 * getResources().getDisplayMetrics().density;
        binding.logoView.setScaleX(0.72f);
        binding.logoView.setScaleY(0.72f);
        binding.logoView.setRotation(-8f);
        binding.logoView.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        binding.haloView.setScaleX(0.6f);
        binding.haloView.setScaleY(0.6f);
        binding.haloView.setAlpha(0.7f);
        binding.haloView.animate().scaleX(1.7f).scaleY(1.7f).alpha(0f).setStartDelay(80).setDuration(560)
                .setInterpolator(new DecelerateInterpolator()).start();
        binding.nameView.setTranslationY(rise);
        binding.nameView.animate().alpha(1f).translationY(0f).setStartDelay(140).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
        binding.taglineView.setTranslationY(rise);
        binding.taglineView.animate().alpha(1f).translationY(0f).setStartDelay(230).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Guest or signed in, the app always opens on Home; sign-in is offered only when an action needs it. */
    private void openHome() {
        if (binding == null) {
            return;
        }
        NavController nav = Ui.nav(this);
        NavDestination current = nav.getCurrentDestination();
        if (current != null && current.getId() == R.id.splashFragment) {
            nav.navigate(R.id.action_global_home);
        }
    }
}
