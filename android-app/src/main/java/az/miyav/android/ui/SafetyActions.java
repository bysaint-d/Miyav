package az.miyav.android.ui;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.DialogReportBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.auth.ProtectedAction;
import az.miyav.domain.user.PublicProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Report a member to moderators or block them, and manage the block list. */
final class SafetyActions {
    private SafetyActions() {
    }

    static void open(Fragment fragment, UUID userId, String name) {
        if (!Gate.require(fragment, ProtectedAction.SUBMIT_REPORT)) {
            return;
        }
        Context context = fragment.requireContext();
        String[] choices = {context.getString(R.string.report_title), context.getString(R.string.block_title)};
        new MaterialAlertDialogBuilder(context)
                .setTitle(name)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        report(fragment, userId);
                    } else {
                        confirmBlock(fragment, userId, name);
                    }
                })
                .show();
    }

    private static void report(Fragment fragment, UUID userId) {
        Context context = fragment.requireContext();
        DialogReportBinding form = DialogReportBinding.inflate(LayoutInflater.from(context));
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.report_title)
                .setView(form.getRoot())
                .setPositiveButton(R.string.report_send, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String reason = Ui.text(form.reasonInput).trim();
            if (reason.length() < 10) {
                form.reasonLayout.setError(context.getString(R.string.report_reason));
                return;
            }
            form.reasonLayout.setError(null);
            view.setEnabled(false);
            Ui.run(fragment, () -> {
                MiyavApplication app = Ui.app(fragment);
                app.safety().report(app.auth().requireSession(), userId, reason);
                return Boolean.TRUE;
            }, done -> {
                dialog.dismiss();
                Ui.snack(fragment.requireView(), R.string.report_sent);
            }, code -> {
                view.setEnabled(true);
                form.reasonLayout.setError(context.getString(Codes.text(code)));
            });
        });
    }

    private static void confirmBlock(Fragment fragment, UUID userId, String name) {
        Context context = fragment.requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.block_confirm_title, name))
                .setMessage(R.string.block_confirm_body)
                .setPositiveButton(R.string.block_title, (dialog, which) -> Ui.run(fragment, () -> {
                    MiyavApplication app = Ui.app(fragment);
                    app.safety().block(app.auth().requireSession(), userId);
                    return Boolean.TRUE;
                }, done -> {
                    Ui.snackText(fragment.requireActivity().findViewById(android.R.id.content),
                            context.getString(R.string.blocked_done, name));
                    Ui.nav(fragment).popBackStack();
                }, code -> Ui.snack(fragment.requireView(), code)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The members this user blocked, each one tap away from being unblocked. */
    static void manage(Fragment fragment) {
        Ui.run(fragment, () -> {
            MiyavApplication app = Ui.app(fragment);
            AuthSession session = app.auth().requireSession();
            Set<UUID> ids = app.safety().blocked(session);
            List<Blocked> people = new ArrayList<>();
            for (UUID id : ids) {
                Optional<PublicProfile> profile = app.userService().publicProfile(session, id);
                people.add(new Blocked(id, profile.map(PublicProfile::fullName).orElse(fragment.getString(R.string.publisher_hidden))));
            }
            return people;
        }, people -> {
            Context context = fragment.requireContext();
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context).setTitle(R.string.blocked_list);
            if (people.isEmpty()) {
                builder.setMessage(R.string.blocked_empty).setPositiveButton(android.R.string.ok, null).show();
                return;
            }
            String[] names = people.stream().map(person -> person.name).toArray(String[]::new);
            builder.setItems(names, (dialog, which) -> confirmUnblock(fragment, people.get(which)))
                    .setNegativeButton(R.string.action_close, null)
                    .show();
        }, code -> Ui.snack(fragment.requireView(), code));
    }

    private static void confirmUnblock(Fragment fragment, Blocked person) {
        Context context = fragment.requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.unblock_confirm, person.name))
                .setPositiveButton(R.string.unblock, (dialog, which) -> Ui.run(fragment, () -> {
                    MiyavApplication app = Ui.app(fragment);
                    app.safety().unblock(app.auth().requireSession(), person.id);
                    return Boolean.TRUE;
                }, done -> Ui.snack(fragment.requireView(), R.string.unblocked),
                        code -> Ui.snack(fragment.requireView(), code)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static final class Blocked {
        final UUID id;
        final String name;

        Blocked(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
