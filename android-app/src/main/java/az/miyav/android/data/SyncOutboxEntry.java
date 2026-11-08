package az.miyav.android.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_outbox")
public class SyncOutboxEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String operation;
    public String payload;
    public long createdAtEpochMs;
    public int attempts;
}
