package com.shinjuku.showdown;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/** 各界面共用：画到系统栏后面、内容避开系统栏和刘海、战斗时隐藏系统栏。 */
final class SystemUi {
    private SystemUi() {
    }

    @SuppressWarnings("deprecation")
    static void drawBehindSystemBars(Activity a) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            a.getWindow().setDecorFitsSystemWindows(false);
        } else {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** 横屏游戏界面：隐藏状态栏和导航栏，从边缘滑动可临时呼出。 */
    @SuppressWarnings("deprecation")
    static void immersive(Activity a) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            a.getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = a.getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /** 让 target 的内边距避开系统栏和刘海（保留它原来的内边距）；overlay 跟着左上角安全区平移。 */
    @SuppressWarnings("deprecation")
    static void applySafeInsets(View target, View overlay) {
        final int l = target.getPaddingLeft(), t = target.getPaddingTop();
        final int r = target.getPaddingRight(), b = target.getPaddingBottom();
        target.setOnApplyWindowInsetsListener((v, insets) -> {
            int sl, st, sr, sb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                sl = s.left;
                st = s.top;
                sr = s.right;
                sb = s.bottom;
            } else {
                sl = insets.getSystemWindowInsetLeft();
                st = insets.getSystemWindowInsetTop();
                sr = insets.getSystemWindowInsetRight();
                sb = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cut = insets.getDisplayCutout();
                    if (cut != null) {
                        sl = Math.max(sl, cut.getSafeInsetLeft());
                        st = Math.max(st, cut.getSafeInsetTop());
                        sr = Math.max(sr, cut.getSafeInsetRight());
                        sb = Math.max(sb, cut.getSafeInsetBottom());
                    }
                }
            }
            v.setPadding(l + sl, t + st, r + sr, b + sb);
            if (overlay != null) {
                overlay.setTranslationX(sl);
                overlay.setTranslationY(st);
            }
            return insets;
        });
        target.requestApplyInsets();
    }
}
