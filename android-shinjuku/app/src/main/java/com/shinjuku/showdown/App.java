package com.shinjuku.showdown;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * 应用级状态：数一数有几个界面处在“已启动”（可见）状态。
 * 从主页跳到选难度、再跳到战斗时，新界面 onStart 先于旧界面 onStop，计数不会掉到 0，
 * 所以背景音乐在界面之间切换时不会断；只有整个应用退到后台才暂停。
 */
public class App extends Application {

    private int started;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                if (started++ == 0) Bgm.get(App.this).setHostPaused(false);
            }

            @Override
            public void onActivityStopped(Activity a) {
                if (--started == 0) Bgm.get(App.this).setHostPaused(true);
            }

            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
