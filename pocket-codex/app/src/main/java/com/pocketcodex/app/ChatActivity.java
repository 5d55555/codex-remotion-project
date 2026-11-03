package com.pocketcodex.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pocketcodex.core.Conversation;
import com.pocketcodex.core.Json;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话页（v0.2 按 ChatGPT 手机端的样子重做）：
 * <ul>
 * <li>顶栏：左边圆形菜单键（回会话列表），新对话时中间是“聊天 / 工作”分段，进了对话显示标题和状态，右边“⋯”；</li>
 * <li>底部大圆角输入卡片：附件条、输入框，下面一行“＋”、模型标签（如“6.1 Sol 中”）、麦克风、蓝色圆形发送键；</li>
 * <li>“＋”弹出拍照 / 照片 / 文件 / 插件；输入“@”在输入框上方列出技能、插件和工作区文件；</li>
 * <li>模型标签打开底部面板：模型、推理、速度、权限，每行点开是下拉卡片，底部黑色“完成”。</li>
 * </ul>
 * 新对话第一次发送时才创建 thread。回合运行中：空输入时发送键变成“停止”，有内容时是“追加”（turn/steer）。审批用底部弹窗。
 */
public final class ChatActivity extends AppCompatActivity implements CodexRuntimeManager.Listener, CodexRuntimeManager.ConversationListener, SessionDrawer.Host {
    static final String EXTRA_THREAD = "thread", EXTRA_CWD = "cwd", EXTRA_DRAFT = "draft", EXTRA_OPEN_DRAWER = "openDrawer";
    private static final int REQ_CAMERA = 11, REQ_PHOTOS = 12, REQ_FILE = 13, REQ_VOICE = 14, REQ_VOICE_SEND = 15;

    private CodexRuntimeManager rt;
    private Conversation conv;
    private String threadId, cwd;
    /** 聊天模式：只聊天，不跑命令不改文件（会话放在专用的空目录里）。 */
    private boolean chatMode;
    private ChatAdapter adapter;
    private RecyclerView list;
    private LinearLayoutManager lm;
    // 顶栏
    private LinearLayout segmented, titleBox;
    private TextView segChat, segWork, title, subtitle;
    // 计划、待处理、空状态
    private TextView planText, pendingText, statusText, emptyTitle, emptyHint, wsPill;
    private LinearLayout planBar, planSteps, pendingBar, emptyBox, suggestRow;
    private HorizontalScrollView suggestScroll;
    private LinearProgressIndicator planProgress;
    private MaterialButton olderBar;
    private ImageView toLatest;
    // 输入卡片
    private LinearLayout composer, attachRow;
    private HorizontalScrollView attachScroll;
    private EditText input;
    private TextView modelChip;
    private ImageView plusBtn, micBtn, sendIcon, plusMini, micMini;
    private FrameLayout sendBtn, voicePill;
    private ImageView voiceIcon;
    private TextView voiceText;
    private LinearLayout bottomRow, header;
    private View topFade;
    private FrameLayout frame;
    // 左边拉出的会话抽屉
    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private SessionDrawer drawer;
    private androidx.activity.OnBackPressedCallback drawerBack;
    private final List<Attachment> attachments = new ArrayList<>();
    // “@”面板（不拦截输入框的触摸）
    private FrameLayout mentionLayer;
    private int mentionStart = -1;
    private String mentionQuery;
    private List<String[]> mentionFiles = new ArrayList<>();

