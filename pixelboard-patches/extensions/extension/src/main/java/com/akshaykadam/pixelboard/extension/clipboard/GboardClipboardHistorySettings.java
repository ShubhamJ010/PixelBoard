/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettings;

import java.security.SecureRandom;

/**
 * Persisted policy for the clipboard history feature.
 *
 * <p>External access is disabled by default. When it is enabled, callers may authenticate either
 * with the signature level permission declared by the patched keyboard or with the per install
 * access token shown on the clipboard history settings screen.
 */
public final class GboardClipboardHistorySettings {

    public static final String PERMISSION_READ_CLIPBOARD_HISTORY =
            "com.akshaykadam.pixelboard.permission.READ_CLIPBOARD_HISTORY";

    static final String PREF_KEY_EXTERNAL_ACCESS_ENABLED =
            "pref_clipboard_history_external_access_enabled";
    static final String PREF_KEY_INCLUDE_SENSITIVE =
            "pref_clipboard_history_include_sensitive";
    static final String PREF_KEY_ACCESS_TOKEN = "pref_clipboard_history_access_token";

    private static final String TAG = "PixelBoardClipboard";
    private static final int ACCESS_TOKEN_BYTES = 16;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private GboardClipboardHistorySettings() {
    }

    public static boolean isExternalAccessEnabled(Context context) {
        SharedPreferences preferences = preferences(context);
        if (preferences == null) {
            return false;
        }
        return preferences.getBoolean(PREF_KEY_EXTERNAL_ACCESS_ENABLED, false);
    }

    public static void setExternalAccessEnabled(Context context, boolean enabled) {
        SharedPreferences preferences = preferences(context);
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(PREF_KEY_EXTERNAL_ACCESS_ENABLED, enabled).apply();
    }

    public static boolean isSensitiveIncluded(Context context) {
        SharedPreferences preferences = preferences(context);
        if (preferences == null) {
            return false;
        }
        return preferences.getBoolean(PREF_KEY_INCLUDE_SENSITIVE, false);
    }

    public static void setSensitiveIncluded(Context context, boolean included) {
        SharedPreferences preferences = preferences(context);
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(PREF_KEY_INCLUDE_SENSITIVE, included).apply();
    }

    /** Returns the stable access token, creating one on first use. */
    public static String getAccessToken(Context context) {
        SharedPreferences preferences = preferences(context);
        if (preferences == null) {
            return "";
        }
        String token = preferences.getString(PREF_KEY_ACCESS_TOKEN, null);
        if (token != null && !token.isEmpty()) {
            return token;
        }
        String generated = generateAccessToken();
        preferences.edit().putString(PREF_KEY_ACCESS_TOKEN, generated).apply();
        return generated;
    }

    /** Replaces the access token, invalidating the previously shared value. */
    public static String regenerateAccessToken(Context context) {
        SharedPreferences preferences = preferences(context);
        if (preferences == null) {
            return "";
        }
        String generated = generateAccessToken();
        preferences.edit().putString(PREF_KEY_ACCESS_TOKEN, generated).apply();
        return generated;
    }

    /**
     * Constant time comparison of a caller supplied token against the stored access token.
     */
    public static boolean isAccessTokenValid(Context context, String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        String expected = getAccessToken(context);
        if (expected.isEmpty()) {
            return false;
        }
        return constantTimeEquals(expected, candidate);
    }

    static boolean constantTimeEquals(String expected, String candidate) {
        if (expected == null || candidate == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes();
        byte[] candidateBytes = candidate.getBytes();
        int difference = expectedBytes.length ^ candidateBytes.length;
        for (int index = 0; index < expectedBytes.length && index < candidateBytes.length; index++) {
            difference |= expectedBytes[index] ^ candidateBytes[index];
        }
        return difference == 0;
    }

    static String generateAccessToken() {
        byte[] bytes = new byte[ACCESS_TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        char[] digits = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            digits[index * 2] = HEX_DIGITS[value >>> 4];
            digits[index * 2 + 1] = HEX_DIGITS[value & 0x0F];
        }
        return new String(digits);
    }

    private static SharedPreferences preferences(Context context) {
        if (context == null) {
            return null;
        }
        try {
            return GboardPatchesSettings.preferences(context);
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to read clipboard history settings", throwable);
            } catch (Throwable ignored) {
                // Logging must never break the settings lookup.
            }
            return null;
        }
    }
}
