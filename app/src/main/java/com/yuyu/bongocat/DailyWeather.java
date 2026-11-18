package com.yuyu.bongocat;

import android.content.SharedPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class DailyWeather {
    private final SharedPreferences prefs;

    DailyWeather(SharedPreferences prefs) { this.prefs = prefs; }

    static boolean sameDay(long time, long now, ZoneId zone) {
        if (time <= 0) return false;
        LocalDate day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate();
        return day.equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate());
    }

    boolean reminded(long now, ZoneId zone) {
        // 3.17 分别保存雨天和低温提醒，升级后沿用当天的记录。
        return sameDay(prefs.getLong("reminded", 0), now, zone)
                || sameDay(prefs.getLong("lastCold", 0), now, zone)
                || sameDay(prefs.getLong("lastRain", 0), now, zone);
    }

    boolean checked(long now, ZoneId zone) {
        return sameDay(prefs.getLong("fetched", 0), now, zone);
    }

    boolean beginCheck(long now, ZoneId zone) {
        if (prefs.getString("coords", "").isEmpty() || checked(now, zone) || reminded(now, zone)) return false;
        long attempt = prefs.getLong("attempted", 0);
        if (sameDay(attempt, now, zone) && Weather.fresh(attempt, now, Weather.HOUR / 4)) return false;
        prefs.edit().putLong("attempted", now).apply();
        return true;
    }

    void setCity(String name, String coords) {
        SharedPreferences.Editor edit = prefs.edit().putString("city", name).putString("coords", coords);
        // 换城后丢弃旧预报，保留当天的调用和提醒记录。
        if (!coords.equals(prefs.getString("coords", ""))) edit.remove("observed").remove("temp").remove("rain").remove("attempted");
        edit.apply();
    }
}
