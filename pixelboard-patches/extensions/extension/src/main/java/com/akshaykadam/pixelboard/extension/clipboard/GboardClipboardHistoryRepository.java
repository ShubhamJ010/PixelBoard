/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.clipboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read/write access to the Gboard clipboard history database.
 *
 * <p>Gboard stores captured clipboard entries in the {@code gboard_clipboard.db} SQLite database
 * that lives in the keyboard application private data directory. The {@code clips} table holds one
 * row per captured clip. This repository is the single place that knows the table layout so the
 * settings screen and the exported content provider cannot drift apart.
 *
 * <p>The keyboard application owns the database, therefore the extension code can open it directly
 * without any additional permission.
 */
public final class GboardClipboardHistoryRepository {

    static final String TAG = "PixelBoardClipboard";

    static final String DATABASE_NAME = "gboard_clipboard.db";
    static final String TABLE_CLIPS = "clips";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TEXT = "text";
    public static final String COLUMN_HTML_TEXT = "html_text";
    public static final String COLUMN_ITEM_TYPE = "item_type";
    public static final String COLUMN_ENTITY_TYPE = "entity_type";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_SENSITIVE = "sensitive";
    public static final String COLUMN_URI = "uri";
    public static final String COLUMN_GROUP_ID = "group_id";

    /** Canonical column order exposed to consumers of the clipboard history provider. */
    public static final String[] ALL_COLUMNS = {
            COLUMN_ID,
            COLUMN_TEXT,
            COLUMN_HTML_TEXT,
            COLUMN_ITEM_TYPE,
            COLUMN_ENTITY_TYPE,
            COLUMN_TIMESTAMP,
            COLUMN_SENSITIVE,
            COLUMN_URI,
            COLUMN_GROUP_ID,
    };

    /** Upper bound for a single history read so a huge history cannot exhaust the heap. */
    public static final int MAX_LIMIT = 500;

    private static final int OPEN_MODE_READ = 0;
    private static final int OPEN_MODE_WRITE = 1;
    private static final int OPEN_MODES = 2;

    private GboardClipboardHistoryRepository() {
    }

    /** A single captured clipboard entry. */
    public static final class ClipboardEntry {
        private final long id;
        private final String text;
        private final String htmlText;
        private final int itemType;
        private final int entityType;
        private final long timestamp;
        private final boolean sensitive;
        private final String uri;
        private final String groupId;

        ClipboardEntry(long id, String text, String htmlText, int itemType, int entityType,
                long timestamp, boolean sensitive, String uri, String groupId) {
            this.id = id;
            this.text = text;
            this.htmlText = htmlText;
            this.itemType = itemType;
            this.entityType = entityType;
            this.timestamp = timestamp;
            this.sensitive = sensitive;
            this.uri = uri;
            this.groupId = groupId;
        }

        public long getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getHtmlText() {
            return htmlText;
        }

        public int getItemType() {
            return itemType;
        }

        public int getEntityType() {
            return entityType;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isSensitive() {
            return sensitive;
        }

        public String getUri() {
            return uri;
        }

        public String getGroupId() {
            return groupId;
        }

        /** Text payload that can be pasted, or an empty string for non textual clips. */
        public String getPasteableText() {
            if (text != null && !text.isEmpty()) {
                return text;
            }
            return htmlText != null ? htmlText : "";
        }
    }

