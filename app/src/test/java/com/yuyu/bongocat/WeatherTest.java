package com.yuyu.bongocat;

import android.app.Application;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class WeatherTest {
    private Weather forecast(int current, int... hourly) throws Exception {
        JSONObject json = new JSONObject("{current:{time:5000,temperature_2m:12},hourly:{time:[3600,7200,10800,14400]}}");
        json.getJSONObject("current").put("weather_code",current);
        json.getJSONObject("hourly").put("weather_code",new org.json.JSONArray(hourly));
        return Weather.parse(json);
    }
    @Test public void rainIncludesNowAndNextTwoHoursOnly() throws Exception {
        assertTrue(forecast(63,0,0,0,0).rain);
        assertTrue(forecast(0,0,0,81,0).rain);
        assertFalse(forecast(0,63,3,71,63).rain);
    }
    @Test public void coldBoundaryAndFreshness() throws Exception {
        assertTrue(Weather.cold(forecast(0,0,0,0,0).temperature));
        assertFalse(Weather.cold(12.1));
        assertFalse(Weather.fresh(0,100,1000));
        assertFalse(Weather.fresh(101,100,1000));
        assertFalse(Weather.fresh(100,1100,1000));
        assertTrue(Weather.fresh(100,1099,1000));
    }
}
