package com.yuyu.bongocat;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import app.rive.runtime.kotlin.RiveAnimationView;
import app.rive.runtime.kotlin.core.Alignment;
import app.rive.runtime.kotlin.core.Direction;
import app.rive.runtime.kotlin.core.Fit;
import app.rive.runtime.kotlin.core.Loop;
import app.rive.runtime.kotlin.core.errors.ViewModelException;
import app.rive.runtime.kotlin.core.ViewModelInstance;
import app.rive.runtime.kotlin.core.ViewModelNumberProperty;

/** 预览和浮窗共用 Rive 文件、外观绑定和触摸入口。 */
public final class RiveView extends FrameLayout {
    private final RiveAnimationView renderer;
    private final SignTextView signText;
    private boolean holdingSign;
    private Runnable signClick;
    private final PetLifecycle lifecycle = new PetLifecycle();
    private final RiveMovement movement = new RiveMovement();
    private final CrawlMotion previewMotion = new CrawlMotion();
    private final ViewModelInstance model;
    private final ViewModelNumberProperty state, progress, directionX, directionY, actionProgress;
    private final PetInteraction interactions = new PetInteraction();
    private Runnable pendingAction;
    private boolean edgeMode, hasClickPoint, touchMoved;
    private float clickX, clickY, touchX, touchY;
    private RiveMovement.Host movementHost;
    private boolean active = true, touching, attached, released, lastWasLeft;
    private boolean playing;
    private CrawlListener crawlListener;
    private boolean lastLyingCrawl;

    /** 趴地爬开始/结束时通知，App 层据此把浮窗变宽/变回方形。 */
    public interface CrawlListener {
        void onLyingCrawlChanged(boolean lying);
    }

    public void setCrawlListener(CrawlListener listener) { crawlListener = listener; }