    /**
     * Loads the most recent clipboard entries.
     *
     * @param context application context of the keyboard process.
     * @param includeSensitive when {@code false} clips captured from sensitive fields (passwords)
     *     are filtered out.
     * @param limit maximum number of entries to return, clamped to {@link #MAX_LIMIT}.
     */
    public static List<ClipboardEntry> loadHistory(Context context, boolean includeSensitive,
            int limit) {
        if (context == null) {
            return Collections.emptyList();
        }
        int effectiveLimit = limit <= 0 ? 0 : Math.min(limit, MAX_LIMIT);
        if (effectiveLimit == 0) {
            return Collections.emptyList();
        }

        Throwable lastFailure = null;
        for (int mode = 0; mode < OPEN_MODES; mode++) {
            SQLiteDatabase database = openClipboardDatabase(context, mode == OPEN_MODE_WRITE);
            if (database == null) {
                continue;
            }
            try {
                return readEntries(database, includeSensitive, effectiveLimit);
            } catch (Throwable throwable) {
                lastFailure = throwable;
            } finally {
                closeQuietly(database);
            }
        }
        if (lastFailure != null) {
            Log.w(TAG, "Failed to read clipboard history", lastFailure);
        }
        return Collections.emptyList();
    }

    /** Counts the clipboard entries that are visible with the given sensitive clip policy. */
    public static int countHistory(Context context, boolean includeSensitive) {
        if (context == null) {
            return 0;
        }
        Throwable lastFailure = null;
        for (int mode = 0; mode < OPEN_MODES; mode++) {
            SQLiteDatabase database = openClipboardDatabase(context, mode == OPEN_MODE_WRITE);
            if (database == null) {
                continue;
            }
            try {
                return countEntries(database, includeSensitive);
            } catch (Throwable throwable) {
                lastFailure = throwable;
            } finally {
                closeQuietly(database);
            }
        }
        if (lastFailure != null) {
            Log.w(TAG, "Failed to count clipboard history", lastFailure);
        }
        return 0;
    }

