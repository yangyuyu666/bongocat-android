package com.yuyu.bongocat;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

/** 系统闹钟与悬浮服务共用同一个到时入口。 */
public final class PetReminder {
    public static final String CHANGED = "com.yuyu.bongocat.TOOLS_CHANGED";
    public static final String ALARM = "com.yuyu.bongocat.TIMER_ALARM";
    public static final String ACK = "com.yuyu.bongocat.TIMER_ACK";
    public static final String CHANNEL = "pet_timer";
    public static final int NOTIFICATION = 30;

    private static PendingIntent alarmIntent(Context context, String token) {
        return PendingIntent.getBroadcast(context, 30, new Intent(context, PetTimerReceiver.class)
                .setAction(ALARM).putExtra("token", token), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean exactAllowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "番茄钟到时提醒", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("倒计时结束时响铃、振动并提醒休息");
        channel.enableVibration(true);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static boolean notificationsAllowed(Context context) {
        createChannel(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static void schedule(Context context) {
        PetToolsStore store = new PetToolsStore(context);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = alarmIntent(context, store.token());
        alarms.cancel(pending);
        if (store.status() != PetToolsStore.RUNNING) return;
        long when = SystemClock.elapsedRealtime() + Math.max(1, store.remaining());
        try {
            if (exactAllowed(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
                return;
            }
        } catch (SecurityException ignored) { /* 精确闹钟权限被收回时改用普通闹钟。 */ }
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
    }

    public static void checkDue(Context context, String token) {
        PetToolsStore store = new PetToolsStore(context);
        if (!store.finishIfDue(token)) return;
        createChannel(context);
        PendingIntent open = PendingIntent.getActivity(context, 30,
                PetToolsPopupActivity.intent(context, "timer"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent ack = PendingIntent.getBroadcast(context, 31,
                new Intent(context, PetTimerReceiver.class).setAction(ACK).putExtra("token", store.token()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_pet)
                .setContentTitle("时间到啦，休息一下吧")
                .setContentText("已完成 " + (store.duration() / 60_000) + " 分钟计时，点开查看或安排休息。")
                .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "知道了", ack).build()).build();
        if (notificationsAllowed(context)) {
            try { context.getSystemService(NotificationManager.class).notify(NOTIFICATION, notification); }
            catch (SecurityException ignored) { /* 拒绝通知后仍保留猫咪的到时牌子。 */ }
        }
        changed(context);
    }

    public static void acknowledge(Context context) {
        PetToolsStore store = new PetToolsStore(context);
        if (store.status() == PetToolsStore.DONE) store.cancel();
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION);
        schedule(context);
        changed(context);
    }

    public static void changed(Context context) { context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName())); }
    private PetReminder() { }
}
