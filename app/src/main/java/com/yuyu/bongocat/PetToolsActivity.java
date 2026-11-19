package com.yuyu.bongocat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
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
    private static final int INK = UiStyle.INK, MUTED = UiStyle.MUTED, PINK = UiStyle.ACCENT;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PetToolsStore store;
    private String page = "menu", noteDraft, minutesDraft, taskDraft;
    private LinearLayout body;
    private EditText noteInput, minutesInput, taskInput;
    private TextView clock;
    private int lastStatus;
    private boolean closeAfterNotificationRequest;
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
            closeAfterNotificationRequest = saved.getBoolean("close_after_notification");
        }
        setFinishOnTouchOutside(true);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setDimAmount(.12f);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        render();
    }

    private int dp(float value) { return UiStyle.dp(this, value); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private void heading(String value) {
        TextView title = text(value, 21, INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(2), 0, dp(6));
        body.addView(title);
    }

    private Button button(String title, Runnable click, boolean primary) {
        Button button = new Button(this);
        button.setText(title); button.setTextSize(15); button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setTypeface(null, primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setBackground(UiStyle.ripple(this,
                primary ? PINK : UiStyle.ACCENT_SOFT,
                primary ? 0 : UiStyle.ACCENT_BORDER,
                primary ? 0f : 1f, 16f, primary ? UiStyle.RIPPLE_LIGHT : UiStyle.RIPPLE_PINK));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8); button.setMinHeight(dp(48)); body.addView(button, lp);
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private EditText input(String value, String hint, int max, boolean number) {
        EditText input = new EditText(this);
        input.setTextSize(16); input.setTextColor(INK); input.setHintTextColor(MUTED);
        input.setPadding(dp(14), dp(11), dp(14), dp(11));
        input.setBackground(UiStyle.card(this, Color.WHITE, UiStyle.BORDER, 14f));
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        input.setHint(hint); input.setText(value); input.setGravity(Gravity.TOP);
        input.setMinLines(number ? 1 : 3); input.setMaxLines(number ? 1 : 7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        body.addView(input, lp);
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
        body.setPadding(dp(20), dp(12), dp(20), dp(18));
        body.setBackground(UiStyle.gradientCard(this, 0xFFFFFDFB, 0xFFFCF4F7, 0xFFEADCE2, 24f));
        body.setFocusableInTouchMode(true);

        // 顶部柔和装饰把手条
        View handle = new View(this);
        handle.setBackground(UiStyle.card(this, 0xFFE8D5DD, 0, 99f));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(10);
        body.addView(handle, handleLp);

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
        Button close = button("menu".equals(page) ? "收起菜单" : "‹ 返回工具箱", () -> { if ("menu".equals(page)) finish(); else go("menu"); }, false);
        close.setBackground(UiStyle.ripple(this, Color.WHITE, UiStyle.BORDER, 1f, 16f, UiStyle.RIPPLE_PINK));
        close.setTextColor(MUTED);
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
            LinearLayout dial = new LinearLayout(this);
            dial.setOrientation(LinearLayout.VERTICAL);
            dial.setGravity(Gravity.CENTER);
            dial.setPadding(dp(12), dp(14), dp(12), dp(14));
            dial.setBackground(UiStyle.card(this, Color.WHITE, UiStyle.ACCENT_BORDER, 18f));
            LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(-1, -2);
            dialLp.topMargin = dp(4); dialLp.bottomMargin = dp(6);
            if (status == PetToolsStore.PAUSED) {
                TextView paused = text("已暂停", 14, PINK);
                paused.setGravity(Gravity.CENTER);
                paused.setTypeface(null, Typeface.BOLD);
                dial.addView(paused);
            }
            clock = text(PetToolsStore.formatTime(store.remaining()), 42, PINK);
            clock.setGravity(Gravity.CENTER); clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            dial.addView(clock);
            body.addView(dial, dialLp);

            button(status == PetToolsStore.RUNNING ? "暂停" : "继续计时", () -> {
                PetReminder.checkDue(this, store.token());
                if (store.status() == PetToolsStore.PAUSED) {
                    store.resume();
                    runTimerInBackground();
                } else {
                    store.pause();
                    PetReminder.schedule(this); PetReminder.changed(this); render();
                }
            }, true);
            button("结束本次计时", () -> { store.cancel(); PetReminder.schedule(this); PetReminder.changed(this); render(); }, false);
        } else if (status == PetToolsStore.DONE) {
            heading("时间到啦！");
            button("知道了，收起提醒", () -> { PetReminder.acknowledge(this); render(); }, true);
            button("再休息 5 分钟", () -> startTimer(5), false);
        } else {
            body.addView(text("倒计时分钟数", 14, MUTED));
            minutesInput = input(minutesDraft == null ? "25" : minutesDraft, "分钟", 3, true);
            LinearLayout choices = new LinearLayout(this);
            LinearLayout.LayoutParams choicesLp = new LinearLayout.LayoutParams(-1, -2);
            choicesLp.topMargin = dp(10); choicesLp.bottomMargin = dp(2);
            body.addView(choices, choicesLp);
            int[] presets = {15, 25, 45, 60};
            for (int i = 0; i < presets.length; i++) {
                int minutes = presets[i];
                Button preset = new Button(this);
                preset.setText(minutes + " 分"); preset.setTextSize(13); preset.setAllCaps(false);
                preset.setStateListAnimator(null);
                preset.setTextColor(UiStyle.ACCENT_DARK);
                preset.setTypeface(null, Typeface.BOLD);
                preset.setBackground(UiStyle.ripple(this, Color.WHITE, UiStyle.ACCENT_BORDER, 1f, 12f, UiStyle.RIPPLE_PINK));
                preset.setMinWidth(0); preset.setMinimumWidth(0); preset.setPadding(dp(2), 0, dp(2), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
                if (i > 0) lp.leftMargin = dp(6);
                choices.addView(preset, lp);
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
        store.start(minutes);
        runTimerInBackground();
    }

    private void runTimerInBackground() {
        PetReminder.schedule(this);
        PetReminder.changed(this);
        PetSettings settings = new PetSettings(this);
        if (Settings.canDrawOverlays(this) && !settings.isVisible()) {
            settings.setVisible(true);
            startForegroundService(new Intent(this, FloatingPetService.class));
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            closeAfterNotificationRequest = true;
            requestNotifications();
        } else {
            if (!PetReminder.notificationsAllowed(this))
                Toast.makeText(this, "系统通知已关闭，可在番茄钟中开启", Toast.LENGTH_SHORT).show();
            finish();
        }
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
        // 举牌纸张风格切换（奶油手账 / 樱花粉纸 / 抹茶便签）
        PetSettings petSettings = new PetSettings(this);
        int currentStyle = petSettings.getSignStyle();
        LinearLayout stylesRow = new LinearLayout(this);
        LinearLayout.LayoutParams stylesLp = new LinearLayout.LayoutParams(-1, -2);
        stylesLp.topMargin = dp(8);
        body.addView(stylesRow, stylesLp);
        int[] styleFills = {0xFFFFF8E8, 0xFFFFEEF4, 0xFFEFF8EE};
        for (int i = 0; i < PetSettings.SIGN_STYLE_NAMES.length; i++) {
            final int idx = i;
            boolean sel = i == currentStyle;
            Button chip = new Button(this);
            chip.setText(PetSettings.SIGN_STYLE_NAMES[i]);
            chip.setTextSize(12); chip.setAllCaps(false); chip.setStateListAnimator(null);
            chip.setTextColor(sel ? UiStyle.ACCENT_DARK : INK);
            chip.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            chip.setBackground(UiStyle.ripple(this, styleFills[i],
                    sel ? PINK : UiStyle.BORDER, sel ? 1.8f : 1f, 12f, UiStyle.RIPPLE_PINK));
            chip.setMinWidth(0); chip.setMinimumWidth(0); chip.setPadding(dp(2), 0, dp(2), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(6);
            stylesRow.addView(chip, lp);
            chip.setOnClickListener(v -> {
                captureDrafts();
                petSettings.setSignStyle(idx);
                if (petSettings.isVisible()) {
                    startService(new Intent(this, FloatingPetService.class)
                            .setAction(FloatingPetService.ACTION_UPDATE_SETTINGS));
                }
                render();
            });
        }
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
        TextView content = text(store.note(), 20, INK);
        content.setTextIsSelectable(true); content.setLineSpacing(dp(5), 1);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.setBackground(UiStyle.card(this, 0xFFFFF8E8, 0xFFEEDAB8, 16f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4); lp.bottomMargin = dp(6);
        body.addView(content, lp);
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
            CheckBox check = new CheckBox(this); check.setText(task.text); check.setTextSize(15);
            check.setButtonTintList(ColorStateList.valueOf(PINK));
            check.setPadding(dp(10), dp(10), dp(12), dp(10));
            check.setBackground(UiStyle.card(this, Color.WHITE, UiStyle.BORDER, 14f));
            check.setChecked(task.done);
            applyTaskStyle(check, task.done);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            body.addView(check, lp);
            check.setOnCheckedChangeListener((view, checked) -> {
                store.setTaskDone(task.id, checked);
                applyTaskStyle(check, checked);
                PetReminder.changed(this);
            });
        }
        if (store.nextOpenTask() != null || store.taskPinned()) {
            boolean pinned = store.taskPinned();
            button(pinned ? "收起待办举牌" : "举起下一条待办", () -> {
                captureDrafts();
                store.pinTask(!pinned);
                PetReminder.changed(this);
                if (!pinned && Settings.canDrawOverlays(this)) {
                    new PetSettings(this).setVisible(true);
                    startForegroundService(new Intent(this, FloatingPetService.class));
                }
                render();
            }, false);
        }
        if (!store.tasks().isEmpty()) button("清理已完成", () -> { captureDrafts(); store.clearCompleted(); PetReminder.changed(this); render(); }, false);
    }

    private void applyTaskStyle(CheckBox check, boolean done) {
        check.setTextColor(done ? MUTED : INK);
        check.setPaintFlags(done
                ? (check.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG)
                : (check.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG));
    }

    @Override public void onBackPressed() { if ("menu".equals(page)) super.onBackPressed(); else go("menu"); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 30 && closeAfterNotificationRequest) {
            closeAfterNotificationRequest = false;
            finish();
            return;
        }
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
        state.putString("minutes", minutesDraft); state.putString("task", taskDraft);
        state.putBoolean("close_after_notification", closeAfterNotificationRequest);
        super.onSaveInstanceState(state);
    }
}