    private static final class PetLifecycle implements LifecycleOwner {
        final LifecycleRegistry registry = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return registry; }
    }

    private static final class RendererContext extends ContextWrapper implements LifecycleOwner {
        private final PetLifecycle owner;
        RendererContext(Context context, PetLifecycle owner) { super(context); this.owner = owner; }
        @Override public Lifecycle getLifecycle() { return owner.getLifecycle(); }
    }

    private final RiveMovement.Host previewHost = new RiveMovement.Host() {
        @Override public void begin(float dx, float dy) {
            if (!(getParent() instanceof View)) return;
            View stage = (View) getParent();
            previewMotion.begin(getTranslationX(), getTranslationY(), -getLeft(), -getTop(),
                    stage.getWidth() - getRight(), stage.getHeight() - getBottom(), dx, dy);
        }
        @Override public boolean move(float fraction) {
            if (!(getParent() instanceof View)) return false;
            boolean room = previewMotion.move(fraction);
            setTranslationX(previewMotion.x);
            setTranslationY(previewMotion.y);
            clampPreview();
            return room;
        }
        @Override public void stopped() { }
    };

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!shouldPlay()) return;
            int currentState = Math.round(state.getValue());
            boolean lying = currentState == RiveMovement.STATE_CRAWL_LIE
                    || currentState == RiveMovement.STATE_CRAWL_LIE_HOLD || currentState == 16;
            if (lying != lastLyingCrawl) {
                lastLyingCrawl = lying;
                // 趴姿是横的：浮窗变宽时用 COVER 让趴猫填满窗口，坐姿时回 CONTAIN。
                try {
                    renderer.setFit(lying ? Fit.COVER : Fit.CONTAIN);
                } catch (RuntimeException e) {
                    Log.w("RiveView", "Rive 切换 Fit 失败", e);
                }
                if (crawlListener != null) crawlListener.onLyingCrawlChanged(lying);
            }
            // 准备状态 16 只展开窗口，进入爬行状态 11 后才开始移动。
            signText.setVisibility(holdingSign && currentState == 26 ? VISIBLE : INVISIBLE);
            if (!edgeMode && !holdingSign && (!lying || currentState == 11 || currentState == 12)) {
                boolean room = movement.update(currentState, progress.getValue(),
                        directionX.getValue() * getWidth(), directionY.getValue() * getHeight(), host());
                if (!room) finishCrawl();
            }
            postDelayed(this, 16);
        }
    };

    public RiveView(Context context) { this(context, null); }

    public RiveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewTreeLifecycleOwner.set(this, lifecycle);
        lifecycle.registry.setCurrentState(Lifecycle.State.CREATED);
        renderer = new RiveAnimationView(new RendererContext(context, lifecycle), null);
        renderer.setOpaque(false);
        renderer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        renderer.setRiveResource(R.raw.bongocat, "BongoCat", null, "PetStateMachine",
                false, true, Fit.CONTAIN, Alignment.CENTER, Loop.AUTO);
        model = renderer.getController().getActiveArtboard().getViewModelInstance();
        if (model == null) throw new IllegalStateException("Rive PetModel 未绑定");
        try {
            state = model.getNumberProperty("state");
            progress = model.getNumberProperty("crawlProgress");
            directionX = model.getNumberProperty("crawlX");
            directionY = model.getNumberProperty("crawlY");
            actionProgress = model.getNumberProperty("actionProgress");
        } catch (ViewModelException e) {
            throw new IllegalStateException("Rive PetModel 属性未绑定", e);
        }
        addView(renderer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        signText = new SignTextView(context);
        signText.setVisibility(INVISIBLE);
        addView(signText, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setClickable(true);
    }

    public void applySettings(PetSettings settings) {
        try {
            model.getColorProperty("bodyColor").setValue(settings.combinedTint());
            model.getNumberProperty("costume").setValue((float) settings.getCostume());
        } catch (ViewModelException e) {
            Log.w("RiveView", "Rive 外观绑定失败", e);
        }
        setAlpha(settings.getOpacity() / 100f);
        setContentDescription(PetSettings.COLOR_NAMES[settings.getColor()] + "猫，配饰："
                + PetSettings.COSTUME_NAMES[settings.getCostume()]);
        if (shouldPlay()) renderer.play("PetStateMachine", Loop.AUTO, Direction.AUTO, true, false);
    }

    public void tapAlternate() { tap(!lastWasLeft); }

    public void tap(boolean left) {
        if (holdingSign) return;
        lastWasLeft = left;
        movement.cancel(host());
        try {
            model.getBooleanProperty("left").setValue(left);
            model.getTriggerProperty("interaction").trigger();
        } catch (ViewModelException e) {
            Log.w("RiveView", "Rive 点按绑定失败", e);
        }
        // 唤醒静止状态机，但不重新创建它，保留七种动作的轮换记录。
        if (shouldPlay()) renderer.play("PetStateMachine", Loop.AUTO, Direction.AUTO, true, false);
    }

    /** 动作页点名触发：按 trigger 名发给状态机，做对应的动作。 */
    public void playAction(final String trigger) {
        cancelPendingAction();
        if (edgeMode || holdingSign) return;
        int current = Math.round(state.getValue());
        boolean crawling = current == RiveMovement.STATE_CRAWL_SIT
                || current == RiveMovement.STATE_CRAWL_SIT_HOLD
                || current == RiveMovement.STATE_CRAWL_LIE
                || current == RiveMovement.STATE_CRAWL_LIE_HOLD;
        movement.cancel(host());
        if (crawling) {
            // 正在爬时先停爬回 base，等一拍再触发动作，避免 trigger 在爬行状态里被吞掉。
            try {
                model.getTriggerProperty("stopCrawl").trigger();
            } catch (ViewModelException e) {
                Log.w("RiveView", "Rive 停止爬行绑定失败", e);
            }
            pendingAction = () -> { pendingAction = null; if (!released && !edgeMode) fireTrigger(trigger); };
            postDelayed(pendingAction, 300);
        } else {
            fireTrigger(trigger);
        }
        if (shouldPlay()) renderer.play("PetStateMachine", Loop.AUTO, Direction.AUTO, true, false);
    }

    private void fireTrigger(String trigger) {
        if (released) return;
        try {
            model.getTriggerProperty(trigger).trigger();
        } catch (ViewModelException e) {
            Log.w("RiveView", "Rive 动作触发失败：" + trigger, e);
        }
    }

    private void cancelPendingAction() {
        if (pendingAction != null) removeCallbacks(pendingAction);
        pendingAction = null;
    }

    public void performPartClick(float x, float y) {
        if (holdingSign) return;
        clickX = x; clickY = y; hasClickPoint = true;
        performClick();
    }

    @Override public boolean performClick() {
        super.performClick();
        if (holdingSign) { if (signClick != null) signClick.run(); return true; }
        if (!hasClickPoint) { if (edgeMode) fireTrigger("petPeek"); else tapAlternate(); return true; }
        hasClickPoint = false;
        if (edgeMode) { fireTrigger("petPeek"); return true; }
        PetInteraction.Action action = interactions.tap(clickX, clickY, getWidth(), getHeight(),
                lastLyingCrawl, Math.round(state.getValue()), actionProgress.getValue(), SystemClock.uptimeMillis());
        if (action == PetInteraction.Action.NONE) return true;
        if (action == PetInteraction.Action.WAKE) { tapAlternate(); return true; }
        if (action == PetInteraction.Action.LEFT_PAW || action == PetInteraction.Action.RIGHT_PAW) {
            tap(action == PetInteraction.Action.LEFT_PAW); return true;
        }
        fireTrigger("touchActivity");
        switch (action) {
            case TAIL: fireTrigger("playTail"); break;
            case HEAD: fireTrigger("petHead"); break;
            case NOSE: fireTrigger("boopNose"); break;
            case STRETCH: fireTrigger("playStretch"); break;
            case BELLY: fireTrigger("tickleBelly"); break;
            case SHY: fireTrigger("teaseHead"); break;
            default: break;
        }
        return true;
    }

    public void setEdgeMode(boolean enabled, boolean peek, boolean faceRight) {
        cancelPendingAction();
        movement.cancel(host());
        interactions.reset();
        edgeMode = enabled;
        try {
            model.getBooleanProperty("edgeMode").setValue(enabled);
            model.getBooleanProperty("edgePeek").setValue(enabled && peek);
            renderer.setScaleX(enabled && faceRight ? -1f : 1f);
            renderer.setFit(Fit.CONTAIN);
            fireTrigger("touchActivity");
        } catch (ViewModelException e) { Log.w("RiveView", "横屏模式绑定失败", e); }
    }

    public void setEdgePresentation(int base, boolean cropped) {
        LayoutParams layout = new LayoutParams(cropped ? base : LayoutParams.MATCH_PARENT,
                cropped ? base : LayoutParams.MATCH_PARENT);
        if (cropped) {
            layout.leftMargin = -(base - Math.round(base * .62f)) / 2;
            layout.topMargin = Math.round(base * .45f) - base;
        }
        renderer.setLayoutParams(layout);
    }

    public void setMovementHost(RiveMovement.Host host) { movementHost = host; }

    public void setSign(String text) {
        boolean enabled = text != null;
        signText.setText(enabled ? text : "");
        if (enabled == holdingSign) return;
        holdingSign = enabled;
        cancelPendingAction(); movement.cancel(host()); interactions.reset();
        if (!enabled) signText.setVisibility(INVISIBLE);
        try { model.getBooleanProperty("holdSign").setValue(enabled); fireTrigger("touchActivity"); }
        catch (ViewModelException exception) { Log.w("RiveView", "举牌绑定失败", exception); }
    }

    public boolean hitsSign(float x, float y) { return holdingSign && signText.hits(x, y); }
    public void setSignClick(Runnable click) { signClick = click; }
    private RiveMovement.Host host() { return movementHost == null ? previewHost : movementHost; }

    private void finishCrawl() {
        movement.cancel(host());
        try {
            model.getTriggerProperty("stopCrawl").trigger();
        } catch (ViewModelException e) {
            Log.w("RiveView", "Rive 停止爬行绑定失败", e);
        }
    }

    public void beginTouch() {
        cancelPendingAction();
        movement.cancel(host());
        touching = true;
        syncPlayback();
    }

    public void endTouch(boolean dragged) {
        touching = false;
        if (dragged) { interactions.reset(); if (!edgeMode) tap(lastWasLeft); }
        syncPlayback();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            touchX = event.getX(); touchY = event.getY(); touchMoved = false;
            beginTouch();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && Math.hypot(event.getX() - touchX,
                event.getY() - touchY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) touchMoved = true;
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            endTouch(false);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (!touchMoved && event.getActionMasked() == MotionEvent.ACTION_UP) performPartClick(event.getX(), event.getY());
            else interactions.reset();
        }
        return true;
    }

    public void setBehaviorActive(boolean value) { active = value; syncPlayback(); }

    private boolean shouldPlay() {
        return attached && active && !touching && !released && getWindowVisibility() == VISIBLE;
    }

    private void syncPlayback() {
        removeCallbacks(frame);
        if (renderer == null || released) return;
        boolean next = shouldPlay();
        if (next && !playing) renderer.play("PetStateMachine", Loop.AUTO, Direction.AUTO, true, false);
        else if (!next) renderer.pause();
        playing = next;
        if (next) post(frame);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        lifecycle.registry.setCurrentState(Lifecycle.State.RESUMED);
        post(this::syncPlayback);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        syncPlayback();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (model != null && (Math.round(state.getValue()) == 7 || Math.round(state.getValue()) == 11)) finishCrawl();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        clampPreview();
    }

    private void clampPreview() {
        if (movementHost != null || !(getParent() instanceof View)) return;
        View stage = (View) getParent();
        setTranslationX(CrawlMotion.clamp(getTranslationX(), -getLeft(), stage.getWidth() - getRight()));
        setTranslationY(CrawlMotion.clamp(getTranslationY(), -getTop(), stage.getHeight() - getBottom()));
    }

    public void stopMotion() {
        cancelPendingAction();
        active = false;
        movement.cancel(host());
        syncPlayback();
    }

    /** 先移除渲染视图，再释放属于本实例的原生资源。 */
    public void release() {
        if (released) return;
        stopMotion();
        removeView(renderer);
        lifecycle.registry.setCurrentState(Lifecycle.State.DESTROYED);
        released = true;
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        syncPlayback();
        super.onDetachedFromWindow();
    }
}
