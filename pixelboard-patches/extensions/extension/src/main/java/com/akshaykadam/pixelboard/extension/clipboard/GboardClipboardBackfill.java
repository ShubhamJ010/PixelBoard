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
import android.net.Uri;
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
     * A snapshot of the primary clip that does not confuse an image payload with text.
     *
     * <p>{@link ClipData.Item#coerceToText(Context)} turns an image URI into the URI string, so it
     * must only be used for clips that actually carry a text stream. Text entries keep
     * {@link #text}/{@link #htmlText}; image entries keep {@link #uri} and leave the text columns
     * empty, exactly like the rows Gboard writes for screenshots and copied images.
     */
    static final class PrimaryClip {
        final String text;
        final String htmlText;
        final String uri;
        final long timestamp;
        final boolean sensitive;

        PrimaryClip(String text, String htmlText, String uri, long timestamp, boolean sensitive) {
            this.text = text;
            this.htmlText = htmlText;
            this.uri = uri;
            this.timestamp = timestamp;
            this.sensitive = sensitive;
        }

        boolean hasText() {
            return text != null && text.length() > 0;
        }
    }

    /**
     * Reads the current primary clip without coercing image payloads into text.
     *
     * @param context context of the keyboard process.
     * @return the snapshot, or {@code null} when the platform exposes no clip (empty clipboard or
     *     another input method owns the input).
     */
    static PrimaryClip readPrimaryClip(Context context) {
        if (context == null) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        ClipboardManager clipboardManager =
                (ClipboardManager) lookupContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            return null;
        }
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        ClipData.Item item = clip.getItemAt(0);
        ClipDescription description = clip.getDescription();

        String text = null;
        String htmlText = null;
        String uri = null;
        CharSequence itemText = item.getText();
        if (itemText != null && itemText.length() > 0) {
            text = itemText.toString();
        }
        String itemHtml = item.getHtmlText();
        if (itemHtml != null && itemHtml.length() > 0) {
            htmlText = itemHtml;
        }

        Uri itemUri = item.getUri();
        if (text == null && htmlText == null && itemUri != null) {
            if (isTextMimeType(description)) {
                // A content URI that resolves to a text stream (for example shared text files).
                CharSequence coerced = item.coerceToText(lookupContext);
                if (coerced != null && coerced.length() > 0) {
                    text = coerced.toString();
                } else {
                    uri = itemUri.toString();
                }
            } else {
                // Image and other binary clips: keep the URI, never the URI string as text.
                uri = itemUri.toString();
            }
        } else if (text == null && htmlText == null) {
            CharSequence coerced = item.coerceToText(lookupContext);
            if (coerced != null && coerced.length() > 0) {
                text = coerced.toString();
            }
        }

        return new PrimaryClip(text, htmlText, uri, resolveTimestamp(description),
                isSensitive(description));
    }

    /**
     * Stores the current primary clip in the clipboard history when it is missing.
     *
     * <p>Non-textual clips such as screenshots and copied images are skipped on purpose. They are
     * already stored by Gboard as image rows with a {@code uri} and no text, and writing the
     * coerced URI string would create a second, wrongly classified text row.
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
            PrimaryClip primaryClip = readPrimaryClip(lookupContext);
            if (primaryClip == null || !primaryClip.hasText()) {
                // Nothing to back-fill. Image clips are handled by Gboard's own image capture.
                return false;
            }
            String clipText = primaryClip.text;
            if (clipText.length() > MAX_TEXT_LENGTH) {
                return false;
            }
            long timestamp = primaryClip.timestamp;
            boolean sensitive = primaryClip.sensitive;

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
        if (description != null) {
            try {
                if (description.getTimestamp() > 0L) {
                    return description.getTimestamp();
                }
            } catch (Throwable throwable) {
                // ClipDescription#getTimestamp is unavailable on older releases.
                Log.d(TAG, "Failed to read the clipboard timestamp", throwable);
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * Returns {@code true} when the clip description advertises a textual stream.
     *
     * <p>A missing MIME type is treated as non-textual. Screenshot and image clips do not always
     * expose a type, and misclassifying them would store the URI as if it were clipboard text.
     */
    private static boolean isTextMimeType(ClipDescription description) {
        if (description == null || description.getMimeTypeCount() == 0) {
            return false;
        }
        try {
            String mimeType = description.getMimeType(0);
            // text/uri-list only names a reference. A screenshot exposed as a raw URI carries no
            // text payload, so it must not be coerced into a text row.
            return mimeType != null && mimeType.startsWith("text/")
                    && !"text/uri-list".equals(mimeType);
        } catch (Throwable throwable) {
            return false;
        }
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
