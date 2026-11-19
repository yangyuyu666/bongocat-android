package com.yuyu.bongocat;

import android.app.Application;
import java.util.List;
import org.json.JSONArray;
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
        json.getJSONObject("current").put("weather_code", current);
        json.getJSONObject("hourly").put("weather_code", new JSONArray(hourly));
        return Weather.parse(json);
    }

    @Test public void rainIncludesNowAndNextTwoHoursOnly() throws Exception {
        assertTrue(forecast(63, 0, 0, 0, 0).rain);
        assertTrue(forecast(0, 0, 0, 81, 0).rain);
        assertFalse(forecast(0, 63, 3, 71, 63).rain);
    }

    @Test public void dailyForecastIncludesTodayRangeAndRain() throws Exception {
        JSONObject json = new JSONObject("{"
                + "current:{time:5000,temperature_2m:18.4,weather_code:2},"
                + "hourly:{time:[3600,7200,10800],weather_code:[2,2,2]},"
                + "daily:{temperature_2m_min:[11.2],temperature_2m_max:[20.6],weather_code:[61]}"
                + "}");
        Weather w = Weather.parse(json);
        assertEquals(18.4, w.temperature, 0.01);
        assertEquals(11.2, w.tempMin, 0.01);
        assertEquals(20.6, w.tempMax, 0.01);
        assertTrue(w.rain);
        assertTrue(Weather.cold(w.temperature, w.tempMin));
        assertEquals("多云", Weather.weatherLabel(w.weatherCode));
    }

    @Test public void headlineAndAdviceCoverAllWeatherCombinations() {
        assertEquals("今天 9°C · 会下雨", Weather.headline(9.2, true));
        assertEquals("记得加衣服，出门带伞", Weather.advice(9.2, 7, 13, true));

        assertEquals("今天 10°C · 不下雨", Weather.headline(10.0, false));
        assertEquals("天凉了，记得加衣服", Weather.advice(10.0, 8, 15, false));

        assertEquals("今天 19°C · 会下雨", Weather.headline(19.1, true));
        assertEquals("出门记得带伞哦", Weather.advice(19.1, 16, 22, true));

        assertEquals("今天 22°C · 不下雨", Weather.headline(22.4, false));
        assertEquals("天气不错，放心出门", Weather.advice(22.4, 17, 25, false));

        assertEquals("今天 33°C · 不下雨", Weather.headline(32.8, false));
        assertEquals("天气偏热，注意防晒", Weather.advice(32.8, 25, 34, false));
    }

    @Test public void nominatimParsesDistrictsAndDeduplicatesNearbyEntries() throws Exception {
        JSONArray raw = new JSONArray("["
                + "{name:'徐家汇',lat:'31.19501',lon:'121.43681',address:{quarter:'徐家汇',suburb:'徐家汇街道',city:'徐汇区',state:'上海市',country:'中国'}},"
                + "{name:'徐家汇',lat:'31.19520',lon:'121.43700',address:{railway:'徐家汇',quarter:'徐家汇',suburb:'徐家汇街道',city:'徐汇区',state:'上海市',country:'中国'}},"
                + "{name:'南山区',lat:'22.53601',lon:'113.92562',address:{city_district:'南山区',city:'深圳市',state:'广东省',country:'中国'}}"
                + "]");
        List<Weather.Place> places = Weather.parseNominatim(raw);
        assertEquals(2, places.size());
        assertEquals("徐汇区·徐家汇", places.get(0).displayName);
        assertTrue(places.get(0).detail.contains("徐汇区"));
        assertTrue(places.get(0).detail.contains("上海市"));
        assertEquals("31.19501,121.43681", places.get(0).coords());
        assertEquals("深圳市·南山区", places.get(1).displayName);
        assertEquals("22.53601,113.92562", places.get(1).coords());
    }

    @Test public void openMeteoFallbackKeepsFullAdministrativeHierarchy() throws Exception {
        JSONArray raw = new JSONArray("["
                + "{name:'余杭',latitude:30.418,longitude:120.299,admin3:'余杭区',admin2:'杭州市',admin1:'浙江省',country:'中国'}"
                + "]");
        List<Weather.Place> places = Weather.parseOpenMeteoCities(raw);
        assertEquals(1, places.size());
        assertEquals("杭州市·余杭", places.get(0).displayName);
        assertEquals("余杭区 · 杭州市 · 浙江省 · 中国", places.get(0).detail);
    }

    @Test public void coldBoundaryAndFreshness() throws Exception {
        assertTrue(Weather.cold(forecast(0, 0, 0, 0, 0).temperature));
        assertFalse(Weather.cold(12.1));
        assertFalse(Weather.fresh(0, 100, 1000));
        assertFalse(Weather.fresh(101, 100, 1000));
        assertFalse(Weather.fresh(100, 1100, 1000));
        assertTrue(Weather.fresh(100, 1099, 1000));
    }
}
