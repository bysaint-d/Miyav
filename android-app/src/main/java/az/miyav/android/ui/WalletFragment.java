package az.miyav.android.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.DialogWithdrawBinding;
import az.miyav.android.databinding.FragmentWalletBinding;
import az.miyav.android.databinding.ItemWithdrawalBinding;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.payout.PayoutDetails;
import az.miyav.domain.wallet.Wallet;
import az.miyav.domain.wallet.Withdrawal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * "MIYAV hesabım": what the walker earned, what can be withdrawn, and every withdrawal with its receipt once
 * an administrator has paid it.
 */
public class WalletFragment extends Fragment {
    private FragmentWalletBinding binding;
    private Loaded loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalletBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.refresh.setColorSchemeResources(R.color.text_on_brand);
        binding.refresh.setProgressBackgroundColorSchemeResource(R.color.brand_coral);
        binding.refresh.setOnRefreshListener(this::load);
        binding.payoutButton.setOnClickListener(view -> Ui.nav(this).navigate(R.id.action_global_payout));
        binding.withdrawButton.setOnClickListener(view -> withdraw());
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
        if (loaded == null && !binding.refresh.isRefreshing()) {
            States.loading(binding.state);
        }
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            AuthSession session = app.auth().requireSession();
            CompletableFuture<Wallet> wallet = Ui.async(() -> app.wallets().wallet(session));
            CompletableFuture<List<Withdrawal>> history = Ui.async(() -> app.wallets().mine(session));
            CompletableFuture<Optional<PayoutDetails>> payout = Ui.async(() -> app.payouts().mine(session));
            return new Loaded(Ui.join(wallet), Ui.join(history), Ui.join(payout).orElse(null));
        }, result -> {
            binding.refresh.setRefreshing(false);
            loaded = result;
            show(result);
        }, code -> {
            binding.refresh.setRefreshing(false);
            if (loaded == null) {
                States.message(binding.state, Codes.text(code), this::load);
            } else {
                Ui.snack(binding.getRoot(), code);
            }
        });
    }

    private void show(Loaded loaded) {
        States.hide(binding.state);
        binding.content.setVisibility(View.VISIBLE);
        Wallet wallet = loaded.wallet;
        binding.availableView.setText(getString(R.string.profile_stat_money, Labels.money(wallet.available())));
        String summary = getString(R.string.wallet_summary, Labels.money(wallet.earned()),
                Labels.money(wallet.pending()), Labels.money(wallet.withdrawn()), wallet.completedWalks());
        if (wallet.held().signum() > 0) {
            summary += "\n" + getString(R.string.wallet_held, Labels.money(wallet.held()));
        }
        binding.summaryView.setText(summary);
        boolean ready = loaded.payout != null && loaded.payout.agreementCurrent();
        binding.withdrawButton.setEnabled(wallet.canWithdraw());
        binding.hintView.setText(!ready ? getString(R.string.wallet_hint_payout)
                : wallet.pending().signum() > 0 ? getString(R.string.wallet_hint_pending)
                : wallet.available().compareTo(wallet.minimum()) < 0
                ? getString(R.string.wallet_hint_minimum, Labels.money(wallet.minimum()))
                : getString(R.string.wallet_hint_fee, Labels.rating(wallet.feePercent()).replace(".0", "")));
        binding.historyEmpty.setVisibility(loaded.history.isEmpty() ? View.VISIBLE : View.GONE);
        binding.historyList.removeAllViews();
        for (Withdrawal withdrawal : loaded.history) {
            binding.historyList.addView(row(withdrawal));
        }
    }

    private View row(Withdrawal withdrawal) {
        ItemWithdrawalBinding item = ItemWithdrawalBinding.inflate(getLayoutInflater(), binding.historyList, false);
        item.amountView.setText(getString(R.string.profile_stat_money, Labels.money(withdrawal.amount())));
        item.statusView.setText(status(withdrawal.status()));
        item.statusView.setTextColor(ContextCompat.getColor(requireContext(), switch (withdrawal.status()) {
            case PAID -> R.color.success;
            case PENDING -> R.color.brand_coral;
            default -> R.color.text_secondary;
        }));
        String detail = getString(R.string.wallet_row_detail, Labels.date(withdrawal.createdAt()),
                Labels.money(withdrawal.net()), withdrawal.maskedIban());
        if (withdrawal.status() == Withdrawal.Status.REJECTED && !withdrawal.note().isBlank()) {
            detail += "\n" + withdrawal.note();
        }
        item.detailView.setText(detail);
        if (withdrawal.paid()) {
            item.actionButton.setVisibility(View.VISIBLE);
            item.actionButton.setText(R.string.wallet_receipt);
            item.actionButton.setOnClickListener(view -> receipt(withdrawal));
        } else if (withdrawal.pending()) {
            item.actionButton.setVisibility(View.VISIBLE);
            item.actionButton.setText(R.string.wallet_cancel);
            item.actionButton.setOnClickListener(view -> cancel(withdrawal));
        }
        return item.getRoot();
    }

    private String status(Withdrawal.Status status) {
        return getString(switch (status) {
            case PENDING -> R.string.wallet_status_pending;
            case PAID -> R.string.wallet_status_paid;
            case REJECTED -> R.string.wallet_status_rejected;
            case CANCELLED -> R.string.wallet_status_cancelled;
        });
    }

    private void withdraw() {
        if (loaded == null) {
            return;
        }
        if (loaded.payout == null || !loaded.payout.agreementCurrent()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.wallet_hint_payout)
                    .setPositiveButton(R.string.profile_contract_bank, (dialog, which) -> Ui.nav(this).navigate(R.id.action_global_payout))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        Wallet wallet = loaded.wallet;
        DialogWithdrawBinding dialog = DialogWithdrawBinding.inflate(getLayoutInflater());
        dialog.amountLayout.setHelperText(getString(R.string.wallet_amount_hint, Labels.money(wallet.minimum()),
                Labels.money(wallet.available())));
        dialog.amountInput.setText(Labels.money(wallet.available()));
        dialog.accountView.setText(getString(R.string.wallet_to_account, loaded.payout.bankName(),
                PayoutDetails.groupedIban(loaded.payout.iban())));
        Runnable breakdown = () -> {
            BigDecimal amount = amount(dialog);
            dialog.breakdownView.setText(amount == null ? "" : getString(R.string.wallet_breakdown,
                    Labels.rating(wallet.feePercent()).replace(".0", ""), Labels.money(wallet.feeFor(amount)),
                    Labels.money(wallet.netFor(amount))));
        };
        dialog.amountInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                dialog.amountLayout.setError(null);
                breakdown.run();
            }
        });
        breakdown.run();
        androidx.appcompat.app.AlertDialog shown = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wallet_withdraw)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.wallet_send_request, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        shown.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            BigDecimal amount = amount(dialog);
            if (amount == null || amount.compareTo(wallet.minimum()) < 0) {
                dialog.amountLayout.setError(getString(R.string.withdrawal_error_minimum, Labels.money(wallet.minimum())));
                return;
            }
            if (amount.compareTo(wallet.available()) > 0) {
                dialog.amountLayout.setError(getString(R.string.withdrawal_error_balance));
                return;
            }
            shown.dismiss();
            act(app -> app.wallets().request(app.auth().requireSession(), wallet, amount), R.string.wallet_requested);
        });
    }

    private static BigDecimal amount(DialogWithdrawBinding dialog) {
        try {
            String text = Ui.text(dialog.amountInput).trim().replace(',', '.');
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void cancel(Withdrawal withdrawal) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.wallet_cancel_confirm)
                .setPositiveButton(R.string.wallet_cancel, (dialog, which) -> act(app -> {
                    app.wallets().cancel(app.auth().requireSession(), withdrawal);
                    return Boolean.TRUE;
                }, R.string.wallet_cancelled))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The receipt the walker can keep or share: who was paid, how much, the fee and the bank reference. */
    private void receipt(Withdrawal withdrawal) {
        String text = getString(R.string.wallet_receipt_text, withdrawal.receiptNumber(),
                withdrawal.processedAt().map(Labels::date).orElse("—"), withdrawal.holderName(), withdrawal.bankName(),
                withdrawal.maskedIban(), Labels.money(withdrawal.amount()),
                Labels.rating(withdrawal.feePercent()).replace(".0", ""), Labels.money(withdrawal.fee()),
                Labels.money(withdrawal.net()), withdrawal.transferReference());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.wallet_receipt_title, withdrawal.receiptNumber()))
                .setMessage(text)
                .setPositiveButton(R.string.wallet_share, (dialog, which) -> Contact.share(requireContext(), text))
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private <T> void act(java.util.function.Function<MiyavApplication, T> work, int done) {
        binding.withdrawButton.setEnabled(false);
        Ui.run(this, () -> work.apply(Ui.app(this)), ok -> {
            Ui.snack(binding.getRoot(), done);
            load();
        }, code -> {
            Ui.snack(binding.getRoot(), code);
            load();
        });
    }

    private static final class Loaded {
        final Wallet wallet;
        final List<Withdrawal> history;
        final PayoutDetails payout;

        Loaded(Wallet wallet, List<Withdrawal> history, PayoutDetails payout) {
            this.wallet = wallet;
            this.history = history;
            this.payout = payout;
        }
    }
}
