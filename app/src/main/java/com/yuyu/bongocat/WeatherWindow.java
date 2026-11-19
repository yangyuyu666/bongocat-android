package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WeatherWindow {
    static final int ICON_SUN = 0;
    static final int ICON_RAIN = 1;
    static final int ICON_COLD = 2;
    static final int ICON_BATTERY = 3;
    static final int ICON_MOON = 4;

    private final Context context;
    private final SharedPreferences prefs;
    private final WindowManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable dismiss = this::hide;
    private boolean loading, closed;
    private final DailyWeather daily;
    private long readyAt;
    private String currentCity = "";
    private View cloud;
    private WindowManager.LayoutParams window;

    WeatherWindow(Context context, WindowManager manager) {
        this.context = context;
        this.manager = manager;
        prefs = Weather.preferences(context);
        daily = new DailyWeather(prefs);
    }

    boolean isShowing() { return cloud != null; }

    void tick(WindowManager.LayoutParams cat, boolean canShow) {
        String coords = prefs.getString("coords", "");
        if (!coords.equals(currentCity)) {
            hide();
            currentCity = coords;
            readyAt = SystemClock.elapsedRealtime() + 8000;
        }
        if (!canShow) hide();
        if (coords.isEmpty() || closed || SystemClock.elapsedRealtime() < readyAt) return;
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        if (daily.reminded(now, zone)) return;
        if (!daily.checked(now, zone)) {
            if (!loading && daily.beginCheck(now, zone)) refresh(coords);
            return;
        }
        if (!canShow || cloud != null || !Weather.fresh(prefs.getLong("observed", 0), now, 2 * Weather.HOUR)) return;
        if (showWeatherNow(cat)) {
            prefs.edit().putLong("reminded", now).apply();
        }
    }

    /** 主动预览今日天气云朵播报（供天气窗台手动触发）。 */
    boolean previewWeather(WindowManager.LayoutParams cat) {
        if (closed) return false;
        long now = System.currentTimeMillis();
        if (Weather.fresh(prefs.getLong("observed", 0), now, 2 * Weather.HOUR)) {
            hide();
            return showWeatherNow(cat);
        }
        String coords = prefs.getString("coords", "");
        if (coords.isEmpty()) return false;
        refresh(coords);
        return false;
    }

    private boolean showWeatherNow(WindowManager.LayoutParams cat) {
        double temp = prefs.getFloat("temp", 99);
        double minTemp = prefs.contains("tempMin") ? prefs.getFloat("tempMin", Float.NaN) : Double.NaN;
        double maxTemp = prefs.contains("tempMax") ? prefs.getFloat("tempMax", Float.NaN) : Double.NaN;
        boolean rain = prefs.getBoolean("rain", false);
        boolean cold = Weather.cold(temp, minTemp);
        int icon = rain ? ICON_RAIN : (cold ? ICON_COLD : ICON_SUN);
        int accent = (rain || cold) ? 0xFFC9557D : 0xFF3E8C63;
        return showBubble(cat, Weather.headline(temp, rain),
                Weather.advice(temp, minTemp, maxTemp, rain), icon, accent, 10000);
    }

    /** 用同款手绘云朵播报低电量或早安/晚安问候。 */
    boolean showMessage(WindowManager.LayoutParams cat, String line1, String line2, int icon, long durationMs) {
        if (closed || cat == null) return false;
        hide();
        int accent = icon == ICON_BATTERY ? 0xFFD9534F : (icon == ICON_MOON ? 0xFF6A6DA8 : 0xFFD96B90);
        return showBubble(cat, line1, line2, icon, accent, durationMs);
    }

    private void refresh(String coords) {
        loading = true;
        worker.execute(() -> {
            Weather weather = null;
            try { weather = Weather.fetch(coords); } catch (Exception ignored) { /* 下次刷新时重试。 */ }
            Weather result = weather;
            handler.post(() -> {
                loading = false;
                if (result != null && coords.equals(prefs.getString("coords", "")))
                    result.save(prefs.edit(), System.currentTimeMillis());
            });
        });
    }

    private boolean showBubble(WindowManager.LayoutParams cat, String line1, String line2,
                               int icon, int accentColor, long durationMs) {
        float density = context.getResources().getDisplayMetrics().density;
        cloud = new Bubble(context, line1, line2, icon, accentColor);
        window = new WindowManager.LayoutParams(Math.round(196 * density), Math.round(70 * density),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        window.gravity = Gravity.TOP | Gravity.LEFT;
        window.alpha = Build.VERSION.SDK_INT >= 31
                ? Math.min(.8f, context.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch()) : .8f;
        if (!place(cat)) { cloud = null; return false; }
        try { manager.addView(cloud, window); }
        catch (RuntimeException error) { cloud = null; return false; }
        handler.postDelayed(dismiss, durationMs);
        return true;
    }

    private boolean place(WindowManager.LayoutParams cat) {
        Point screen = new Point();
        manager.getDefaultDisplay().getSize(screen);
        window.x = Math.max(0, Math.min(screen.x - window.width, cat.x + (cat.width - window.width) / 2));
        window.y = Math.max(0, cat.y - window.height + cat.height / 8);
        if ((cat.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
            // 穿透时把云朵放在猫窗外，避免两个窗口的遮挡率叠加。
            if (cat.y >= window.height) window.y = cat.y - window.height;
            else if (cat.y + cat.height + window.height <= screen.y) window.y = cat.y + cat.height;
            else if (cat.x >= window.width) window.x = cat.x - window.width;
            else if (cat.x + cat.width + window.width <= screen.x) window.x = cat.x + cat.width;
            else return false;
        }
        return true;
    }

    void follow(WindowManager.LayoutParams cat) {
        if (cloud == null) return;
        if (!place(cat)) { hide(); return; }
        try { manager.updateViewLayout(cloud, window); }
        catch (RuntimeException error) { hide(); }
    }

    private void hide() {
        handler.removeCallbacks(dismiss);
        if (cloud == null) return;
        try { manager.removeView(cloud); } catch (IllegalArgumentException ignored) { /* 系统已移除窗口。 */ }
        cloud = null;
    }

    void close() {
        closed = true;
        hide();
        worker.shutdownNow();
    }

    static final class Bubble extends View {
        private final int icon, accentColor;
        private final String line1, line2;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bubble(Context context, double temp, double minTemp, double maxTemp, boolean rain) {
            this(context,
                    Weather.headline(temp, rain),
                    Weather.advice(temp, minTemp, maxTemp, rain),
                    rain ? ICON_RAIN : (Weather.cold(temp, minTemp) ? ICON_COLD : ICON_SUN),
                    (rain || Weather.cold(temp, minTemp)) ? 0xFFC9557D : 0xFF3E8C63);
        }

        Bubble(Context context, String line1, String line2, int icon, int accentColor) {
            super(context);
            this.line1 = line1;
            this.line2 = line2;
            this.icon = icon;
            this.accentColor = accentColor;
            setContentDescription(line1 + "，" + line2);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = 196f, h = 70f;
            canvas.save();
            canvas.scale(getWidth() / w, getHeight() / h);

            Path outline = new Path();
            outline.moveTo(28, h - 11);
            outline.cubicTo(4, h - 10, 2, 19, 24, 17);
            outline.cubicTo(26, 3, 50, 1, 64, 9);
            outline.cubicTo(82, -1, 114, 1, 126, 9);
            outline.cubicTo(144, 2, 169, 6, 172, 17);
            outline.cubicTo(194, 18, 194, h - 10, 168, h - 11);
            outline.lineTo(106, h - 11);
            outline.quadTo(102, h - 4, 92, h - 2);
            outline.lineTo(95, h - 11);
            outline.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFBF6);
            canvas.drawPath(outline, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setColor(0xFF4F4452);
            canvas.drawPath(outline, paint);

            drawIcon(canvas);

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(13.5f);
            paint.setColor(0xFF3E3440);
            canvas.drawText(line1, 108, 29, paint);

            paint.setTextSize(11.8f);
            paint.setColor(accentColor);
            canvas.drawText(line2, 108, 48, paint);

            canvas.restore();
        }

        private void drawIcon(Canvas canvas) {
            float cx = 27f, cy = 36f;
            if (icon == ICON_RAIN) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFD96B90);
                canvas.drawArc(cx - 9, cy - 10, cx + 9, cy + 8, 180, 180, true, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.7f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                Path handle = new Path();
                handle.moveTo(cx, cy - 1);
                handle.lineTo(cx, cy + 5.5f);
                handle.quadTo(cx, cy + 9.5f, cx - 3.8f, cy + 7.2f);
                canvas.drawPath(handle, paint);
            } else if (icon == ICON_COLD) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFF5D9BD5);
                paint.setStrokeWidth(1.7f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI / 3.0;
                    float dx = (float) Math.cos(a) * 7.5f, dy = (float) Math.sin(a) * 7.5f;
                    canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint);
                }
            } else if (icon == ICON_BATTERY) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFFD9534F);
                paint.setStrokeWidth(1.8f);
                canvas.drawRoundRect(cx - 8.5f, cy - 5.5f, cx + 7f, cy + 5.5f, 2f, 2f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(cx + 7.5f, cy - 2.5f, cx + 10f, cy + 2.5f, paint);
                canvas.drawRect(cx - 6f, cy - 3f, cx - 2.5f, cy + 3f, paint);
            } else if (icon == ICON_MOON) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF7D85C4);
                canvas.drawCircle(cx, cy, 7.5f, paint);
                paint.setColor(0xFFFFFBF6);
                canvas.drawCircle(cx + 3.8f, cy - 2.2f, 6.4f, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFF4B643);
                canvas.drawCircle(cx, cy, 4.8f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.6f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4.0;
                    canvas.drawLine(
                            cx + (float) Math.cos(a) * 6.8f, cy + (float) Math.sin(a) * 6.8f,
                            cx + (float) Math.cos(a) * 9.2f, cy + (float) Math.sin(a) * 9.2f, paint);
                }
            }
        }
    }
}
