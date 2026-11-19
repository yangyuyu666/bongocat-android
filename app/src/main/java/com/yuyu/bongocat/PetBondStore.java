package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 轻度陪伴养成：记录亲密度、小鱼干与昼夜问候，克制无打扰。 */
public final class PetBondStore {
    public static final int FEED_COST = 3;
    private static final int[] THRESHOLDS = {0, 30, 80, 160, 300};
    private static final String[] TITLES = {"初次相遇", "熟悉伙伴", "粘人小猫", "贴心闺蜜", "灵魂伴侣"};
    private final SharedPreferences prefs;

    public PetBondStore(Context context) {
        prefs = context.getSharedPreferences("bongocat_bond", Context.MODE_PRIVATE);
    }

    public int xp() { return Math.max(0, prefs.getInt("xp", 0)); }
    public int fish() { return Math.max(0, prefs.getInt("fish", 5)); }
    public int interactions() { return Math.max(0, prefs.getInt("interactions", 0)); }

    public int level() {
        int current = xp();
        int lv = 1;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (current >= THRESHOLDS[i]) lv = i + 1;
        }
        return lv;
    }

    public String levelTitle() { return TITLES[level() - 1]; }

    public int progressPercent() {
        int lv = level();
        if (lv >= THRESHOLDS.length) return 100;
        int base = THRESHOLDS[lv - 1], next = THRESHOLDS[lv];
        return Math.max(0, Math.min(100, Math.round((xp() - base) * 100f / (next - base))));
    }

    public String nextLevelHint() {
        int lv = level();
        if (lv >= THRESHOLDS.length) return "已满级 · 亲密值 " + xp();
        return "亲密 " + xp() + " / " + THRESHOLDS[lv];
    }

    /** 摸头/挠肚皮奖励（带 5 秒冷却，避免连点刷爆）。 */
    public boolean rewardPet(long nowMs) {
        long last = prefs.getLong("last_pet_reward", 0);
        if (nowMs - last < 5000L && last <= nowMs) return false;
        prefs.edit()
                .putInt("xp", xp() + 1)
                .putInt("fish", fish() + 1)
                .putInt("interactions", interactions() + 1)
                .putLong("last_pet_reward", nowMs)
                .apply();
        return true;
    }

    public void rewardTask() {
        prefs.edit().putInt("xp", xp() + 3).putInt("fish", fish() + 2).apply();
    }

    public void rewardFocus() {
        prefs.edit().putInt("xp", xp() + 10).putInt("fish", fish() + 5).apply();
    }

    /** 喂小鱼干：消耗 3 条小鱼干，换取 +6 亲密度。 */
    public boolean feedFish() {
        int currentFish = fish();
        if (currentFish < FEED_COST) return false;
        prefs.edit().putInt("fish", currentFish - FEED_COST).putInt("xp", xp() + 6).apply();
        return true;
    }

    /** 昼夜作息问候：早安（06:00~10:30）与深夜提醒（23:00~04:59），每个时段每天仅提醒一次。 */
    public String[] consumeGreeting(long nowMs, ZoneId zone) {
        java.time.ZonedDateTime dt = Instant.ofEpochMilli(nowMs).atZone(zone);
        LocalDate today = dt.toLocalDate();
        int hour = dt.getHour(), minute = dt.getMinute();
        String dayKey = today.toString();
        if ((hour >= 6 && hour < 10) || (hour == 10 && minute <= 30)) {
            if (dayKey.equals(prefs.getString("greet_morning", ""))) return null;
            prefs.edit().putString("greet_morning", dayKey).putInt("xp", xp() + 3).putInt("fish", fish() + 2).apply();
            return new String[]{"早安呀，新的一天", "小猫今天也会陪着你"};
        }
        if (hour >= 23 || hour < 5) {
            // 凌晨 0~4 点算作前一夜，避免 23:55 和 00:05 连续提醒两次。
            String nightKey = (hour < 5 ? today.minusDays(1) : today).toString();
            if (nightKey.equals(prefs.getString("greet_night", ""))) return null;
            prefs.edit().putString("greet_night", nightKey).apply();
            return new String[]{"太晚啦，早点休息", "小猫陪你一起睡觉"};
        }
        return null;
    }
}
