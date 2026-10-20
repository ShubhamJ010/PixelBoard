package com.akshaykadam.pixelboard.extension.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

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
    public void screenExposesHistoryExportExternalAccessAndDangerSections() throws Exception {
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

    private static Object reflect(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static List<?> reflectList(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (List<?>) method.invoke(target);
    }
}
