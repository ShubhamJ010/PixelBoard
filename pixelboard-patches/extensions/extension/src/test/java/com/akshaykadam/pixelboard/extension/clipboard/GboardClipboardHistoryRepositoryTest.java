package com.akshaykadam.pixelboard.extension.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GboardClipboardHistoryRepositoryTest {

    private static final String CREATE_CLIPS =
            "CREATE TABLE IF NOT EXISTS clips (_id INTEGER PRIMARY KEY NOT NULL, text TEXT, "
                    + "html_text TEXT, item_type INTEGER NOT NULL, entity_type INTEGER NOT NULL, "
                    + "timestamp INTEGER, sensitive BOOL, uri TEXT, group_id TEXT, "
                    + "UNIQUE(_id, text, item_type) ON CONFLICT REPLACE)";

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void missingDatabaseReturnsEmptyHistory() {
        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);

        assertNotNull(entries);
        assertTrue(entries.isEmpty());
        assertEquals(0, GboardClipboardHistoryRepository.countHistory(context, false));
    }

    @Test
    public void historyIsReturnedNewestFirst() {
        createClipboardDatabase();

        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);

        assertEquals(2, entries.size());
        assertEquals("newer", entries.get(0).getText());
        assertEquals("older", entries.get(1).getText());
        assertEquals(2000L, entries.get(0).getTimestamp());
        assertEquals(1L, entries.get(0).getId());
    }

    @Test
    public void sensitiveClipsAreHiddenUnlessExplicitlyEnabled() {
        createClipboardDatabase();

        List<GboardClipboardHistoryRepository.ClipboardEntry> visible =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);
        List<GboardClipboardHistoryRepository.ClipboardEntry> all =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);

        assertEquals(2, visible.size());
        assertEquals(3, all.size());
        assertFalse(containsText(visible, "secret"));
        assertTrue(containsText(all, "secret"));
        assertEquals(2, GboardClipboardHistoryRepository.countHistory(context, false));
        assertEquals(3, GboardClipboardHistoryRepository.countHistory(context, true));
    }

    @Test
    public void limitIsHonoured() {
        createClipboardDatabase();

        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, true, 1);

        assertEquals(1, entries.size());
        // The rows are ordered by timestamp, so the sensitive entry is the newest one.
        assertEquals("secret", entries.get(0).getText());
    }

    @Test
    public void imageRowsKeepTheirUriAndHaveNoText() {
        File file = context.getDatabasePath(GboardClipboardHistoryRepository.DATABASE_NAME);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL(CREATE_CLIPS);
        database.execSQL("INSERT OR REPLACE INTO clips VALUES (9, NULL, NULL, 0, 0, 9000, 0,"
                + " 'content://media/external/images/media/42', NULL)");
        database.close();

        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);

        assertEquals(1, entries.size());
        assertEquals("", entries.get(0).getPasteableText());
        assertEquals("content://media/external/images/media/42",
                entries.get(0).getUri());
    }

    @Test
    public void clearHistoryRemovesEveryEntry() {
        createClipboardDatabase();

        assertTrue(GboardClipboardHistoryRepository.clearHistory(context));
        assertTrue(GboardClipboardHistoryRepository.loadHistory(context, true, 10).isEmpty());
    }

    @Test
    public void missingSensitiveColumnIsTolerated() {
        File file = context.getDatabasePath(GboardClipboardHistoryRepository.DATABASE_NAME);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS clips (_id INTEGER PRIMARY KEY NOT NULL, "
                + "text TEXT, timestamp INTEGER)");
        database.execSQL("INSERT INTO clips VALUES (7, 'legacy', 1234)");
        database.close();

        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);

        assertEquals(1, entries.size());
        assertEquals("legacy", entries.get(0).getText());
        assertEquals(7L, entries.get(0).getId());
    }

    @Test
    public void insertClipStoresANewRow() {
        createClipboardDatabase();

        long rowId = GboardClipboardHistoryRepository.insertClip(context, "backfilled", 5000L,
                false);

        assertTrue(rowId > 0L);
        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);
        assertEquals(4, entries.size());
        assertEquals("backfilled", entries.get(0).getText());
        assertEquals(5000L, entries.get(0).getTimestamp());
        assertFalse(entries.get(0).isSensitive());
    }

    @Test
    public void insertClipKeepsTheSensitiveFlag() {
        createClipboardDatabase();

        long rowId = GboardClipboardHistoryRepository.insertClip(context, "secret", 5000L, true);

        assertTrue(rowId > 0L);
        assertEquals(2, GboardClipboardHistoryRepository.loadHistory(context, false, 10).size());
        List<GboardClipboardHistoryRepository.ClipboardEntry> all =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);
        assertEquals(4, all.size());
        assertTrue(all.get(0).isSensitive());
    }

    @Test
    public void insertClipIfAbsentSkipsAnAlreadyStoredPayload() {
        createClipboardDatabase();

        assertEquals(0L, GboardClipboardHistoryRepository.insertClipIfAbsent(
                context, "newer", 5000L, false, 25));
        assertEquals(3, GboardClipboardHistoryRepository.loadHistory(context, true, 10).size());
    }

    @Test
    public void insertClipIfAbsentStoresAMissingPayload() {
        createClipboardDatabase();

        long rowId = GboardClipboardHistoryRepository.insertClipIfAbsent(
                context, "brand new", 5000L, false, 25);

        assertTrue(rowId > 0L);
        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, true, 10);
        assertEquals(4, entries.size());
        assertEquals("brand new", entries.get(0).getText());
    }

    @Test
    public void insertClipRejectsEmptyPayloads() {
        createClipboardDatabase();

        assertEquals(-1L, GboardClipboardHistoryRepository.insertClip(context, null, 5000L, false));
        assertEquals(-1L, GboardClipboardHistoryRepository.insertClip(context, "", 5000L, false));
        assertEquals(-1L, GboardClipboardHistoryRepository.insertClip(null, "text", 5000L, false));
        assertEquals(3, GboardClipboardHistoryRepository.loadHistory(context, true, 10).size());
    }

    @Test
    public void insertClipWorksOnAReducedSchema() {
        File file = context.getDatabasePath(GboardClipboardHistoryRepository.DATABASE_NAME);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS clips (_id INTEGER PRIMARY KEY NOT NULL, "
                + "text TEXT, timestamp INTEGER)");
        database.close();

        long rowId = GboardClipboardHistoryRepository.insertClip(context, "legacy", 1234L, false);

        assertTrue(rowId > 0L);
        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, false, 10);
        assertEquals(1, entries.size());
        assertEquals("legacy", entries.get(0).getText());
    }

    private void createClipboardDatabase() {
        File file = context.getDatabasePath(GboardClipboardHistoryRepository.DATABASE_NAME);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL(CREATE_CLIPS);
        database.execSQL("INSERT OR REPLACE INTO clips VALUES (1, 'newer', NULL, 0, 0, 2000, 0,"
                + " NULL, NULL)");
        database.execSQL("INSERT OR REPLACE INTO clips VALUES (2, 'older', NULL, 0, 0, 1000, 0,"
                + " NULL, NULL)");
        database.execSQL("INSERT OR REPLACE INTO clips VALUES (3, 'secret', NULL, 0, 0, 3000, 1,"
                + " NULL, NULL)");
        database.close();
    }

    private static boolean containsText(
            List<GboardClipboardHistoryRepository.ClipboardEntry> entries, String text) {
        for (GboardClipboardHistoryRepository.ClipboardEntry entry : entries) {
            if (text.equals(entry.getText())) {
                return true;
            }
        }
        return false;
    }
}
