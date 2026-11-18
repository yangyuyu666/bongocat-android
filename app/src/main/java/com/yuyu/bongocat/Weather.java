package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class Weather {
    static final long HOUR = 3_600_000L;
    final double temperature;
    final long observed;
    final boolean rain;

    Weather(double temperature, long observed, boolean rain) {
        this.temperature = temperature;
        this.observed = observed;
        this.rain = rain;
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("weather_window", Context.MODE_PRIVATE);
    }

    static JSONArray cities(String query) throws Exception {
        JSONObject json = get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=zh&name=" + Uri.encode(query));
        JSONArray results = json.optJSONArray("results");
        return results == null ? new JSONArray() : results;
    }

    static Weather fetch(String coordinates) throws Exception {
        String[] parts = coordinates.split(",");
        double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
        if (!(Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) throw new IOException("经纬度无效");
        return parse(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&hourly=weather_code&forecast_hours=3&timeformat=unixtime"));
    }

    static Weather parse(JSONObject json) throws JSONException {
        JSONObject current = json.getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        if (!Double.isFinite(temperature)) throw new JSONException("缺少气温");
        long time = current.getLong("time");
        boolean rain = isRain(current.getInt("weather_code"));
        JSONObject hourly = json.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time"), codes = hourly.getJSONArray("weather_code");
        // 只看当前时刻之后两小时内的雨类天气码。
        for (int i = 0; i < times.length(); i++) {
            long ahead = times.getLong(i) - time;
            if (ahead > 0 && ahead <= 7200 && !codes.isNull(i)) rain |= isRain(codes.getInt(i));
        }
        return new Weather(temperature, time * 1000L, rain);
    }

    private static boolean isRain(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95 && code <= 99;
    }

    static boolean cold(double temperature) { return temperature <= 12; }

    static boolean fresh(long timestamp, long now, long duration) {
        return timestamp > 0 && timestamp <= now && now - timestamp < duration;
    }

    void save(SharedPreferences.Editor edit, long now) {
        edit.putFloat("temp", (float)temperature).putBoolean("rain", rain)
                .putLong("observed", observed).putLong("fetched", now).apply();
    }

    private static JSONObject get(String url) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setRequestProperty("User-Agent", "BongoCat-Android/" + BuildConfig.VERSION_NAME);
        try {
            if (conn.getResponseCode() != 200) throw new IOException("天气服务暂不可用");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                return new JSONObject(body.toString());
            }
        } finally { conn.disconnect(); }
    }
}
