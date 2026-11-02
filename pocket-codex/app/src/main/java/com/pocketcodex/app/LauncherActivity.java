package com.pocketcodex.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** 入口：装好、有凭据、选好工作区就直接进主界面（主界面里再启动 Codex），否则走首次设置。 */
public final class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CodexRuntimeManager rt = CodexRuntimeManager.get(this);
        boolean installed = new java.io.File(rt.paths.root, "current").isFile();
        boolean auth = rt.paths.authFile().isFile();
        boolean ws = rt.prefs.workspace() != null;
        startActivity(new Intent(this, installed && auth && ws ? MainActivity.class : SetupActivity.class));
        finish();
    }
}
