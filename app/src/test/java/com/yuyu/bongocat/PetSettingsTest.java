package com.yuyu.bongocat;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PetSettingsTest {
    private Context context;
    private SharedPreferences prefs;

    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences("bongocat_pet", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test public void legacyMigrationRetainsUserSettingsAndRunsOnce() {
        int[] legacyColors = {0, 5, 6, 7, 2};
        for (int costume = 0; costume < 5; costume++) {
            for (int color = 0; color < 8; color++) {
                prefs.edit().clear().putInt("costume", costume).putInt("color", color)
                        .putInt("size_dp", 240).putInt("opacity", 10)
                        .putInt("pos_x", 123).putInt("pos_y", 456).putBoolean("visible", true).commit();
                PetSettings settings = new PetSettings(context);
                assertEquals(0, settings.getCostume());
                assertEquals(color == 0 ? legacyColors[costume] : color, settings.getColor());
                assertEquals(240, settings.getSizeDp());
                assertEquals(10, settings.getOpacity());
                assertEquals(123, settings.getPosX());
                assertEquals(456, settings.getPosY());
                assertTrue(settings.isVisible());
                settings.setCostume(5);
                settings.setColor(3);
                assertEquals(5, new PetSettings(context).getCostume());
                assertEquals(3, new PetSettings(context).getColor());
            }
        }
    }

    @Test public void all48AppearancesSurviveReopeningWithoutMovingPet() {
        PetSettings settings = new PetSettings(context);
        settings.setPos(137, 291);
        for (int costume = 0; costume < 6; costume++) {
            for (int color = 0; color < 8; color++) {
                settings.setCostume(costume);
                settings.setColor(color);
                PetSettings reopened = new PetSettings(context);
                assertEquals(costume, reopened.getCostume());
                assertEquals(color, reopened.getColor());
                assertEquals(PetSettings.COLOR_TINTS[color], reopened.combinedTint());
                assertEquals(137, reopened.getPosX());
                assertEquals(291, reopened.getPosY());
            }
        }
    }

    @Test public void currentVersionDoesNotRemigrateCostume() {
        prefs.edit().putBoolean("costume_migrated", true).putInt("costume", 4)
                .putInt("color", 7).putInt("size_dp", 300).putInt("opacity", 55).commit();
        PetSettings settings = new PetSettings(context);
        assertEquals(4, settings.getCostume());
        assertEquals(7, settings.getColor());
        assertEquals(300, settings.getSizeDp());
        assertEquals(55, settings.getOpacity());
    }

    @Test public void corruptValuesAreClampedAndVisibilityPersists() {
        PetSettings settings = new PetSettings(context);
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 10, 100, 120, 180, 300, 999, Integer.MAX_VALUE}) {
            settings.setSizeDp(value);
            settings.setOpacity(value);
            assertEquals(Math.max(120, Math.min(300, value)), settings.getSizeDp());
            assertEquals(Math.max(10, Math.min(100, value)), settings.getOpacity());
            prefs.edit().putInt("size_dp", value).putInt("opacity", value).commit();
            assertEquals(Math.max(120, Math.min(300, value)), settings.getSizeDp());
            assertEquals(Math.max(10, Math.min(100, value)), settings.getOpacity());
        }
        settings.setCostume(-1);
        settings.setColor(99);
        assertEquals(0, settings.getCostume());
        assertEquals(0xFFFFFFFF, settings.combinedTint());
        settings.setVisible(true);
        assertTrue(new PetSettings(context).isVisible());
        settings.setVisible(false);
        assertFalse(new PetSettings(context).isVisible());
    }
}
