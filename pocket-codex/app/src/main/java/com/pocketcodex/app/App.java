package com.pocketcodex.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatDelegate;

/** 应用入口：通知频道、主题、前后台判断（空闲的运行时在退到后台 60 秒后关掉）。 */
public final class App extends Application {
    private static int started;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final long IDLE_SHUTDOWN_MS = 60_000;

    static boolean inForeground() {
        return started > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RuntimeService.createChannels(this);
        Workspaces.loadNames(this);
        applyTheme(new Prefs(this).theme());
        final CodexRuntimeManager rt = CodexRuntimeManager.get(this);
        final Runnable[] idleCheck = new Runnable[1];
        idleCheck[0] = () -> {
            // 退到后台超过一分钟、而且没有活：关掉 codex，省电省内存（下次打开再启动，约 1–2 秒）
            if (started != 0) return;
            if (RuntimeService.needed(rt)) main.postDelayed(idleCheck[0], IDLE_SHUTDOWN_MS); // 还在忙：过一分钟再看
            else if (rt.serverAlive()) rt.shutdown();
        };
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                started++;
                main.removeCallbacks(idleCheck[0]);
            }

            @Override
            public void onActivityStopped(Activity a) {
                started--;
                if (started == 0) main.postDelayed(idleCheck[0], IDLE_SHUTDOWN_MS);
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityResumed(Activity a) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }

    static void applyTheme(int t) {
        AppCompatDelegate.setDefaultNightMode(t == 1 ? AppCompatDelegate.MODE_NIGHT_NO : t == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
