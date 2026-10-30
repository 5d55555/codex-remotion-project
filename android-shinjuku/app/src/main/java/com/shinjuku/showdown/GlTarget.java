package com.shinjuku.showdown;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * GLES2 实现的像素画面（游戏里用）：像素层画进一个低分辨率帧缓冲（FBO），present() 时用 GL_NEAREST 放大到屏幕原生尺寸，
 * 再画屏幕分辨率的文字。所有图（图集、背景）一直在显存里，每帧不上传纹理。
 * 贴图和几何（圆、环、扇形、矩形、线）共用一个着色器，攒成一批一起画，状态（纹理、混合、目标）变了才提交。
 * 颜色按 4 个字节传（ARGB → RGBA 字节，透明度最低位清掉以免拼成 NaN）。
 * 只在战斗线程上用。EGL 上下文跨暂停保留，Surface 没了只销毁窗口表面；上下文丢了就整个重建。
 */
final class GlTarget implements PixelTarget {
    private static final String TAG = "GlTarget";
    private static final int MAX_QUADS = 4096;
    private static final int FLOATS = 10; // x y u v mul add rOut rIn a0 aLen
    private static final int STRIDE = FLOATS * 4;

    private static final String VS =
            "attribute vec2 a_pos; attribute vec2 a_uv; attribute vec4 a_mul; attribute vec4 a_add; attribute vec4 a_shape;\n"
                    + "uniform vec2 u_size;\n"
                    + "varying vec2 v_uv; varying vec4 v_mul; varying vec4 v_add; varying vec4 v_shape;\n"
                    + "void main() {\n"
                    + "  gl_Position = vec4(a_pos.x / u_size.x * 2.0 - 1.0, 1.0 - a_pos.y / u_size.y * 2.0, 0.0, 1.0);\n"
                    + "  v_uv = a_uv; v_mul = a_mul; v_add = a_add; v_shape = a_shape;\n"
                    + "}\n";
    private static final String FS =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + "uniform sampler2D u_tex;\n"
                    + "varying vec2 v_uv; varying vec4 v_mul; varying vec4 v_add; varying vec4 v_shape;\n"
                    + "void main() {\n"
                    + "  if (v_add.a > 0.5) {\n"                       // 几何：v_uv 是相对圆心的坐标
                    + "    if (v_shape.x > 0.0) {\n"
                    + "      float d = length(v_uv);\n"
                    + "      if (d > v_shape.x || d < v_shape.y) discard;\n"
                    + "      if (v_shape.w < 6.283) {\n"
                    + "        float a = mod(atan(v_uv.y, v_uv.x) - v_shape.z, 6.2831853);\n"
                    + "        if (a > v_shape.w) discard;\n"
                    + "      }\n"
                    + "    }\n"
                    + "    gl_FragColor = vec4(v_mul.rgb * v_mul.a, v_mul.a);\n"
                    + "  } else {\n"                                    // 贴图：纹理是预乘透明度的
                    + "    vec4 t = texture2D(u_tex, v_uv);\n"
                    + "    if (t.a < 0.004) discard;\n"
                    + "    gl_FragColor = vec4((t.rgb * v_mul.rgb + v_add.rgb * t.a) * v_mul.a, t.a * v_mul.a);\n"
                    + "  }\n"
                    + "}\n";

    // EGL
    static final int SWAP_OK = 0, SWAP_BAD_SURFACE = 1, SWAP_CONTEXT_LOST = 2;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private boolean initialized; // 我们对这个 display 调过 eglInitialize（退出时配对 eglTerminate）
    private EGLConfig config;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;

    // GL 资源（上下文重建后要重新创建）
    private boolean glReady;
    private int program, vbo, ibo, locSize, locTex;
    private int aPos, aUv, aMul, aAdd, aShape;
    private final int[] tex = new int[4];        // 图集、新宿背景、无量空处背景、文字
    private final int[] texW = new int[4], texH = new int[4];
    private static final int TEX_HUD = 3;
    private int fbo, fboTex, fboW, fboH;
    private final Bitmap atlasBitmap, bgBitmap, voidBitmap;
    private Bitmap hudBitmap;
    private boolean hudDirty;

    // 批
    private final float[] verts = new float[MAX_QUADS * 4 * FLOATS];
    private final FloatBuffer vbuf;
    private int quads, vboQuads; // 这一批攒了几个；这一帧已经写进顶点缓冲几个
    private int curTex = -1;
    private boolean curAdd, wantAdd;

