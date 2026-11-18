package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;

/** 保存配饰、身体颜色、大小、透明度及悬浮窗位置。 */
public class PetSettings {
    private static final String PREF = "bongocat_pet";
    public static final String[] COSTUME_NAMES = {"无", "蝴蝶结", "帽子", "皇冠", "花", "围巾"};
    public static final String[] COLOR_NAMES = {"白", "奶油", "粉", "蓝", "绿", "橘", "灰", "黑"};
    public static final int[] COLOR_TINTS = {
            0xFFFFFFFF, 0xFFFFF2CC, 0xFFFFD6E8, 0xFFD6E8FF,
            0xFFD6FFD6, 0xFFFFD9A0, 0xFFC8C8C8, 0xFF707070};

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
    public int getColor() { return index(prefs.getInt("color", 0), COLOR_NAMES.length); }
    public void setColor(int i) { prefs.edit().putInt("color", index(i, COLOR_NAMES.length)).apply(); }
    public int getSizeDp() { return clamp(prefs.getInt("size_dp", 180), 120, 300); }
    public void setSizeDp(int dp) { prefs.edit().putInt("size_dp", clamp(dp, 120, 300)).apply(); }
    public int getOpacity() { return clamp(prefs.getInt("opacity", 100), 10, 100); }
    public void setOpacity(int pct) { prefs.edit().putInt("opacity", clamp(pct, 10, 100)).apply(); }
    /** 拖动结束后保存窗口坐标，更新外观时不改坐标。 */
    public int getPosX() { return prefs.getInt("pos_x", 100); }
    public int getPosY() { return prefs.getInt("pos_y", 400); }
    public void setPos(int x, int y) { prefs.edit().putInt("pos_x", x).putInt("pos_y", y).apply(); }
    public boolean isVisible() { return prefs.getBoolean("visible", false); }
    public void setVisible(boolean v) { prefs.edit().putBoolean("visible", v).apply(); }

    /** 身体滤镜只取换色值，不叠加配饰颜色。 */
    public int combinedTint() {
        return COLOR_TINTS[getColor()];
    }
}
