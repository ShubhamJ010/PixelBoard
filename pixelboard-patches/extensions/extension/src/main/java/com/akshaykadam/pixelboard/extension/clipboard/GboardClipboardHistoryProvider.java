/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.clipboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
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
 *     <li>{@code content://com.akshaykadam.pixelboard.gboard_clipboard_history/primary}</li>
 * </ul>
 *
 * <p>The {@code /primary} endpoint serves the entry that is on the system clipboard right now,
 * which also covers clips that were copied while another keyboard was active. Android only exposes
 * the primary clip to the focused application and to the current input method, so the endpoint
 * returns an empty cursor while another keyboard owns the input.
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
    public static final String PATH_PRIMARY = "primary";
    public static final String QUERY_PARAM_TOKEN = "token";

    public static final String MIME_TYPE_CLIPS_DIR = "vnd.android.cursor.dir/clips";
    public static final String MIME_TYPE_CLIP_ITEM = "vnd.android.cursor.item/clips";
    public static final String MIME_TYPE_PRIMARY = "vnd.android.cursor.item/primary";

    private static final String TAG = "PixelBoardClipboard";
    private static final int MATCH_CLIPS = 1;
    private static final int MATCH_CLIP_ITEM = 2;
    private static final int MATCH_PRIMARY = 3;

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
        matcher.addURI(AUTHORITY, PATH_PRIMARY, MATCH_PRIMARY);
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
            if (match != MATCH_CLIPS && match != MATCH_CLIP_ITEM && match != MATCH_PRIMARY) {
                return null;
            }
            boolean includeSensitive = GboardClipboardHistorySettings.isSensitiveIncluded(context);
            if (match == MATCH_PRIMARY) {
                return queryPrimaryClip(context, includeSensitive);
            }
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
        if (match == MATCH_PRIMARY) {
            return MIME_TYPE_PRIMARY;
        }
        return null;
    }

    /**
     * Serves the entry that is on the system clipboard right now.
     *
     * <p>Android only exposes the primary clip to the focused application and to the current input
     * method. While another keyboard owns the input the platform answers with an empty clip, so
     * the endpoint returns an empty cursor instead of an error. Sensitive clips follow the same
     * policy as the history endpoint and stay hidden unless the user opted in.
     */
    private Cursor queryPrimaryClip(Context context, boolean includeSensitive) {
        MatrixCursor cursor = new MatrixCursor(GboardClipboardHistoryRepository.ALL_COLUMNS);
        try {
            ClipboardManager clipboardManager =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                return cursor;
            }
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return cursor;
            }
            ClipDescription description = clip.getDescription();
            boolean sensitive = GboardClipboardBackfill.isSensitive(description);
            if (sensitive && !includeSensitive) {
                return cursor;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            long timestamp = description != null && description.getTimestamp() > 0L
                    ? description.getTimestamp()
                    : System.currentTimeMillis();
            cursor.addRow(new Object[] {
                    0L,
                    text == null ? null : text.toString(),
                    null,
                    0,
                    0,
                    timestamp,
                    sensitive ? 1 : 0,
                    null,
                    null,
            });
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to serve the primary clipboard entry", throwable);
        }
        return cursor;
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
