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
import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Back-fills clipboard entries that were copied while another keyboard was active.
 *
 * <p>Android only exposes the primary clip to the focused application and to the <em>current</em>
 * input method. Gboard therefore never observes a clip that was copied while a different keyboard
 * owned the input, and the clip is missing from the {@code clips} table until the user returns to
 * PixelBoard. At that moment the clip is still on the system clipboard and PixelBoard is again the
 * current input method, so it can legally read it and store the missing entry.
 *
 * <p>The back-fill is deliberately conservative:
 * <ul>
 *     <li>it only runs while PixelBoard is the active keyboard (the only legal read window);</li>
 *     <li>it never duplicates an entry, because the content is compared against the persisted
 *     marker and against the most recent rows of the {@code clips} table;</li>
 *     <li>it never logs clip content, only hashes and metadata;</li>
 *     <li>it classifies entries conservatively ({@code item_type = 0}, {@code entity_type = 0})
 *     because Gboard's own entity classifier is not reproducible outside of Gboard.</li>
 * </ul>
 */
public final class GboardClipboardBackfill {

    static final String TAG = "PixelBoardClipboard";

    /** Number of recent rows inspected when looking for an already stored copy of the clip. */
    private static final int RECENT_ENTRY_SCAN_LIMIT = 25;

    /** Maximum accepted clip length. Longer payloads are skipped to keep the table sane. */
    private static final int MAX_TEXT_LENGTH = 100000;

    private GboardClipboardBackfill() {
    }

    /**
     * Stores the current primary clip in the clipboard history when it is missing.
     *
     * @param context context of the keyboard process.
     * @return {@code true} when a new row was written, {@code false} otherwise.
     */
    public static boolean backfillPrimaryClip(Context context) {
        if (context == null) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        try {
            ClipboardManager clipboardManager =
                    (ClipboardManager) lookupContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                return false;
            }
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                // Either the clipboard is empty or the platform denied the read because PixelBoard
                // is not the current input method. Both cases mean "nothing to back-fill".
                return false;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(lookupContext);
            if (text == null || text.length() == 0 || text.length() > MAX_TEXT_LENGTH) {
                return false;
            }
            String clipText = text.toString();
            ClipDescription description = clip.getDescription();
            long timestamp = resolveTimestamp(description);
            boolean sensitive = isSensitive(description);

            String marker = buildMarker(clipText);
            if (marker.equals(GboardClipboardHistorySettings.getBackfillMarker(lookupContext))) {
                return false;
            }
            // The duplicate check and the insert run on the same database connection, so a clip
            // that Gboard stored in the meantime is never written twice.
            long rowId = GboardClipboardHistoryRepository.insertClipIfAbsent(
                    lookupContext, clipText, timestamp, sensitive, RECENT_ENTRY_SCAN_LIMIT);
            if (rowId < 0L) {
                // The database could not be written, for example because Gboard holds the write
                // lock. The marker stays untouched so the next start retries the back-fill.
                return false;
            }
            GboardClipboardHistorySettings.setBackfillMarker(lookupContext, marker);
            if (rowId == 0L) {
                // The payload is already part of the history.
                return false;
            }
            Log.i(TAG, "Back-filled the primary clip that was copied under another keyboard");
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to back-fill the primary clipboard entry", throwable);
            return false;
        }
    }

    /**
     * Builds the marker that identifies a clip content. The marker is a hash so the clip itself is
     * never persisted outside of the clipboard database.
     */
    static String buildMarker(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to hash the clipboard content", throwable);
            return Integer.toHexString(text.hashCode());
        }
    }

    private static long resolveTimestamp(ClipDescription description) {
        if (description != null && description.getTimestamp() > 0L) {
            return description.getTimestamp();
        }
        return System.currentTimeMillis();
    }

    /**
     * Returns {@code true} when the clip was flagged as sensitive by the copying application.
     * The flag was standardised in Android 13, older releases use the legacy extra name.
     */
    static boolean isSensitive(ClipDescription description) {
        if (description == null) {
            return false;
        }
        try {
            android.os.PersistableBundle extras = description.getExtras();
            if (extras == null) {
                return false;
            }
            if (extras.containsKey(ClipDescription.EXTRA_IS_SENSITIVE)) {
                return extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
            }
            return extras.getBoolean("android.content.extra.IS_SENSITIVE");
        } catch (Throwable throwable) {
            return false;
        }
    }
}
