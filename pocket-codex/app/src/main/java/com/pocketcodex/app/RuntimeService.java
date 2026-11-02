package com.pocketcodex.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import com.pocketcodex.core.Conversation;
import com.pocketcodex.core.Installer;

/**
 * 前台服务：安装、登录、Agent 工作期间让 App 进程（以及 codex 子进程）保持在前台优先级，并显示通知。
 * 没事做时自己停掉。所有逻辑在 {@link CodexRuntimeManager}，这里只管通知和唤醒锁。
 */
public final class RuntimeService extends Service {
    static final String CH_RUNTIME = "runtime", CH_ATTENTION = "attention", CH_DOWNLOAD = "downloads";
    private static final int ID_ONGOING = 1, ID_ATTENTION = 2;
    private static final String ACTION_STOP_TURN = "stop_turn", ACTION_SYNC = "sync";

    private static boolean running;
    private PowerManager.WakeLock wake;
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    /** 忙的时候每 5 分钟续一次（每次 10 分钟），不忙就停。 */
    private final Runnable renew = new Runnable() {
        @Override
        public void run() {
            CodexRuntimeManager rt = CodexRuntimeManager.get(RuntimeService.this);
            boolean busy = rt.stage == CodexRuntimeManager.Stage.INSTALLING || (rt.runningThreadId != null && rt.pendingApprovalCount() == 0);
            holdWake(busy);
            if (busy) h.postDelayed(this, 5 * 60_000L);
        }
    };

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel rt = new NotificationChannel(CH_RUNTIME, "Codex 运行状态", NotificationManager.IMPORTANCE_LOW);
        rt.setDescription("Agent 工作时的常驻通知");
        rt.setShowBadge(false);
        rt.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationChannel at = new NotificationChannel(CH_ATTENTION, "需要你处理", NotificationManager.IMPORTANCE_DEFAULT);
        at.setDescription("审批请求、需要登录、任务完成或失败");
        at.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationChannel dl = new NotificationChannel(CH_DOWNLOAD, "下载与安装", NotificationManager.IMPORTANCE_LOW);
        dl.setShowBadge(false);
        nm.createNotificationChannel(rt);
        nm.createNotificationChannel(at);
        nm.createNotificationChannel(dl);
    }

    /** 需要前台服务吗：安装中、登录中、启动中、有回合在跑、有审批等着。 */
    static boolean needed(CodexRuntimeManager rt) {
        return rt.stage == CodexRuntimeManager.Stage.INSTALLING || rt.stage == CodexRuntimeManager.Stage.LOGGING_IN
                || rt.stage == CodexRuntimeManager.Stage.STARTING || rt.runningThreadId != null || rt.pendingApprovalCount() > 0
                || rt.toolkitState == CodexRuntimeManager.ToolkitState.INSTALLING;
    }

    /** 状态变了就调：按需启动/更新/停止服务。 */
    static void sync(Context c, CodexRuntimeManager rt) {
        boolean need = needed(rt);
        if (need) {
            Intent i = new Intent(c, RuntimeService.class).setAction(ACTION_SYNC);
            try {
                if (!running) c.startForegroundService(i);
                else c.startService(i);
            } catch (Exception ignored) {
                // 后台启动限制（极少见：App 在后台时由服务自身状态变化触发）——下次回前台再同步
            }
        } else if (running) {
            c.stopService(new Intent(c, RuntimeService.class));
        }
        updateAttention(c, rt);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannels(this);
        startForeground(ID_ONGOING, build(CodexRuntimeManager.get(this)));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        CodexRuntimeManager rt = CodexRuntimeManager.get(this);
        if (intent != null && ACTION_STOP_TURN.equals(intent.getAction())) {
            Conversation c = rt.runningConversation();
            if (c != null) rt.interrupt(c);
        }
        if (!needed(rt)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        getSystemService(NotificationManager.class).notify(ID_ONGOING, build(rt));
        h.removeCallbacks(renew);
        renew.run();
        return START_NOT_STICKY;
    }

    private void holdWake(boolean on) {
        if (on) {
            if (wake == null) {
                wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pocketcodex:agent");
                wake.setReferenceCounted(false);
            }
            wake.acquire(10 * 60_000L); // 每次续 10 分钟，状态变化时会再续
        } else if (wake != null && wake.isHeld()) {
            wake.release();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(renew);
        holdWake(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification build(CodexRuntimeManager rt) {
        Notification.Builder b = new Notification.Builder(this, rt.stage == CodexRuntimeManager.Stage.INSTALLING ? CH_DOWNLOAD : CH_RUNTIME)
                .setSmallIcon(R.drawable.ic_stat_codex)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(this))
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        switch (rt.stage) {
            case INSTALLING: {
                CodexRuntimeManager.InstallProgress p = rt.install;
                String what = p.stage == Installer.Stage.DOWNLOAD ? "正在下载 Codex CLI" : p.stage == Installer.Stage.VERIFY ? "正在校验安装包" : p.stage == Installer.Stage.EXTRACT ? "正在安装" : "正在准备下载";
                b.setContentTitle(what);
                if (p.total > 0) {
                    b.setProgress(1000, (int) (p.done * 1000 / p.total), false);
                    b.setContentText(Format.mb(p.done) + " / " + Format.mb(p.total));
                } else {
                    b.setProgress(0, 0, true);
                }
                break;
            }
            case LOGGING_IN:
                b.setContentTitle("等待登录").setContentText("在浏览器里输入一次性代码，完成后回到这里");
                break;
            case STARTING:
                b.setContentTitle("Codex 正在启动").setProgress(0, 0, true);
                break;
            default: {
                int approvals = rt.pendingApprovalCount();
                if (approvals == 0 && rt.runningThreadId == null && rt.toolkitState == CodexRuntimeManager.ToolkitState.INSTALLING) {
                    b.setContentTitle("正在安装开发工具").setContentText(rt.toolkitStep == null ? "Node.js、Git、Python…" : rt.toolkitStep);
                    if (rt.toolkitTotal > 0) b.setProgress(1000, (int) (rt.toolkitDone * 1000 / rt.toolkitTotal), false);
                    else b.setProgress(0, 0, true);
                } else if (approvals > 0) {
                    b.setContentTitle("等你确认").setContentText(approvals + " 个操作等待批准");
                } else {
                    b.setContentTitle("Codex 正在工作").setContentText("在 " + Format.workspaceName(rt) + " 里修改文件、运行命令");
                    b.setUsesChronometer(true);
                    Intent stop = new Intent(this, RuntimeService.class).setAction(ACTION_STOP_TURN);
                    b.addAction(new Notification.Action.Builder(null, "停止", PendingIntent.getService(this, 1, stop,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
                }
            }
        }
        return b.build();
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int lastApprovals;

    /** App 在后台时，有新的审批请求就发一个提醒通知（不弹界面）。 */
    private static void updateAttention(Context c, CodexRuntimeManager rt) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        int n = rt.pendingApprovalCount();
        if (n > lastApprovals && !App.inForeground()) {
            Notification.Builder b = new Notification.Builder(c, CH_ATTENTION)
                    .setSmallIcon(R.drawable.ic_stat_codex)
                    .setContentTitle("Codex 需要你确认")
                    .setContentText("有 " + n + " 个操作等待批准，点开查看")
                    .setAutoCancel(true)
                    .setContentIntent(openApp(c))
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            nm.notify(ID_ATTENTION, b.build());
        } else if (n == 0) {
            nm.cancel(ID_ATTENTION);
        }
        lastApprovals = n;
    }

    /** 回合结束时（App 在后台）提醒一下。 */
    static void notifyTurnEnded(Context c, String title, String text) {
        if (App.inForeground()) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify(ID_ATTENTION, new Notification.Builder(c, CH_ATTENTION)
                .setSmallIcon(R.drawable.ic_stat_codex)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build());
    }
}
