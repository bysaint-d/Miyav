package az.miyav.android.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.android.databinding.SheetPremiumBinding;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.premium.PremiumPlan;
import az.miyav.domain.premium.PremiumRequest;

import java.util.UUID;

/**
 * "Make premium": the owner picks M10 or card, copies the number, sends 5 ₼ outside the app, attaches a photo
 * of the receipt and sends it for an administrator to confirm.
 */
public class PremiumSheet extends BottomSheetDialogFragment {
    static final String RESULT = "premium_sent";
    private static final String TAG = "miyav-premium";
    private static final String ARG_LISTING = "listingId";
    private static final int RECEIPT_EDGE = 1800;
    private SheetPremiumBinding binding;
    private Uri receipt;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onReceipt);

    static void open(Fragment host, Pet listing) {
        if (host.getChildFragmentManager().findFragmentByTag(TAG) != null || host.getChildFragmentManager().isStateSaved()) {
            return;
        }
        PremiumSheet sheet = new PremiumSheet();
        Bundle args = new Bundle();
        args.putString(ARG_LISTING, listing.id().toString());
        sheet.setArguments(args);
        Ui.app(host).cache().remember(listing);
        sheet.show(host.getChildFragmentManager(), TAG);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetPremiumBinding.inflate(inflater, container, false);
        binding.subtitleView.setText(getString(R.string.premium_sheet_subtitle, Labels.money(PremiumPlan.PRICE), PremiumPlan.DAYS));
        binding.payTitle.setText(getString(R.string.premium_step_pay, Labels.money(PremiumPlan.PRICE)));
        binding.methodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                renderMethod();
            }
        });
        renderMethod();
        binding.copyButton.setOnClickListener(view -> copy());
        binding.pickButton.setOnClickListener(view -> picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.sendButton.setOnClickListener(view -> send());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private PremiumRequest.Method method() {
        return binding.methodGroup.getCheckedButtonId() == R.id.methodCard ? PremiumRequest.Method.CARD : PremiumRequest.Method.M10;
    }

    private void renderMethod() {
        boolean card = method() == PremiumRequest.Method.CARD;
        binding.destinationLabel.setText(card ? R.string.premium_pay_card : R.string.premium_pay_m10);
        binding.destinationView.setText(card ? PremiumPlan.CARD_NUMBER : PremiumPlan.M10_NUMBER);
        binding.copyButton.setText(R.string.premium_copy);
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("MIYAV", PremiumPlan.destination(method())));
            binding.copyButton.setText(R.string.premium_copied);
        }
    }

    private void onReceipt(Uri uri) {
        if (uri == null || binding == null) {
            return;
        }
        receipt = uri;
        binding.receiptView.setVisibility(View.VISIBLE);
        binding.receiptView.setImageURI(uri);
        binding.pickButton.setText(R.string.premium_change_receipt);
        binding.messageView.setVisibility(View.GONE);
    }

    private void send() {
        if (receipt == null) {
            error(getString(R.string.premium_error_receipt));
            return;
        }
        UUID listingId = UUID.fromString(requireArguments().getString(ARG_LISTING));
        MiyavApplication app = Ui.app(this);
        Pet listing = app.cache().listing(listingId);
        if (listing == null) {
            dismiss();
            return;
        }
        PremiumRequest.Method method = method();
        Uri photo = receipt;
        Context context = requireContext().getApplicationContext();
        busy(true);
        Ui.run(this, () -> {
            byte[] jpeg = JpegEncoder.fromUri(context, photo, RECEIPT_EDGE);
            return app.premium().request(app.auth().requireSession(), listing, method, jpeg);
        }, id -> {
            getParentFragmentManager().setFragmentResult(RESULT, new Bundle());
            dismiss();
        }, code -> {
            busy(false);
            error(getString(Codes.text("validation.pet".equals(code) ? "premium.receipt" : code)));
        });
    }

    private void error(String text) {
        binding.messageView.setText(text);
        binding.messageView.setVisibility(View.VISIBLE);
    }

    private void busy(boolean busy) {
        setCancelable(!busy);
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.sendButton.setEnabled(!busy);
        binding.pickButton.setEnabled(!busy);
        binding.methodGroup.setEnabled(!busy);
    }
}
