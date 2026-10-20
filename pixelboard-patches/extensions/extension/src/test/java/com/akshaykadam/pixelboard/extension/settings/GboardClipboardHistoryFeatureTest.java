package com.akshaykadam.pixelboard.extension.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class GboardClipboardHistoryFeatureTest {

    private Context context;
    private GboardClipboardHistoryFeature feature;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        feature = new GboardClipboardHistoryFeature(context);
    }

    @Test
    public void featureIsAlwaysAvailableAndExposesAnEntry() {
        assertTrue(feature.isAvailable(context));
        assertNotNull(feature.getEntryTitle());
        assertNotNull(feature.getEntrySummary());
    }

    @Test
    public void screenExposesHistoryExternalAccessAndDangerSections() throws Exception {
        GboardPatchesSettingsContract.Screen screen =
                feature.buildScreen(new GboardPatchesSettingsTestHost(context));

        assertNotNull(screen);
        assertEquals(feature.getEntryTitle(), screen.getToolbarTitle());

        List<?> sections = reflectList(screen, "getSections");
        assertEquals(3, sections.size());

        List<?> externalAccessItems = reflectList(sections.get(1), "getItems");
        assertTrue(externalAccessItems.size() >= 5);
        assertEquals("ToggleRow", externalAccessItems.get(0).getClass().getSimpleName());
        assertEquals("ToggleRow", externalAccessItems.get(1).getClass().getSimpleName());

        List<?> dangerItems = reflectList(sections.get(2), "getItems");
        assertEquals(1, dangerItems.size());
        assertEquals("DangerRow", dangerItems.get(0).getClass().getSimpleName());
    }

    @Test
    public void externalAccessTogglePersistsThePreference() throws Exception {
        GboardPatchesSettingsContract.Screen screen =
                feature.buildScreen(new GboardPatchesSettingsTestHost(context));

        List<?> sections = reflectList(screen, "getSections");
        List<?> externalAccessItems = reflectList(sections.get(1), "getItems");
        Object toggle = externalAccessItems.get(0);
        assertFalse((Boolean) reflect(toggle, "isChecked"));

        Object action = reflect(toggle, "getToggleAction");
        ((GboardPatchesSettingsContract.ToggleAction) action).accept(true);

        assertTrue(com.akshaykadam.pixelboard.extension.clipboard
                .GboardClipboardHistorySettings.isExternalAccessEnabled(context));
    }

    @Test
    public void emptyHistoryShowsAnEmptyStateStatusBlock() throws Exception {
        GboardPatchesSettingsContract.Screen screen =
                feature.buildScreen(new GboardPatchesSettingsTestHost(context));

        List<?> statusBlocks = reflectList(screen, "getStatusBlocks");
        assertEquals(1, statusBlocks.size());
    }

    @Test
    public void imageEntriesAreLabelledInsteadOfShowingNoText() throws Exception {
        createClipsTableAndInsert(9, null, "content://media/external/images/media/42", 9000L);

        GboardPatchesSettingsTestHost host = new GboardPatchesSettingsTestHost(context);
        GboardPatchesSettingsContract.Screen screen = feature.buildScreen(host);

        List<?> sections = reflectList(screen, "getSections");
        List<?> historyItems = reflectList(sections.get(0), "getItems");
        assertEquals(1, historyItems.size());
        Object imageRow = historyItems.get(0);
        assertEquals("Image clip", reflect(imageRow, "getTitle"));
        assertEquals("content://media/external/images/media/42",
                reflect(imageRow, "getThumbnailUri"));

        ((Runnable) reflect(imageRow, "getAction")).run();

        assertEquals("content://media/external/images/media/42", host.getOpenedContentUri());
        assertEquals("image/*", host.getOpenedContentMimeType());
    }

    @Test
    public void textEntriesDoNotExposeAThumbnail() throws Exception {
        createClipsTableAndInsert(10, "hello", "content://media/external/images/media/42", 10000L);

        GboardPatchesSettingsContract.Screen screen =
                feature.buildScreen(new GboardPatchesSettingsTestHost(context));

        List<?> sections = reflectList(screen, "getSections");
        List<?> historyItems = reflectList(sections.get(0), "getItems");
        assertEquals(1, historyItems.size());
        assertEquals("hello", reflect(historyItems.get(0), "getTitle"));
        assertNull(reflect(historyItems.get(0), "getThumbnailUri"));
    }

    @Test
    public void captureWindowIsDisclosedAsTheHistorySectionDescription() throws Exception {
        GboardPatchesSettingsContract.Screen screen =
                feature.buildScreen(new GboardPatchesSettingsTestHost(context));

        List<?> sections = reflectList(screen, "getSections");
        Object historySection = sections.get(0);
        String description = (String) reflect(historySection, "getDescription");
        assertNotNull(description);
        assertEquals("Only copies made while PixelBoard is the active keyboard are recorded.",
                description);
    }

    private void createClipsTableAndInsert(long id, String text, String uri, long timestamp) {
        File file = context.getDatabasePath("gboard_clipboard.db");
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS clips (_id INTEGER PRIMARY KEY NOT NULL, "
                + "text TEXT, html_text TEXT, item_type INTEGER NOT NULL, "
                + "entity_type INTEGER NOT NULL, timestamp INTEGER, sensitive BOOL, uri TEXT, "
                + "group_id TEXT)");
        database.execSQL("INSERT INTO clips VALUES (" + id + ", "
                + (text == null ? "NULL" : "'" + text + "'") + ", NULL, 0, 0, " + timestamp
                + ", 0, '" + uri + "', NULL)");
        database.close();
    }

    private static Object reflect(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static List<?> reflectList(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (List<?>) method.invoke(target);
    }
}
