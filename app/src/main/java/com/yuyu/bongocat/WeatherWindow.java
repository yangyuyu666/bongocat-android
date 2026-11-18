package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
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
        if (!canShow || cloud != null || !Weather.fresh(prefs.getLong("observed", 0), now, 2*Weather.HOUR)) return;
        boolean cold = Weather.cold(prefs.getFloat("temp", 99));
        boolean rain = prefs.getBoolean("rain", false);
        if ((cold || rain) && show(cat, cold, rain)) {
            prefs.edit().putLong("reminded", now).apply();
        }
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

    private boolean show(WindowManager.LayoutParams cat, boolean cold, boolean rain) {
        float density = context.getResources().getDisplayMetrics().density;
        cloud = new Bubble(context, cold, rain);
        window = new WindowManager.LayoutParams(Math.round(134*density), Math.round((cold && rain ? 65 : 49)*density),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        window.gravity = Gravity.TOP | Gravity.LEFT;
        window.alpha = .8f;
        place(cat);
        try { manager.addView(cloud, window); }
        catch (RuntimeException error) { cloud = null; return false; }
        handler.postDelayed(dismiss, 9000);
        return true;
    }

    private void place(WindowManager.LayoutParams cat) {
        Point screen = new Point();
        manager.getDefaultDisplay().getSize(screen);
        window.x = Math.max(0, Math.min(screen.x-window.width, cat.x+(cat.width-window.width)/2));
        window.y = Math.max(0, cat.y-window.height + cat.height/8);
    }

    void follow(WindowManager.LayoutParams cat) {
        if (cloud == null) return;
        place(cat);
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

    private static final class Bubble extends View {
        private final boolean cold, rain;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bubble(Context context, boolean cold, boolean rain) {
            super(context);
            this.cold = cold;
            this.rain = rain;
            setContentDescription((cold ? "记到加衣服" : "") + (rain ? " 出门记得带伞" : ""));
        }
        @Override protected void onDraw(Canvas canvas) {
            float h = cold && rain ? 65 : 49;
            canvas.save();
            canvas.scale(getWidth()/134f, getHeight()/h);
            Path outline = new Path();
            outline.moveTo(23, h-10);
            outline.cubicTo(1,h-9, 0,17, 20,16);
            outline.cubicTo(21,3, 38,0, 48,8);
            outline.cubicTo(61,-1, 78,1, 84,9);
            outline.cubicTo(96,3, 114,6, 116,16);
            outline.cubicTo(134,16, 137,h-10, 114,h-10);
            outline.lineTo(73,h-10);
            outline.quadTo(70,h-4,61,h-2);
            outline.lineTo(63,h-10);
            outline.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xfffffaf5);
            canvas.drawPath(outline,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setColor(0xff594e59);
            canvas.drawPath(outline,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(14);
            if (cold) canvas.drawText("记到加衣服",67,29,paint);
            if (rain) {
                float y = cold ? 47 : 29;
                canvas.drawText("出门记得带伞",76,y,paint);
                paint.setColor(0xffcc668c);
                canvas.drawArc(16,y-12,34,y+6,180,180,true,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.6f);
                Path handle = new Path();
                handle.moveTo(25,y-4); handle.lineTo(25,y+1); handle.quadTo(25,y+6,21,y+3);
                canvas.drawPath(handle,paint);
            }
            canvas.restore();
        }
    }
}
