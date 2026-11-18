package com.yuyu.bongocat;

/** 优先把菜单放在宠物右侧，空间不足时换边并限制在屏幕内。 */
public final class ToolPopupPlacement {
    public static int[] place(int screenW, int screenH, int x, int y, int petW, int width, int height, int gap) {
        int left = x + petW + gap;
        if (left + width > screenW - gap) left = x - width - gap;
        left = Math.max(gap, Math.min(Math.max(gap, screenW - width - gap), left));
        int top = Math.max(gap, Math.min(Math.max(gap, screenH - height - gap), y));
        return new int[]{left, top};
    }
    private ToolPopupPlacement() { }
}
