package com.yuyu.bongocat;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.Context;
import android.view.WindowManager;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DailyWeatherTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final long evening = Instant.parse("2026-11-18T15:59:55Z").toEpochMilli();
    private SharedPreferences prefs;
    private DailyWeather daily;

    @Before public void setup() {
        prefs = Weather.preferences(RuntimeEnvironment.getApplication());
        prefs.edit().clear().commit();
        daily = new DailyWeather(prefs);
        daily.setCity("上海", "31,121");
    }

    @Test public void sunnyCheckSurvivesReopenAndExpiresAtLocalMidnight() {
        assertTrue(daily.beginCheck(evening, zone));
        new Weather(21, evening, false).save(prefs.edit(), evening);
        DailyWeather reopened = new DailyWeather(prefs);
        assertFalse(reopened.beginCheck(evening + 1000, zone));
        assertTrue(reopened.beginCheck(evening + 5000, zone));
    }

    @Test public void failedAttemptBacksOffAcrossReopenWithoutUsingDailyCheck() {
        long morning = evening - 12*Weather.HOUR;
        assertTrue(daily.beginCheck(morning, zone));
        assertFalse(new DailyWeather(prefs).beginCheck(morning + 60000, zone));
        assertTrue(daily.beginCheck(morning + Weather.HOUR/4, zone));
    }

    @Test public void switchingCityDropsCachedAlertAndKeepsDailyBudget() {
        new Weather(6, evening, true).save(prefs.edit(), evening);
        daily.setCity("北京", "40,116");
        assertFalse(prefs.contains("observed"));
        assertFalse(daily.beginCheck(evening + 1000, zone));
        prefs.edit().putLong("reminded", evening).apply();
        daily.setCity("", "");
        daily.setCity("上海", "31,121");
        assertTrue(daily.reminded(evening + 1000, zone));
        assertFalse(daily.reminded(evening + 5000, zone));
    }

    @Test public void upgradeHonorsEarlierRainReminder() {
        prefs.edit().putLong("lastRain", evening).commit();
        assertFalse(daily.beginCheck(evening + 1000, zone));
        assertTrue(daily.beginCheck(evening + 5000, zone));
    }

    @Test public void cloudWaitsForPetAndShowsOnceWithoutUserInput() {
        Application app = RuntimeEnvironment.getApplication();
        WindowManager manager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams cat = new WindowManager.LayoutParams();
        cat.width = cat.height = 180;
        long now = System.currentTimeMillis();
        new Weather(8, now, true).save(prefs.edit(), now);
        WeatherWindow first = new WeatherWindow(app, manager);
        WeatherWindow reopened = new WeatherWindow(app, manager);
        try {
            first.tick(cat, true);
            assertFalse(prefs.contains("reminded"));
            ShadowSystemClock.advanceBy(Duration.ofSeconds(8));
            first.tick(cat, false);
            assertFalse(prefs.contains("reminded"));
            first.tick(cat, true);
            long shown = prefs.getLong("reminded", 0);
            assertTrue(shown > 0);
            first.close();
            reopened.tick(cat, true);
            ShadowSystemClock.advanceBy(Duration.ofSeconds(10));
            reopened.tick(cat, true);
            assertEquals(shown, prefs.getLong("reminded", 0));
            assertFalse(prefs.contains("attempted"));
        } finally {
            first.close();
            reopened.close();
        }
    }
}
