package com.akshaykadam.pixelboard.extension.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GboardClipboardHistoryProviderTest {

    private static final String CREATE_CLIPS =
            "CREATE TABLE IF NOT EXISTS clips (_id INTEGER PRIMARY KEY NOT NULL, text TEXT, "
                    + "html_text TEXT, item_type INTEGER NOT NULL, entity_type INTEGER NOT NULL, "
                    + "timestamp INTEGER, sensitive BOOL, uri TEXT, group_id TEXT, "
                    + "UNIQUE(_id, text, item_type) ON CONFLICT REPLACE)";

    private Context context;
    private GboardClipboardHistoryProvider provider;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        provider = new GboardClipboardHistoryProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = GboardClipboardHistoryProvider.AUTHORITY;
        info.grantUriPermissions = false;
        provider.attachInfo(context, info);
    }

    @Test
    public void providerIsClosedWhileExternalAccessIsDisabled() {
        createClipboardDatabase();

        try {
            provider.query(clipsUri(), null, null, null, null);
            fail("Expected SecurityException while external access is disabled.");
        } catch (SecurityException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void providerServesClipboardHistoryWhenExternalAccessIsEnabled() {
        createClipboardDatabase();
        GboardClipboardHistorySettings.setExternalAccessEnabled(context, true);

        Cursor cursor = provider.query(clipsUri(), null, null, null, null);

        assertNotNull(cursor);
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("hello", cursor.getString(
                    cursor.getColumnIndex(GboardClipboardHistoryRepository.COLUMN_TEXT)));
            assertEquals(1, cursor.getInt(
                    cursor.getColumnIndex(GboardClipboardHistoryRepository.COLUMN_ITEM_TYPE)));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void providerServesASingleClipById() {
        createClipboardDatabase();
        GboardClipboardHistorySettings.setExternalAccessEnabled(context, true);

        Cursor cursor = provider.query(
                Uri.parse("content://" + GboardClipboardHistoryProvider.AUTHORITY + "/clips/42"),
                null, null, null, null);

        assertNotNull(cursor);
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("hello", cursor.getString(
                    cursor.getColumnIndex(GboardClipboardHistoryRepository.COLUMN_TEXT)));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void providerExposesMimeTypes() {
        assertEquals(GboardClipboardHistoryProvider.MIME_TYPE_CLIPS_DIR,
                provider.getType(clipsUri()));
        assertEquals(GboardClipboardHistoryProvider.MIME_TYPE_CLIP_ITEM,
                provider.getType(Uri.parse("content://"
                        + GboardClipboardHistoryProvider.AUTHORITY + "/clips/42")));
    }

    @Test
    public void providerRejectsMutations() {
        try {
            provider.insert(clipsUri(), new ContentValues());
            fail("Expected UnsupportedOperationException for insert.");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            provider.delete(clipsUri(), null, null);
            fail("Expected UnsupportedOperationException for delete.");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            provider.update(clipsUri(), new ContentValues(), null, null);
            fail("Expected UnsupportedOperationException for update.");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void accessTokenIsRequiredWhenTheCallerHasNoSignaturePermission() {
        createClipboardDatabase();
        GboardClipboardHistorySettings.setExternalAccessEnabled(context, true);

        Cursor cursor = provider.query(clipsUri(), null, null, null, null);

        assertNotNull(cursor);
        cursor.close();
    }

    private static Uri clipsUri() {
        return Uri.parse("content://" + GboardClipboardHistoryProvider.AUTHORITY + "/clips");
    }

    private void createClipboardDatabase() {
        File file = context.getDatabasePath(GboardClipboardHistoryRepository.DATABASE_NAME);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL(CREATE_CLIPS);
        database.execSQL("INSERT OR REPLACE INTO clips VALUES (42, 'hello', NULL, 1, 0, 4242, 0,"
                + " NULL, NULL)");
        database.close();
    }
}
