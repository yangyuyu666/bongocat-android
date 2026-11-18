package com.yuyu.bongocat;

import android.content.Context;
import android.content.Intent;

/** 悬浮猫的表单使用独立任务，不把后台主界面带到前台。 */
public final class PetToolsPopupActivity extends PetToolsActivity {
    public static Intent intent(Context context, String page) {
        return new Intent(context, PetToolsPopupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("page", page);
    }
}
