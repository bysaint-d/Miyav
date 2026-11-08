package az.miyav.android.ui;

import android.view.View;

import az.miyav.android.databinding.ViewStateBinding;

/** Shows the shared loading / empty / error block. */
final class States {
    private States() {
    }

    static void loading(ViewStateBinding state) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.VISIBLE);
        state.stateMessage.setText("");
        state.stateRetry.setVisibility(View.GONE);
    }

    static void message(ViewStateBinding state, int text, Runnable retry) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.GONE);
        state.stateMessage.setText(text);
        state.stateRetry.setVisibility(retry == null ? View.GONE : View.VISIBLE);
        state.stateRetry.setOnClickListener(retry == null ? null : view -> retry.run());
    }

    static void hide(ViewStateBinding state) {
        state.getRoot().setVisibility(View.GONE);
    }
}
