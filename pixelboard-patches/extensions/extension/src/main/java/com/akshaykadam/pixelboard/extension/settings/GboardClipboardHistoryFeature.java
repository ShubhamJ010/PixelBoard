/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.settings;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.akshaykadam.pixelboard.extension.R;
import com.akshaykadam.pixelboard.extension.clipboard.GboardClipboardHistoryProvider;
import com.akshaykadam.pixelboard.extension.clipboard.GboardClipboardHistoryRepository;
import com.akshaykadam.pixelboard.extension.clipboard.GboardClipboardHistorySettings;

/**
 * Settings screen that surfaces the Gboard clipboard history.
 *
 * <p>The screen lists the captured entries, allows copying a single entry back to the clipboard and
 * controls whether other applications may read the history through the exported content provider.
 */
public final class GboardClipboardHistoryFeature
        implements GboardPatchesSettingsContract.Feature {

    private static final String TAG = "GboardPatches";
    private static final int MAX_VISIBLE_ENTRIES = 25;
    private static final int PREVIEW_LENGTH = 72;
    private static final long REFRESH_INTERVAL_MS = 5000L;
    private static final String IMAGE_MIME_TYPE = "image/*";

    private final String headerBadge;
    private final String entryTitle;
    private final String entrySummary;
    private final String headerSummary;
    private final String errorTitle;
    private final String errorSummary;
    private final String emptyTitle;
    private final String emptySummary;
    private final String captureWindowSummary;
    private final String sectionHistory;
    private final String sectionExternalAccess;
    private final String externalAccessDescription;
    private final String sectionDanger;
    private final String untitledEntry;
    private final String imageEntry;
    private final String unknownTime;
    private final String copyMessage;
    private final String externalAccessTitle;
    private final String externalAccessSummary;
    private final String includeSensitiveTitle;
    private final String includeSensitiveSummary;
    private final String accessTokenTitle;
    private final String accessTokenSummary;
    private final String copyTokenTitle;
    private final int accessTokenValueResId;
    private final int providerValueResId;
    private final int providerEndpointsValueResId;
    private final String providerUriTitle;
    private final String providerEndpointsTitle;
    private final String providerPermissionTitle;
    private final String regenerateTokenTitle;
    private final String regenerateTokenSummary;
    private final String clearTitle;
    private final String clearSummary;
    private final String clearConfirmTitle;
    private final String clearConfirmMessage;
    private final String clearedMessage;

    public GboardClipboardHistoryFeature(Context context) {
        headerBadge = GboardSettingsText.get(context, R.string.gboard_patches_header_badge);
        entryTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_title);
        entrySummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_summary);
        headerSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_header_summary);
        errorTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_error_title);
        errorSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_error_summary);
        emptyTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_empty_title);
        emptySummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_empty_summary);
        captureWindowSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_capture_window_summary);
        sectionHistory = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_history);
        sectionExternalAccess = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_external_access);
        externalAccessDescription = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_external_access_description);
        sectionDanger = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_danger);
        untitledEntry = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_entry_untitled);
        imageEntry = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_entry_image);
        unknownTime = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_entry_unknown_time);
        copyMessage = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_copy_message);
        externalAccessTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_external_access_title);
        externalAccessSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_external_access_summary);
        includeSensitiveTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_include_sensitive_title);
        includeSensitiveSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_include_sensitive_summary);
        accessTokenTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_access_token_title);
        accessTokenSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_access_token_summary);
        copyTokenTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_copy_token_title);
        accessTokenValueResId = R.string.gboard_patches_clipboard_history_access_token_value;
        providerValueResId = R.string.gboard_patches_clipboard_history_provider_value;
        providerEndpointsValueResId =
                R.string.gboard_patches_clipboard_history_provider_endpoints_value;
        providerUriTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_provider_uri_title);
        providerEndpointsTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_provider_endpoints_title);
        providerPermissionTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_provider_permission_title);
        regenerateTokenTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_regenerate_token_title);
        regenerateTokenSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_regenerate_token_summary);
        clearTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_clear_title);
        clearSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_clear_summary);
        clearConfirmTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_clear_confirm_title);
        clearConfirmMessage = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_clear_confirm_message);
        clearedMessage = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_cleared_message);
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return true;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            Context context = host.getContext();
            boolean includeSensitive = GboardClipboardHistorySettings.isSensitiveIncluded(context);
            List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                    GboardClipboardHistoryRepository.loadHistory(context, includeSensitive,
                            MAX_VISIBLE_ENTRIES);
            boolean externalAccessEnabled =
                    GboardClipboardHistorySettings.isExternalAccessEnabled(context);
            String accessToken = GboardClipboardHistorySettings.getAccessToken(context);

            List<GboardPatchesSettingsContract.Row> historyRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            for (GboardClipboardHistoryRepository.ClipboardEntry entry : entries) {
                String thumbnailUri = isImageEntry(entry) ? entry.getUri() : null;
                historyRows.add(new GboardPatchesSettingsContract.NavigationRow(
                        describeEntry(entry),
                        describeEntryTimestamp(entry),
                        true,
                        () -> activateEntry(host, entry),
                        thumbnailUri));
            }

            List<GboardPatchesSettingsContract.Row> externalAccessRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            externalAccessRows.add(new GboardPatchesSettingsContract.ToggleRow(
                    externalAccessTitle,
                    externalAccessSummary,
                    true,
                    externalAccessEnabled,
                    value -> {
                        GboardClipboardHistorySettings.setExternalAccessEnabled(context, value);
                        GboardPatchesSettingsContract.refresh(host);
                    }));
            externalAccessRows.add(new GboardPatchesSettingsContract.ToggleRow(
                    includeSensitiveTitle,
                    includeSensitiveSummary,
                    true,
                    includeSensitive,
                    value -> {
                        GboardClipboardHistorySettings.setSensitiveIncluded(context, value);
                        GboardPatchesSettingsContract.refresh(host);
                    }));
            externalAccessRows.add(new GboardPatchesSettingsContract.DetailRow(
                    accessTokenTitle,
                    GboardSettingsText.format(context, accessTokenValueResId, accessToken),
                    true,
                    true));
            externalAccessRows.add(new GboardPatchesSettingsContract.CommandRow(
                    copyTokenTitle,
                    accessTokenSummary,
                    true,
                    () -> {
                        GboardPatchesSettingsContract.copyTextToClipboard(
                                host, copyTokenTitle, accessToken);
                        GboardPatchesSettingsContract.showMessage(host, copyMessage);
                    }));
            externalAccessRows.add(new GboardPatchesSettingsContract.CommandRow(
                    regenerateTokenTitle,
                    regenerateTokenSummary,
                    true,
                    () -> {
                        GboardClipboardHistorySettings.regenerateAccessToken(context);
                        GboardPatchesSettingsContract.refresh(host);
                    }));
            externalAccessRows.add(new GboardPatchesSettingsContract.DetailRow(
                    providerUriTitle,
                    GboardSettingsText.format(context, providerValueResId,
                            GboardClipboardHistoryProvider.AUTHORITY),
                    true,
                    true));
            externalAccessRows.add(new GboardPatchesSettingsContract.DetailRow(
                    providerEndpointsTitle,
                    GboardSettingsText.get(context, providerEndpointsValueResId),
                    true,
                    true));
            externalAccessRows.add(new GboardPatchesSettingsContract.DetailRow(
                    providerPermissionTitle,
                    GboardClipboardHistorySettings.PERMISSION_READ_CLIPBOARD_HISTORY,
                    true,
                    true));

            List<GboardPatchesSettingsContract.Row> dangerRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            dangerRows.add(new GboardPatchesSettingsContract.DangerRow(
                    clearTitle,
                    clearSummary,
                    true,
                    () -> {
                        GboardClipboardHistoryRepository.clearHistory(context);
                        GboardPatchesSettingsContract.showMessage(host, clearedMessage);
                        GboardPatchesSettingsContract.refresh(host);
                    },
                    clearConfirmTitle,
                    clearConfirmMessage));

            List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                    new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
            if (entries.isEmpty()) {
                statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                        emptyTitle,
                        emptySummary,
                        GboardPatchesSettingsContract.StatusTone.NEUTRAL));
            }

            List<GboardPatchesSettingsContract.Section> sections =
                    new ArrayList<GboardPatchesSettingsContract.Section>();
            // The capture window is a single line under the section title instead of a card, so it
            // informs without competing with the entries.
            sections.add(new GboardPatchesSettingsContract.Section(
                    sectionHistory,
                    captureWindowSummary,
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    historyRows));
            sections.add(new GboardPatchesSettingsContract.Section(
                    sectionExternalAccess,
                    externalAccessDescription,
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    externalAccessRows));
            sections.add(new GboardPatchesSettingsContract.Section(sectionDanger, dangerRows));

            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    headerSummary,
                    statusBlocks,
                    sections,
                    GboardPatchesSettingsContract.RefreshPolicy.fullScreen(REFRESH_INTERVAL_MS),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render clipboard history settings screen", throwable);
            return buildErrorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                errorTitle,
                errorSummary,
                GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                headerSummary,
                statusBlocks,
                Collections.emptyList());
    }

    private void activateEntry(GboardPatchesSettingsContract.FeatureHost host,
            GboardClipboardHistoryRepository.ClipboardEntry entry) {
        if (isImageEntry(entry)) {
            // Gboard stores the pixels of a copied image or screenshot in a file shared through the
            // keyboard fileprovider, or in MediaStore for screenshots it captured. Handing that URI
            // to the viewer with a read grant lets the user open the image instead of copying a text
            // placeholder.
            GboardPatchesSettingsContract.openContentUri(host, entry.getUri(), IMAGE_MIME_TYPE);
            return;
        }
        copyEntry(host, entry);
    }

    /** True when the clip carries no text and stores its payload in the {@code uri} column. */
    private boolean isImageEntry(GboardClipboardHistoryRepository.ClipboardEntry entry) {
        return TextUtils.isEmpty(entry.getPasteableText()) && !TextUtils.isEmpty(entry.getUri());
    }

    private void copyEntry(GboardPatchesSettingsContract.FeatureHost host,
            GboardClipboardHistoryRepository.ClipboardEntry entry) {
        String text = entry.getPasteableText();
        if (TextUtils.isEmpty(text)) {
            GboardPatchesSettingsContract.showMessage(host, emptyTitle);
            return;
        }
        GboardPatchesSettingsContract.copyTextToClipboard(host, entryTitle, text);
        GboardPatchesSettingsContract.showMessage(host, copyMessage);
    }

    private String describeEntry(GboardClipboardHistoryRepository.ClipboardEntry entry) {
        if (isImageEntry(entry)) {
            return imageEntry;
        }
        String text = entry.getPasteableText();
        if (TextUtils.isEmpty(text)) {
            return untitledEntry;
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= PREVIEW_LENGTH) {
            return singleLine;
        }
        return singleLine.substring(0, PREVIEW_LENGTH) + "…";
    }

    private String describeEntryTimestamp(GboardClipboardHistoryRepository.ClipboardEntry entry) {
        long timestamp = entry.getTimestamp();
        if (timestamp <= 0L) {
            return unknownTime;
        }
        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
        return relative.toString();
    }
}
