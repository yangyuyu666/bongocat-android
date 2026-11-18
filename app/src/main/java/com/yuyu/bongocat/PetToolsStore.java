package com.yuyu.bongocat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 将倒计时、便利贴和待办保存在独立的本地首选项中。 */
public final class PetToolsStore {
    public static final int IDLE = 0, RUNNING = 1, PAUSED = 2, DONE = 3;
    public static final int MAX_NOTE = 1000, MAX_TASKS = 20;
    private final SharedPreferences prefs;
    private final Context context;

    public PetToolsStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("bongocat_tools", Context.MODE_PRIVATE);
    }

    public int status() { return prefs.getInt("timer_status", IDLE); }
    public String token() { return prefs.getString("timer_token", ""); }
    public long duration() { return prefs.getLong("timer_duration", 25 * 60_000L); }
    private int boot() { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0); }

    public long remaining() { return remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot()); }

    long remaining(long wall, long elapsed, int boot) {
        if (status() == PAUSED) return prefs.getLong("timer_remaining", 0);
        if (status() != RUNNING) return 0;
        // 同次开机用单调时钟，重启后用保存的日期恢复剩余时间。
        long left = prefs.getInt("timer_boot", -1) == boot
                ? prefs.getLong("timer_elapsed", 0) - elapsed : prefs.getLong("timer_wall", 0) - wall;
        return Math.max(0, Math.min(duration(), left));
    }

    public void start(int minutes) {
        if (minutes < 1 || minutes > 360) throw new IllegalArgumentException("请输入 1～360 分钟");
        long time = minutes * 60_000L;
        writeRunning(time, time);
    }

    private void writeRunning(long duration, long remaining) {
        prefs.edit().putInt("timer_status", RUNNING).putString("timer_token", UUID.randomUUID().toString())
                .putLong("timer_duration", duration).putLong("timer_wall", System.currentTimeMillis() + remaining)
                .putLong("timer_elapsed", SystemClock.elapsedRealtime() + remaining).putInt("timer_boot", boot()).commit();
    }

    public void pause() {
        if (status() != RUNNING) return;
        long left = remaining();
        if (left == 0) return;
        prefs.edit().putInt("timer_status", PAUSED).putLong("timer_remaining", left)
                .putString("timer_token", UUID.randomUUID().toString()).commit();
    }

    public void resume() { if (status() == PAUSED) writeRunning(duration(), remaining()); }

    public void cancel() {
        prefs.edit().putInt("timer_status", IDLE).putString("timer_token", UUID.randomUUID().toString()).commit();
    }

    public boolean finishIfDue(String expectedToken) {
        synchronized (PetToolsStore.class) {
            if (status() != RUNNING || !token().equals(expectedToken) || remaining() > 0) return false;
            // 先持久化完成状态，防止服务轮询和系统闹钟重复提醒。
            prefs.edit().putInt("timer_status", DONE).commit();
            return true;
        }
    }

    public String note() { return prefs.getString("note", ""); }
    public boolean notePinned() { return prefs.getBoolean("note_pinned", false) && !note().trim().isEmpty(); }
    public void saveNote(String text, boolean pinned) {
        if (text.length() > MAX_NOTE) throw new IllegalArgumentException("便利贴最多 1000 字");
        prefs.edit().putString("note", text).putBoolean("note_pinned", pinned && !text.trim().isEmpty()).commit();
    }
    public void pinNote(boolean pinned) { saveNote(note(), pinned); }
    public boolean hasSign() { return status() == DONE || notePinned(); }
    public String signText() { return status() == DONE ? "时间到啦！\n休息一下吧" : note(); }

    public static final class Task {
        public final String id, text;
        public boolean done;
        Task(String id, String text, boolean done) { this.id = id; this.text = text; this.done = done; }
    }

    public List<Task> tasks() {
        List<Task> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("tasks", "[]"));
            for (int i = 0; i < Math.min(MAX_TASKS, array.length()); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Task(item.getString("id"), item.getString("text"), item.optBoolean("done")));
            }
        } catch (JSONException ignored) { /* 损坏的待办不影响倒计时和便利贴。 */ }
        return result;
    }

    public boolean addTask(String text) {
        List<Task> list = tasks();
        text = text.trim();
        if (text.isEmpty() || text.length() > 120 || list.size() >= MAX_TASKS) return false;
        list.add(new Task(UUID.randomUUID().toString(), text, false));
        saveTasks(list);
        return true;
    }

    public void setTaskDone(String id, boolean done) {
        List<Task> list = tasks();
        for (Task task : list) if (task.id.equals(id)) task.done = done;
        saveTasks(list);
    }

    public void clearCompleted() {
        List<Task> list = tasks();
        list.removeIf(task -> task.done);
        saveTasks(list);
    }

    private void saveTasks(List<Task> list) {
        JSONArray array = new JSONArray();
        try {
            for (Task task : list) array.put(new JSONObject().put("id", task.id).put("text", task.text).put("done", task.done));
        } catch (JSONException exception) { throw new IllegalStateException(exception); }
        prefs.edit().putString("tasks", array.toString()).commit();
    }

    public static String formatTime(long milliseconds) {
        long seconds = (Math.max(0, milliseconds) + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