    private static List<ClipboardEntry> readEntries(SQLiteDatabase database,
            boolean includeSensitive, int limit) {
        Set<String> availableColumns = resolveAvailableColumns(database);
        String[] projection = resolveProjection(availableColumns);
        if (projection.length == 0) {
            return Collections.emptyList();
        }
        String selection = includeSensitive || !availableColumns.contains(COLUMN_SENSITIVE)
                ? null
                : COLUMN_SENSITIVE + " = 0";
        List<ClipboardEntry> entries = new ArrayList<ClipboardEntry>();
        Cursor cursor = database.query(TABLE_CLIPS, projection, selection, null, null, null,
                COLUMN_TIMESTAMP + " DESC, " + COLUMN_ID + " DESC",
                String.valueOf(limit));
        if (cursor == null) {
            return Collections.emptyList();
        }
        try {
            while (cursor.moveToNext()) {
                ClipboardEntry entry = readEntry(cursor, availableColumns);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } finally {
            cursor.close();
        }
        return Collections.unmodifiableList(entries);
    }

    private static int countEntries(SQLiteDatabase database, boolean includeSensitive) {
        Set<String> availableColumns = resolveAvailableColumns(database);
        String selection = includeSensitive || !availableColumns.contains(COLUMN_SENSITIVE)
                ? null
                : COLUMN_SENSITIVE + " = 0";
        Cursor cursor = database.query(TABLE_CLIPS, new String[]{COLUMN_ID}, selection, null,
                null, null, null, null);
        if (cursor == null) {
            return 0;
        }
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    /**
     * Writes a clipboard entry that Gboard did not capture itself.
     *
     * <p>This is used to back-fill clips that were copied while another keyboard was active. Only
     * the columns that actually exist in the {@code clips} table are written, so the insert also
     * works on Gboard builds that ship a reduced schema.
     *
     * @param context application context of the keyboard process.
     * @param text the clip payload.
     * @param timestamp the moment the clip was copied, in milliseconds since the epoch.
     * @param sensitive whether the clip was copied from a password field.
     * @return the identifier of the new row, or {@code -1} when the entry could not be stored.
     */
    public static long insertClip(Context context, String text, long timestamp,
            boolean sensitive) {
        if (context == null || text == null || text.isEmpty()) {
            return -1L;
        }
        SQLiteDatabase database = null;
        try {
            database = openClipboardDatabase(context, true);
            if (database == null) {
                return -1L;
            }
            Set<String> availableColumns = resolveAvailableColumns(database);
            return insertRow(database, availableColumns, text, timestamp, sensitive);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to insert a clipboard entry", throwable);
            return -1L;
        } finally {
            closeQuietly(database);
        }
    }

    /**
     * Writes a clipboard entry unless the same payload is already part of the recent history.
     *
     * <p>The duplicate check and the insert run on the same connection, so a clip that Gboard
     * stored in the meantime is never written twice.
     *
     * @param context application context of the keyboard process.
     * @param text the clip payload.
     * @param timestamp the moment the clip was copied, in milliseconds since the epoch.
     * @param sensitive whether the clip was copied from a password field.
     * @param recentLimit number of recent rows that are inspected for the same payload.
     * @return the identifier of the new row, {@code 0} when the payload is already stored, and
     *     {@code -1} when the entry could not be written.
     */
    public static long insertClipIfAbsent(Context context, String text, long timestamp,
            boolean sensitive, int recentLimit) {
        if (context == null || text == null || text.isEmpty()) {
            return -1L;
        }
        SQLiteDatabase database = null;
        try {
            database = openClipboardDatabase(context, true);
            if (database == null) {
                return -1L;
            }
            Set<String> availableColumns = resolveAvailableColumns(database);
            if (!availableColumns.contains(COLUMN_TEXT)) {
                return -1L;
            }
            if (containsRecentText(database, text, recentLimit)) {
                return 0L;
            }
            return insertRow(database, availableColumns, text, timestamp, sensitive);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to insert a clipboard entry", throwable);
            return -1L;
        } finally {
            closeQuietly(database);
        }
    }

    /**
     * Returns {@code true} when one of the most recent rows already holds the given payload.
     */
    private static boolean containsRecentText(SQLiteDatabase database, String text, int limit) {
        if (limit <= 0) {
            return false;
        }
        Cursor cursor = null;
        try {
            cursor = database.query(TABLE_CLIPS, new String[]{COLUMN_TEXT}, null, null, null, null,
                    COLUMN_TIMESTAMP + " DESC, " + COLUMN_ID + " DESC", String.valueOf(limit));
            if (cursor == null) {
                return false;
            }
            int textIndex = cursor.getColumnIndex(COLUMN_TEXT);
            while (cursor.moveToNext()) {
                if (textIndex < 0 || cursor.isNull(textIndex)) {
                    continue;
                }
                if (text.equals(cursor.getString(textIndex))) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to inspect the recent clipboard entries", throwable);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static long insertRow(SQLiteDatabase database, Set<String> availableColumns,
            String text, long timestamp, boolean sensitive) {
        if (!availableColumns.contains(COLUMN_TEXT)) {
            return -1L;
        }
        ContentValues values = new ContentValues();
        if (availableColumns.contains(COLUMN_ID)) {
            values.put(COLUMN_ID, resolveNextRowId(database, timestamp));
        }
        values.put(COLUMN_TEXT, text);
        if (availableColumns.contains(COLUMN_HTML_TEXT)) {
            values.putNull(COLUMN_HTML_TEXT);
        }
        if (availableColumns.contains(COLUMN_ITEM_TYPE)) {
            values.put(COLUMN_ITEM_TYPE, 0);
        }
        if (availableColumns.contains(COLUMN_ENTITY_TYPE)) {
            values.put(COLUMN_ENTITY_TYPE, 0);
        }
        if (availableColumns.contains(COLUMN_TIMESTAMP)) {
            values.put(COLUMN_TIMESTAMP, timestamp);
        }
        if (availableColumns.contains(COLUMN_SENSITIVE)) {
            values.put(COLUMN_SENSITIVE, sensitive ? 1 : 0);
        }
        if (availableColumns.contains(COLUMN_URI)) {
            values.putNull(COLUMN_URI);
        }
        if (availableColumns.contains(COLUMN_GROUP_ID)) {
            values.putNull(COLUMN_GROUP_ID);
        }
        return database.insertWithOnConflict(TABLE_CLIPS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Resolves an unused row identifier. Gboard derives the identifier from the copy moment, so
     * the timestamp is preferred and only increased when it would collide with an existing row.
     */
    private static long resolveNextRowId(SQLiteDatabase database, long timestamp) {
        long candidate = timestamp > 0L ? timestamp : System.currentTimeMillis();
        long highest = 0L;
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + TABLE_CLIPS, null);
            if (cursor != null && cursor.moveToFirst()) {
                highest = cursor.getLong(0);
            }
        } catch (Throwable throwable) {
            Log.d(TAG, "Failed to read the highest clipboard row id", throwable);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (candidate <= highest) {
            candidate = highest + 1L;
        }
        return candidate;
    }

    /** Removes every clipboard entry. Returns {@code true} when the delete statement succeeded. */
    public static boolean clearHistory(Context context) {
        if (context == null) {
            return false;
        }
        SQLiteDatabase database = null;
        try {
            database = openClipboardDatabase(context, true);
            if (database == null) {
                return false;
            }
            database.delete(TABLE_CLIPS, null, null);
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to clear clipboard history", throwable);
            return false;
        } finally {
            closeQuietly(database);
        }
    }

    private static SQLiteDatabase openClipboardDatabase(Context context, boolean writable) {
        File databaseFile = context.getDatabasePath(DATABASE_NAME);
        if (databaseFile == null || !databaseFile.exists()) {
            return null;
        }
        String path = databaseFile.getAbsolutePath();
        if (!writable) {
            try {
                return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            } catch (Throwable throwable) {
                Log.d(TAG, "Read-only clipboard database open failed, retrying read-write", throwable);
            }
        }
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE);
    }

    private static Set<String> resolveAvailableColumns(SQLiteDatabase database) {
        Set<String> columns = new HashSet<String>();
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("PRAGMA table_info(" + TABLE_CLIPS + ")", null);
            if (cursor == null) {
                return columns;
            }
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex < 0) {
                return columns;
            }
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (name != null && !name.isEmpty()) {
                    columns.add(name);
                }
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to inspect clipboard table columns", throwable);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return columns;
    }

    private static String[] resolveProjection(Set<String> availableColumns) {
        List<String> projection = new ArrayList<String>();
        for (String column : ALL_COLUMNS) {
            if (availableColumns.contains(column)) {
                projection.add(column);
            }
        }
        return projection.toArray(new String[0]);
    }

    private static ClipboardEntry readEntry(Cursor cursor, Set<String> availableColumns) {
        long id = readLong(cursor, COLUMN_ID);
        String text = readString(cursor, COLUMN_TEXT);
        if (text == null) {
            text = "";
        }
        String htmlText = readString(cursor, COLUMN_HTML_TEXT);
        int itemType = (int) readLong(cursor, COLUMN_ITEM_TYPE);
        int entityType = (int) readLong(cursor, COLUMN_ENTITY_TYPE);
        long timestamp = readLong(cursor, COLUMN_TIMESTAMP);
        boolean sensitive = readLong(cursor, COLUMN_SENSITIVE) != 0L;
        String uri = readString(cursor, COLUMN_URI);
        String groupId = readString(cursor, COLUMN_GROUP_ID);
        if (!availableColumns.contains(COLUMN_ID)) {
            id = 0L;
        }
        return new ClipboardEntry(id, text, htmlText, itemType, entityType, timestamp, sensitive, uri,
                groupId);
    }

    private static String readString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return null;
        }
        try {
            return cursor.getString(index);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static long readLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return 0L;
        }
        try {
            return cursor.getLong(index);
        } catch (Throwable throwable) {
            return 0L;
        }
    }

    private static void closeQuietly(SQLiteDatabase database) {
        if (database == null) {
            return;
        }
        try {
            database.close();
        } catch (Throwable ignored) {
            // Closing a shared database handle must never break the caller.
        }
    }
}
