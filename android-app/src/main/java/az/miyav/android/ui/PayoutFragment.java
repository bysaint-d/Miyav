package az.miyav.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.FragmentPayoutBinding;
import az.miyav.domain.payout.PayoutDetails;

import java.util.Optional;

/**
 * "Müqavilə və bank məlumatları": where the walker is paid (holder, bank, IBAN) and acceptance of the walker
 * agreement. Saving re-accepts the current agreement version.
 */
public class PayoutFragment extends Fragment {
    private FragmentPayoutBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPayoutBinding.inflate(inflater, container, false);
        binding.toolbar.setNavigationOnClickListener(view -> Ui.nav(this).popBackStack());
        binding.bankInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.payout_banks)));
        binding.agreementButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.payout_section_agreement)
                .setMessage(R.string.payout_agreement_text)
                .setPositiveButton(R.string.payout_agreement_accept_short, (dialog, which) -> binding.agreementCheck.setChecked(true))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        binding.agreementCheck.setOnCheckedChangeListener((button, checked) -> binding.messageView.setText(""));
        binding.saveButton.setOnClickListener(view -> save());
        if (savedInstanceState == null) {
            load();
        }
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            return app.payouts().mine(app.auth().requireSession());
        }, this::fill, code -> {
            busy(false);
            binding.messageView.setText(Codes.text(code));
        });
    }

    private void fill(Optional<PayoutDetails> saved) {
        busy(false);
        if (saved.isEmpty()) {
            return;
        }
        PayoutDetails details = saved.get();
        binding.holderInput.setText(details.holderName());
        binding.bankInput.setText(details.bankName(), false);
        binding.ibanInput.setText(PayoutDetails.groupedIban(details.iban()));
        binding.agreementCheck.setChecked(details.agreementCurrent());
        if (details.agreementAcceptedAt() != null) {
            binding.acceptedView.setVisibility(View.VISIBLE);
            binding.acceptedView.setText(getString(details.agreementCurrent()
                    ? R.string.payout_agreement_accepted_at : R.string.payout_agreement_outdated,
                    Labels.date(details.agreementAcceptedAt())));
        }
    }

    private void save() {
        String holder = Ui.text(binding.holderInput).trim();
        String bank = Ui.text(binding.bankInput).trim();
        String iban = Ui.text(binding.ibanInput);
        boolean accepted = binding.agreementCheck.isChecked();
        binding.holderLayout.setError(null);
        binding.bankLayout.setError(null);
        binding.ibanLayout.setError(null);
        binding.messageView.setText("");
        if (holder.length() < 3 || holder.length() > 100) {
            binding.holderLayout.setError(getString(R.string.payout_error_holder));
            return;
        }
        if (bank.length() < 2 || bank.length() > 80) {
            binding.bankLayout.setError(getString(R.string.payout_error_bank));
            return;
        }
        if (!PayoutDetails.validIban(iban)) {
            binding.ibanLayout.setError(getString(R.string.payout_error_iban));
            return;
        }
        if (!accepted) {
            binding.messageView.setText(R.string.payout_error_agreement);
            return;
        }
        busy(true);
        Ui.run(this, () -> {
            MiyavApplication app = Ui.app(this);
            app.payouts().save(app.auth().requireSession(), holder, bank, iban, true);
            return Boolean.TRUE;
        }, ok -> {
            Ui.snack(requireActivity().findViewById(android.R.id.content), R.string.payout_saved);
            Ui.nav(this).popBackStack();
        }, code -> {
            busy(false);
            switch (code) {
                case "payout.holder" -> binding.holderLayout.setError(getString(Codes.text(code)));
                case "payout.bank" -> binding.bankLayout.setError(getString(Codes.text(code)));
                case "payout.iban" -> binding.ibanLayout.setError(getString(Codes.text(code)));
                default -> binding.messageView.setText(Codes.text(code));
            }
        });
    }

    private void busy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.saveButton.setEnabled(!busy);
    }
}
