package com.shinjuku.showdown;

import android.graphics.Bitmap;

import org.lwjgl.opengles.GLES20;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** 测试用的 Gl：转给 LWJGL（底下是 Mesa 的软件 GLES），让 GlTarget 能在没有手机的地方跑。 */
final class LwjglGl implements Gl {
    @Override public void glActiveTexture(int t) { GLES20.glActiveTexture(t); }
    @Override public void glAttachShader(int p, int s) { GLES20.glAttachShader(p, s); }
    @Override public void glBindBuffer(int t, int b) { GLES20.glBindBuffer(t, b); }
    @Override public void glBindFramebuffer(int t, int f) { GLES20.glBindFramebuffer(t, f); }
    @Override public void glBindTexture(int t, int x) { GLES20.glBindTexture(t, x); }
    @Override public void glBlendFunc(int s, int d) { GLES20.glBlendFunc(s, d); }

    @Override
    public void glBufferData(int target, int size, Buffer data, int usage) {
        if (data == null) GLES20.glBufferData(target, (long) size, usage);
        else if (data instanceof ShortBuffer) GLES20.glBufferData(target, (ShortBuffer) data, usage);
        else if (data instanceof FloatBuffer) GLES20.glBufferData(target, (FloatBuffer) data, usage);
        else GLES20.glBufferData(target, (ByteBuffer) data, usage);
    }

    @Override
    public void glBufferSubData(int target, int offset, int size, Buffer data) {
        FloatBuffer f = ((FloatBuffer) data).duplicate();
        f.position(0);
        f.limit(size / 4);
        GLES20.glBufferSubData(target, offset, f);
    }

    @Override public int glCheckFramebufferStatus(int t) { return GLES20.glCheckFramebufferStatus(t); }
    @Override public void glClear(int m) { GLES20.glClear(m); }
    @Override public void glClearColor(float r, float g, float b, float a) { GLES20.glClearColor(r, g, b, a); }
    @Override public void glCompileShader(int s) { GLES20.glCompileShader(s); }
    @Override public int glCreateProgram() { return GLES20.glCreateProgram(); }
    @Override public int glCreateShader(int t) { return GLES20.glCreateShader(t); }
    @Override public void glDeleteBuffers(int n, int[] b, int o) { for (int i = 0; i < n; i++) GLES20.glDeleteBuffers(b[o + i]); }
    @Override public void glDeleteFramebuffers(int n, int[] b, int o) { for (int i = 0; i < n; i++) GLES20.glDeleteFramebuffers(b[o + i]); }
    @Override public void glDeleteProgram(int p) { GLES20.glDeleteProgram(p); }
    @Override public void glDeleteShader(int s) { GLES20.glDeleteShader(s); }
    @Override public void glDeleteTextures(int n, int[] b, int o) { for (int i = 0; i < n; i++) GLES20.glDeleteTextures(b[o + i]); }
    @Override public void glDisable(int c) { GLES20.glDisable(c); }
    @Override public void glDrawElements(int mode, int count, int type, int offset) { GLES20.glDrawElements(mode, count, type, offset); }
    @Override public void glEnable(int c) { GLES20.glEnable(c); }
    @Override public void glEnableVertexAttribArray(int i) { GLES20.glEnableVertexAttribArray(i); }
    @Override public void glFramebufferTexture2D(int t, int a, int tt, int tex, int l) { GLES20.glFramebufferTexture2D(t, a, tt, tex, l); }
    @Override public void glGenBuffers(int n, int[] b, int o) { for (int i = 0; i < n; i++) b[o + i] = GLES20.glGenBuffers(); }
    @Override public void glGenFramebuffers(int n, int[] b, int o) { for (int i = 0; i < n; i++) b[o + i] = GLES20.glGenFramebuffers(); }
    @Override public void glGenTextures(int n, int[] b, int o) { for (int i = 0; i < n; i++) b[o + i] = GLES20.glGenTextures(); }
    @Override public int glGetAttribLocation(int p, String n) { return GLES20.glGetAttribLocation(p, n); }
    @Override public String glGetProgramInfoLog(int p) { return GLES20.glGetProgramInfoLog(p); }
    @Override public void glGetProgramiv(int p, int n, int[] out, int o) { out[o] = GLES20.glGetProgrami(p, n); }
    @Override public String glGetShaderInfoLog(int s) { return GLES20.glGetShaderInfoLog(s); }
    @Override public void glGetShaderiv(int s, int n, int[] out, int o) { out[o] = GLES20.glGetShaderi(s, n); }
    @Override public int glGetUniformLocation(int p, String n) { return GLES20.glGetUniformLocation(p, n); }
    @Override public void glLinkProgram(int p) { GLES20.glLinkProgram(p); }
    @Override public void glReadPixels(int x, int y, int w, int h, int f, int t, Buffer px) { GLES20.glReadPixels(x, y, w, h, f, t, (ByteBuffer) px); }
    @Override public void glShaderSource(int s, String src) { GLES20.glShaderSource(s, src); }

    @Override
    public void glTexImage2D(int target, int level, int internal, int w, int h, int border, int format, int type, Buffer px) {
        GLES20.glTexImage2D(target, level, internal, w, h, border, format, type, (ByteBuffer) px);
    }

    @Override public void glTexParameteri(int t, int p, int v) { GLES20.glTexParameteri(t, p, v); }
    @Override public void glUniform1i(int l, int x) { GLES20.glUniform1i(l, x); }
    @Override public void glUniform2f(int l, float x, float y) { GLES20.glUniform2f(l, x, y); }
    @Override public void glUseProgram(int p) { GLES20.glUseProgram(p); }
    @Override public void glVertexAttribPointer(int i, int s, int t, boolean n, int st, int o) { GLES20.glVertexAttribPointer(i, s, t, n, st, (long) o); }
    @Override public void glViewport(int x, int y, int w, int h) { GLES20.glViewport(x, y, w, h); }

    /** 和 Android 的 GLUtils 一样传预乘透明度的 RGBA。 */
    @Override
    public void texImage2D(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] argb = new int[w * h];
        b.getPixels(argb, 0, w, 0, 0, w, h);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int v : argb) {
            int a = v >>> 24;
            buf.put((byte) (((v >> 16) & 0xFF) * a / 255)).put((byte) (((v >> 8) & 0xFF) * a / 255)).put((byte) ((v & 0xFF) * a / 255)).put((byte) a);
        }
        buf.flip();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
    }
}
