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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

final class Weather {
    static final long HOUR = 3_600_000L;
    final double temperature;
    final long observed;
    final boolean rain;
    final double tempMin;
    final double tempMax;
    final int weatherCode;

    static final class Place {
        final String displayName;
        final String title;
        final String detail;
        final double lat;
        final double lon;

        Place(String displayName, String title, String detail, double lat, double lon) {
            this.displayName = displayName;
            this.title = title;
            this.detail = detail;
            this.lat = lat;
            this.lon = lon;
        }

        String coords() {
            return String.format(Locale.US, "%.5f,%.5f", lat, lon);
        }
    }

    Weather(double temperature, long observed, boolean rain) {
        this(temperature, observed, rain, Double.NaN, Double.NaN, -1);
    }

    Weather(double temperature, long observed, boolean rain, double tempMin, double tempMax, int weatherCode) {
        this.temperature = temperature;
        this.observed = observed;
        this.rain = rain;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.weatherCode = weatherCode;
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("weather_window", Context.MODE_PRIVATE);
    }

    static JSONArray cities(String query) throws Exception {
        JSONObject json = getObject("https://geocoding-api.open-meteo.com/v1/search?count=10&language=zh&name=" + Uri.encode(query));
        JSONArray results = json.optJSONArray("results");
        return results == null ? new JSONArray() : results;
    }

    /** 优先用 OpenStreetMap Nominatim 精确检索到区县/街道/商圈级，失败或无结果时回退 Open-Meteo。 */
    static List<Place> searchPlaces(String query) throws Exception {
        Exception primaryError = null;
        try {
            JSONArray raw = getArray("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&accept-language=zh-CN&limit=10&q="
                    + Uri.encode(query));
            List<Place> places = parseNominatim(raw);
            if (!places.isEmpty()) return places;
        } catch (Exception error) {
            primaryError = error;
        }
        try {
            List<Place> fallback = parseOpenMeteoCities(cities(query));
            if (!fallback.isEmpty() || primaryError == null) return fallback;
        } catch (Exception fallbackError) {
            if (primaryError != null) throw primaryError;
            throw fallbackError;
        }
        throw primaryError;
    }

    static List<Place> parseNominatim(JSONArray array) {
        List<Place> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            double lat = parseDouble(item.optString("lat"));
            double lon = parseDouble(item.optString("lon"));
            if (!(Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) continue;
            String rawName = item.optString("name").trim();
            JSONObject addr = item.optJSONObject("address");
            String title = rawName;
            List<String> parts = new ArrayList<>();
            if (addr != null) {
                String[] keys = {"quarter", "neighbourhood", "suburb", "town", "village",
                        "city_district", "district", "county", "city", "municipality", "state", "province", "country"};
                Set<String> seen = new LinkedHashSet<>();
                for (String key : keys) {
                    String val = addr.optString(key).trim();
                    if (!val.isEmpty()) seen.add(val);
                }
                parts.addAll(seen);
                if (title.isEmpty() && !parts.isEmpty()) title = parts.get(0);
            }
            if (title.isEmpty()) {
                String dn = item.optString("display_name").trim();
                if (dn.isEmpty()) continue;
                title = dn.split(",")[0].trim();
            }
            // 构造简洁但有区县辨识度的保存名，例如「深圳市·南山区」或「上海市·徐家汇」。
            String displayName = buildDisplayName(title, addr);
            List<String> detailParts = new ArrayList<>();
            for (String p : parts) {
                if (!p.equals(title)) detailParts.add(p);
            }
            String detail = detailParts.isEmpty() ? item.optString("display_name").trim() : join(" · ", detailParts);
            if (isNearDuplicate(result, title, lat, lon)) continue;
            result.add(new Place(displayName, title, detail, lat, lon));
            if (result.size() >= 8) break;
        }
        return result;
    }

    private static String buildDisplayName(String title, JSONObject addr) {
        if (addr == null) return title;
        String parent = "";
        String[] parentKeys = {"city", "municipality", "county", "state", "province"};
        for (String key : parentKeys) {
            String val = addr.optString(key).trim();
            if (!val.isEmpty() && !val.equals(title) && !title.contains(val) && !val.contains(title)) {
                parent = val;
                break;
            }
        }
        if (parent.isEmpty()) return title;
        return parent + "·" + title;
    }

