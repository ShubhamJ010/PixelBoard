/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.clipboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import java.util.List;

/**
 * Read only content provider that publishes the Gboard clipboard history to other applications.
 *
 * <p>The provider runs inside the keyboard process, therefore it can read the private clipboard
 * database that Gboard owns. The exposed authority is intentionally independent from the host
 * package name so the provider keeps working with or without the package rename patch.
 *
 * <p>Available URIs:
 * <ul>
 *     <li>{@code content://com.akshaykadam.pixelboard.gboard_clipboard_history/clips}</li>
 *     <li>{@code content://com.akshaykadam.pixelboard.gboard_clipboard_history/clips/&#123;id&#125;}</li>
 * </ul>
 *
 * <p>Callers authenticate either with the signature level permission
 * {@code com.akshaykadam.pixelboard.permission.READ_CLIPBOARD_HISTORY} or with the per install
 * access token that the clipboard history settings screen can copy. The provider stays closed
 * until the user enables external access.
 */
public final class GboardClipboardHistoryProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.akshaykadam.pixelboard.gboard_clipboard_history";
    public static final String PATH_CLIPS = "clips";
    public static final String QUERY_PARAM_TOKEN = "token";

    public static final String MIME_TYPE_CLIPS_DIR = "vnd.android.cursor.dir/clips";
    public static final String MIME_TYPE_CLIP_ITEM = "vnd.android.cursor.item/clips";

    private static final String TAG = "PixelBoardClipboard";
    private static final int MATCH_CLIPS = 1;
    private static final int MATCH_CLIP_ITEM = 2;

    private UriMatcher matcher;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(AUTHORITY, PATH_CLIPS, MATCH_CLIPS);
        matcher.addURI(AUTHORITY, PATH_CLIPS + "/#", MATCH_CLIP_ITEM);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Context context = getContext();
        if (context == null || matcher == null) {
            return null;
        }
        try {
            enforceAuthorizedCaller(context, uri);
            int match = matcher.match(uri);
            if (match != MATCH_CLIPS && match != MATCH_CLIP_ITEM) {
                return null;
            }
            boolean includeSensitive = GboardClipboardHistorySettings.isSensitiveIncluded(context);
            List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                    GboardClipboardHistoryRepository.loadHistory(context, includeSensitive,
                            GboardClipboardHistoryRepository.MAX_LIMIT);
            MatrixCursor cursor = new MatrixCursor(GboardClipboardHistoryRepository.ALL_COLUMNS);
            if (match == MATCH_CLIP_ITEM) {
                long requestedId = parseId(uri);
                for (GboardClipboardHistoryRepository.ClipboardEntry entry : entries) {
                    if (entry.getId() == requestedId) {
                        appendRow(cursor, entry);
                        break;
                    }
                }
                return cursor;
            }
            for (GboardClipboardHistoryRepository.ClipboardEntry entry : entries) {
                appendRow(cursor, entry);
            }
            return cursor;
        } catch (SecurityException securityException) {
            throw securityException;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to serve clipboard history query", throwable);
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        if (matcher == null) {
            return null;
        }
        int match = matcher.match(uri);
        if (match == MATCH_CLIPS) {
            return MIME_TYPE_CLIPS_DIR;
        }
        if (match == MATCH_CLIP_ITEM) {
            return MIME_TYPE_CLIP_ITEM;
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Clipboard history is read only.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Clipboard history is read only.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Clipboard history is read only.");
    }

    private void enforceAuthorizedCaller(Context context, Uri uri) {
        if (!GboardClipboardHistorySettings.isExternalAccessEnabled(context)) {
            throw new SecurityException(
                    "PixelBoard clipboard history export is disabled. Enable it in the PixelBoard "
                            + "settings to allow external access.");
        }
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return;
        }
        if (context.checkPermission(GboardClipboardHistorySettings.PERMISSION_READ_CLIPBOARD_HISTORY,
                Binder.getCallingPid(), callingUid) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String token = uri == null ? null : uri.getQueryParameter(QUERY_PARAM_TOKEN);
        if (GboardClipboardHistorySettings.isAccessTokenValid(context, token)) {
            return;
        }
        throw new SecurityException(
                "PixelBoard clipboard history access requires the "
                        + GboardClipboardHistorySettings.PERMISSION_READ_CLIPBOARD_HISTORY
                        + " permission or a valid access token.");
    }

    private static void appendRow(MatrixCursor cursor,
            GboardClipboardHistoryRepository.ClipboardEntry entry) {
        cursor.addRow(new Object[] {
                entry.getId(),
                entry.getText(),
                entry.getHtmlText(),
                entry.getItemType(),
                entry.getEntityType(),
                entry.getTimestamp(),
                entry.isSensitive() ? 1 : 0,
                entry.getUri(),
                entry.getGroupId(),
        });
    }

    private static long parseId(Uri uri) {
        try {
            String lastSegment = uri.getLastPathSegment();
            return lastSegment == null ? -1L : Long.parseLong(lastSegment);
        } catch (Throwable throwable) {
            return -1L;
        }
    }
}
