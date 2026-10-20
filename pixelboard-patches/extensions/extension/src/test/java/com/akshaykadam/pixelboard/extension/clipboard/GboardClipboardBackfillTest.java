package com.akshaykadam.pixelboard.extension.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.PersistableBundle;

import java.io.File;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GboardClipboardBackfillTest {

    private static final String CREATE_CLIPS =
            "CREATE TABLE IF NOT EXISTS clips (_id INTEGER PRIMARY KEY NOT NULL, text TEXT, "
                    + "html_text TEXT, item_type INTEGER NOT NULL, entity_type INTEGER NOT NULL, "
                    + "timestamp INTEGER, sensitive BOOL, uri TEXT, group_id TEXT, "
                    + "UNIQUE(_id, text, item_type) ON CONFLICT REPLACE)";

    private Context context;
    private ClipboardManager clipboardManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        createClipboardDatabase();
    }

    @Test
    public void clipCopiedUnderAnotherKeyboardIsStoredOnReturn() {
        setPrimaryClip("copied under another keyboard", false);

        assertTrue(GboardClipboardBackfill.backfillPrimaryClip(context));

        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);
        assertEquals(1, entries.size());
        assertEquals("copied under another keyboard", entries.get(0).getText());
    }

    @Test
    public void repeatedBackfillDoesNotCreateDuplicates() {
        setPrimaryClip("same clip", false);

        assertTrue(GboardClipboardBackfill.backfillPrimaryClip(context));
        assertFalse(GboardClipboardBackfill.backfillPrimaryClip(context));

        assertEquals(1, GboardClipboardHistoryRepository.loadHistory(context, true, 10).size());
    }

    @Test
    public void backfillDoesNotDuplicateAClipThatGboardAlreadyStored() {
        setPrimaryClip("already stored", false);
        long rowId = GboardClipboardHistoryRepository.insertClip(context, "already stored", 1000L,
                false);
        assertTrue(rowId > 0L);

        assertFalse(GboardClipboardBackfill.backfillPrimaryClip(context));

        assertEquals(1, GboardClipboardHistoryRepository.loadHistory(context, true, 10).size());
    }

    @Test
    public void backfillKeepsTheMarkerWhenAnotherClipBecomesTheNewestRow() {
        setPrimaryClip("first clip", false);
        assertTrue(GboardClipboardBackfill.backfillPrimaryClip(context));

        // Gboard stores an unrelated clip afterwards while the primary clip stays the same.
        GboardClipboardHistoryRepository.insertClip(context, "unrelated clip", 5000L, false);

        assertFalse(GboardClipboardBackfill.backfillPrimaryClip(context));

        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);
        assertEquals(2, entries.size());
    }

    @Test
    public void sensitiveClipsAreStoredWithTheSensitiveFlag() {
        setPrimaryClip("password", true);

        assertTrue(GboardClipboardBackfill.backfillPrimaryClip(context));

        List<GboardClipboardHistoryRepository.ClipboardEntry> visible =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);
        List<GboardClipboardHistoryRepository.ClipboardEntry> all =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);
        assertTrue(visible.isEmpty());
        assertEquals(1, all.size());
        assertTrue(all.get(0).isSensitive());
    }

    @Test
    public void emptyClipboardIsIgnored() {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("label", ""));
        assertFalse(GboardClipboardBackfill.backfillPrimaryClip(context));
    }

    @Test
    public void missingClipboardServiceIsIgnored() {
        assertFalse(GboardClipboardBackfill.backfillPrimaryClip(null));
    }

    @Test
    public void markerIsDerivedFromTheContentOnly() {
        String marker = GboardClipboardBackfill.buildMarker("hello");

        assertNotNull(marker);
        assertEquals(64, marker.length());
        assertEquals(marker, GboardClipboardBackfill.buildMarker("hello"));
        assertFalse(marker.equals(GboardClipboardBackfill.buildMarker("hello world")));
    }

    private void setPrimaryClip(String text, boolean sensitive) {
        ClipData clip = ClipData.newPlainText("label", text);
        if (sensitive) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboardManager.setPrimaryClip(clip);
    }

    private void createClipboardDatabase() {
        File file = context.getDatabasePath(GboardClipboardHistoryRepository.DATABASE_NAME);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL(CREATE_CLIPS);
        database.close();
    }
}
