package com.yuyu.bongocat;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 长按菜单使用独立对话窗口，编辑时让系统正常管理键盘和返回键。 */
public class PetToolsActivity extends Activity {
    private static final int INK = 0xff493f49, MUTED = 0xff817781, PINK = 0xffad5777;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PetToolsStore store;
    private String page = "menu", noteDraft, minutesDraft, taskDraft;
    private LinearLayout body;
    private EditText noteInput, minutesInput, taskInput;
    private TextView clock;
    private int lastStatus;
    private boolean permissionRequested;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            PetReminder.checkDue(PetToolsActivity.this, store.token());
            if (clock != null) clock.setText(PetToolsStore.formatTime(store.remaining()));
            if (lastStatus != store.status()) { captureDrafts(); render(); }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new PetToolsStore(this);
        page = saved == null ? getIntent().getStringExtra("page") : saved.getString("page");
        if (page == null) page = "menu";
        if (saved != null) {
            noteDraft = saved.getString("note"); minutesDraft = saved.getString("minutes"); taskDraft = saved.getString("task");
            permissionRequested = saved.getBoolean("requested");
        }
        setFinishOnTouchOutside(true);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setDimAmount(.08f);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        render();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(16));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private void heading(String value) {
        TextView title = text(value, 22, INK); title.setTypeface(null, Typeface.BOLD); body.addView(title);
    }
    private Button button(String title, Runnable click, boolean primary) {
        Button button = new Button(this);
        button.setText(title); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setBackground(background(primary ? PINK : 0xfff5eef1, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8); button.setMinHeight(dp(48)); body.addView(button, lp);
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private EditText input(String value, String hint, int max, boolean number) {
        EditText input = new EditText(this);
        input.setTextSize(16); input.setTextColor(INK); input.setHintTextColor(MUTED);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(background(Color.WHITE, 0xffe6dbe0));
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        input.setHint(hint); input.setText(value); input.setGravity(Gravity.TOP);
        input.setMinLines(number ? 1 : 3); input.setMaxLines(number ? 1 : 7);
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private void captureDrafts() {
        if (noteInput != null) noteDraft = noteInput.getText().toString();
        if (minutesInput != null) minutesDraft = minutesInput.getText().toString();
        if (taskInput != null) taskDraft = taskInput.getText().toString();
    }

    private void go(String next) { captureDrafts(); page = next; render(); }

    private void render() {
        noteInput = minutesInput = taskInput = null; clock = null; lastStatus = store.status();
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(18));
        body.setBackground(background(0xfffffbfc, 0xffeadfe4));
        body.setFocusableInTouchMode(true);
        ScrollView scroll = new ScrollView(this) {
            @Override protected void onMeasure(int width, int height) {
                int maximum = Math.round(getResources().getDisplayMetrics().heightPixels * .80f);
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(Math.min(maximum, MeasureSpec.getSize(height)), MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(body); setContentView(scroll);
        if ("timer".equals(page)) renderTimer();
        else if ("note".equals(page)) renderNote();
        else if ("read".equals(page)) renderRead();
        else if ("tasks".equals(page)) renderTasks();
        else {
            page = "menu";
            heading("猫咪工具箱");
            button("番茄钟  ›", () -> go("timer"), true);
            button("便利贴  ›", () -> go("note"), false);
            button("待办清单  ›", () -> go("tasks"), false);
        }
        button("menu".equals(page) ? "收起菜单" : "‹ 返回工具箱", () -> { if ("menu".equals(page)) finish(); else go("menu"); }, false);
        body.requestFocus();
        positionWindow();
    }

    private void positionWindow() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(dp("read".equals(page) ? 400 : 336), screenW - dp(24));
        getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (getIntent().hasExtra("pet_x")) {
            body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int)(screenH * .8f), View.MeasureSpec.AT_MOST));
            int[] position = ToolPopupPlacement.place(screenW, screenH, getIntent().getIntExtra("pet_x", 0),
                    getIntent().getIntExtra("pet_y", 0), getIntent().getIntExtra("pet_width", dp(180)), width, body.getMeasuredHeight(), dp(12));
            layout.gravity = Gravity.TOP | Gravity.LEFT; layout.x = position[0]; layout.y = position[1];
        } else { layout.gravity = Gravity.CENTER; layout.x = layout.y = 0; }
        getWindow().setAttributes(layout);
    }

    private void renderTimer() {
        heading("番茄钟");
        int status = store.status();
        if (status == PetToolsStore.RUNNING || status == PetToolsStore.PAUSED) {
            if (status == PetToolsStore.PAUSED) body.addView(text("已暂停", 16, PINK));
            clock = text(PetToolsStore.formatTime(store.remaining()), 44, PINK);
            clock.setGravity(Gravity.CENTER); clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); body.addView(clock);
            button(status == PetToolsStore.RUNNING ? "暂停" : "继续计时", () -> {
                PetReminder.checkDue(this, store.token());
                if (store.status() == PetToolsStore.RUNNING) store.pause(); else if (store.status() == PetToolsStore.PAUSED) store.resume();
                PetReminder.schedule(this); PetReminder.changed(this); render();
            }, true);
            button("结束本次计时", () -> { store.cancel(); PetReminder.schedule(this); PetReminder.changed(this); render(); }, false);
        } else if (status == PetToolsStore.DONE) {
            heading("时间到啦！");
            button("知道了，收起提醒", () -> { PetReminder.acknowledge(this); render(); }, true);
            button("再休息 5 分钟", () -> startTimer(5), false);
        } else {
            body.addView(text("倒计时（分钟）", 16, INK));
            minutesInput = input(minutesDraft == null ? "25" : minutesDraft, "分钟", 3, true);
            LinearLayout choices = new LinearLayout(this); body.addView(choices);
            for (int minutes : new int[]{15, 25, 45, 60}) {
                Button preset = new Button(this); preset.setText(minutes + " 分"); preset.setTextSize(13); preset.setAllCaps(false);
                choices.addView(preset, new LinearLayout.LayoutParams(0, dp(48), 1));
                preset.setOnClickListener(view -> minutesInput.setText(String.valueOf(minutes)));
            }
            button("开始专注", () -> {
                try {
                    int value = Integer.parseInt(minutesInput.getText().toString().trim());
                    if (value < 1 || value > 360) throw new NumberFormatException();
                    minutesDraft = String.valueOf(value); startTimer(value);
                } catch (NumberFormatException exception) { minutesInput.setError("请输入 1～360 分钟"); }
            }, true);
        }
        if (!PetReminder.notificationsAllowed(this)) {
            button("系统通知已关闭 · 开启", this::requestNotifications, false);
            button("打开通知设置", this::openNotificationSettings, false);
        }
        if (!PetReminder.exactAllowed(this)) {
            button("允许锁屏准时提醒", () -> {
                if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }, false);
        }
    }

    private void startTimer(int minutes) {
        PetReminder.acknowledge(this);
        store.start(minutes); PetReminder.schedule(this); PetReminder.changed(this); render();
        if (!permissionRequested && !PetReminder.notificationsAllowed(this)) { permissionRequested = true; requestNotifications(); }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 30);
        else openNotificationSettings();
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, PetReminder.CHANNEL));
    }

    private void renderNote() {
        heading("便利贴");
        noteInput = input(noteDraft == null ? store.note() : noteDraft, "写下便利贴内容", PetToolsStore.MAX_NOTE, false);
        button("保存并举牌", () -> {
            String value = noteInput.getText().toString();
            if (value.trim().isEmpty()) { noteInput.setError("先写一点内容吧"); return; }
            store.saveNote(value, true); noteDraft = value; PetReminder.changed(this);
            if (Settings.canDrawOverlays(this)) {
                new PetSettings(this).setVisible(true);
                startForegroundService(new Intent(this, FloatingPetService.class));
                finish();
            } else {
                Toast.makeText(this, "内容已保存，请允许悬浮窗后显示猫咪", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            }
        }, true);
        button("只保存内容", () -> { captureDrafts(); store.saveNote(noteDraft, store.notePinned()); PetReminder.changed(this); Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show(); }, false);
        if (store.notePinned()) button("收起牌子", () -> { captureDrafts(); store.pinNote(false); PetReminder.changed(this); render(); }, false);
        if (!store.note().isEmpty()) button("放大阅读", () -> go("read"), false);
    }

    private void renderRead() {
        heading("猫咪的便利贴");
        TextView content = text(store.note(), 21, INK);
        content.setTextIsSelectable(true); content.setLineSpacing(dp(5), 1); body.addView(content);
        button("编辑内容", () -> go("note"), true);
        if (store.notePinned()) button("收起牌子", () -> { store.pinNote(false); PetReminder.changed(this); finish(); }, false);
    }

    private void renderTasks() {
        heading("待办清单");
        taskInput = input(taskDraft == null ? "" : taskDraft, "添加一件要做的事", 120, false);
        taskInput.setMinLines(1); taskInput.setMaxLines(3);
        button("添加待办", () -> {
            if (store.addTask(taskInput.getText().toString())) { taskDraft = ""; render(); }
            else taskInput.setError(store.tasks().size() >= 20 ? "先清理已完成的事项吧" : "请输入待办内容");
        }, true);
        for (PetToolsStore.Task task : store.tasks()) {
            CheckBox check = new CheckBox(this); check.setText(task.text); check.setTextColor(INK); check.setTextSize(16);
            check.setPadding(0, dp(8), 0, dp(8)); check.setChecked(task.done); body.addView(check);
            check.setOnCheckedChangeListener((view, checked) -> store.setTaskDone(task.id, checked));
        }
        if (!store.tasks().isEmpty()) button("清理已完成", () -> { captureDrafts(); store.clearCompleted(); render(); }, false);
    }

    @Override public void onBackPressed() { if ("menu".equals(page)) super.onBackPressed(); else go("menu"); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        captureDrafts(); render();
    }
    @Override protected void onResume() {
        super.onResume();
        PetReminder.checkDue(this, store.token()); PetReminder.schedule(this);
        captureDrafts(); render(); handler.removeCallbacks(tick); handler.post(tick);
    }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        captureDrafts(); state.putString("page", page); state.putString("note", noteDraft);
        state.putString("minutes", minutesDraft); state.putString("task", taskDraft); state.putBoolean("requested", permissionRequested);
        super.onSaveInstanceState(state);
    }
}