    // 帧
    private int vw, vh, sw, sh;
    private float scale;

    private final Gl gl;

    GlTarget(Gl gl, Bitmap atlas, Bitmap bg, Bitmap voidBg) {
        this.gl = gl;
        atlasBitmap = atlas;
        bgBitmap = bg;
        voidBitmap = voidBg;
        vbuf = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    void setHud(Bitmap b) {
        hudBitmap = b;
        hudDirty = true;
    }

    /* ---------------- EGL ---------------- */

    /** 在这个 Surface 上准备好画（第一次调用时建上下文和 GL 资源）。失败抛 RuntimeException。 */
    void attach(Surface s) {
        if (display == EGL14.EGL_NO_DISPLAY) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) throw new RuntimeException("eglInitialize " + err());
            initialized = true;
        }
        if (context == EGL14.EGL_NO_CONTEXT) {
            config = chooseConfig(0);
            if (config == null) config = chooseConfig(8);
            if (config == null) throw new RuntimeException("no EGL config");
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (context == EGL14.EGL_NO_CONTEXT) throw new RuntimeException("eglCreateContext " + err());
            glReady = false;
        }
        detachSurface();
        surface = EGL14.eglCreateWindowSurface(display, config, s, new int[]{EGL14.EGL_NONE}, 0);
        if (surface == EGL14.EGL_NO_SURFACE) throw new RuntimeException("eglCreateWindowSurface " + err());
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) throw new RuntimeException("eglMakeCurrent " + err());
        EGL14.eglSwapInterval(display, 1);
        if (!glReady) createGl();
    }

    private EGLConfig chooseConfig(int alpha) {
        int[] attrs = {
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, alpha,
                EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_NONE};
        EGLConfig[] out = new EGLConfig[1];
        int[] n = new int[1];
        if (!EGL14.eglChooseConfig(display, attrs, 0, out, 0, 1, n, 0) || n[0] == 0) return null;
        return out[0];
    }

    /** Surface 要没了：不再往上画。上下文从当前线程解绑但保留（里面的纹理还在，回来时不用重新上传）。 */
    void detachSurface() {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, surface);
            surface = EGL14.EGL_NO_SURFACE;
        }
    }

    boolean hasSurface() {
        return surface != EGL14.EGL_NO_SURFACE;
    }

    /**
     * 交换缓冲。SWAP_BAD_SURFACE：窗口表面坏了（已经放开，下次 attach 重新建表面）；
     * SWAP_CONTEXT_LOST：上下文丢了（已经清理，下次 attach 连上下文和纹理一起重建）。
     */
    int swap() {
        if (EGL14.eglSwapBuffers(display, surface)) return SWAP_OK;
        int e = EGL14.eglGetError();
        Log.w(TAG, "eglSwapBuffers " + Integer.toHexString(e));
        detachSurface();
        if (e == 0x300E /* EGL_CONTEXT_LOST */) {
            EGL14.eglDestroyContext(display, context);
            context = EGL14.EGL_NO_CONTEXT;
            glReady = false;
            return SWAP_CONTEXT_LOST;
        }
        return SWAP_BAD_SURFACE;
    }

    /** 退出（或改用 Canvas）：释放 GL 资源、上下文，配对 eglTerminate（Android 对 display 的初始化按引用计数，不影响系统界面的渲染）。 */
    void release() {
        if (display == EGL14.EGL_NO_DISPLAY) return;
        if (context != EGL14.EGL_NO_CONTEXT && glReady && surface != EGL14.EGL_NO_SURFACE) {
            try {
                deleteGl();
            } catch (RuntimeException ignored) {
                // 上下文已经不可用：销毁上下文时一起释放
            }
        }
        detachSurface();
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
        context = EGL14.EGL_NO_CONTEXT;
        glReady = false;
        if (initialized) EGL14.eglTerminate(display);
        initialized = false;
        EGL14.eglReleaseThread();
        display = EGL14.EGL_NO_DISPLAY;
    }

    private static String err() {
        return Integer.toHexString(EGL14.eglGetError());
    }

    /** 测试用：当前线程上已经有可用的 GL 上下文（不经过 EGL 窗口表面），直接建资源。 */
    void initForTest() {
        if (!glReady) createGl();
    }

    /** 测试用：读回像素层（FBO）的内容，按行从上到下，ARGB。 */
    void readScene(int[] argb) {
        ByteBuffer buf = ByteBuffer.allocateDirect(vw * vh * 4).order(ByteOrder.nativeOrder());
        gl.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        gl.glReadPixels(0, 0, vw, vh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        for (int y = 0; y < vh; y++) {
            for (int x = 0; x < vw; x++) {
                int o = ((vh - 1 - y) * vw + x) * 4; // GL 的第 0 行在底部
                int r = buf.get(o) & 0xFF, g = buf.get(o + 1) & 0xFF, b = buf.get(o + 2) & 0xFF, a = buf.get(o + 3) & 0xFF;
                argb[y * vw + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        gl.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    /* ---------------- GL 资源 ---------------- */

    private void createGl() {
        program = link(VS, FS);
        aPos = gl.glGetAttribLocation(program, "a_pos");
        aUv = gl.glGetAttribLocation(program, "a_uv");
        aMul = gl.glGetAttribLocation(program, "a_mul");
        aAdd = gl.glGetAttribLocation(program, "a_add");
        aShape = gl.glGetAttribLocation(program, "a_shape");
        locSize = gl.glGetUniformLocation(program, "u_size");
        locTex = gl.glGetUniformLocation(program, "u_tex");
        int[] ids = new int[2];
        gl.glGenBuffers(2, ids, 0);
        vbo = ids[0];
        ibo = ids[1];
        ShortBuffer idx = ByteBuffer.allocateDirect(MAX_QUADS * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int q = 0; q < MAX_QUADS; q++) {
            short b = (short) (q * 4);
            idx.put(b).put((short) (b + 1)).put((short) (b + 2)).put(b).put((short) (b + 2)).put((short) (b + 3));
        }
        idx.position(0);
        gl.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, MAX_QUADS * 6 * 2, idx, GLES20.GL_STATIC_DRAW);
        gl.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.length * 4, null, GLES20.GL_STREAM_DRAW);
        gl.glGenTextures(4, tex, 0);
        upload(TEX_ATLAS, atlasBitmap, GLES20.GL_NEAREST);
        upload(TEX_BG, bgBitmap, GLES20.GL_NEAREST);
        upload(TEX_VOID, voidBitmap, GLES20.GL_NEAREST);
        hudDirty = true;
        fbo = 0;
        fboTex = 0;
        fboW = fboH = 0;
        gl.glDisable(GLES20.GL_DEPTH_TEST);
        gl.glDisable(GLES20.GL_CULL_FACE);
        gl.glDisable(GLES20.GL_DITHER);
        gl.glEnable(GLES20.GL_BLEND);
        glReady = true;
    }

    private void deleteGl() {
        gl.glDeleteProgram(program);
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        gl.glDeleteTextures(4, tex, 0);
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(1, new int[]{fboTex}, 0);
        }
        glReady = false;
    }

    private void upload(int slot, Bitmap b, int filter) {
        gl.glBindTexture(GLES20.GL_TEXTURE_2D, tex[slot]);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        gl.texImage2D(b);
        texW[slot] = b.getWidth();
        texH[slot] = b.getHeight();
        curTex = -1;
    }

    private void ensureFbo(int w, int h) {
        if (fbo != 0 && fboW == w && fboH == h) return;
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(1, new int[]{fboTex}, 0);
        }
        int[] id = new int[1];
        gl.glGenTextures(1, id, 0);
        fboTex = id[0];
        gl.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        gl.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        gl.glGenFramebuffers(1, id, 0);
        fbo = id[0];
        gl.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0);
        int st = gl.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (st != GLES20.GL_FRAMEBUFFER_COMPLETE) throw new RuntimeException("fbo " + Integer.toHexString(st));
        fboW = w;
        fboH = h;
        curTex = -1;
    }

    private int link(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs), f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("link: " + gl.glGetProgramInfoLog(p));
        gl.glDeleteShader(v);
        gl.glDeleteShader(f);
        return p;
    }

    private int compile(int type, String src) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, src);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("compile: " + gl.glGetShaderInfoLog(s));
        return s;
    }

    /* ---------------- 一帧 ---------------- */

    @Override
    public void begin(int vw, int vh, int sw, int sh, float scale) {
        this.vw = vw;
        this.vh = vh;
        this.sw = sw;
        this.sh = sh;
        this.scale = scale;
        if (hudDirty && hudBitmap != null) {
            upload(TEX_HUD, hudBitmap, GLES20.GL_LINEAR);
            hudDirty = false;
        }
        ensureFbo(vw, vh);
        gl.glUseProgram(program);
        gl.glUniform1i(locTex, 0);
        gl.glActiveTexture(GLES20.GL_TEXTURE0);
        gl.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        gl.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glEnableVertexAttribArray(aPos);
        gl.glEnableVertexAttribArray(aUv);
        gl.glEnableVertexAttribArray(aMul);
        gl.glEnableVertexAttribArray(aAdd);
        gl.glEnableVertexAttribArray(aShape);
        orphan();
        gl.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, vw, vh);
        gl.glUniform2f(locSize, vw, vh);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        curAdd = wantAdd = false;
        gl.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        quads = 0;
    }

    @Override
    public void sprite(int t, int sx, int sy, int w, int h, float x, float y, float pivotX, float pivotY,
                       float deg, float scaleX, float scaleY, int mul, int add) {
        useTexture(tex[t]);
        float iw = 1f / texW[t], ih = 1f / texH[t];
        float u0 = sx * iw, v0 = sy * ih, u1 = (sx + w) * iw, v1 = (sy + h) * ih;
        float x0 = -pivotX * scaleX, y0 = -pivotY * scaleY, x1 = (w - pivotX) * scaleX, y1 = (h - pivotY) * scaleY;
        float c = 1, s = 0;
        if (deg != 0) {
            double r = Math.toRadians(deg);
            c = (float) Math.cos(r);
            s = (float) Math.sin(r);
        }
        float m = packColor(mul), a = packColor(add & 0xFFFFFF);
        quad(x + x0 * c - y0 * s, y + x0 * s + y0 * c, u0, v0,
                x + x1 * c - y0 * s, y + x1 * s + y0 * c, u1, v0,
                x + x1 * c - y1 * s, y + x1 * s + y1 * c, u1, v1,
                x + x0 * c - y1 * s, y + x0 * s + y1 * c, u0, v1, m, a, 0, 0, 0, 0);
    }

    @Override
    public void rect(float x, float y, float w, float h, int argb) {
        float m = packColor(argb), a = packColor(0xFF000000);
        quad(x, y, 0, 0, x + w, y, 0, 0, x + w, y + h, 0, 0, x, y + h, 0, 0, m, a, 0, 0, 0, 0);
    }

    @Override
    public void shape(float cx, float cy, float rOut, float rIn, float a0, float aLen, int argb) {
        if (rOut <= 0) return;
        float r = rOut + 1;
        // 片段着色器里按“外半径 = 1”比较，角度先在这里归约到 [0, 2π)：手机只有 mediump 时也不失真
        float k = r / rOut;
        double twoPi = Math.PI * 2;
        float a = (float) (a0 - Math.floor(a0 / twoPi) * twoPi);
        float m = packColor(argb), mode = packColor(0xFF000000);
        quad(cx - r, cy - r, -k, -k, cx + r, cy - r, k, -k, cx + r, cy + r, k, k, cx - r, cy + r, -k, k,
                m, mode, 1, rIn / rOut, a, aLen);
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            rect(x0 - width * 0.5f, y0 - width * 0.5f, width, width, argb);
            return;
        }
        float hw = Math.max(1, width) * 0.5f;
        float nx = -dy / len * hw, ny = dx / len * hw;
        float ex = dx / len * 0.5f, ey = dy / len * 0.5f; // 两头各延长半个像素，像 Canvas 的方头
        float m = packColor(argb), a = packColor(0xFF000000);
        quad(x0 - ex + nx, y0 - ey + ny, 0, 0, x1 + ex + nx, y1 + ey + ny, 0, 0, x1 + ex - nx, y1 + ey - ny, 0, 0, x0 - ex - nx, y0 - ey - ny, 0, 0,
                m, a, 0, 0, 0, 0);
    }

    @Override
    public void additive(boolean on) {
        wantAdd = on;
    }

    @Override
    public void present() {
        present(0, 0, vw, vh);
    }

    @Override
    public void present(int cx, int cy, int cw, int ch) {
        flush();
        gl.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        gl.glViewport(0, 0, sw, sh);
        gl.glUniform2f(locSize, sw, sh);
        wantAdd = false;
        // FBO 的第 0 行在底部：v 反过来取
        useTexture(fboTex);
        float m = packColor(0xFFFFFFFF), a = packColor(0);
        float w = vw * scale, h = vh * scale;
        float u0 = cx / (float) vw, u1 = (cx + cw) / (float) vw, vt = 1 - cy / (float) vh, vb = 1 - (cy + ch) / (float) vh;
        quad(0, 0, u0, vt, w, 0, u1, vt, w, h, u1, vb, 0, h, u0, vb, m, a, 0, 0, 0, 0);
    }

    @Override
    public void hud(int sx, int sy, int w, int h, float x, float y, int mul) {
        if (texW[TEX_HUD] == 0) return;
        useTexture(tex[TEX_HUD]);
        float iw = 1f / texW[TEX_HUD], ih = 1f / texH[TEX_HUD];
        float u0 = sx * iw, v0 = sy * ih, u1 = (sx + w) * iw, v1 = (sy + h) * ih;
        float m = packColor(mul), a = packColor(0);
        quad(x, y, u0, v0, x + w, y, u1, v0, x + w, y + h, u1, v1, x, y + h, u0, v1, m, a, 0, 0, 0, 0);
    }

    @Override
    public void hudRect(float x, float y, float w, float h, int argb) {
        rect(x, y, w, h, argb); // present() 之后坐标就是屏幕像素
    }

    @Override
    public void end() {
        flush();
    }

    /* ---------------- 批 ---------------- */

    private void useTexture(int id) {
        if (id == curTex) return;
        flush();
        gl.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        curTex = id;
    }

    private void quad(float x0, float y0, float u0, float v0, float x1, float y1, float u1, float v1,
                      float x2, float y2, float u2, float v2, float x3, float y3, float u3, float v3,
                      float mul, float add, float rOut, float rIn, float a0, float aLen) {
        if (wantAdd != curAdd) {
            flush();
            gl.glBlendFunc(GLES20.GL_ONE, wantAdd ? GLES20.GL_ONE : GLES20.GL_ONE_MINUS_SRC_ALPHA);
            curAdd = wantAdd;
        }
        if (quads == MAX_QUADS) flush();
        int o = quads * 4 * FLOATS;
        float[] v = verts;
        o = vtx(v, o, x0, y0, u0, v0, mul, add, rOut, rIn, a0, aLen);
        o = vtx(v, o, x1, y1, u1, v1, mul, add, rOut, rIn, a0, aLen);
        o = vtx(v, o, x2, y2, u2, v2, mul, add, rOut, rIn, a0, aLen);
        vtx(v, o, x3, y3, u3, v3, mul, add, rOut, rIn, a0, aLen);
        quads++;
    }

    private static int vtx(float[] v, int o, float x, float y, float u, float vv, float mul, float add, float rOut, float rIn, float a0, float aLen) {
        v[o] = x;
        v[o + 1] = y;
        v[o + 2] = u;
        v[o + 3] = vv;
        v[o + 4] = mul;
        v[o + 5] = add;
        v[o + 6] = rOut;
        v[o + 7] = rIn;
        v[o + 8] = a0;
        v[o + 9] = aLen;
        return o + FLOATS;
    }

    /** 顶点缓冲：每帧开头（或写满时）丢掉旧的重新分配一次，不等 GPU 用完；这一帧里各批按偏移往后接着写。 */
    private void orphan() {
        gl.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.length * 4, null, GLES20.GL_STREAM_DRAW);
        vboQuads = 0;
    }

    private void flush() {
        if (quads == 0) return;
        if (vboQuads + quads > MAX_QUADS) orphan();
        int n = quads * 4 * FLOATS;
        vbuf.position(0);
        vbuf.put(verts, 0, n);
        vbuf.position(0);
        int base = vboQuads * 4 * STRIDE;
        gl.glBufferSubData(GLES20.GL_ARRAY_BUFFER, base, n * 4, vbuf);
        gl.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, STRIDE, base);
        gl.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, STRIDE, base + 8);
        gl.glVertexAttribPointer(aMul, 4, GLES20.GL_UNSIGNED_BYTE, true, STRIDE, base + 16);
        gl.glVertexAttribPointer(aAdd, 4, GLES20.GL_UNSIGNED_BYTE, true, STRIDE, base + 20);
        gl.glVertexAttribPointer(aShape, 4, GLES20.GL_FLOAT, false, STRIDE, base + 24);
        gl.glDrawElements(GLES20.GL_TRIANGLES, quads * 6, GLES20.GL_UNSIGNED_SHORT, 0);
        vboQuads += quads;
        quads = 0;
    }

    /** ARGB → 内存里 R G B A 四个字节（小端 int 0xAABBGGRR），透明度最低位清掉，免得拼成 NaN 被规范化。 */
    private static float packColor(int argb) {
        int a = (argb >>> 24) & 0xFE, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return Float.intBitsToFloat((a << 24) | (b << 16) | (g << 8) | r);
    }
}
