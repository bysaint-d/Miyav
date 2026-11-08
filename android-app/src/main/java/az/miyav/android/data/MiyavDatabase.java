package az.miyav.android.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {SyncOutboxEntry.class}, version = 2, exportSchema = false)
public abstract class MiyavDatabase extends RoomDatabase {
    public abstract SyncOutboxDao outbox();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sync_outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");
        }
    };
}
