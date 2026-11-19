package com.yuyu.bongocat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;
import java.time.ZoneId;

/** 显示可拖动的悬浮猫，支持贴边躲藏、听歌律动、充电小闪电、低电量与昼夜提醒。 */
public class FloatingPetService extends Service {
    public static final String ACTION_UPDATE_SETTINGS = "com.yuyu.bongocat.UPDATE_SETTINGS";
    public static final String ACTION_PLAY = "com.yuyu.bongocat.PLAY_ACTION";
    public static final String ACTION_PREVIEW_WEATHER = "com.yuyu.bongocat.PREVIEW_WEATHER";
    public static final String ACTION_TOGGLE_TOUCH_THROUGH = "com.yuyu.bongocat.TOGGLE_TOUCH_THROUGH";
    public static final String EXTRA_TRIGGER = "trigger";
    private static final String ACTION_HIDE = "com.yuyu.bongocat.HIDE";
    private static final String CHANNEL_ID = "floating_pet";
    private static final int NOTIFICATION_ID = 1;
    /** 趴姿猫包围盒宽高比（实测 338/238.4 ≈ 1.42，留一点边距取 1.5）。 */
    private static final float LYING_ASPECT = 1.5f;

    private WindowManager windowManager;
    private AudioManager audioManager;
    private PetSettings settings;
    private RiveView catView;
    private WindowManager.LayoutParams params;
    private boolean lyingCrawl;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean attached;
    private boolean foregroundStarted;
    private boolean touching;
    private boolean moved;
    private int touchSlop;
    private float downX, downY, lastRawX, lastStepDx;
    private int downParamX, downParamY;
    private final CrawlMotion crawlMotion = new CrawlMotion();
    private final EdgePeekMotion edge = new EdgePeekMotion();
    private int petBase, edgePhase, edgeWidth, edgeHeight;
    private int sideDock, undockX;
    private boolean lastLandscape, edgeOverride;
    private long touchStarted;
    private Runnable pendingManualAction;
    private PetToolsStore tools;
    private boolean holdingSign;
    private WeatherWindow weatherWindow;
    private boolean lowBatteryNotified;
    private int musicBeats;

