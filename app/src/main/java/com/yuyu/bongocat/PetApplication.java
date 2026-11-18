package com.yuyu.bongocat;

import android.app.Application;
import app.rive.runtime.kotlin.core.RendererType;
import app.rive.runtime.kotlin.core.Rive;

public final class PetApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // 正式包先核对安装签名，再初始化动画和其他应用功能。
        if (!BuildConfig.DEBUG) AppIntegrity.requireTrusted(this);
        Rive.INSTANCE.init(this, RendererType.Rive);
    }
}
