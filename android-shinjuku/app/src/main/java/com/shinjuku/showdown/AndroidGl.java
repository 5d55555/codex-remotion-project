package com.shinjuku.showdown;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.Buffer;

/** Gl 的 Android 实现：原样转给 GLES20。 */
final class AndroidGl implements Gl {
    @Override
    public void glActiveTexture(int texture) {
        GLES20.glActiveTexture(texture);
    }

    @Override
    public void glAttachShader(int program, int shader) {
        GLES20.glAttachShader(program, shader);
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        GLES20.glBindBuffer(target, buffer);
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        GLES20.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void glBindTexture(int target, int texture) {
        GLES20.glBindTexture(target, texture);
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        GLES20.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void glBufferData(int target, int size, Buffer data, int usage) {
        GLES20.glBufferData(target, size, data, usage);
    }

    @Override
    public void glBufferSubData(int target, int offset, int size, Buffer data) {
        GLES20.glBufferSubData(target, offset, size, data);
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        return GLES20.glCheckFramebufferStatus(target);
    }

    @Override
    public void glClear(int mask) {
        GLES20.glClear(mask);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GLES20.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void glCompileShader(int shader) {
        GLES20.glCompileShader(shader);
    }

    @Override
    public int glCreateProgram() {
        return GLES20.glCreateProgram();
    }

    @Override
    public int glCreateShader(int type) {
        return GLES20.glCreateShader(type);
    }

    @Override
    public void glDeleteBuffers(int n, int[] buffers, int offset) {
        GLES20.glDeleteBuffers(n, buffers, offset);
    }

    @Override
    public void glDeleteFramebuffers(int n, int[] framebuffers, int offset) {
        GLES20.glDeleteFramebuffers(n, framebuffers, offset);
    }

    @Override
    public void glDeleteProgram(int program) {
        GLES20.glDeleteProgram(program);
    }

    @Override
    public void glDeleteShader(int shader) {
        GLES20.glDeleteShader(shader);
    }

    @Override
    public void glDeleteTextures(int n, int[] textures, int offset) {
        GLES20.glDeleteTextures(n, textures, offset);
    }

    @Override
    public void glDisable(int cap) {
        GLES20.glDisable(cap);
    }

    @Override
    public void glDrawElements(int mode, int count, int type, int offset) {
        GLES20.glDrawElements(mode, count, type, offset);
    }

    @Override
    public void glEnable(int cap) {
        GLES20.glEnable(cap);
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        GLES20.glEnableVertexAttribArray(index);
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void glGenBuffers(int n, int[] buffers, int offset) {
        GLES20.glGenBuffers(n, buffers, offset);
    }

    @Override
    public void glGenFramebuffers(int n, int[] framebuffers, int offset) {
        GLES20.glGenFramebuffers(n, framebuffers, offset);
    }

    @Override
    public void glGenTextures(int n, int[] textures, int offset) {
        GLES20.glGenTextures(n, textures, offset);
    }

    @Override
    public int glGetAttribLocation(int program, String name) {
        return GLES20.glGetAttribLocation(program, name);
    }

    @Override
    public String glGetProgramInfoLog(int program) {
        return GLES20.glGetProgramInfoLog(program);
    }

    @Override
    public void glGetProgramiv(int program, int pname, int[] params, int offset) {
        GLES20.glGetProgramiv(program, pname, params, offset);
    }

    @Override
    public String glGetShaderInfoLog(int shader) {
        return GLES20.glGetShaderInfoLog(shader);
    }

    @Override
    public void glGetShaderiv(int shader, int pname, int[] params, int offset) {
        GLES20.glGetShaderiv(shader, pname, params, offset);
    }

    @Override
    public int glGetUniformLocation(int program, String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    @Override
    public void glLinkProgram(int program) {
        GLES20.glLinkProgram(program);
    }

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels) {
        GLES20.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void glShaderSource(int shader, String string) {
        GLES20.glShaderSource(shader, string);
    }

    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels) {
        GLES20.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void glTexParameteri(int target, int pname, int param) {
        GLES20.glTexParameteri(target, pname, param);
    }

    @Override
    public void glUniform1i(int location, int x) {
        GLES20.glUniform1i(location, x);
    }

    @Override
    public void glUniform2f(int location, float x, float y) {
        GLES20.glUniform2f(location, x, y);
    }

    @Override
    public void glUseProgram(int program) {
        GLES20.glUseProgram(program);
    }

    @Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int offset) {
        GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, offset);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        GLES20.glViewport(x, y, width, height);
    }

    @Override
    public void texImage2D(Bitmap b) {
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0);
    }
}
