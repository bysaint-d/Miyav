package az.miyav.android.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY createdAtEpochMs")
    List<SyncOutboxEntry> pending();

    @Insert
    long insert(SyncOutboxEntry entry);

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    void delete(long id);

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id = :id")
    void incrementAttempts(long id);
}
