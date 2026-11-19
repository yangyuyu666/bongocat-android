package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;

/** 统一的暖奶油猫咖视觉风格工具：无第三方依赖，轻量原生绘制。 */
final class UiStyle {
    static final int BG = 0xFFFAF5F1;
    static final int SURFACE = 0xFFFFFFFF;
    static final int SURFACE_SOFT = 0xFFF7F0EC;
    static final int BORDER = 0xFFEDE2DC;
    static final int INK = 0xFF3E3440;
    static final int MUTED = 0xFF887C8B;
    static final int ACCENT = 0xFFD96B90;
    static final int ACCENT_DARK = 0xFFBE5378;
    static final int ACCENT_SOFT = 0xFFFCECF1;
    static final int ACCENT_BORDER = 0xFFF1B8CB;
    static final int SUCCESS = 0xFF349464;
    static final int SUCCESS_SOFT = 0xFFEAF7F0;
    static final int STAGE_TOP = 0xFFFFFDFB;
    static final int STAGE_BOTTOM = 0xFFFCEFF3;
    static final int RIPPLE_PINK = 0x26D96B90;
    static final int RIPPLE_LIGHT = 0x33FFFFFF;

    private UiStyle() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 统一浅色暖奶油状态栏与导航栏，避免纯白刺眼或深色割裂。 */
    static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;
        window.setStatusBarColor(BG);
        if (Build.VERSION.SDK_INT >= 27) window.setNavigationBarColor(BG);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    static GradientDrawable card(Context context, int fill, int stroke, float radiusDp) {
        return card(context, fill, stroke, 1f, radiusDp);
    }

    static GradientDrawable card(Context context, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(Math.max(1, dp(context, strokeDp)), stroke);
        return drawable;
    }

    static GradientDrawable gradientCard(Context context, int topColor, int bottomColor, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{topColor, bottomColor});
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(Math.max(1, dp(context, 1f)), stroke);
        return drawable;
    }

    static Drawable ripple(Context context, int fill, int stroke, float strokeDp, float radiusDp, int rippleColor) {
        GradientDrawable content = card(context, fill, stroke, strokeDp, radiusDp);
        GradientDrawable mask = card(context, Color.WHITE, 0, 0f, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    static void stylePrimaryButton(Context context, Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setStateListAnimator(null);
        button.setBackground(ripple(context, ACCENT, 0, 0f, 16f, RIPPLE_LIGHT));
    }

    static void styleSecondaryButton(Context context, Button button) {
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setStateListAnimator(null);
        button.setBackground(ripple(context, ACCENT_SOFT, ACCENT_BORDER, 1f, 16f, RIPPLE_PINK));
    }

    static void styleSurfaceButton(Context context, Button button) {
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setStateListAnimator(null);
        button.setBackground(ripple(context, SURFACE, BORDER, 1f, 16f, RIPPLE_PINK));
    }

    static void styleSeekBar(SeekBar seekBar) {
        seekBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFEBDCE2));
    }
}
