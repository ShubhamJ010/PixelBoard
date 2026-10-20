package com.akshaykadam.pixelboard.extension.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GboardClipboardHistorySettingsTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void externalAccessIsDisabledByDefault() {
        assertFalse(GboardClipboardHistorySettings.isExternalAccessEnabled(context));

        GboardClipboardHistorySettings.setExternalAccessEnabled(context, true);

        assertTrue(GboardClipboardHistorySettings.isExternalAccessEnabled(context));
    }

    @Test
    public void sensitiveClipsAreExcludedByDefault() {
        assertFalse(GboardClipboardHistorySettings.isSensitiveIncluded(context));

        GboardClipboardHistorySettings.setSensitiveIncluded(context, true);

        assertTrue(GboardClipboardHistorySettings.isSensitiveIncluded(context));
    }

    @Test
    public void accessTokenIsStableAndRegeneratable() {
        String token = GboardClipboardHistorySettings.getAccessToken(context);

        assertFalse(token.isEmpty());
        assertEquals(token, GboardClipboardHistorySettings.getAccessToken(context));

        String regenerated = GboardClipboardHistorySettings.regenerateAccessToken(context);

        assertNotEquals(token, regenerated);
        assertEquals(regenerated, GboardClipboardHistorySettings.getAccessToken(context));
    }

    @Test
    public void onlyTheCurrentTokenIsAccepted() {
        String token = GboardClipboardHistorySettings.getAccessToken(context);

        assertTrue(GboardClipboardHistorySettings.isAccessTokenValid(context, token));
        assertFalse(GboardClipboardHistorySettings.isAccessTokenValid(context, "not-the-token"));
        assertFalse(GboardClipboardHistorySettings.isAccessTokenValid(context, ""));
        assertFalse(GboardClipboardHistorySettings.isAccessTokenValid(context, null));
        assertFalse(GboardClipboardHistorySettings.isAccessTokenValid(context, token + "x"));
    }

    @Test
    public void tokenComparisonRejectsDifferentLengths() {
        assertFalse(GboardClipboardHistorySettings.constantTimeEquals("abc", "ab"));
        assertFalse(GboardClipboardHistorySettings.constantTimeEquals("abc", "abd"));
        assertTrue(GboardClipboardHistorySettings.constantTimeEquals("abc", "abc"));
        assertFalse(GboardClipboardHistorySettings.constantTimeEquals(null, "abc"));
    }

    @Test
    public void generatedTokensAreHexEncodedAndUnique() {
        String first = GboardClipboardHistorySettings.generateAccessToken();
        String second = GboardClipboardHistorySettings.generateAccessToken();

        assertEquals(32, first.length());
        assertTrue(first.matches("[0-9a-f]+"));
        assertNotEquals(first, second);
    }
}
