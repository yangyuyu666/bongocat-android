package com.yuyu.bongocat;

import android.app.Application;
import app.rive.runtime.kotlin.core.RendererType;
import app.rive.runtime.kotlin.core.Rive;

public final class PetApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Rive.INSTANCE.init(this, RendererType.Rive);
    }
}