    static List<Place> parseOpenMeteoCities(JSONArray array) {
        List<Place> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || item.isNull("latitude") || item.isNull("longitude")) continue;
            double lat = item.optDouble("latitude", Double.NaN);
            double lon = item.optDouble("longitude", Double.NaN);
            if (!(Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) continue;
            String name = item.optString("name").trim();
            if (name.isEmpty()) continue;
            Set<String> hierarchy = new LinkedHashSet<>();
            for (String key : new String[]{"admin3", "admin2", "admin1", "country"}) {
                String val = item.optString(key).trim();
                if (!val.isEmpty() && !val.equals(name)) hierarchy.add(val);
            }
            String parent = "";
            for (String key : new String[]{"admin2", "admin1"}) {
                String val = item.optString(key).trim();
                if (!val.isEmpty() && !val.equals(name)) { parent = val; break; }
            }
            String displayName = parent.isEmpty() ? name : parent + "·" + name;
            String detail = join(" · ", new ArrayList<>(hierarchy));
            result.add(new Place(displayName, name, detail, lat, lon));
        }
        return result;
    }

    private static boolean isNearDuplicate(List<Place> existing, String title, double lat, double lon) {
        for (Place p : existing) {
            if (p.title.equals(title) && Math.abs(p.lat - lat) < 0.03 && Math.abs(p.lon - lon) < 0.03) {
                return true;
            }
        }
        return false;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return Double.NaN; }
    }

    private static String join(String sep, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    static Weather fetch(String coordinates) throws Exception {
        String[] parts = coordinates.split(",");
        double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
        if (!(Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) throw new IOException("经纬度无效");
        return parse(getObject("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&hourly=weather_code&forecast_hours=3"
                + "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=1&timezone=auto&timeformat=unixtime"));
    }

    static Weather parse(JSONObject json) throws JSONException {
        JSONObject current = json.getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        if (!Double.isFinite(temperature)) throw new JSONException("缺少气温");
        long time = current.getLong("time");
        int currentCode = current.getInt("weather_code");
        boolean rain = isRain(currentCode);
        JSONObject hourly = json.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time"), codes = hourly.getJSONArray("weather_code");
        // 检查当前时刻之后两小时内的雨类天气码。
        for (int i = 0; i < times.length(); i++) {
            long ahead = times.getLong(i) - time;
            if (ahead > 0 && ahead <= 7200 && !codes.isNull(i)) rain |= isRain(codes.getInt(i));
        }
        double tempMin = Double.NaN, tempMax = Double.NaN;
        JSONObject daily = json.optJSONObject("daily");
        if (daily != null) {
            JSONArray mins = daily.optJSONArray("temperature_2m_min");
            JSONArray maxs = daily.optJSONArray("temperature_2m_max");
            JSONArray dailyCodes = daily.optJSONArray("weather_code");
            if (mins != null && mins.length() > 0 && !mins.isNull(0)) tempMin = mins.optDouble(0, Double.NaN);
            if (maxs != null && maxs.length() > 0 && !maxs.isNull(0)) tempMax = maxs.optDouble(0, Double.NaN);
            if (dailyCodes != null && dailyCodes.length() > 0 && !dailyCodes.isNull(0)) {
                rain |= isRain(dailyCodes.optInt(0, -1));
            }
        }
        return new Weather(temperature, time * 1000L, rain, tempMin, tempMax, currentCode);
    }

    static boolean isRain(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99);
    }

    static String weatherLabel(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "有雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code == 61 || code == 80) return "小雨";
        if (code == 63 || code == 81) return "中雨";
        if (code == 65 || code == 82) return "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "有雪";
        if (code >= 95 && code <= 99) return "雷阵雨";
        return "";
    }

    static boolean cold(double temperature) { return temperature <= 12; }

    static boolean cold(double temperature, double tempMin) {
        return temperature <= 12 || (Double.isFinite(tempMin) && tempMin <= 12);
    }

    static boolean hot(double temperature, double tempMax) {
        return temperature >= 31 || (Double.isFinite(tempMax) && tempMax >= 31);
    }

    /** 第一行：今天多少度、下不下雨。 */
    static String headline(double temperature, boolean rain) {
        return "今天 " + Math.round(temperature) + "°C · " + (rain ? "会下雨" : "不下雨");
    }

    /** 第二行：穿衣 / 带伞 / 出行贴心提醒。 */
    static String advice(double temperature, double tempMin, double tempMax, boolean rain) {
        boolean isCold = cold(temperature, tempMin);
        boolean isHot = hot(temperature, tempMax);
        if (isCold && rain) return "记得加衣服，出门带伞";
        if (isCold) return "天凉了，记得加衣服";
        if (rain) return "出门记得带伞哦";
        if (isHot) return "天气偏热，注意防晒";
        return "天气不错，放心出门";
    }

    static boolean fresh(long timestamp, long now, long duration) {
        return timestamp > 0 && timestamp <= now && now - timestamp < duration;
    }

    void save(SharedPreferences.Editor edit, long now) {
        edit.putFloat("temp", (float)temperature).putBoolean("rain", rain)
                .putLong("observed", observed).putLong("fetched", now)
                .putInt("weatherCode", weatherCode);
        if (Double.isFinite(tempMin)) edit.putFloat("tempMin", (float)tempMin); else edit.remove("tempMin");
        if (Double.isFinite(tempMax)) edit.putFloat("tempMax", (float)tempMax); else edit.remove("tempMax");
        edit.apply();
    }

    private static String fetchBody(String url) throws Exception {
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
                return body.toString();
            }
        } finally { conn.disconnect(); }
    }

    private static JSONObject getObject(String url) throws Exception {
        return new JSONObject(fetchBody(url));
    }

    private static JSONArray getArray(String url) throws Exception {
        return new JSONArray(fetchBody(url));
    }
}
