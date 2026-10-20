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
 * <p>The screen lists the captured entries, allows copying a single entry back to the clipboard,
 * exports the whole history to a text document and controls whether other applications may read
 * the history through the exported content provider.
 */
public final class GboardClipboardHistoryFeature
        implements GboardPatchesSettingsContract.Feature {

    private static final String TAG = "GboardPatches";
    private static final int MAX_VISIBLE_ENTRIES = 25;
    private static final int PREVIEW_LENGTH = 72;
    private static final long REFRESH_INTERVAL_MS = 5000L;

    private final String headerBadge;
    private final String entryTitle;
    private final String entrySummary;
    private final String headerSummary;
    private final String errorTitle;
    private final String errorSummary;
    private final String emptyTitle;
    private final String emptySummary;
    private final String sectionHistory;
    private final String sectionExport;
    private final String sectionExternalAccess;
    private final String sectionDanger;
    private final String untitledEntry;
    private final String unknownTime;
    private final String copyMessage;
    private final String exportTitle;
    private final String exportSummary;
    private final String exportFileName;
    private final String exportedMessage;
    private final String externalAccessTitle;
    private final String externalAccessSummary;
    private final String includeSensitiveTitle;
    private final String includeSensitiveSummary;
    private final String accessTokenTitle;
    private final String accessTokenSummary;
    private final int accessTokenValueResId;
    private final int providerValueResId;
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
        sectionHistory = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_history);
        sectionExport = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_export);
        sectionExternalAccess = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_external_access);
        sectionDanger = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_section_danger);
        untitledEntry = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_entry_untitled);
        unknownTime = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_entry_unknown_time);
        copyMessage = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_copy_message);
        exportTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_export_title);
        exportSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_export_summary);
        exportFileName = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_export_file_name);
        exportedMessage = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_history_exported_message);
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
        accessTokenValueResId = R.string.gboard_patches_clipboard_history_access_token_value;
        providerValueResId = R.string.gboard_patches_clipboard_history_provider_value;
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
                historyRows.add(new GboardPatchesSettingsContract.NavigationRow(
                        describeEntry(entry),
                        describeEntryTimestamp(entry),
                        true,
                        () -> copyEntry(host, entry)));
            }

            List<GboardPatchesSettingsContract.Row> exportRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            exportRows.add(new GboardPatchesSettingsContract.CommandRow(
                    exportTitle,
                    exportSummary,
                    true,
                    () -> exportHistory(host, context, includeSensitive)));

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
                    accessTokenTitle,
                    accessTokenSummary,
                    true,
                    () -> {
                        GboardPatchesSettingsContract.copyTextToClipboard(
                                host, accessTokenTitle, accessToken);
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
                    GboardClipboardHistorySettings.PERMISSION_READ_CLIPBOARD_HISTORY,
                    GboardSettingsText.format(context, providerValueResId,
                            GboardClipboardHistoryProvider.AUTHORITY),
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
            if (!historyRows.isEmpty()) {
                sections.add(new GboardPatchesSettingsContract.Section(
                        sectionHistory,
                        historyRows));
            }
            sections.add(new GboardPatchesSettingsContract.Section(sectionExport, exportRows));
            sections.add(new GboardPatchesSettingsContract.Section(
                    sectionExternalAccess,
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
    private void exportHistory(GboardPatchesSettingsContract.FeatureHost host, Context context,
            boolean includeSensitive) {
        List<GboardClipboardHistoryRepository.ClipboardEntry> entries =
                GboardClipboardHistoryRepository.loadHistory(context, includeSensitive,
                        GboardClipboardHistoryRepository.MAX_LIMIT);
        if (entries.isEmpty()) {
            GboardPatchesSettingsContract.showMessage(host, emptyTitle);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (GboardClipboardHistoryRepository.ClipboardEntry entry : entries) {
            builder.append(describeEntryTimestamp(entry)).append('\n');
            builder.append(entry.getPasteableText()).append("\n\n");
        }
        GboardPatchesSettingsContract.createTextDocument(host, exportFileName, "text/plain",
                builder.toString(),
                () -> GboardPatchesSettingsContract.showMessage(host, exportedMessage));
    }

    private String describeEntry(GboardClipboardHistoryRepository.ClipboardEntry entry) {
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
