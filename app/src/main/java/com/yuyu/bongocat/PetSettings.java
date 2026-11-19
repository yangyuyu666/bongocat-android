package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;

/** 保存配饰、花纹、身体颜色、大小、透明度、举牌样式、互动开关及悬浮窗位置。 */
public class PetSettings {
    private static final String PREF = "bongocat_pet";
    public static final String[] COSTUME_NAMES = {
            "无", "蝴蝶结", "帽子", "皇冠", "花", "围巾",
            "墨镜", "耳机", "小铃铛", "睡帽", "雨帽"};
    public static final String[] PATTERN_NAMES = {"纯色", "虎斑", "奶牛", "三花", "暹罗"};
    public static final String[] SIGN_STYLE_NAMES = {"奶油手账", "樱花粉纸", "抹茶便签"};
    public static final String[] COLOR_NAMES = {"白", "奶油", "粉", "蓝", "绿", "橘", "灰", "黑"};
    public static final int[] COLOR_TINTS = {
            0xFFFFFFFF, 0xFFFFF2CC, 0xFFFFD6E8, 0xFFD6E8FF,
            0xFFD6FFD6, 0xFFFFD9A0, 0xFFC8C8C8, 0xFF707070};

    public static final int COSTUME_SCARF = 5;
    public static final int COSTUME_SUNGLASSES = 6;
    public static final int COSTUME_RAINHAT = 10;

    private final SharedPreferences prefs;

    public PetSettings(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        migrateOldCostume();
    }

    private void migrateOldCostume() {
        if (prefs.getBoolean("costume_migrated", false)) return;
        int oldCostume = prefs.getInt("costume", 0);
        int oldColor = getColor();
        int[] map = {0, 5, 6, 7, 2};
        // 保留已选的非白换色；换色为白时才从旧换装恢复颜色。
        int color = oldColor == 0 && oldCostume >= 0 && oldCostume < map.length
                ? map[oldCostume] : oldColor;
        prefs.edit().putInt("color", color).putInt("costume", 0)
                .putBoolean("costume_migrated", true).apply();
    }

    private static int index(int value, int count) {
        return value >= 0 && value < count ? value : 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public int getCostume() { return index(prefs.getInt("costume", 0), COSTUME_NAMES.length); }
    public void setCostume(int i) { prefs.edit().putInt("costume", index(i, COSTUME_NAMES.length)).apply(); }
    public int getPattern() { return index(prefs.getInt("pattern", 0), PATTERN_NAMES.length); }
    public void setPattern(int i) { prefs.edit().putInt("pattern", index(i, PATTERN_NAMES.length)).apply(); }
    public int getSignStyle() { return index(prefs.getInt("sign_style", 0), SIGN_STYLE_NAMES.length); }
    public void setSignStyle(int i) { prefs.edit().putInt("sign_style", index(i, SIGN_STYLE_NAMES.length)).apply(); }
    public int getColor() { return index(prefs.getInt("color", 0), COLOR_NAMES.length); }
    public void setColor(int i) { prefs.edit().putInt("color", index(i, COLOR_NAMES.length)).apply(); }
    public int getSizeDp() { return clamp(prefs.getInt("size_dp", 180), 120, 300); }
    public void setSizeDp(int dp) { prefs.edit().putInt("size_dp", clamp(dp, 120, 300)).apply(); }
    public int getOpacity() { return clamp(prefs.getInt("opacity", 100), 10, 100); }
    public void setOpacity(int pct) { prefs.edit().putInt("opacity", clamp(pct, 10, 100)).apply(); }
    public boolean isTouchThrough() { return prefs.getBoolean("touch_through", false); }
    public void setTouchThrough(boolean enabled) { prefs.edit().putBoolean("touch_through", enabled).apply(); }
    public boolean isHapticEnabled() { return prefs.getBoolean("haptic_enabled", true); }
    public void setHapticEnabled(boolean enabled) { prefs.edit().putBoolean("haptic_enabled", enabled).apply(); }
    public boolean isMusicDanceEnabled() { return prefs.getBoolean("music_dance", true); }
    public void setMusicDanceEnabled(boolean enabled) { prefs.edit().putBoolean("music_dance", enabled).apply(); }
    public boolean isWeatherCostumeEnabled() { return prefs.getBoolean("weather_costume", false); }
    public void setWeatherCostumeEnabled(boolean enabled) { prefs.edit().putBoolean("weather_costume", enabled).apply(); }
    /** 拖动结束后保存窗口坐标，更新外观时不改坐标。 */
    public int getPosX() { return prefs.getInt("pos_x", 100); }
    public int getPosY() { return prefs.getInt("pos_y", 400); }
    public void setPos(int x, int y) { prefs.edit().putInt("pos_x", x).putInt("pos_y", y).apply(); }
    public boolean isVisible() { return prefs.getBoolean("visible", false); }
    public void setVisible(boolean v) { prefs.edit().putBoolean("visible", v).apply(); }

    /** 开启随天气换装且有新鲜预报时，按雨天/降温/晴热自动匹配雨帽、围巾或墨镜。 */
    public int effectiveCostume(Context context) {
        if (!isWeatherCostumeEnabled() || context == null) return getCostume();
        SharedPreferences w = Weather.preferences(context);
        if (!Weather.fresh(w.getLong("observed", 0), System.currentTimeMillis(), 2 * Weather.HOUR)) {
            return getCostume();
        }
        boolean rain = w.getBoolean("rain", false);
        double temp = w.getFloat("temp", 20f);
        double minTemp = w.contains("tempMin") ? w.getFloat("tempMin", Float.NaN) : Double.NaN;
        double maxTemp = w.contains("tempMax") ? w.getFloat("tempMax", Float.NaN) : Double.NaN;
        if (rain) return COSTUME_RAINHAT;
        if (Weather.cold(temp, minTemp)) return COSTUME_SCARF;
        if (temp >= 28 || (Double.isFinite(maxTemp) && maxTemp >= 30)) return COSTUME_SUNGLASSES;
        return getCostume();
    }

    /** 身体滤镜只取换色值，不叠加配饰颜色。 */
    public int combinedTint() {
        return COLOR_TINTS[getColor()];
    }
}
