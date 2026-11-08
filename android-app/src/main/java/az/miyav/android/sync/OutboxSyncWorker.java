package az.miyav.android.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import az.miyav.android.MiyavApplication;
import az.miyav.android.data.SyncOutboxEntry;
import az.miyav.common.error.AppException;
import az.miyav.common.error.PersistenceException;
import az.miyav.domain.auth.AuthSession;
import az.miyav.domain.pet.Pet;
import az.miyav.domain.user.User;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class OutboxSyncWorker extends Worker {
    public static final String APPLY_ADOPTION = "APPLY_ADOPTION";

    public OutboxSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueueNow(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "miyav-outbox-now",
                ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(OutboxSyncWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()
        );
    }

    @NonNull
    @Override
    public Result doWork() {
        MiyavApplication application = (MiyavApplication) getApplicationContext();
        List<SyncOutboxEntry> pending = application.database().outbox().pending();
        if (pending.isEmpty()) {
            return Result.success();
        }
        if (application.authService().current().isEmpty()) {
            return Result.success();
        }
        boolean retry = false;
        for (SyncOutboxEntry entry : pending) {
            try {
                process(application, entry);
                application.database().outbox().delete(entry.id);
            } catch (AppException exception) {
                if (OutboxPolicy.afterFailure(exception.code(), entry.attempts) == OutboxPolicy.Decision.RETRY) {
                    application.database().outbox().incrementAttempts(entry.id);
                    retry = true;
                } else {
                    application.database().outbox().delete(entry.id);
                }
            }
        }
        return retry ? Result.retry() : Result.success();
    }

    private void process(MiyavApplication application, SyncOutboxEntry entry) {
        OutboxPolicy.Adoption adoption = APPLY_ADOPTION.equals(entry.operation)
                ? OutboxPolicy.decodeAdoption(entry.payload)
                : null;
        if (adoption == null) {
            return;
        }
        AuthSession session = application.authService().requireSession();
        User applicant = application.userService().current(session);
        Pet pet = application.petService().find(adoption.listingId, session)
                .orElseThrow(() -> new PersistenceException("adoption.listing_unavailable"));
        application.adoptionService().submit(session, applicant, pet, adoption.message);
    }
}