    private final BroadcastReceiver toolsChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refreshTools(); }
    };

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!attached || catView == null || intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                lowBatteryNotified = false;
                if (sideDock == 0 && !edge.active) {
                    catView.burstLightning();
                    if (!holdingSign) catView.playAction("playTail");
                    catView.triggerHaptic();
                }
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                if (level < 0 || scale <= 0) return;
                int pct = level * 100 / scale;
                if (plugged != 0 || pct > 20) {
                    lowBatteryNotified = false;
                } else if (pct <= 15 && !lowBatteryNotified && sideDock == 0 && !edge.active && !holdingSign) {
                    lowBatteryNotified = true;
                    catView.playAction("playYawn");
                    weatherWindow.showMessage(params, "电量低啦 (" + pct + "%)", "记得给手机充电哦",
                            WeatherWindow.ICON_BATTERY, 8500);
                }
            }
        }
    };

    private final Runnable toolTick = new Runnable() {
        @Override public void run() {
            PetReminder.checkDue(FloatingPetService.this, tools.token());
            refreshTools();
            if (attached) {
                // 随天气自动换装开启时同步刷新配饰。
                if (settings.isWeatherCostumeEnabled() && catView != null) {
                    catView.applySettings(settings);
                    catView.setAlpha(1f);
                }
                weatherWindow.tick(params, !touching && !holdingSign && sideDock == 0
                        && (!edge.active || edgeOverride || edge.phase == EdgePeekMotion.PEEK));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable musicTick = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            if (settings.isMusicDanceEnabled() && audioManager != null && audioManager.isMusicActive()
                    && !touching && !holdingSign && !edge.active && sideDock == 0 && catView != null) {
                int st = catView.currentStateCode();
                if (st == 0 || st == 15) {
                    catView.tapAlternate();
                    catView.swayBeat((musicBeats % 2) == 0);
                    if ((musicBeats % 4) == 0) catView.burstMusicNote();
                    musicBeats++;
                }
            } else {
                musicBeats = 0;
            }
            handler.postDelayed(this, 560);
        }
    };

    private final Runnable greetTask = () -> {
        if (!attached || touching || holdingSign || sideDock != 0 || edge.active
                || weatherWindow.isShowing() || catView == null) return;
        String[] greet = new PetBondStore(this).consumeGreeting(System.currentTimeMillis(), ZoneId.systemDefault());
        if (greet == null) return;
        boolean night = greet[0].contains("晚");
        catView.playAction(night ? "playYawn" : "playStretch");
        weatherWindow.showMessage(params, greet[0], greet[1],
                night ? WeatherWindow.ICON_MOON : WeatherWindow.ICON_SUN, 7500);
    };

    private final Runnable longPress = () -> {
        if (!touching || moved || !attached) return;
        touching = false;
        catView.setDragMotion(0f, false);
        catView.endTouch(false);
        if (edge.active && !edgeOverride) {
            edge.pauseFor(SystemClock.uptimeMillis() - touchStarted);
            handler.post(this.edgeTick);
        }
        catView.performLongClick();
    };

    private final Runnable edgeTick = new Runnable() {
        @Override public void run() {
            if (!attached || !edge.active || edgeOverride || touching) return;
            applyEdgeFrame();
            if (edge.phase != EdgePeekMotion.PEEK) handler.postDelayed(this, 16);
        }
    };
    private final Runnable resumeEdge = new Runnable() {
        @Override public void run() {
            if (!attached || !landscape()) return;
            if (touching) handler.postDelayed(this, 200); else enterEdge();
        }
    };
    private final Runnable orientationChange = this::updateOrientation;

    private final Runnable permissionCheck = new Runnable() {
        @Override public void run() {
            if (!Settings.canDrawOverlays(FloatingPetService.this)) {
                hidePet();
                return;
            }
            handler.postDelayed(this, 3000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        weatherWindow = new WeatherWindow(this, windowManager);
        settings = new PetSettings(this);
        tools = new PetToolsStore(this);
        androidx.core.content.ContextCompat.registerReceiver(this, toolsChanged,
                new IntentFilter(PetReminder.CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        IntentFilter powerFilter = new IntentFilter();
        powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        powerFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(powerReceiver, powerFilter);
        PetReminder.schedule(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    private Notification buildPetNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "悬浮猫", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hide = PendingIntent.getService(this, 1,
                new Intent(this, FloatingPetService.class).setAction(ACTION_HIDE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggleThrough = PendingIntent.getService(this, 2,
                new Intent(this, FloatingPetService.class).setAction(ACTION_TOGGLE_TOUCH_THROUGH),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean through = settings.isTouchThrough();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pet)
                .setContentTitle(through ? "BongoCat 正在陪你（穿透中）" : "BongoCat 正在陪你")
                .setContentText("点此调整外观，或快捷切换穿透与收起")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, through ? "恢复触控" : "触摸穿透", toggleThrough).build())
                .addAction(new Notification.Action.Builder(null, "收起", hide).build())
                .build();
    }

    private void startPetForeground() {
        Notification notification = buildPetNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updatePetNotification() {
        if (!foregroundStarted) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildPetNotification());
    }

    private void createCat() {
        catView = new RiveView(this);
        catView.setOnLongClickListener(view -> { openTools("menu"); return true; });
        catView.setSignClick(() -> openTools(tools.status() == PetToolsStore.DONE ? "timer" : (tools.notePinned() ? "read" : "tasks")));
        catView.setCrawlListener(lying -> {
            lyingCrawl = lying;
            applySettings();
        });
        params = new WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = settings.getPosX();
        params.y = settings.getPosY();
        catView.setMovementHost(new RiveMovement.Host() {
            @Override public void begin(float dx, float dy) {
                Point screen = screenSize();
                crawlMotion.begin(params.x, params.y, 0, 0, Math.max(0, screen.x - params.width),
                        Math.max(0, screen.y - params.height), dx, dy);
            }
            @Override public boolean move(float progress) {
                if (!attached || touching || sideDock != 0) return false;
                boolean room = crawlMotion.move(progress);
                params.x = Math.round(crawlMotion.x);
                params.y = Math.round(crawlMotion.y);
                clampPosition();
                updateWindow();
                return room;
            }
            @Override public void stopped() { savePosition(); }
        });
        catView.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelManualAction();
                        touching = true;
                        touchStarted = SystemClock.uptimeMillis();
                        handler.removeCallbacks(edgeTick);
                        catView.beginTouch();
                        downX = lastRawX = event.getRawX();
                        downY = event.getRawY();
                        lastStepDx = 0f;
                        downParamX = params.x;
                        downParamY = params.y;
                        moved = false;
                        handler.removeCallbacks(longPress);
                        handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!touching) return true;
                        int dx = Math.round(event.getRawX() - downX);
                        int dy = Math.round(event.getRawY() - downY);
                        float stepDx = event.getRawX() - lastRawX;
                        lastRawX = event.getRawX();
                        lastStepDx = lastStepDx * 0.5f + stepDx * 0.5f;
                        if (Math.hypot(dx, dy) > touchSlop) {
                            if (!moved && sideDock != 0) {
                                // 从贴边状态直接拖出时恢复完整小猫。
                                exitSideDock(false);
                                downParamX = params.x;
                                downParamY = params.y;
                            }
                            moved = true;
                            handler.removeCallbacks(longPress);
                        }
                        if (moved) {
                            params.x = downParamX + dx;
                            params.y = downParamY + dy;
                            clampPosition();
                            updateWindow();
                            catView.setDragMotion(lastStepDx, true);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        if (!touching) return true;
                        touching = false;
                        catView.setDragMotion(0f, false);
                        boolean up = event.getActionMasked() == MotionEvent.ACTION_UP;
                        if (sideDock != 0 && !moved && up) {
                            catView.endTouch(false);
                            exitSideDock(true);
                            return true;
                        }
                        if (moved && up && !edge.active && !holdingSign && !landscape() && trySideDock()) {
                            catView.endTouch(false);
                            return true;
                        }
                        catView.endTouch(moved && up);
                        if (edge.active && !edgeOverride) {
                            edge.pauseFor(SystemClock.uptimeMillis() - touchStarted);
                            if (moved) enterEdge(); else handler.post(edgeTick);
                        }
                        savePosition();
                        if (!moved && up) {
                            if (catView.hitsSign(event.getX(), event.getY())) {
                                openTools(tools.status() == PetToolsStore.DONE ? "timer" : (tools.notePinned() ? "read" : "tasks"));
                            } else {
                                catView.performPartClick(event.getX(), event.getY());
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        applySettings();
        windowManager.addView(catView, params);
        attached = true;
        lastLandscape = landscape();
        refreshTools();
        if (lastLandscape) enterEdge();
        handler.postDelayed(permissionCheck, 3000);
        handler.post(toolTick);
        handler.postDelayed(musicTick, 1200);
        handler.postDelayed(greetTask, 3200);
        PetQuickTileService.requestSync(this);
    }

    /** 竖屏拖至屏幕左右边缘或轻甩向边缘时自动半藏露小耳朵。 */
    private boolean trySideDock() {
        Point screen = screenSize();
        int edgeGap = Math.round(14 * getResources().getDisplayMetrics().density);
        int side = 0;
        if (params.x <= edgeGap || (lastStepDx < -18f && params.x < screen.x * 0.25f)) side = -1;
        else if (params.x + params.width >= screen.x - edgeGap || (lastStepDx > 18f && params.x > screen.x * 0.55f)) side = 1;
        if (side == 0) return false;
        sideDock = side;
        undockX = Math.max(edgeGap * 2, Math.min(Math.max(edgeGap * 2, screen.x - petBase - edgeGap * 2),
                side < 0 ? edgeGap * 2 : screen.x - petBase - edgeGap * 2));
        lyingCrawl = false;
        int dockWidth = Math.max(1, Math.round(petBase * 0.44f));
        params.width = dockWidth;
        params.height = petBase;
        params.x = side < 0 ? 0 : Math.max(0, screen.x - dockWidth);
        catView.setSideDockPresentation(petBase, side);
        catView.triggerHaptic();
        updateWindow();
        return true;
    }

    private void exitSideDock(boolean animateRecall) {
        if (sideDock == 0) return;
        sideDock = 0;
        catView.setSideDockPresentation(petBase, 0);
        params.width = petBase;
        params.height = petBase;
        params.x = undockX;
        clampPosition();
        updateWindow();
        savePosition();
        if (animateRecall) {
            catView.triggerHaptic();
            catView.tapAlternate();
            catView.burstHearts();
        }
    }

    private Point screenSize() {
        Point size = new Point();
        windowManager.getDefaultDisplay().getSize(size);
        return size;
    }

    private void clampPosition() {
        Point screen = screenSize();
        params.x = Math.max(0, Math.min(Math.max(0, screen.x - params.width), params.x));
        params.y = Math.max(0, Math.min(Math.max(0, screen.y - params.height), params.y));
    }

    static void applyTouchMode(Context context, WindowManager.LayoutParams window, boolean through, float opacity) {
        window.alpha = opacity;
        if (through) {
            window.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            // Android 12 起按窗口遮挡率决定能否穿透到其他应用。
            if (Build.VERSION.SDK_INT >= 31)
                window.alpha = Math.min(opacity, context.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch());
        } else {
            window.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
    }

    private void applySettings() {
        catView.applySettings(settings);
        boolean through = settings.isTouchThrough();
        applyTouchMode(this, params, through, catView.getAlpha());
        catView.setAlpha(1f);
        updatePetNotification();
        if (through && touching) {
            handler.removeCallbacks(longPress);
            touching = false;
            catView.setDragMotion(0f, false);
            catView.endTouch(false);
            if (edge.active && !edgeOverride) edge.pauseFor(SystemClock.uptimeMillis() - touchStarted);
        }
        Point screen = screenSize();
        int requestedSize = Math.round(settings.getSizeDp() * getResources().getDisplayMetrics().density);
        int base = Math.max(1, Math.min(requestedSize, Math.min(screen.x, screen.y)));
        int oldBase = petBase;
        petBase = base;
        if (edge.active && !edgeOverride) {
            if (oldBase != base || edgeWidth != screen.x || edgeHeight != screen.y) {
                edge.resize(screen.x, screen.y, base, SystemClock.uptimeMillis());
                edgeWidth = screen.x; edgeHeight = screen.y;
                edgePhase = 0;
            }
            applyEdgeFrame();
            handler.removeCallbacks(edgeTick);
            handler.post(edgeTick);
            return;
        }
        if (sideDock != 0) {
            int dockWidth = Math.max(1, Math.round(base * 0.44f));
            params.width = dockWidth;
            params.height = base;
            params.x = sideDock < 0 ? 0 : Math.max(0, screen.x - dockWidth);
            catView.setSideDockPresentation(base, sideDock);
            clampPosition();
            if (attached) updateWindow();
            return;
        }
        if (lyingCrawl) {
            // 趴姿是横的：窗口变宽、高度不变，配 RiveView 里的 COVER 让趴猫填满窗口。
            params.width = Math.min(Math.round(base * LYING_ASPECT), screen.x);
            params.height = base;
        } else {
            params.width = params.height = base;
        }
        // 仅当窗口越过屏幕边缘时收回，普通外观更新保留坐标。
        clampPosition();
        if (attached) {
            updateWindow();
            savePosition();
        }
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void savePosition() {
        if (!edge.active && sideDock == 0 && !landscape() && params != null) settings.setPos(params.x, params.y);
    }

    private void enterEdge() {
        if (!attached || touching || holdingSign) return;
        if (sideDock != 0) exitSideDock(false);
        handler.removeCallbacks(edgeTick);
        handler.removeCallbacks(resumeEdge);
        int x = params.x, y = params.y;
        if (edge.active && !edgeOverride && edge.phase == EdgePeekMotion.PEEK) {
            x -= (petBase - params.width) / 2;
            y -= petBase - params.height;
        }
        boolean first = !edge.active;
        Point screen = screenSize();
        petBase = Math.max(1, Math.min(Math.round(settings.getSizeDp() * getResources().getDisplayMetrics().density), Math.min(screen.x, screen.y)));
        edge.enter(x, y, screen.x, screen.y, petBase, SystemClock.uptimeMillis());
        if (first) { edge.restoreX = settings.getPosX(); edge.restoreY = settings.getPosY(); }
        edgeWidth = screen.x; edgeHeight = screen.y; edgeOverride = false; edgePhase = 0;
        catView.setEdgeMode(true, false, edge.right);
        applyEdgeFrame();
        handler.post(edgeTick);
    }

    private void applyEdgeFrame() {
        if (!attached || !edge.active || edgeOverride) return;
        int[] bounds = edge.bounds(SystemClock.uptimeMillis());
        if (edgePhase != edge.phase) {
            edgePhase = edge.phase;
            catView.setEdgeMode(true, edge.phase != EdgePeekMotion.RUN, edge.right);
            catView.setEdgePresentation(petBase, edge.phase == EdgePeekMotion.PEEK);
        }
        params.x = bounds[0]; params.y = bounds[1]; params.width = bounds[2]; params.height = bounds[3];
        updateWindow();
    }

    private void updateOrientation() {
        if (!attached) return;
        handler.removeCallbacks(longPress);
        boolean next = landscape();
        if (touching) { touching = false; moved = true; catView.setDragMotion(0f, false); catView.endTouch(false); }
        if (sideDock != 0) exitSideDock(false);
        if (holdingSign) {
            if (next && !lastLandscape) {
                settings.setPos(params.x, params.y);
                edge.restoreX = params.x; edge.restoreY = params.y; edge.active = true; edgeOverride = true;
            } else if (!next && edge.active) {
                Point screen = screenSize();
                int[] restored = edge.leave(screen.x, screen.y, petBase);
                params.x = restored[0]; params.y = restored[1]; edgeOverride = false;
            }
            applySettings(); lastLandscape = next;
            return;
        }
        if (next) {
            if (!lastLandscape && !edge.active) settings.setPos(params.x, params.y);
            if (!edge.active) enterEdge(); else applySettings();
        } else if (edge.active) {
            handler.removeCallbacks(edgeTick);
            handler.removeCallbacks(resumeEdge);
            cancelManualAction();
            Point screen = screenSize();
            int base = Math.max(1, Math.min(Math.round(settings.getSizeDp() * getResources().getDisplayMetrics().density), Math.min(screen.x, screen.y)));
            int[] position = edge.leave(screen.x, screen.y, base);
            edgeOverride = false; edgePhase = 0; lyingCrawl = false;
            catView.setEdgeMode(false, false, false);
            catView.setEdgePresentation(base, false);
            params.x = position[0]; params.y = position[1];
            applySettings();
        } else applySettings();
        lastLandscape = next;
    }

    private void cancelManualAction() {
        if (pendingManualAction != null) handler.removeCallbacks(pendingManualAction);
        pendingManualAction = null;
    }

    private void openTools(String page) {
        startActivity(PetToolsPopupActivity.intent(this, page)
                .putExtra("pet_x", params.x).putExtra("pet_y", params.y).putExtra("pet_width", params.width));
    }

    private void refreshTools() {
        if (!attached || touching) return;
        boolean next = tools.hasSign();
        if (next && !holdingSign) {
            holdingSign = true;
            if (sideDock != 0) exitSideDock(false);
            handler.removeCallbacks(edgeTick); handler.removeCallbacks(resumeEdge); cancelManualAction();
            if (landscape() && !edge.active) {
                edge.restoreX = settings.getPosX(); edge.restoreY = settings.getPosY(); edge.active = true;
            }
            if (edge.active) {
                if (edge.phase == EdgePeekMotion.PEEK) { params.x -= (petBase - params.width)/2; params.y -= petBase - params.height; }
                edgeOverride = true;
            }
            catView.setEdgeMode(false, false, false);
            catView.setEdgePresentation(petBase, false);
            lyingCrawl = false; applySettings();
        }
        catView.setSign(next ? tools.signText() : null);
        if (!next && holdingSign) {
            holdingSign = false;
            if (landscape()) enterEdge(); else applySettings();
        }
    }

    private void updateWindow() {
        if (!attached) return;
        try {
            windowManager.updateViewLayout(catView, params);
            weatherWindow.follow(params);
        } catch (SecurityException | IllegalArgumentException exception) {
            hidePet();
        }
    }

    private void hidePet() {
        settings.setVisible(false);
        PetQuickTileService.requestSync(this);
        stopSelf();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE_TOUCH_THROUGH.equals(intent.getAction())) {
            boolean next = !settings.isTouchThrough();
            settings.setTouchThrough(next);
            if (attached) applySettings();
            Toast.makeText(this, next ? "已开启触摸穿透" : "已恢复小猫触控", Toast.LENGTH_SHORT).show();
            return START_STICKY;
        }
        if (intent != null && ACTION_PREVIEW_WEATHER.equals(intent.getAction())) {
            previewWeather();
            return START_STICKY;
        }
        if (intent != null && ACTION_PLAY.equals(intent.getAction())) {
            playAction(intent.getStringExtra(EXTRA_TRIGGER));
            return START_STICKY;
        }
        if ((intent != null && ACTION_HIDE.equals(intent.getAction()))
                || !settings.isVisible() || !Settings.canDrawOverlays(this)) {
            hidePet();
            return START_NOT_STICKY;
        }
        try {
            if (!foregroundStarted) {
                startPetForeground();
                foregroundStarted = true;
            }
            if (!attached) createCat();
            else applySettings();
        } catch (RuntimeException exception) {
            hidePet();
            Toast.makeText(this, "悬浮猫启动失败，请检查悬浮窗权限后重试", Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void previewWeather() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!foregroundStarted) {
            startPetForeground();
            foregroundStarted = true;
        }
        if (!attached) {
            settings.setVisible(true);
            createCat();
        } else {
            if (sideDock != 0) exitSideDock(false);
            applySettings();
        }
        handler.postDelayed(() -> {
            if (attached && !weatherWindow.previewWeather(params)) {
                Toast.makeText(this, "请先选择位置并等待天气加载完成", Toast.LENGTH_SHORT).show();
            }
        }, 250);
    }

    /** 动作页点名触发：猫没出来就先把它放出来，再做动作。 */
    private void playAction(String trigger) {
        if (trigger == null || trigger.isEmpty()) return;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (!foregroundStarted) {
                startPetForeground();
                foregroundStarted = true;
            }
            boolean justCreated = false;
            if (!attached) {
                settings.setVisible(true);
                createCat();
                justCreated = true;
            } else {
                if (sideDock != 0) exitSideDock(false);
                applySettings();
            }
            if (catView == null) return;
            if (tools.hasSign()) {
                refreshTools();
                Toast.makeText(this, "先收起牌子，再播放动作", Toast.LENGTH_SHORT).show();
                return;
            }
            cancelManualAction();
            if (edge.active) {
                edgeOverride = true;
                handler.removeCallbacks(edgeTick);
                handler.removeCallbacks(resumeEdge);
                catView.setEdgeMode(false, false, false);
                catView.setEdgePresentation(petBase, false);
                lyingCrawl = false;
                applySettings();
                handler.postDelayed(resumeEdge, 6200);
            }
            pendingManualAction = () -> { pendingManualAction = null; if (catView != null) catView.playAction(trigger); };
            handler.postDelayed(pendingManualAction, justCreated ? 400 : edgeOverride ? 120 : 0);
        } catch (RuntimeException exception) {
            hidePet();
            Toast.makeText(this, "悬浮猫启动失败，请检查悬浮窗权限后重试", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        handler.removeCallbacks(orientationChange);
        handler.post(orientationChange);
    }

    @Override public void onDestroy() {
        weatherWindow.close();
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(toolsChanged); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(powerReceiver); } catch (IllegalArgumentException ignored) {}
        if (catView != null) catView.stopMotion();
        if (attached) {
            savePosition();
            try {
                windowManager.removeView(catView);
            } catch (IllegalArgumentException exception) {
                // 权限撤销后系统可能已移除窗口。
            }
        }
        attached = false;
        if (catView != null) catView.release();
        catView = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        PetQuickTileService.requestSync(this);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