    private final java.util.Set<Integer> changedRows = new java.util.HashSet<>();
    private InputDialog inputDialog;
    private ApprovalSheet sheet;
    private boolean sending, stopping, loading;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean refreshPending, structurePending;
    private File pendingCamera;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rt = CodexRuntimeManager.get(this);
        threadId = getIntent().getStringExtra(EXTRA_THREAD);
        cwd = getIntent().getStringExtra(EXTRA_CWD);
        if (b != null) { // 旋转/重建：新对话发送后已经有 thread 了
            if (b.getString(EXTRA_THREAD) != null) threadId = b.getString(EXTRA_THREAD);
            if (b.getString(EXTRA_CWD) != null) cwd = b.getString(EXTRA_CWD);
            if (b.getString("camera") != null) pendingCamera = new File(b.getString("camera"));
        }
        if (cwd == null) cwd = rt.prefs.workspace();
        chatMode = threadId != null ? rt.isChatCwd(cwd) : (b != null && b.containsKey("chat") ? b.getBoolean("chat") : !rt.prefs.workMode());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sheet = new ApprovalSheet(this, rt);
        setContentView(build());
        if (b != null) restoreAttachments(b.getString("attachments"));
        String draft = b != null && b.getString("draftText") != null ? b.getString("draftText") : getIntent().getStringExtra(EXTRA_DRAFT);
        if (draft != null) input.setText(draft);
        if (threadId != null) {
            String saved = getSharedPreferences("drafts", MODE_PRIVATE).getString(threadId, null);
            if (saved != null && draft == null) input.setText(saved);
        }
        frame.requestFocus();
        bindComposer();
        if (b == null && getIntent().getBooleanExtra(EXTRA_OPEN_DRAWER, false)) drawerLayout.post(this::openDrawer);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(EXTRA_THREAD, threadId);
        out.putString(EXTRA_CWD, cwd);
        out.putBoolean("chat", chatMode);
        out.putString("draftText", input.getText().toString());
        if (pendingCamera != null) out.putString("camera", pendingCamera.getAbsolutePath());
        List<Object> l = new ArrayList<>();
        for (Attachment a : attachments) l.add(Json.map("kind", a.kind.name(), "name", a.name, "id", a.id, "path", a.path, "inline", a.inline));
        out.putString("attachments", Json.write(Json.map("list", l)));
    }

    private void restoreAttachments(String s) {
        if (s == null) return;
        try {
            for (Object o : Json.arr(Json.parseObject(s), "list")) {
                Attachment a = new Attachment(Attachment.Kind.valueOf(Json.str(o, "kind")), Json.str(o, "name"), Json.str(o, "id"), Json.str(o, "path"));
                a.inline = Json.str(o, "inline");
                attachments.add(a);
            }
        } catch (Exception ignored) {
            // 坏了就不恢复
        }
        renderAttachments();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private int color(int res) {
        return Ui.color(this, res);
    }

    /* ---------------- 布局 ---------------- */

    private View build() {
        frame = new FrameLayout(this);
        frame.setFitsSystemWindows(true);
        // 一进来不让输入框抢焦点：ChatGPT 的输入框收起时是一行，点了才展开
        frame.setFocusableInTouchMode(true);
        // 很淡的从上到下的渐变（ChatGPT 空白页那种底部发灰）
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{color(R.color.bg), color(R.color.bg), color(R.color.bg_low)}));
        LinearLayout root = Ui.vbox(this);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mentionLayer = new FrameLayout(this);
        frame.addView(mentionLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部：顶栏、计划条、“更早的记录”浮在消息列表上，列表滚上去是渐隐的（不是一刀切）
        header = Ui.vbox(this);
        header.addView(buildTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        // 计划
        planBar = Ui.vbox(this);
        planBar.setPadding(dp(16), dp(10), dp(16), dp(12));
        planBar.setBackground(Ui.round(this, R.color.surface2, 0, 20));
        LinearLayout prow = Ui.hbox(this);
        prow.addView(Ui.icon(this, R.drawable.ic_tasks, R.color.text, 18));
        planText = Ui.text(this, "", 14, R.color.text);
        LinearLayout.LayoutParams pl = Ui.weight1();
        pl.leftMargin = dp(10);
        prow.addView(planText, pl);
        prow.addView(Ui.icon(this, R.drawable.ic_chev_down, R.color.text2, 18));
        planBar.addView(prow);
        planProgress = new LinearProgressIndicator(this);
        planProgress.setTrackStopIndicatorSize(0);
        planProgress.setIndicatorColor(color(R.color.success));
        planProgress.setTrackColor(color(R.color.border));
        planProgress.setTrackThickness(dp(3));
        planProgress.setTrackCornerRadius(dp(2));
        planBar.addView(planProgress, Ui.matchWrap(this, 8));
        planSteps = Ui.vbox(this);
        planSteps.setVisibility(View.GONE);
        planBar.addView(planSteps, Ui.matchWrap(this, 4));
        planBar.setOnClickListener(v -> planSteps.setVisibility(planSteps.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        planBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbl = Ui.matchWrap();
        pbl.leftMargin = pbl.rightMargin = dp(14);
        pbl.bottomMargin = dp(4);
        header.addView(planBar, pbl);

        olderBar = Ui.textButton(this, "加载更早的记录", v -> loadOlder());
        olderBar.setTextColor(color(R.color.text2));
        olderBar.setVisibility(View.GONE);
        header.addView(olderBar, Ui.matchWrap());

        // 消息列表 + 空状态
        FrameLayout listHost = new FrameLayout(this);
        list = new RecyclerView(this);
        lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        list.setItemAnimator(null); // 流式更新不要闪
        list.setPadding(0, dp(72), 0, dp(16));
        list.setClipToPadding(false);
        adapter = new ChatAdapter(this);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                toLatest.setVisibility(nearBottom() ? View.GONE : View.VISIBLE);
            }
        });
        listHost.addView(list);
        listHost.addView(buildEmpty(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        toLatest = Ui.roundButton(this, R.drawable.ic_arrow_up, "回到最新", v -> scrollToBottom());
        toLatest.setRotation(180);
        toLatest.setPadding(dp(10), dp(10), dp(10), dp(10));
        toLatest.setVisibility(View.GONE);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = dp(12);
        listHost.addView(toLatest, fl);
        // 底部：消息滑到输入框上方时淡出
        View chatBottomFade = Ui.fade(this, R.color.bg_low, false);
        listHost.addView(chatBottomFade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28), Gravity.BOTTOM));
        Ui.followScroll(chatBottomFade, list, false);
        topFade = Ui.fade(this, R.color.bg, true);
        listHost.addView(topFade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96), Gravity.TOP));
        Ui.followScroll(topFade, list, true);
        listHost.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h == ob - ot && list.getPaddingTop() == h + dp(4)) return;
            list.setPadding(0, h + dp(4), 0, dp(16));
            ViewGroup.LayoutParams fp = topFade.getLayoutParams();
            fp.height = h + dp(28);
            topFade.setLayoutParams(fp);
            emptyBox.setPadding(dp(28), h, dp(28), dp(24));
        });
        root.addView(listHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 待处理（审批、提问）
        pendingBar = Ui.hbox(this);
        pendingBar.setBackground(Ui.round(this, R.color.warning_soft, 0, 20));
        pendingBar.setMinimumHeight(dp(52));
        pendingBar.setPadding(dp(16), 0, dp(6), 0);
        pendingBar.addView(Ui.icon(this, R.drawable.ic_warning, R.color.warning, 20));
        pendingText = Ui.text(this, "", 14, R.color.text);
        LinearLayout.LayoutParams ptl = Ui.weight1();
        ptl.leftMargin = dp(10);
        pendingBar.addView(pendingText, ptl);
        MaterialButton look = Ui.textButton(this, "查看", v -> showPending(true));
        look.setTextColor(color(R.color.text));
        pendingBar.addView(look);
        pendingBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pdl = Ui.matchWrap();
        pdl.leftMargin = pdl.rightMargin = dp(14);
        pdl.bottomMargin = dp(6);
        root.addView(pendingBar, pdl);

        // 建议（只在空的新对话里）
        suggestScroll = new HorizontalScrollView(this);
        suggestScroll.setHorizontalScrollBarEnabled(false);
        suggestScroll.setClipToPadding(false);
        suggestScroll.setPadding(dp(14), 0, dp(14), 0);
        suggestRow = Ui.hbox(this);
        suggestScroll.addView(suggestRow);
        LinearLayout.LayoutParams sgl = Ui.matchWrap();
        sgl.bottomMargin = dp(8);
        root.addView(suggestScroll, sgl);

        root.addView(buildComposer(), composerParams());
        statusText = Ui.text(this, "", 12, R.color.text3);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(statusText, Ui.matchWrap());

        drawerLayout = new androidx.drawerlayout.widget.DrawerLayout(this);
        drawerLayout.addView(frame, new androidx.drawerlayout.widget.DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        drawer = new SessionDrawer(this, rt, this);
        int dw = Math.min(dp(340), Math.round(getResources().getDisplayMetrics().widthPixels * 0.84f));
        drawerLayout.addView(drawer.root, new androidx.drawerlayout.widget.DrawerLayout.LayoutParams(dw, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        drawerLayout.setScrimColor(color(R.color.scrim));
        drawerBack = new androidx.activity.OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeDrawer();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, drawerBack);
        drawerLayout.addDrawerListener(new androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View v) {
                drawerBack.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(View v) {
                drawerBack.setEnabled(false);
            }

            @Override
            public void onDrawerStateChanged(int state) {
                if (state != androidx.drawerlayout.widget.DrawerLayout.STATE_IDLE && !drawerLayout.isDrawerOpen(Gravity.START)) {
                    hideKeyboard();
                    hideMention();
                    rt.refreshThreads();
                    drawer.bind();
                }
            }
        });
        return drawerLayout;
    }

    /* ---------------- 抽屉（SessionDrawer.Host） ---------------- */

    void openDrawer() {
        hideKeyboard();
        hideMention();
        rt.refreshThreads();
        drawer.bind();
        drawerLayout.openDrawer(Gravity.START, Overlay.animate);
        drawerBack.setEnabled(true);
    }

    @Override
    public void closeDrawer() {
        drawerLayout.closeDrawer(Gravity.START, Overlay.animate);
        drawerBack.setEnabled(false);
    }

    @Override
    public void openThread(CodexRuntimeManager.ThreadSummary t) {
        startActivity(new Intent(this, ChatActivity.class).putExtra(EXTRA_THREAD, t.id).putExtra(EXTRA_CWD, t.cwd));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void newChat() {
        if (fresh() && attachments.isEmpty() && input.getText().length() == 0) return;
        startActivity(new Intent(this, ChatActivity.class).putExtra(EXTRA_CWD, rt.prefs.workspace()));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public String currentThreadId() {
        return threadId;
    }

    private LinearLayout.LayoutParams composerParams() {
        LinearLayout.LayoutParams cl = Ui.matchWrap();
        cl.leftMargin = cl.rightMargin = dp(12);
        cl.topMargin = dp(2);
        cl.bottomMargin = dp(10);
        return cl;
    }

    private View buildTopBar() {
        FrameLayout bar = new FrameLayout(this);
        ImageView menu = Ui.roundButton(this, R.drawable.ic_menu, "会话列表", v -> openDrawer());
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.START | Gravity.CENTER_VERTICAL);
        ml.leftMargin = dp(14);
        bar.addView(menu, ml);
        // 右上角：白色胶囊里两个键（新对话的编辑图标、更多的三个点），和 ChatGPT 一样
        LinearLayout pill = Ui.hbox(this);
        pill.setBackground(Ui.round(this, R.color.card, R.color.border, 26));
        Ui.elevate(pill, 4);
        ImageView newChat = Ui.plainIcon(this, R.drawable.ic_compose, R.color.text, 46, "新对话", v -> newChat());
        newChat.setPadding(dp(12), dp(12), dp(12), dp(12));
        ImageView more = Ui.plainIcon(this, R.drawable.ic_dots, R.color.text, 46, "更多", this::moreMenu);
        more.setPadding(dp(12), dp(12), dp(12), dp(12));
        pill.addView(newChat, new LinearLayout.LayoutParams(dp(46), dp(46)));
        pill.addView(more, new LinearLayout.LayoutParams(dp(46), dp(46)));
        FrameLayout.LayoutParams mr = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46), Gravity.END | Gravity.CENTER_VERTICAL);
        mr.rightMargin = dp(14);
        bar.addView(pill, mr);
        // 新对话：“聊天 / 工作”
        segmented = Ui.hbox(this);
        segmented.setBackground(Ui.round(this, R.color.seg_track, 0, 26));
        segmented.setPadding(dp(4), dp(4), dp(4), dp(4));
        segChat = segItem("聊天", v -> setMode(true));
        segWork = segItem("工作", v -> setMode(false));
        segmented.addView(segChat, new LinearLayout.LayoutParams(dp(98), ViewGroup.LayoutParams.MATCH_PARENT));
        segmented.addView(segWork, new LinearLayout.LayoutParams(dp(98), ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(segmented, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52), Gravity.CENTER));
        // 对话里：标题 + 状态
        titleBox = Ui.vbox(this);
        titleBox.setGravity(Gravity.CENTER);
        title = Overlay.strong(this, "新对话", 16);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        subtitle = Ui.text(this, "", 12, R.color.text3);
        subtitle.setSingleLine();
        subtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subtitle.setGravity(Gravity.CENTER);
        titleBox.addView(title, Ui.matchWrap());
        titleBox.addView(subtitle, Ui.matchWrap());
        titleBox.setOnClickListener(v -> rename());
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tl.leftMargin = tl.rightMargin = dp(72);
        bar.addView(titleBox, tl);
        return bar;
    }

    private TextView segItem(String s, View.OnClickListener l) {
        TextView t = Ui.text(this, s, 16, R.color.text);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        return t;
    }

    private View buildEmpty() {
        emptyBox = Ui.vbox(this);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(dp(28), 0, dp(28), dp(24));
        emptyTitle = Overlay.strong(this, "", 26);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyBox.addView(emptyTitle, Ui.wrap());
        emptyHint = Ui.text(this, "", 14, R.color.text3);
        emptyHint.setGravity(Gravity.CENTER);
        emptyBox.addView(emptyHint, Ui.matchWrap(this, 10));
        wsPill = Ui.text(this, "", 15, R.color.text);
        wsPill.setGravity(Gravity.CENTER_VERTICAL);
        wsPill.setBackground(Ui.round(this, R.color.card, R.color.border, 22));
        wsPill.setPadding(dp(14), dp(9), dp(12), dp(9));
        wsPill.setCompoundDrawablePadding(dp(8));
        android.graphics.drawable.Drawable fd = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_folder_line).mutate();
        fd.setTint(color(R.color.text2));
        fd.setBounds(0, 0, dp(18), dp(18));
        android.graphics.drawable.Drawable cd = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_chev_down).mutate();
        cd.setTint(color(R.color.text2));
        cd.setBounds(0, 0, dp(16), dp(16));
        wsPill.setCompoundDrawablesRelative(fd, null, cd, null);
        Ui.ripple(wsPill, false);
        wsPill.setOnClickListener(v -> chooseWorkspace());
        LinearLayout.LayoutParams wl = Ui.wrap();
        wl.topMargin = dp(18);
        emptyBox.addView(wsPill, wl);
        return emptyBox;
    }

    private View buildComposer() {
        composer = Ui.vbox(this);
        GradientDrawable bg = Ui.round(this, R.color.card, 0, 30);
        bg.setStroke(Math.max(1, dp(1) / 2), color(R.color.border));
        composer.setBackground(bg);
        Ui.elevate(composer, 8);
        attachScroll = new HorizontalScrollView(this);
        attachScroll.setHorizontalScrollBarEnabled(false);
        attachRow = Ui.hbox(this);
        attachRow.setPadding(dp(14), dp(14), dp(14), 0);
        attachScroll.addView(attachRow);
        attachScroll.setVisibility(View.GONE);
        composer.addView(attachScroll, Ui.matchWrap());
        // 第一行：收起时是“＋ 输入框 麦克风 语音”，展开时只剩输入框
        LinearLayout topRow = Ui.hbox(this);
        plusMini = Ui.plainIcon(this, R.drawable.ic_plus, R.color.text, 46, "添加照片、文件和插件", v -> plusMenu());
        plusMini.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams pml = (LinearLayout.LayoutParams) plusMini.getLayoutParams();
        pml.leftMargin = dp(8);
        topRow.addView(plusMini, pml);
        input = new EditText(this);
        input.setTextSize(17);
        input.setMinHeight(dp(52));
        input.setTextColor(color(R.color.text));
        input.setHintTextColor(color(R.color.text3));
        input.setBackground(null);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                bindStatus();
                updateMention();
            }
        });
        input.setOnKeyListener((v, code, ev) -> {
            // 物理键盘 Ctrl+Enter 发送
            if (code == KeyEvent.KEYCODE_ENTER && ev.isCtrlPressed() && ev.getAction() == KeyEvent.ACTION_DOWN) {
                onSend();
                return true;
            }
            return false;
        });
        input.setOnFocusChangeListener((v, has) -> bindComposer());
        topRow.addView(input, Ui.weight1());
        micMini = Ui.plainIcon(this, R.drawable.ic_mic, R.color.text, 46, "语音输入", v -> dictate(false));
        micMini.setPadding(dp(11), dp(11), dp(11), dp(11));
        topRow.addView(micMini);
        voicePill = new FrameLayout(this);
        LinearLayout vp = Ui.hbox(this);
        vp.setGravity(Gravity.CENTER);
        voiceIcon = new ImageView(this);
        vp.addView(voiceIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        voiceText = Ui.text(this, "语音", 16, R.color.text);
        voiceText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams vtl = Ui.wrap();
        vtl.leftMargin = dp(6);
        vp.addView(voiceText, vtl);
        voicePill.addView(vp, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        voicePill.setPadding(dp(14), 0, dp(16), 0);
        Ui.ripple(voicePill, false);
        LinearLayout.LayoutParams vpl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        vpl.leftMargin = dp(4);
        vpl.rightMargin = dp(8);
        topRow.addView(voicePill, vpl);
        LinearLayout.LayoutParams trl = Ui.matchWrap();
        trl.topMargin = trl.bottomMargin = dp(4);
        composer.addView(topRow, trl);
        // 第二行（展开时）：＋ …… 模型标签 麦克风 发送
        bottomRow = Ui.hbox(this);
        bottomRow.setPadding(dp(8), 0, dp(10), dp(10));
        plusBtn = Ui.plainIcon(this, R.drawable.ic_plus, R.color.text, 46, "添加照片、文件和插件", v -> plusMenu());
        plusBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        bottomRow.addView(plusBtn);
        // 占位要给固定高度：WRAP_CONTENT 的空 View 在 AT_MOST 下会撑满全部高度
        bottomRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        modelChip = Overlay.strong(this, "", 15);
        modelChip.setPadding(dp(12), dp(8), dp(10), dp(8));
        modelChip.setGravity(Gravity.CENTER_VERTICAL);
        modelChip.setCompoundDrawablePadding(dp(4));
        Ui.ripple(modelChip, false);
        modelChip.setOnClickListener(v -> openModelSheet());
        bottomRow.addView(modelChip);
        micBtn = Ui.plainIcon(this, R.drawable.ic_mic, R.color.text, 46, "语音输入", v -> dictate(false));
        micBtn.setPadding(dp(11), dp(11), dp(11), dp(11));
        LinearLayout.LayoutParams mcl = (LinearLayout.LayoutParams) micBtn.getLayoutParams();
        mcl.leftMargin = dp(2);
        bottomRow.addView(micBtn, mcl);
        sendBtn = new FrameLayout(this);
        sendIcon = new ImageView(this);
        sendBtn.addView(sendIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        Ui.ripple(sendBtn, true);
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(dp(42), dp(42));
        sbl.leftMargin = dp(6);
        bottomRow.addView(sendBtn, sbl);
        composer.addView(bottomRow, Ui.matchWrap());
        // 键盘收起、输入框是空的：把焦点还给页面，输入框收回一行
        composer.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!input.hasFocus() || input.getText().length() > 0 || !attachments.isEmpty()) return;
            androidx.core.view.WindowInsetsCompat ins = androidx.core.view.ViewCompat.getRootWindowInsets(composer);
            if (ins != null && imeWasVisible && !ins.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) {
                imeWasVisible = false;
                frame.requestFocus();
            } else if (ins != null && ins.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) {
                imeWasVisible = true;
            }
        });
        return composer;
    }

    private boolean imeWasVisible;

    /** 输入框收起（一行，ChatGPT 未聚焦时的样子）还是展开（两行，带模型标签和发送键）。 */
    private boolean expanded() {
        return input.hasFocus() || input.getText().length() > 0 || !attachments.isEmpty();
    }

    private void bindComposer() {
        if (bottomRow == null) return;
        boolean ex = expanded();
        bottomRow.setVisibility(ex ? View.VISIBLE : View.GONE);
        plusMini.setVisibility(ex ? View.GONE : View.VISIBLE);
        micMini.setVisibility(ex ? View.GONE : View.VISIBLE);
        voicePill.setVisibility(ex ? View.GONE : View.VISIBLE);
        input.setMaxLines(ex ? 8 : 1);
        input.setPadding(dp(ex ? 20 : 6), dp(ex ? 14 : 6), dp(ex ? 20 : 4), dp(ex ? 2 : 6));
        // 收起时右边的胶囊：空闲 = 语音（说完直接发送）；运行中 = 停止
        boolean running = conv != null && conv.running();
        boolean ready = rt.stage == CodexRuntimeManager.Stage.READY;
        if (running) {
            // 运行中：强调色的圆形停止键（和 ChatGPT 一样只有一个方块，不写字）
            GradientDrawable d = new GradientDrawable();
            d.setColor(Ui.accent(this));
            d.setCornerRadius(dp(22));
            voicePill.setBackground(d);
            voiceIcon.setImageResource(R.drawable.ic_stop_square);
            voiceIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.onAccent(this)));
            voiceText.setVisibility(View.GONE);
            voicePill.setContentDescription("停止");
            voicePill.setOnClickListener(stopping ? null : v -> stopTurn());
        } else {
            GradientDrawable d = new GradientDrawable();
            d.setColor(ready ? Ui.accent(this) : color(R.color.icon_circle));
            d.setCornerRadius(dp(22));
            voicePill.setBackground(d);
            int fg = ready ? Ui.onAccent(this) : color(R.color.text3);
            voiceIcon.setImageResource(R.drawable.ic_wave);
            voiceIcon.setImageTintList(android.content.res.ColorStateList.valueOf(fg));
            voiceText.setVisibility(View.VISIBLE);
            voiceText.setText("语音");
            voiceText.setTextColor(fg);
            voicePill.setContentDescription("语音输入并发送");
            voicePill.setOnClickListener(ready ? v -> dictate(true) : null);
        }
    }

    /* ---------------- 生命周期 ---------------- */

    @Override
    protected void onStart() {
        super.onStart();
        rt.addListener(this);
        rt.addConversationListener(this);
        if (rt.stage == CodexRuntimeManager.Stage.CHECKING || rt.stage == CodexRuntimeManager.Stage.ERROR && !rt.serverAlive()) rt.check();
        else if (rt.stage == CodexRuntimeManager.Stage.READY) rt.refreshThreads();
        if (needsSetup()) return;
        attach();
        bindAll();
        if (conv != null) { // 回到页面：还挂着的审批/提问重新弹出来
            adapter.notifyDataSetChanged();
            showPending(true);
        }
    }

    @Override
    protected void onStop() {
        rt.removeListener(this);
        rt.removeConversationListener(this);
        sheet.dismiss();
        if (inputDialog != null) inputDialog.dismiss();
        if (threadId != null) getSharedPreferences("drafts", MODE_PRIVATE).edit().putString(threadId, input.getText().toString()).apply();
        super.onStop();
    }

    /** 打开已有会话（resume + 历史）。新对话等第一次发送时再建。 */
    private void attach() {
        if (conv != null || threadId == null || loading) return;
        Conversation c = rt.conversation(threadId);
        if (c != null) {
            setConversation(c);
            return;
        }
        if (!rt.serverAlive()) return; // 等 READY 后 onRuntimeChanged 再来
        loading = true;
        statusText.setText("正在载入历史…");
        rt.openThread(threadId, cwd, (cc, err) -> {
            loading = false;
            if (cc == null) {
                statusText.setText("无法打开这个会话：" + err);
                return;
            }
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            setConversation(cc);
        });
    }

    private void setConversation(Conversation c) {
        conv = c;
        threadId = c.threadId;
        adapter.setConversation(c, cwd);
        scrollToBottom();
        bindAll();
        showPending(true);
    }

    /** 没装好、没登录、没选工作区：去首次设置（App 现在一打开就是新对话，所以这里也要判断）。 */
    private boolean needsSetup() {
        CodexRuntimeManager.Stage s = rt.stage;
        if (s == CodexRuntimeManager.Stage.NOT_INSTALLED || s == CodexRuntimeManager.Stage.NEED_LOGIN || s == CodexRuntimeManager.Stage.INSTALL_FAILED
                || rt.prefs.workspace() == null) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onRuntimeChanged() {
        if (needsSetup()) return;
        if (drawer != null && drawerLayout.isDrawerOpen(Gravity.START)) drawer.bind();
        attach();
        bindStatus();
        if (mentionStart >= 0) renderMention(); // 技能/插件列表可能刚加载好
    }

    @Override
    public void onConversationUpdate(Conversation c, Conversation.Update u) {
        if (conv == null || c != conv) return;
        if (u.approvals) {
            showPending(true);
            adapter.notifyDataSetChanged();
        }
        if (u.structure) structurePending = true;
        else changedRows.addAll(u.changed);
        // 合并刷新：流式 delta 很密，最多每 100ms 刷一次列表
        if (!refreshPending) {
            refreshPending = true;
            h.postDelayed(flush, u.structure || u.turnEnded || u.approvals ? 0 : 100);
        }
        if (u.status || u.turnEnded || u.approvals) bindStatus();
        if (u.turnEnded) stopping = false;
    }

    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            refreshPending = false;
            boolean stick = nearBottom();
            if (structurePending) {
                structurePending = false;
                changedRows.clear();
                adapter.notifyDataSetChanged();
            } else {
                // 只刷这段时间里真的变了的卡片
                int n = adapter.getItemCount();
                for (int i : changedRows) if (i >= 0 && i < n) adapter.notifyItemChanged(i);
                changedRows.clear();
            }
            olderBar.setVisibility(conv != null && conv.olderCursor != null ? View.VISIBLE : View.GONE);
            bindEmpty();
            if (stick) scrollToBottom();
        }
    };

    private boolean nearBottom() {
        if (adapter.getItemCount() == 0) return true;
        return !list.canScrollVertically(1) || list.computeVerticalScrollRange() - list.computeVerticalScrollOffset() - list.computeVerticalScrollExtent() < dp(80);
    }

    private void scrollToBottom() {
        int n = adapter.getItemCount();
        if (n > 0) list.scrollToPosition(n - 1);
        toLatest.setVisibility(View.GONE);
    }

    /* ---------------- 状态显示 ---------------- */

    private boolean fresh() {
        return conv == null && threadId == null;
    }

    private void bindAll() {
        bindEmpty();
        bindStatus();
    }

    private void bindEmpty() {
        boolean empty = adapter.getItemCount() == 0 && !loading;
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        emptyTitle.setText(chatMode ? "有什么可以帮忙的？" : "今天要做点什么？");
        emptyHint.setText(chatMode ? "聊天模式只回答问题，不会运行命令或修改文件" : "Codex 会在这个工作区里读写文件、运行命令");
        wsPill.setText(Workspaces.name(cwd));
        wsPill.setVisibility(!chatMode && fresh() ? View.VISIBLE : View.GONE);
        boolean sug = empty && fresh() && input.getText().length() == 0 && attachments.isEmpty();
        if (sug && suggestRow.getChildCount() == 0 || sug && !String.valueOf(chatMode).equals(suggestRow.getTag())) fillSuggestions();
        suggestScroll.setVisibility(sug ? View.VISIBLE : View.GONE);
    }

    private void fillSuggestions() {
        suggestRow.removeAllViews();
        suggestRow.setTag(String.valueOf(chatMode));
        String[] work = {"看看这个文件夹里有什么", "创建一份待办清单", "用 Node.js 写个小网页", "初始化一个 Git 仓库", "帮我整理这份文档"};
        String[] chat = {"解释一个概念", "帮我写一段文案", "翻译这段话", "给我一些点子"};
        for (String s : chatMode ? chat : work) {
            TextView t = Ui.text(this, s, 15, R.color.text);
            t.setBackground(Ui.round(this, R.color.card, R.color.border, 20));
            t.setPadding(dp(16), dp(10), dp(16), dp(10));
            Ui.ripple(t, false);
            t.setOnClickListener(v -> {
                input.setText(s);
                input.setSelection(s.length());
                input.requestFocus();
            });
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.rightMargin = dp(8);
            suggestRow.addView(t, lp);
        }
    }

    private String threadTitle() {
        if (conv != null && conv.threadName != null) return conv.threadName;
        if (threadId != null) for (CodexRuntimeManager.ThreadSummary t : rt.threads) if (t.id.equals(threadId)) return t.title();
        return "新对话";
    }

    private void bindSegmented() {
        for (TextView t : new TextView[]{segChat, segWork}) {
            boolean on = (t == segChat) == chatMode;
            if (on) {
                t.setBackground(Ui.round(this, R.color.seg_on, 0, 22));
                Ui.elevate(t, 1.5f);
            } else {
                t.setBackground(null);
                t.setElevation(0);
            }
            t.setTextColor(color(on ? R.color.text : R.color.text2));
            t.setTypeface(on ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
            t.setContentDescription(t.getText() + (on ? "，已选中" : ""));
        }
    }

    private void bindStatus() {
        if (title == null) return;
        boolean fresh = fresh();
        segmented.setVisibility(fresh ? View.VISIBLE : View.GONE);
        titleBox.setVisibility(fresh ? View.GONE : View.VISIBLE);
        bindSegmented();
        title.setText(threadTitle());
        boolean running = conv != null && conv.running();
        int approvals = conv == null ? 0 : conv.pendingApprovals().size() + conv.pendingInputs().size();
        String state;
        if (rt.stage == CodexRuntimeManager.Stage.STARTING || rt.stage == CodexRuntimeManager.Stage.CHECKING) state = "正在启动 Codex…";
        else if (rt.stage != CodexRuntimeManager.Stage.READY) state = "Codex 没有运行";
        else if (stopping) state = "正在停止…";
        else if (approvals > 0) state = "等待你的确认";
        else if (running) state = retrying() ? "连接中断，正在重试" : "正在处理";
        else if (conv != null && "interrupted".equals(conv.turnStatus)) state = "已停止";
        else if (conv != null && "failed".equals(conv.turnStatus)) state = "本轮未完成";
        else if (conv != null && "completed".equals(conv.turnStatus)) state = "已完成";
        else state = "准备就绪";
        boolean auto = Prefs.MODE_AUTO.equals(rt.prefs.mode());
        subtitle.setText((chatMode ? "聊天" : Workspaces.name(cwd)) + " · " + state + (!chatMode && auto ? " · 完全自动" : ""));
        // 副标题只在有事要说的时候出现（没运行、没出错、没等确认：和 ChatGPT 一样只剩标题）
        boolean problem = rt.stage != CodexRuntimeManager.Stage.READY || retrying() || approvals > 0 || stopping;
        subtitle.setVisibility(problem || (!chatMode && auto) ? View.VISIBLE : View.GONE);
        // 模型标签
        modelChip.setText(ModelPicker.chipLabel(rt));
        if (ModelPicker.fast(rt)) {
            android.graphics.drawable.Drawable bolt = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_bolt).mutate();
            bolt.setTint(color(R.color.text));
            bolt.setBounds(0, 0, dp(15), dp(15));
            modelChip.setCompoundDrawablesRelative(bolt, null, null, null);
        } else {
            modelChip.setCompoundDrawablesRelative(null, null, null, null);
        }
        modelChip.setContentDescription("模型和推理：" + modelChip.getText() + "，点按更改");
        input.setHint(running ? "追加指令…" : chatMode ? "问问 Codex" : "用 Codex 工作");
        // 计划
        if (conv != null && !conv.plan.isEmpty()) {
            int done = 0;
            planSteps.removeAllViews();
            for (Conversation.PlanStep s : conv.plan) {
                if ("completed".equals(s.status)) done++;
                String mark = "completed".equals(s.status) ? "✓ " : "inProgress".equals(s.status) ? "▶ " : "○ ";
                TextView st = Ui.caption(this, mark + s.step);
                if ("inProgress".equals(s.status)) st.setTextColor(color(R.color.text));
                planSteps.addView(st, Ui.matchWrap(this, 6));
            }
            boolean ended = !running && done < conv.plan.size();
            planText.setText("计划 · 已完成 " + done + "/" + conv.plan.size() + (ended ? " · 本轮已结束，部分步骤未完成" : ""));
            planProgress.setMax(conv.plan.size());
            planProgress.setProgressCompat(done, true);
            planBar.setVisibility(View.VISIBLE);
        } else {
            planBar.setVisibility(View.GONE);
        }
        // 待处理
        pendingBar.setVisibility(approvals > 0 ? View.VISIBLE : View.GONE);
        boolean submitting = conv != null && !conv.pendingApprovals().isEmpty() && conv.pendingApprovals().get(0).submitting;
        boolean asking = conv != null && conv.pendingApprovals().isEmpty() && !conv.pendingInputs().isEmpty();
        pendingText.setText(submitting ? "正在提交决定…" : asking ? "Codex 在问你问题" : "有 " + approvals + " 项操作等待确认");
        if (olderBar != null) olderBar.setVisibility(conv != null && conv.olderCursor != null ? View.VISIBLE : View.GONE);
        // 发送键：空 → 语音（运行中是停止）；有内容 → 发送/追加
        boolean otherRunning = rt.runningThreadId != null && (conv == null || !rt.runningThreadId.equals(conv.threadId));
        boolean hasContent = input.getText().toString().trim().length() > 0 || !attachments.isEmpty();
        boolean ready = rt.stage == CodexRuntimeManager.Stage.READY;
        if (running && !hasContent) {
            setSend(R.drawable.ic_stop_square, R.color.primary, R.color.on_primary, !stopping, "停止", v -> stopTurn());
        } else if (hasContent) {
            boolean can = ready && !sending && !otherRunning;
            setSend(R.drawable.ic_arrow_up, can ? R.color.accent : R.color.icon_circle, can ? R.color.on_accent : R.color.text3, can, running ? "追加" : "发送", v -> onSend());
        } else {
            setSend(R.drawable.ic_wave, ready ? R.color.accent : R.color.icon_circle, ready ? R.color.on_accent : R.color.text3, ready, "语音输入并发送", v -> dictate(true));
        }
        String hint;
        if (!ready) hint = rt.stage == CodexRuntimeManager.Stage.ERROR ? "Codex 没有运行，到会话页重试" : "正在启动 Codex…";
        else if (otherRunning) hint = "另一个会话正在运行，结束后可以发送";
        else if (sending) hint = "正在提交…";
        else if (running) hint = hasContent ? "追加的内容会加入正在进行的这一轮" : "";
        else if (conv != null && "interrupted".equals(conv.turnStatus)) hint = "已发生的文件改动会保留";
        else hint = "";
        bindComposer();
        if (!loading) statusText.setText(hint);
        statusText.setVisibility(statusText.getText().length() == 0 ? View.GONE : View.VISIBLE);
        if (suggestScroll != null && suggestScroll.getVisibility() == View.VISIBLE && (hasContent || !fresh)) suggestScroll.setVisibility(View.GONE);
        else if (suggestScroll != null && !hasContent && fresh && adapter.getItemCount() == 0) bindEmptySuggestOnly();
    }

    private void bindEmptySuggestOnly() {
        boolean sug = !loading && fresh() && input.getText().length() == 0 && attachments.isEmpty();
        if (sug && (suggestRow.getChildCount() == 0 || !String.valueOf(chatMode).equals(suggestRow.getTag()))) fillSuggestions();
        suggestScroll.setVisibility(sug ? View.VISIBLE : View.GONE);
    }

    private void setSend(int icon, int bgRes, int tintRes, boolean enabled, String desc, View.OnClickListener l) {
        sendBtn.setBackground(bgRes == R.color.accent ? Ui.circleColor(Ui.accent(this)) : Ui.circle(this, bgRes));
        sendIcon.setImageResource(icon);
        sendIcon.setImageTintList(android.content.res.ColorStateList.valueOf(tintRes == R.color.on_accent ? Ui.onAccent(this) : color(tintRes)));
        sendBtn.setEnabled(enabled);
        sendBtn.setContentDescription(desc);
        sendBtn.setOnClickListener(enabled ? l : null);
        sendBtn.setClickable(enabled);
    }

    /** 先处理审批（按到达顺序），没有审批再处理 Agent 的提问。 */
    private void showPending(boolean open) {
        if (conv == null) return;
        sheet.update(conv, title.getText().toString(), open);
        if (!conv.pendingApprovals().isEmpty() || sheet.showing()) return;
        List<Conversation.InputRequest> qs = conv.pendingInputs();
        if (qs.isEmpty()) {
            if (inputDialog != null) inputDialog.dismiss();
            return;
        }
        Conversation.InputRequest r = qs.get(0);
        if (inputDialog != null && inputDialog.isFor(r)) return;
        if (inputDialog != null) inputDialog.dismiss();
        if (!open) return;
        inputDialog = new InputDialog(this, rt, conv, r);
        inputDialog.show();
    }

    private void loadOlder() {
        if (conv == null) return;
        olderBar.setEnabled(false);
        olderBar.setText("正在加载…");
        rt.loadOlder(conv, err -> {
            olderBar.setEnabled(true);
            olderBar.setText("加载更早的记录");
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            olderBar.setVisibility(conv.olderCursor != null ? View.VISIBLE : View.GONE);
        });
    }

    private boolean retrying() {
        if (conv == null) return false;
        for (int i = conv.items.size() - 1; i >= 0 && i >= conv.items.size() - 3; i--) {
            if ("retry".equals(conv.items.get(i).level)) return true;
        }
        return false;
    }

    /* ---------------- 聊天 / 工作 ---------------- */

    private void setMode(boolean chat) {
        if (!fresh() || chat == chatMode) return;
        chatMode = chat;
        rt.prefs.setWorkMode(!chat);
        // 文件附件：聊天模式不能跑命令读文件，只能带小文本；切回工作就用路径
        List<Attachment> drop = new ArrayList<>();
        for (Attachment a : attachments) {
            if (a.kind != Attachment.Kind.FILE) continue;
            a.inline = chat ? Attach.readTextIfSmall(new File(a.path)) : null;
            if (chat && a.inline == null) drop.add(a);
        }
        if (!drop.isEmpty()) {
            attachments.removeAll(drop);
            Toast.makeText(this, "聊天模式只能附带 64 KB 以内的文本文件，已移除 " + drop.size() + " 个附件", Toast.LENGTH_LONG).show();
        }
        renderAttachments();
        hideMention();
        bindAll();
    }

    @Override
    public void chooseWorkspace() {
        final List<String> l = Workspaces.list(this);
        String cur = rt.prefs.workspace();
        if (cur != null && !l.contains(cur)) l.add(0, cur);
        List<String> labels = new ArrayList<>();
        for (String p : l) labels.add(Workspaces.name(p) + "\n" + (Workspaces.isShared(rt, p) ? Format.prettyPath(rt, p) : "应用内"));
        new AlertDialog.Builder(this).setTitle("选择工作区").setItems(labels.toArray(new String[0]), (d, w) -> {
            String p = l.get(w);
            if (!new File(p).isDirectory()) {
                Toast.makeText(this, "这个工作区已移动或删除", Toast.LENGTH_LONG).show();
                Workspaces.forget(this, p);
                return;
            }
            rt.prefs.setWorkspace(p);
            Workspaces.remember(this, p);
            if (!fresh()) {
                // 已有的会话属于原来的工作区（Codex 那边的目录不会跟着变）：换工作区就开一个新对话
                startActivity(new Intent(this, ChatActivity.class).putExtra(EXTRA_CWD, p));
                overridePendingTransition(0, 0);
                finish();
                return;
            }
            cwd = p;
            bindAll();
        }).setNegativeButton("取消", null).show();
    }

    /* ---------------- 模型面板 ---------------- */

    private void openModelSheet() {
        hideKeyboard();
        hideMention();
        ModelPicker.show(this, rt, chatMode, this::bindStatus);
    }

    private void hideKeyboard() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    /* ---------------- “＋”菜单 ---------------- */

    private void plusMenu() {
        hideMention();
        Overlay ov = new Overlay(this, false);
        LinearLayout card = Overlay.card(this, 30);
        card.setPadding(0, dp(10), 0, dp(10));
        card.addView(Overlay.circleRow(this, R.drawable.ic_camera, "拍照", v -> {
            ov.close();
            takePhoto();
        }));
        card.addView(Overlay.circleRow(this, R.drawable.ic_image, "照片", v -> {
            ov.close();
            pickPhotos();
        }));
        card.addView(Overlay.circleRow(this, R.drawable.ic_attach, "文件", v -> {
            ov.close();
            pickFile();
        }));
        card.addView(Overlay.circleRow(this, R.drawable.ic_plugin, "插件", v -> {
            ov.close();
            pluginPanel();
        }));
        int[] b = ov.boundsOf(composer);
        int w = Math.min(dp(290), Math.round(ov.layerWidth() * 0.7f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = b[0];
        lp.bottomMargin = Math.max(0, ov.layerHeight() - b[3]);
        card.setPivotX(0);
        card.setPivotY(dp(300));
        ov.add(card, lp);
        ov.show();
    }

    /** “插件”：已安装的插件和 Codex 自带的技能，选了加到输入框。 */
    private void pluginPanel() {
        Overlay ov = new Overlay(this, false);
        LinearLayout card = Overlay.card(this, 30);
        card.setPadding(0, dp(6), 0, dp(10));
        card.addView(Overlay.header(this, "插件与技能"));
        LinearLayout rows = Ui.vbox(this);
        if (rt.catalog.isEmpty()) {
            TextView t = Ui.text(this, rt.serverAlive() ? "还没有可用的插件。Codex 自带的技能（创建图像、代码审查……）会在这里出现。" : "Codex 启动后会列出可用的技能和插件。", 15, R.color.text2);
            t.setPadding(dp(24), dp(8), dp(24), dp(12));
            rows.addView(t);
        }
        boolean first = true;
        for (Catalog c : rt.catalog) {
            rows.addView(catalogRow(c, first, v -> {
                ov.close();
                addAttachment(c.toAttachment());
                input.requestFocus();
            }));
            first = false;
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(rows);
        card.addView(sv);
        int[] b = ov.boundsOf(composer);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(b[2] - b[0], ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = b[0];
        lp.bottomMargin = Math.max(0, ov.layerHeight() - b[1] + dp(8));
        card.measure(View.MeasureSpec.makeMeasureSpec(b[2] - b[0], View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxH = Math.max(dp(160), b[1] - dp(80));
        if (card.getMeasuredHeight() > maxH) lp.height = maxH;
        ov.add(card, lp);
        ov.show();
    }

    private View catalogRow(Catalog c, boolean highlight, View.OnClickListener l) {
        LinearLayout outer = Ui.vbox(this);
        outer.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout row = Ui.hbox(this);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(6), dp(14), dp(6));
        if (highlight) row.setBackground(Ui.round(this, R.color.surface2, 0, 20));
        row.addView(catalogIcon(c, 28));
        TextView t = Ui.text(this, c.label(), 17, R.color.text);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = Ui.weight1();
        tl.leftMargin = dp(16);
        row.addView(t, tl);
        Ui.ripple(row, false);
        row.setOnClickListener(l);
        row.setContentDescription(c.label() + (c.description == null || c.description.isEmpty() ? "" : "：" + c.description));
        outer.addView(row, Ui.matchWrap());
        return outer;
    }

    /** @ 面板里的“网页搜索”：和插件、技能的行一样，点了变成输入框里的蓝色标签。 */
    private View webSearchRow() {
        LinearLayout outer = Ui.vbox(this);
        outer.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout row = Ui.hbox(this);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(6), dp(14), dp(6));
        ImageView ic = Ui.icon(this, R.drawable.ic_web_search, R.color.text2, 28);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.accent(this)));
        row.addView(ic);
        TextView t = Ui.text(this, Attachment.WEB_NAME, 17, R.color.text);
        t.setSingleLine();
        LinearLayout.LayoutParams tl = Ui.weight1();
        tl.leftMargin = dp(16);
        row.addView(t, tl);
        Ui.ripple(row, false);
        row.setOnClickListener(v -> pickMention(new Attachment(Attachment.Kind.WEB_SEARCH, Attachment.WEB_NAME, "web_search", null)));
        row.setContentDescription(Attachment.WEB_NAME);
        outer.addView(row, Ui.matchWrap());
        return outer;
    }

    private ImageView catalogIcon(Catalog c, int sizeDp) {
        String bmp = c.localBitmapIcon();
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            Bitmap bm = Attach.thumb(bmp, dp(sizeDp));
            if (bm != null) {
                iv.setImageBitmap(bm);
                iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
                return iv;
            }
        }
        ImageView iv = Ui.icon(this, c.iconRes(), R.color.text, sizeDp);
        if (c.colored()) iv.setImageTintList(null);
        return iv;
    }

    /* ---------------- “@” ---------------- */

    private void updateMention() {
        if (input == null || mentionLayer == null) return;
        int sel = input.getSelectionStart();
        Editable e = input.getText();
        if (sel < 0 || sel > e.length()) {
            hideMention();
            return;
        }
        int st = sel;
        while (st > 0 && !Character.isWhitespace(e.charAt(st - 1))) st--;
        if (st >= sel || e.charAt(st) != '@') {
            hideMention();
            return;
        }
        mentionStart = st;
        String q = e.subSequence(st + 1, sel).toString();
        if (!q.equals(mentionQuery)) {
            mentionQuery = q;
            mentionFiles = new ArrayList<>();
            h.removeCallbacks(fileSearch);
            if (!chatMode && !q.isEmpty()) h.postDelayed(fileSearch, 150);
        }
        renderMention();
    }

    private final Runnable fileSearch = () -> {
        final String q = mentionQuery;
        if (q == null || q.isEmpty()) return;
        rt.fileSearch(q, cwd, (qq, files) -> {
            if (qq.equals(mentionQuery) && mentionStart >= 0) {
                mentionFiles = files;
                renderMention();
            }
        });
    };

    private void hideMention() {
        mentionStart = -1;
        mentionQuery = null;
        if (mentionLayer != null) mentionLayer.removeAllViews();
    }

    private void renderMention() {
        mentionLayer.removeAllViews();
        if (mentionStart < 0) return;
        String q = mentionQuery == null ? "" : mentionQuery.toLowerCase(Locale.ROOT);
        List<Catalog> cats = new ArrayList<>();
        for (Catalog c : rt.catalog) {
            if (q.isEmpty() || c.label().toLowerCase(Locale.ROOT).contains(q) || c.id.toLowerCase(Locale.ROOT).contains(q)
                    || (c.displayName != null && c.displayName.toLowerCase(Locale.ROOT).contains(q))) cats.add(c);
        }
        boolean webMatch = q.isEmpty() || Attachment.WEB_NAME.contains(q) || "web".contains(q) || "搜索".contains(q);
        if (cats.isEmpty() && mentionFiles.isEmpty() && !q.isEmpty() && !webMatch) return;
        LinearLayout card = Overlay.card(this, 30);
        card.setPadding(0, dp(6), 0, dp(10));
        LinearLayout rows = Ui.vbox(this);
        rows.addView(Overlay.header(this, "插件与技能"));
        if (webMatch) rows.addView(webSearchRow());
        if (cats.isEmpty() && !webMatch) {
            TextView t = Ui.text(this, rt.serverAlive() ? "没有可用的插件或技能" : "Codex 启动后会列出可用的技能和插件", 15, R.color.text2);
            t.setPadding(dp(24), dp(4), dp(24), dp(10));
            rows.addView(t);
        }
        boolean first = true;
        for (Catalog c : cats) {
            rows.addView(catalogRow(c, first, v -> pickMention(c.toAttachment())));
            first = false;
        }
        if (!mentionFiles.isEmpty()) {
            rows.addView(Overlay.header(this, "文件"));
            for (String[] f : mentionFiles) {
                LinearLayout outer = Ui.vbox(this);
                outer.setPadding(dp(10), 0, dp(10), 0);
                LinearLayout row = Ui.hbox(this);
                row.setMinimumHeight(dp(52));
                row.setPadding(dp(14), dp(4), dp(14), dp(4));
                row.addView(Ui.icon(this, "directory".equals(f[2]) ? R.drawable.ic_folder_line : R.drawable.ic_file, R.color.text2, 24));
                LinearLayout tx = Ui.vbox(this);
                TextView n = Ui.text(this, f[1], 16, R.color.text);
                n.setSingleLine();
                TextView p = Ui.text(this, f[0], 12, R.color.text3);
                p.setSingleLine();
                p.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tx.addView(n);
                tx.addView(p);
                LinearLayout.LayoutParams tl = Ui.weight1();
                tl.leftMargin = dp(16);
                row.addView(tx, tl);
                Ui.ripple(row, false);
                final String rel = f[0];
                row.setOnClickListener(v -> pickMention(new Attachment(Attachment.Kind.FILE, rel, null, new File(cwd, rel).getAbsolutePath())));
                outer.addView(row, Ui.matchWrap());
                rows.addView(outer);
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(rows);
        card.addView(sv);
        int[] lc = new int[2], cc = new int[2];
        mentionLayer.getLocationInWindow(lc);
        composer.getLocationInWindow(cc);
        int top = cc[1] - lc[1];
        int left = cc[0] - lc[0];
        int w = composer.getWidth() > 0 ? composer.getWidth() : mentionLayer.getWidth() - dp(24);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = left;
        int layerH = mentionLayer.getHeight() > 0 ? mentionLayer.getHeight() : getResources().getDisplayMetrics().heightPixels;
        lp.bottomMargin = Math.max(0, layerH - top + dp(8));
        card.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, w), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxH = Math.max(dp(150), Math.min(top - dp(76), Math.round(layerH * 0.45f)));
        if (card.getMeasuredHeight() > maxH) lp.height = maxH;
        mentionLayer.addView(card, lp);
    }

    private void pickMention(Attachment a) {
        Editable e = input.getText();
        int end = input.getSelectionStart();
        if (mentionStart >= 0 && end >= mentionStart && end <= e.length()) e.delete(mentionStart, end);
        hideMention();
        addAttachment(a);
    }

    /* ---------------- 附件 ---------------- */

    void addAttachment(Attachment a) {
        for (Attachment x : attachments) {
            if (x.kind == a.kind && TextUtils.equals(x.path, a.path) && TextUtils.equals(x.id, a.id)) return; // 不重复加
        }
        if (a.kind == Attachment.Kind.FILE && chatMode && a.inline == null) {
            a.inline = Attach.readTextIfSmall(new File(a.path));
            if (a.inline == null) {
                Toast.makeText(this, "聊天模式只能附带 64 KB 以内的文本文件。其他文件请切到“工作”，让 Codex 自己读。", Toast.LENGTH_LONG).show();
                return;
            }
        }
        attachments.add(a);
        renderAttachments();
    }

    private void renderAttachments() {
        if (attachRow == null) return;
        attachRow.removeAllViews();
        for (Attachment a : attachments) attachRow.addView(attachChip(a));
        attachScroll.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
        bindAll();
    }

    private View attachChip(Attachment a) {
        View.OnClickListener remove = v -> {
            attachments.remove(a);
            renderAttachments();
        };
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.rightMargin = dp(8);
        if (a.kind == Attachment.Kind.IMAGE) {
            FrameLayout f = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bm = Attach.thumb(a.path, dp(64));
            if (bm != null) iv.setImageBitmap(bm);
            iv.setBackground(Ui.round(this, R.color.surface2, 0, 16));
            iv.setClipToOutline(true);
            iv.setContentDescription("图片 " + a.name);
            f.addView(iv, new FrameLayout.LayoutParams(dp(64), dp(64)));
            ImageView x = new ImageView(this);
            x.setImageResource(R.drawable.ic_x);
            x.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            GradientDrawable xb = new GradientDrawable();
            xb.setShape(GradientDrawable.OVAL);
            xb.setColor(0xCC000000);
            x.setBackground(xb);
            x.setPadding(dp(4), dp(4), dp(4), dp(4));
            x.setContentDescription("移除");
            x.setOnClickListener(remove);
            FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
            xl.topMargin = xl.rightMargin = dp(4);
            f.addView(x, xl);
            f.setLayoutParams(outer);
            return f;
        }
        LinearLayout chip = Ui.hbox(this);
        chip.setBackground(Ui.round(this, R.color.surface2, 0, 20));
        chip.setPadding(dp(12), 0, dp(6), 0);
        chip.setMinimumHeight(dp(42));
        ImageView ic;
        boolean web = a.kind == Attachment.Kind.WEB_SEARCH;
        if (a.kind == Attachment.Kind.FILE) {
            ic = Ui.icon(this, R.drawable.ic_file, R.color.text2, 18);
        } else if (web) {
            ic = Ui.icon(this, R.drawable.ic_web_search, R.color.text2, 20);
            ic.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.accent(this)));
        } else {
            Catalog found = null;
            for (Catalog c : rt.catalog) if (c.id.equals(a.id)) found = c;
            ic = found != null ? catalogIcon(found, 20) : Ui.icon(this, R.drawable.ic_plugin, R.color.text2, 18);
        }
        chip.addView(ic);
        TextView t = Ui.text(this, a.name, 14, R.color.text);
        if (web) t.setTextColor(Ui.accent(this));
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.setMaxWidth(dp(170));
        LinearLayout.LayoutParams tl = Ui.wrap();
        tl.leftMargin = dp(8);
        chip.addView(t, tl);
        ImageView x = Ui.plainIcon(this, R.drawable.ic_x, R.color.text2, 30, "移除 " + a.name, remove);
        x.setPadding(dp(8), dp(8), dp(8), dp(8));
        chip.addView(x);
        chip.setLayoutParams(outer);
        return chip;
    }

    private void takePhoto() {
        try {
            File f = new File(getCacheDir(), "camera/IMG_" + System.currentTimeMillis() + ".jpg");
            f.getParentFile().mkdirs();
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("photo", u));
            pendingCamera = f;
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            pendingCamera = null;
            Toast.makeText(this, "没有找到相机应用", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            pendingCamera = null;
            Toast.makeText(this, "无法打开相机：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickPhotos() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "选择照片"), REQ_PHOTOS);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有找到相册应用", Toast.LENGTH_LONG).show();
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_FILE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有找到文件管理器", Toast.LENGTH_LONG).show();
        }
    }

    private void dictate(boolean sendAfter) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, sendAfter ? "说完会直接发送" : "请说话");
        try {
            startActivityForResult(i, sendAfter ? REQ_VOICE_SEND : REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this).setTitle("没有语音识别服务")
                    .setMessage("这台手机上没有可用的语音识别应用。可以点输入法键盘上的麦克风键说话，文字会直接进输入框。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CAMERA) {
            File f = pendingCamera;
            pendingCamera = null;
            if (res == RESULT_OK && f != null && f.length() > 0) importImages(java.util.Collections.singletonList(Uri.fromFile(f)), f);
            else if (f != null) f.delete();
            return;
        }
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_PHOTOS) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            importImages(uris, null);
        } else if (req == REQ_FILE && data.getData() != null) {
            importFile(data.getData(), data.getType() != null ? data.getType() : getContentResolver().getType(data.getData()));
        } else if (req == REQ_VOICE || req == REQ_VOICE_SEND) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r == null || r.isEmpty() || r.get(0).trim().isEmpty()) return;
            String said = r.get(0).trim();
            int at = Math.max(0, input.getSelectionStart());
            input.getText().insert(at, said);
            if (req == REQ_VOICE_SEND) onSend();
        }
    }

    private void importImages(List<Uri> uris, File deleteAfter) {
        if (uris.isEmpty()) return;
        Toast.makeText(this, "正在添加图片…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            List<Attachment> got = new ArrayList<>();
            String err = null;
            for (Uri u : uris) {
                try {
                    File f = Attach.importImage(this, rt, u);
                    if (f != null) got.add(new Attachment(Attachment.Kind.IMAGE, f.getName(), null, f.getAbsolutePath()));
                    else err = "有一张图片无法读取";
                } catch (Exception e) {
                    err = "图片添加失败：" + e.getMessage();
                }
            }
            if (deleteAfter != null) deleteAfter.delete();
            final String ferr = err;
            h.post(() -> {
                if (isFinishing()) return;
                for (Attachment a : got) addAttachment(a);
                if (ferr != null) Toast.makeText(this, ferr, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void importFile(Uri uri, String mime) {
        if (mime != null && mime.startsWith("image/") && !mime.contains("svg")) {
            importImages(java.util.Collections.singletonList(uri), null);
            return;
        }
        io.execute(() -> {
            try {
                Object[] r = Attach.importFile(this, rt, uri);
                File f = (File) r[0];
                String name = (String) r[1];
                h.post(() -> {
                    if (isFinishing()) return;
                    addAttachment(new Attachment(Attachment.Kind.FILE, name, null, f.getAbsolutePath()));
                });
            } catch (Exception e) {
                h.post(() -> Toast.makeText(this, "文件添加失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /* ---------------- 操作 ---------------- */

    private void onSend() {
        final String text = input.getText().toString().trim();
        if ((text.isEmpty() && attachments.isEmpty()) || sending) return;
        if (!chatMode && !rt.prefs.sandboxNoticeSeen()) {
            new AlertDialog.Builder(this).setTitle("在本机运行")
                    .setMessage("文件和命令在本机运行，受 Android 应用沙箱限制。Agent 可能访问本应用内的其他文件；项目目录不是独立隔离区。模型请求需要联网。")
                    .setPositiveButton("知道了", (d, w) -> {
                        rt.prefs.setSandboxNoticeSeen();
                        onSend();
                    }).setNegativeButton("取消", null).show();
            return;
        }
        hideMention();
        if (conv != null && conv.running()) { // 追加
            if (text.isEmpty()) {
                Toast.makeText(this, "运行中只能追加文字，附件请在这一轮结束后发送", Toast.LENGTH_LONG).show();
                return;
            }
            sending = true;
            bindStatus();
            rt.steer(conv, text, err -> {
                sending = false;
                if (err == null) input.setText("");
                else Toast.makeText(this, err + "（内容已保留）", Toast.LENGTH_LONG).show();
                bindStatus();
            });
            return;
        }
        sending = true;
        bindStatus();
        if (conv == null) {
            final File ws = chatMode ? rt.chatDir() : new File(cwd);
            rt.newThread(ws, (c, err) -> {
                if (c == null) {
                    sending = false;
                    Toast.makeText(this, "创建对话失败：" + err, Toast.LENGTH_LONG).show();
                    bindStatus();
                    return;
                }
                cwd = ws.getAbsolutePath();
                setConversation(c);
                doSend(text);
            });
        } else {
            doSend(text);
        }
    }

    private void doSend(String text) {
        final List<Attachment> atts = new ArrayList<>(attachments);
        attachments.clear();
        renderAttachments();
        input.setText("");
        rt.sendTurn(conv, text, atts, err -> {
            sending = false;
            if (err != null) {
                input.setText(text);
                attachments.addAll(atts);
                renderAttachments();
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            }
            bindStatus();
        });
        emptyBox.setVisibility(View.GONE);
        suggestScroll.setVisibility(View.GONE);
        scrollToBottom();
    }

    private void stopTurn() {
        if (conv == null || !conv.running()) return;
        stopping = true;
        rt.interrupt(conv);
        bindStatus();
    }

    private void rename() {
        if (threadId == null) return;
        final EditText et = new EditText(this);
        et.setText(threadTitle());
        et.setSingleLine();
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        new AlertDialog.Builder(this).setTitle("重命名").setView(box)
                .setPositiveButton("保存", (d2, w2) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    rt.renameThread(threadId, n);
                    if (conv != null) conv.threadName = n;
                    bindStatus();
                }).setNegativeButton("取消", null).show();
    }

    /** “⋯”：右上角弹出的菜单卡片。 */
    private void moreMenu(View anchor) {
        hideMention();
        Overlay ov = new Overlay(this, false);
        LinearLayout card = Overlay.card(this, 24);
        card.setPadding(0, dp(8), 0, dp(8));
        card.addView(Overlay.iconRow(this, R.drawable.ic_compose, "新对话", R.color.text, v -> {
            ov.close();
            newChat();
        }));
        if (threadId != null) card.addView(Overlay.iconRow(this, R.drawable.ic_pen, "重命名", R.color.text, v -> {
            ov.close();
            rename();
        }));
        if (conv != null && conv.turnDiff != null && !conv.turnDiff.isEmpty()) card.addView(Overlay.iconRow(this, R.drawable.ic_diff, "本轮改动", R.color.text, v -> {
            ov.close();
            startActivity(new Intent(this, FileViewerActivity.class).putExtra(FileViewerActivity.EXTRA_TEXT, conv.turnDiff)
                    .putExtra(FileViewerActivity.EXTRA_DIFF, true).putExtra(FileViewerActivity.EXTRA_TITLE, "本轮改动"));
        }));
        card.addView(Overlay.iconRow(this, R.drawable.ic_gauge, "用量", R.color.text, v -> {
            ov.close();
            UsageDialog.show(this, rt, conv);
        }));
        if (!chatMode) card.addView(Overlay.iconRow(this, R.drawable.ic_folder_line, "工作区文件", R.color.text, v -> {
            ov.close();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_FILES));
        }));
        card.addView(Overlay.iconRow(this, R.drawable.ic_terminal_line, "终端", R.color.text, v -> {
            ov.close();
            startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_CWD, chatMode ? rt.prefs.workspace() : cwd));
        }));
        if (conv != null && conv.running()) card.addView(Overlay.iconRow(this, R.drawable.ic_stop_line, "停止本轮", R.color.error, v -> {
            ov.close();
            stopTurn();
        }));
        int[] b = ov.boundsOf(anchor);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = b[3] + dp(6);
        lp.rightMargin = Math.max(dp(8), ov.layerWidth() - b[2]);
        card.setMinimumWidth(dp(200));
        card.setPivotX(dp(200));
        card.setPivotY(0);
        ov.add(card, lp);
        ov.show();
    }
}
