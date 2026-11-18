package com.yuyu.bongocat;

/** 读取 Rive 的爬行输出，把进度交给预览或窗口坐标。 */
public final class RiveMovement {
    public static final int STATE_CRAWL_SIT = 7;
    public static final int STATE_CRAWL_SIT_HOLD = 8;
    public static final int STATE_CRAWL_LIE = 11;
    public static final int STATE_CRAWL_LIE_HOLD = 12;

    public interface Host {
        void begin(float dx, float dy);
        boolean move(float progress);
        void stopped();
    }

    private boolean moving;
    private boolean waitingForExit;

    public boolean update(int state, float progress, float dx, float dy, Host host) {
        if (state != STATE_CRAWL_SIT && state != STATE_CRAWL_LIE) {
            if (moving) {
                // 正常爬完补齐最后一帧，避免位置停在终点前。
                if (state == STATE_CRAWL_SIT_HOLD || state == STATE_CRAWL_LIE_HOLD) host.move(1f);
                cancel(host);
            }
            waitingForExit = false;
            return true;
        }
        if (waitingForExit) return true;
        if (!moving) {
            moving = true;
            host.begin(dx, dy);
        }
        return host.move(CrawlMotion.clamp(progress, 0, 1));
    }

    public void cancel(Host host) {
        if (moving) host.stopped();
        moving = false;
        waitingForExit = true;
    }

    public boolean isMoving() { return moving; }
}
