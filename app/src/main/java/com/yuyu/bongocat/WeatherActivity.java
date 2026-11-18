package com.yuyu.bongocat;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class WeatherActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout choices;
    private TextView status;
    private EditText query;
    private int generation;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = Weather.preferences(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);
        setContentView(scroll);
        status = new TextView(this);
        status.setTextSize(19);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        layout.addView(status);
        showCity();
        query = new EditText(this);
        query.setSingleLine();
        query.setHint("输入城市名或拼音");
        layout.addView(query);
        button(layout, "搜索城市", v -> search());
        query.setOnEditorActionListener((v, id, event) -> { search(); return true; });
        choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        layout.addView(choices);
        button(layout, "关闭天气提醒", v -> {
            generation++;
            new DailyWeather(prefs).setCity("", "");
            finish();
        });
        TextView credit = new TextView(this);
        credit.setText("天气数据 · Open-Meteo");
        credit.setPadding(0, pad, 0, pad);
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))));
        layout.addView(credit);
    }

    private void showCity() {
        String name = prefs.getString("city", "");
        if (name.isEmpty()) status.setText("天气窗台 · 选择城市");
        else if (Weather.fresh(prefs.getLong("observed", 0), System.currentTimeMillis(), 2*Weather.HOUR))
            status.setText(name + " · " + Math.round(prefs.getFloat("temp", 0)) + "°C");
        else status.setText(name + " · 天气提醒已开启");
    }

    private void button(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void search() {
        String text = query.getText().toString().trim();
        int request = ++generation;
        choices.removeAllViews();
        if (text.isEmpty()) { query.setError("请输入城市名"); return; }
        status.setText("正在查找城市…");
        worker.execute(() -> {
            try {
                JSONArray results = Weather.cities(text);
                reply(request, () -> {
                    status.setText(results.length() == 0 ? "没有找到城市，试试拼音" : "选择你的城市");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject city = results.optJSONObject(i);
                        if (city == null || city.isNull("latitude") || city.isNull("longitude")) continue;
                        String name = city.optString("name");
                        String label = android.text.TextUtils.join(" · ", new String[]{name, city.optString("admin1"), city.optString("country")});
                        String coords = city.optString("latitude") + "," + city.optString("longitude");
                        button(choices, label, v -> select(name, coords));
                    }
                });
            } catch (Exception error) { reply(request, () -> status.setText("城市查询失败，请重试")); }
        });
    }

    private void select(String city, String coords) {
        generation++;
        new DailyWeather(prefs).setCity(city, coords);
        choices.removeAllViews();
        showCity();
    }

    private void reply(int request, Runnable action) {
        runOnUiThread(() -> { if (request == generation && !isFinishing() && !isDestroyed()) action.run(); });
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
