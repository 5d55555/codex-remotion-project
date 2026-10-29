package com.shinjuku.showdown;

import android.graphics.Bitmap;

import java.nio.Buffer;

/** GlTarget 用到的 GLES2 调用。游戏里是 AndroidGl（直接转给 GLES20）；测试里换成桌面上的软件 GLES，好在没有手机的地方验证 GL 画面。 */
interface Gl {
    void glActiveTexture(int texture);

    void glAttachShader(int program, int shader);

    void glBindBuffer(int target, int buffer);

    void glBindFramebuffer(int target, int framebuffer);

    void glBindTexture(int target, int texture);

    void glBlendFunc(int sfactor, int dfactor);

    void glBufferData(int target, int size, Buffer data, int usage);

    void glBufferSubData(int target, int offset, int size, Buffer data);

    int glCheckFramebufferStatus(int target);

    void glClear(int mask);

    void glClearColor(float red, float green, float blue, float alpha);

    void glCompileShader(int shader);

    int glCreateProgram();

    int glCreateShader(int type);

    void glDeleteBuffers(int n, int[] buffers, int offset);

    void glDeleteFramebuffers(int n, int[] framebuffers, int offset);

    void glDeleteProgram(int program);

    void glDeleteShader(int shader);

    void glDeleteTextures(int n, int[] textures, int offset);

    void glDisable(int cap);

    void glDrawElements(int mode, int count, int type, int offset);

    void glEnable(int cap);

    void glEnableVertexAttribArray(int index);

    void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);

    void glGenBuffers(int n, int[] buffers, int offset);

    void glGenFramebuffers(int n, int[] framebuffers, int offset);

    void glGenTextures(int n, int[] textures, int offset);

    int glGetAttribLocation(int program, String name);

    String glGetProgramInfoLog(int program);

    void glGetProgramiv(int program, int pname, int[] params, int offset);

    String glGetShaderInfoLog(int shader);

    void glGetShaderiv(int shader, int pname, int[] params, int offset);

    int glGetUniformLocation(int program, String name);

    void glLinkProgram(int program);

    void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels);

    void glShaderSource(int shader, String string);

    void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels);

    void glTexParameteri(int target, int pname, int param);

    void glUniform1i(int location, int x);

    void glUniform2f(int location, float x, float y);

    void glUseProgram(int program);

    void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int offset);

    void glViewport(int x, int y, int width, int height);

    /** 把位图传成当前绑定的 2D 纹理（第 0 级）。 */
    void texImage2D(Bitmap b);
}
